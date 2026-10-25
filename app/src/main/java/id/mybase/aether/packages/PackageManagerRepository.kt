package id.mybase.aether.packages

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import id.mybase.aether.domain.AppPackage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Real [PackageRepository] backed by Android's [PackageManager]. No demo data: every field comes
 * from the device. Individual resolution failures are retained as partial-inventory warnings;
 * bulk-query failures propagate to the caller's error state.
 */
class PackageManagerRepository(private val context: Context) : PackageRepository {

    override suspend fun getInstalledPackages(): List<AppPackage> = getInventory().apps

    override suspend fun getInventory(): PackageInventory =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val installed = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val warnings = mutableListOf<String>()
            val apps = installed.mapNotNull { info ->
                currentCoroutineContext().ensureActive()
                try {
                    info.toAppPackage(pm)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One unreadable package must not poison the whole inventory.
                    warnings.add(info.packageName)
                    null
                }
            }
            PackageInventory(apps, warnings)
        }

    private fun PackageInfo.toAppPackage(pm: PackageManager): AppPackage {
        val ai: ApplicationInfo =
            applicationInfo ?: throw IllegalStateException("no ApplicationInfo for $packageName")
        val flags = ai.flags
        return AppPackage(
            packageName = packageName,
            label = pm.getApplicationLabel(ai).toString().ifBlank { packageName },
            versionName = versionName,
            versionCode = longVersionCode,
            isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            isEnabled = ai.enabled,
            isUpdatedSystemApp = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            installerPackageName = installerOf(pm, packageName),
            targetSdk = ai.targetSdkVersion,
            permissions = requestedPermissions?.toList().orEmpty(),
            firstInstallTime = firstInstallTime,
            lastUpdateTime = lastUpdateTime,
        )
    }

    private fun installerOf(pm: PackageManager, packageName: String): String? {
        return try {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }
}
