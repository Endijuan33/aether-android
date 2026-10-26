package id.mybase.aether.device

import android.os.Build
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidDeviceInfoProvider {
    suspend fun detect(): DeviceProfile =
        withContext(Dispatchers.IO) {
            val build =
                DeviceBuild(
                    Build.MANUFACTURER,
                    Build.BRAND,
                    Build.MODEL,
                    Build.DEVICE,
                    Build.PRODUCT,
                    Build.FINGERPRINT,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT,
                    Build.ID,
                    Build.DISPLAY,
                    Build.VERSION.SECURITY_PATCH,
                )
            val keys =
                listOf(
                    "ro.mi.os.version.name",
                    "ro.miui.ui.version.name",
                    "ro.build.version.oneui",
                    "ro.build.version.oplusrom",
                    "ro.build.version.opporom",
                    "ro.oxygen.version",
                    "ro.vivo.os.name",
                    "ro.vivo.os.version",
                    "ro.build.version.realmeui",
                )
            DeviceProfileDetector.detect(
                build,
                keys.mapNotNull { key -> readProperty(key)?.let { key to it } }.toMap(),
            )
        }

    /**
     * Fixed public command and allowlisted keys; no reflection, shell interpolation, root or
     * writes.
     */
    private fun readProperty(key: String): String? =
        try {
            val process = ProcessBuilder("/system/bin/getprop", key).start()
            try {
                if (!process.waitFor(200, TimeUnit.MILLISECONDS) || process.exitValue() != 0) null
                else
                    process.inputStream.bufferedReader().use { reader ->
                        val chars = CharArray(128)
                        val length = reader.read(chars)
                        if (length > 0) String(chars, 0, length).trim().takeIf { it.isNotEmpty() }
                        else null
                    }
            } finally {
                process.destroy()
            }
        } catch (_: Exception) {
            null
        }
}
