package id.mybase.aether.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {

    private fun app(
        isSystem: Boolean = false,
        isEnabled: Boolean = true,
        isUpdatedSystemApp: Boolean = false,
    ) =
        AppPackage(
            packageName = "com.example.app",
            label = "Example",
            versionName = "1.0",
            versionCode = 1L,
            isSystem = isSystem,
            isEnabled = isEnabled,
            isUpdatedSystemApp = isUpdatedSystemApp,
            installerPackageName = null,
            targetSdk = 34,
            permissions = emptyList(),
            firstInstallTime = 0L,
            lastUpdateTime = 0L,
        )

    // -- kind() -----------------------------------------------------------------

    @Test
    fun `kind is USER for plain user apps`() {
        assertEquals(AppKind.USER, app().kind())
    }

    @Test
    fun `kind is SYSTEM for system apps`() {
        assertEquals(AppKind.SYSTEM, app(isSystem = true).kind())
    }

    @Test
    fun `kind is UPDATED_SYSTEM for updated system apps`() {
        assertEquals(AppKind.UPDATED_SYSTEM, app(isSystem = true, isUpdatedSystemApp = true).kind())
    }

    @Test
    fun `kind is DISABLED when not enabled regardless of flags`() {
        assertEquals(AppKind.DISABLED, app(isEnabled = false).kind())
        assertEquals(AppKind.DISABLED, app(isSystem = true, isEnabled = false).kind())
        assertEquals(
            AppKind.DISABLED,
            app(isSystem = true, isUpdatedSystemApp = true, isEnabled = false).kind(),
        )
    }

    // -- OperationType ------------------------------------------------------------

    @Test
    fun `label is non-blank for every operation type`() {
        OperationType.entries.forEach {
            assertTrue(it.label().isNotBlank())
        }
    }

    @Test
    fun `isReversible matrix`() {
        assertFalse(OperationType.UNINSTALL_FOR_USER.isReversible())
        assertTrue(OperationType.DISABLE.isReversible())
        assertTrue(OperationType.ENABLE.isReversible())
    }

    // -- riskOf -------------------------------------------------------------------

    @Test
    fun `ESSENTIAL always yields CRITICAL`() {
        for (type in OperationType.entries) {
            for (kind in AppKind.entries) {
                assertEquals(
                    "type=$type kind=$kind",
                    RiskLevel.CRITICAL,
                    riskOf(type, SafetyRating.ESSENTIAL, kind),
                )
            }
        }
    }

    @Test
    fun `uninstalling a system app yields HIGH`() {
        assertEquals(
            RiskLevel.HIGH,
            riskOf(OperationType.UNINSTALL_FOR_USER, SafetyRating.SAFE, AppKind.SYSTEM),
        )
    }

    @Test
    fun `uninstalling an updated system app is MEDIUM not HIGH`() {
        // UPDATED_SYSTEM is a distinct kind from SYSTEM: uninstall --user 0 only
        // reverts to the factory version, so it falls through to the plain
        // UNINSTALL_FOR_USER -> MEDIUM rule.
        assertEquals(
            RiskLevel.MEDIUM,
            riskOf(OperationType.UNINSTALL_FOR_USER, SafetyRating.UNKNOWN, AppKind.UPDATED_SYSTEM),
        )
    }

    @Test
    fun `RISKY rating yields HIGH for any operation`() {
        for (type in OperationType.entries) {
            assertEquals(
                "type=$type",
                RiskLevel.HIGH,
                riskOf(type, SafetyRating.RISKY, AppKind.USER),
            )
        }
    }

    @Test
    fun `UNKNOWN system app yields MEDIUM`() {
        assertEquals(
            RiskLevel.MEDIUM,
            riskOf(OperationType.DISABLE, SafetyRating.UNKNOWN, AppKind.SYSTEM),
        )
        assertEquals(
            RiskLevel.MEDIUM,
            riskOf(OperationType.ENABLE, SafetyRating.UNKNOWN, AppKind.SYSTEM),
        )
    }

    @Test
    fun `plain user uninstall yields MEDIUM`() {
        assertEquals(
            RiskLevel.MEDIUM,
            riskOf(OperationType.UNINSTALL_FOR_USER, SafetyRating.SAFE, AppKind.USER),
        )
    }

    @Test
    fun `benign operations yield LOW`() {
        assertEquals(RiskLevel.LOW, riskOf(OperationType.DISABLE, SafetyRating.SAFE, AppKind.USER))
        assertEquals(RiskLevel.LOW, riskOf(OperationType.ENABLE, SafetyRating.SAFE, AppKind.USER))
        assertEquals(
            RiskLevel.LOW,
            riskOf(OperationType.DISABLE, SafetyRating.SAFE, AppKind.SYSTEM),
        )
        assertEquals(
            RiskLevel.LOW,
            riskOf(OperationType.ENABLE, SafetyRating.UNKNOWN, AppKind.USER),
        )
    }

    // -- OperationRecord ------------------------------------------------------------

    @Test
    fun `OperationRecord defaults id to 0`() {
        val r =
            OperationRecord(
                timestamp = 1L,
                packageName = "p",
                label = "l",
                operation = "o",
                status = "s",
                message = "m",
            )
        assertEquals(0L, r.id)
    }
}
