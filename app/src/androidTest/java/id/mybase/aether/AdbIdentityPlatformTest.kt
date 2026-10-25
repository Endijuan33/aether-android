package id.mybase.aether

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.mybase.aether.adb.AndroidAdbKeyStore
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import javax.crypto.Cipher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual AndroidKeyStore/Conscrypt delegation contract. Requires device execution. */
@RunWith(AndroidJUnit4::class)
class AdbIdentityPlatformTest {
    @Test
    fun productionIdentitySupportsRawTlsSigningWithoutExportOrDecryptionPurpose() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val keys = AndroidAdbKeyStore(context).getOrCreateKeyPair()
        assertNull(keys.private.encoded)
        val info =
            KeyFactory.getInstance("RSA", "AndroidKeyStore")
                .getKeySpec(keys.private, KeyInfo::class.java)
        assertEquals(KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY, info.purposes)
        assertTrue(KeyProperties.DIGEST_NONE in info.digests)
        assertTrue(KeyProperties.ENCRYPTION_PADDING_NONE in info.encryptionPaddings)
        val block = ByteArray(256).apply { this[lastIndex] = 42 }
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keys.private)
        val signature = cipher.doFinal(block)
        val public = keys.public as RSAPublicKey
        assertEquals(
            BigInteger(1, block),
            BigInteger(1, signature).modPow(public.publicExponent, public.modulus),
        )
    }

    @Test
    fun oldDigestAndPaddingPolicyRejectsTheActualTlsDelegateOperation() {
        val alias = "AETHER_TEST_OLD_TLS_POLICY_" + UUID.randomUUID()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
            generator.initialize(
                KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                    )
                    .setKeySize(2048)
                    .setDigests(
                        KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384,
                        KeyProperties.DIGEST_SHA512,
                    )
                    .setSignaturePaddings(
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                        KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                    )
                    .build(),
            )
            val key = generator.generateKeyPair().private
            assertThrows(GeneralSecurityException::class.java) {
                val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                cipher.doFinal(ByteArray(256).apply { this[lastIndex] = 42 })
            }
        } finally {
            // Only the unique test-created identity is deleted; app/V1/V2/V3 keys are preserved.
            store.deleteEntry(alias)
        }
    }
}
