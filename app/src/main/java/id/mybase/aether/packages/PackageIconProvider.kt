package id.mybase.aether.packages

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Process-scoped, application-context owner; never retains an Activity or an icon bitmap copy. */
class PackageIconProvider(context: Context) : ComponentCallbacks2 {
    private val context = context.applicationContext
    private val cache = IconCache<String, Drawable.ConstantState>(64)
    private val _generation = MutableStateFlow(0L)
    val generation = _generation.asStateFlow()
    private val changes =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                invalidate()
            }
        }

    init {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        ContextCompat.registerReceiver(
            this.context,
            changes,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        this.context.registerComponentCallbacks(this)
    }

    suspend fun load(packageName: String): Drawable? =
        withContext(Dispatchers.IO) {
            val state =
                cache.get(packageName) {
                    try {
                        context.packageManager.getApplicationIcon(packageName).constantState
                    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                        null
                    } catch (_: SecurityException) {
                        null
                    } catch (_: RuntimeException) {
                        null // Broken OEM resources must not break the inventory.
                    }
                }
            try {
                state?.newDrawable(context.resources)?.mutate()
            } catch (_: RuntimeException) {
                null
            }
        }

    fun invalidate() {
        cache.clear()
        _generation.update { it + 1 }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = invalidate()

    // Still delivered on supported older Android releases.
    @Suppress("OVERRIDE_DEPRECATION") override fun onLowMemory() = invalidate()

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) cache.clear()
    }
}
