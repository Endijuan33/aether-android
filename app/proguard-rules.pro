# AETHER — System Control · ProGuard / R8 rules.
# The ADB pairing crypto uses reflection-free code paths; keep rules minimal.
-keepattributes Signature, InnerClasses, EnclosingMethod
# DataStore + kotlinx.serialization-free JSON: no extra keeps required.
# Keep the pairing protocol doc markers out of obfuscation noise:
-keep class id.mybase.aether.adb.PairingCrypto { *; }
