package id.mybase.aether.packages

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import id.mybase.aether.domain.AppPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real [PackageRepository] backed by Android's [PackageManager]. No demo data: every field comes
 * from the device. Packages that fail to resolve are skipped individually; a [SecurityException] on
 * the bulk query yields an empty list instead of crashing the caller.
 */
class PackageManagerRepository(private val context: Context) : PackageRepository {

    override suspend fun getInstalledPackages(): List<AppPackage> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed: List<PackageInfo> =
                try {
                    @Suppress("DEPRECATION") pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                } catch (e: SecurityException) {
                    return@withContext emptyList()
                } catch (e: RuntimeException) {
                    return@withContext emptyList()
                }
            installed.mapNotNull { info ->
                try {
                    info.toAppPackage(pm)
                } catch (e: Exception) {
                    // One unreadable package must not poison the whole inventory.
                    null
                }
            }
        }

    private fun PackageInfo.toAppPackage(pm: PackageManager): AppPackage {
        val ai: ApplicationInfo =
            applicationInfo ?: throw IllegalStateException("no ApplicationInfo for $packageName")
        val flags = ai.flags
        return AppPackage(
            packageName = packageName,
            label = pm.getApplicationLabel(ai)?.toString()?.ifBlank { packageName } ?: packageName,
            versionName = versionName,
            versionCode = longVersionCodeCompat(),
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

    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION") versionCode.toLong()
        }

    private fun installerOf(pm: PackageManager, packageName: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION") pm.getInstallerPackageName(packageName)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }
}
