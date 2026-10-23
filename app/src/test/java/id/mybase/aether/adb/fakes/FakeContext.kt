package id.mybase.aether.adb.fakes

import android.content.Context
import java.io.File

/** Minimal [Context] for JVM tests: only filesDir/system-service surface. */
class FakeContext(filesDir: File) : Context() {
    override val applicationContext: Context = this
    override val filesDir: File = filesDir

    override fun getSystemService(name: String): Any? = null
}
