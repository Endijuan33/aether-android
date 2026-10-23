package id.mybase.aether.adb.fakes

import android.content.Context
import android.test.mock.MockContext
import java.io.File

/**
 * Minimal [Context] for JVM unit tests, backed by [MockContext] so the ~100 abstract [Context]
 * members don't all need hand-written stubs.
 *
 * Only the surface the app actually touches in unit tests is functional: [getFilesDir],
 * [getApplicationContext], and [getSystemService]. Everything else throws (MockContext behavior).
 */
class FakeContext(
    private val dir: File = File(System.getProperty("java.io.tmpdir")!!, "aether-test"),
) : MockContext() {
    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = dir.also { it.mkdirs() }

    override fun getSystemService(name: String): Any? = null
}
