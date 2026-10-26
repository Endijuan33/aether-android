package id.mybase.aether

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.mybase.aether.di.AppContainer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageIconPlatformTest {
    @Test
    fun loadsUserAndSystemIconsAndFallsBackForMissingPackage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = AppContainer.get(context).icons
        assertNotNull(provider.load(context.packageName))
        assertNotNull(provider.load("android"))
        assertNull(provider.load("id.mybase.aether.nonexistent"))
        val before = provider.generation.value
        provider.invalidate()
        assertTrue(provider.generation.value > before)
        assertNotNull(provider.load(context.packageName))
    }
}
