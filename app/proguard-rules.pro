# AETHER — System Control · ProGuard / R8 rules.
# The ADB pairing crypto uses reflection-free code paths; keep rules minimal.
-keepattributes Signature, InnerClasses, EnclosingMethod
# DataStore + kotlinx.serialization-free JSON: no extra keeps required.
# Keep the pairing protocol doc markers out of obfuscation noise:
-keep class id.mybase.aether.adb.PairingCrypto { *; }
# Conscrypt 2.5.2 Platform.wrapSocketFactoryIfNeeded selects these adapters only
# below API 22. AETHER's minSdk is 30; their hidden platform types cannot run.
# Do not broaden these exceptions to other missing classes.
-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
