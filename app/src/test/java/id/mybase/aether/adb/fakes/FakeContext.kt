package id.mybase.aether.adb.fakes

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.view.Display
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Minimal [Context] for JVM unit tests.
 *
 * Only [getFilesDir], [getApplicationContext], and [getSystemService] are functional (the surface
 * the app touches in unit tests). Every other abstract [Context] member throws
 * [NotImplementedError] via TODO() — the stubs exist solely so the class compiles against the real
 * android.jar and must never be called by a passing test.
 */
class FakeContext(
    private val dir: File = File(System.getProperty("java.io.tmpdir")!!, "aether-test"),
) : Context() {
    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = dir.also { it.mkdirs() }

    override fun getSystemService(name: String): Any? = null

    override fun bindService(p0: Intent, p1: ServiceConnection, p2: Int): Boolean = false

    override fun checkCallingOrSelfPermission(p0: String): Int = 0

    override fun checkCallingOrSelfUriPermission(p0: Uri, p1: Int): Int = 0

    override fun checkCallingPermission(p0: String): Int = 0

    override fun checkCallingUriPermission(p0: Uri, p1: Int): Int = 0

    override fun checkPermission(p0: String, p1: Int, p2: Int): Int = 0

    override fun checkSelfPermission(p0: String): Int = 0

    override fun checkUriPermission(p0: Uri, p1: Int, p2: Int, p3: Int): Int = 0

    override fun checkUriPermission(
        p0: Uri?,
        p1: String?,
        p2: String?,
        p3: Int,
        p4: Int,
        p5: Int,
    ): Int = 0

    override fun clearWallpaper(): Unit {}

    override fun createConfigurationContext(p0: Configuration): Context = TODO("FakeContext stub")

    override fun createContextForSplit(p0: String): Context = TODO("FakeContext stub")

    override fun createDeviceProtectedStorageContext(): Context = TODO("FakeContext stub")

    override fun createDisplayContext(p0: Display): Context = TODO("FakeContext stub")

    override fun createPackageContext(p0: String, p1: Int): Context = TODO("FakeContext stub")

    override fun databaseList(): Array<String> = emptyArray()

    override fun deleteDatabase(p0: String): Boolean = false

    override fun deleteFile(p0: String): Boolean = false

    override fun deleteSharedPreferences(p0: String): Boolean = false

    override fun enforceCallingOrSelfPermission(p0: String, p1: String?): Unit {}

    override fun enforceCallingOrSelfUriPermission(p0: Uri, p1: Int, p2: String): Unit {}

    override fun enforceCallingPermission(p0: String, p1: String?): Unit {}

    override fun enforceCallingUriPermission(p0: Uri, p1: Int, p2: String): Unit {}

    override fun enforcePermission(p0: String, p1: Int, p2: Int, p3: String?): Unit {}

    override fun enforceUriPermission(p0: Uri, p1: Int, p2: Int, p3: Int, p4: String): Unit {}

    override fun enforceUriPermission(
        p0: Uri?,
        p1: String?,
        p2: String?,
        p3: Int,
        p4: Int,
        p5: Int,
        p6: String?,
    ): Unit {}

    override fun fileList(): Array<String> = emptyArray()

    override fun getApplicationInfo(): ApplicationInfo = TODO("FakeContext stub")

    override fun getAssets(): AssetManager = TODO("FakeContext stub")

    override fun getCacheDir(): File = TODO("FakeContext stub")

    override fun getClassLoader(): ClassLoader = TODO("FakeContext stub")

    override fun getCodeCacheDir(): File = TODO("FakeContext stub")

    override fun getContentResolver(): ContentResolver = TODO("FakeContext stub")

    override fun getDataDir(): File = TODO("FakeContext stub")

    override fun getDatabasePath(p0: String): File = TODO("FakeContext stub")

    override fun getDir(p0: String, p1: Int): File = TODO("FakeContext stub")

    override fun getExternalCacheDir(): File? = null

    override fun getExternalCacheDirs(): Array<File> = emptyArray()

    override fun getExternalFilesDir(p0: String?): File? = null

    override fun getExternalFilesDirs(p0: String): Array<File> = emptyArray()

    override fun getExternalMediaDirs(): Array<File> = emptyArray()

    override fun getFileStreamPath(p0: String): File = TODO("FakeContext stub")

    override fun getMainLooper(): Looper = TODO("FakeContext stub")

    override fun getNoBackupFilesDir(): File = TODO("FakeContext stub")

    override fun getObbDir(): File = TODO("FakeContext stub")

    override fun getObbDirs(): Array<File> = emptyArray()

    override fun getPackageCodePath(): String = ""

    override fun getPackageManager(): PackageManager = TODO("FakeContext stub")

    override fun getPackageName(): String = ""

    override fun getPackageResourcePath(): String = ""

    override fun getResources(): Resources = TODO("FakeContext stub")

    override fun getSharedPreferences(p0: String, p1: Int): SharedPreferences =
        TODO("FakeContext stub")

    override fun getSystemServiceName(p0: java.lang.Class<*>): String? = null

    override fun getTheme(): Resources.Theme = TODO("FakeContext stub")

    override fun getWallpaper(): Drawable = TODO("FakeContext stub")

    override fun getWallpaperDesiredMinimumHeight(): Int = 0

    override fun getWallpaperDesiredMinimumWidth(): Int = 0

    override fun grantUriPermission(p0: String, p1: Uri, p2: Int): Unit {}

    override fun isDeviceProtectedStorage(): Boolean = false

    override fun moveDatabaseFrom(p0: Context, p1: String): Boolean = false

    override fun moveSharedPreferencesFrom(p0: Context, p1: String): Boolean = false

    override fun openFileInput(p0: String): FileInputStream = TODO("FakeContext stub")

    override fun openFileOutput(p0: String, p1: Int): FileOutputStream = TODO("FakeContext stub")

    override fun openOrCreateDatabase(
        p0: String,
        p1: Int,
        p2: SQLiteDatabase.CursorFactory,
    ): SQLiteDatabase = TODO("FakeContext stub")

    override fun openOrCreateDatabase(
        p0: String,
        p1: Int,
        p2: SQLiteDatabase.CursorFactory,
        p3: DatabaseErrorHandler?,
    ): SQLiteDatabase = TODO("FakeContext stub")

    override fun peekWallpaper(): Drawable = TODO("FakeContext stub")

    override fun registerReceiver(
        p0: BroadcastReceiver,
        p1: IntentFilter,
        p2: String?,
        p3: Handler?,
    ): Intent? = null

    override fun registerReceiver(
        p0: BroadcastReceiver,
        p1: IntentFilter,
        p2: String?,
        p3: Handler?,
        p4: Int,
    ): Intent? = null

    override fun registerReceiver(p0: BroadcastReceiver?, p1: IntentFilter): Intent? = null

    override fun registerReceiver(p0: BroadcastReceiver?, p1: IntentFilter, p2: Int): Intent? = null

    override fun removeStickyBroadcast(p0: Intent): Unit {}

    override fun removeStickyBroadcastAsUser(p0: Intent, p1: UserHandle): Unit {}

    override fun revokeUriPermission(p0: String, p1: Uri, p2: Int): Unit {}

    override fun revokeUriPermission(p0: Uri, p1: Int): Unit {}

    override fun sendBroadcast(p0: Intent): Unit {}

    override fun sendBroadcast(p0: Intent, p1: String?): Unit {}

    override fun sendBroadcastAsUser(p0: Intent, p1: UserHandle): Unit {}

    override fun sendBroadcastAsUser(p0: Intent, p1: UserHandle, p2: String?): Unit {}

    override fun sendOrderedBroadcast(p0: Intent, p1: String?): Unit {}

    override fun sendOrderedBroadcast(
        p0: Intent,
        p1: String?,
        p2: BroadcastReceiver?,
        p3: Handler?,
        p4: Int,
        p5: String?,
        p6: Bundle?,
    ): Unit {}

    override fun sendOrderedBroadcastAsUser(
        p0: Intent,
        p1: UserHandle,
        p2: String?,
        p3: BroadcastReceiver,
        p4: Handler?,
        p5: Int,
        p6: String?,
        p7: Bundle?,
    ): Unit {}

    override fun sendStickyBroadcast(p0: Intent): Unit {}

    override fun sendStickyBroadcastAsUser(p0: Intent, p1: UserHandle): Unit {}

    override fun sendStickyOrderedBroadcast(
        p0: Intent,
        p1: BroadcastReceiver,
        p2: Handler?,
        p3: Int,
        p4: String?,
        p5: Bundle?,
    ): Unit {}

    override fun sendStickyOrderedBroadcastAsUser(
        p0: Intent,
        p1: UserHandle,
        p2: BroadcastReceiver,
        p3: Handler?,
        p4: Int,
        p5: String?,
        p6: Bundle?,
    ): Unit {}

    override fun setTheme(p0: Int): Unit {}

    override fun setWallpaper(p0: Bitmap): Unit {}

    override fun setWallpaper(p0: InputStream): Unit {}

    override fun startActivities(p0: Array<Intent>): Unit {}

    override fun startActivities(p0: Array<Intent>, p1: Bundle): Unit {}

    override fun startActivity(p0: Intent): Unit {}

    override fun startActivity(p0: Intent, p1: Bundle?): Unit {}

    override fun startForegroundService(p0: Intent): ComponentName? = null

    override fun startInstrumentation(p0: ComponentName, p1: String?, p2: Bundle?): Boolean = false

    override fun startIntentSender(
        p0: IntentSender,
        p1: Intent?,
        p2: Int,
        p3: Int,
        p4: Int,
    ): Unit {}

    override fun startIntentSender(
        p0: IntentSender,
        p1: Intent?,
        p2: Int,
        p3: Int,
        p4: Int,
        p5: Bundle?,
    ): Unit {}

    override fun startService(p0: Intent): ComponentName? = null

    override fun stopService(p0: Intent): Boolean = false

    override fun unbindService(p0: ServiceConnection): Unit {}

    override fun unregisterReceiver(p0: BroadcastReceiver): Unit {}
}
