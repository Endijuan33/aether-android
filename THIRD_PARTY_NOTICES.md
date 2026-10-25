# Third-party notices

AETHER uses AndroidX/Jetpack Compose, Kotlin/coroutines and Conscrypt under Apache-2.0. The project Apache license is in LICENSE. Conscrypt includes BoringSSL native code under the terms in app/src/main/assets/licenses/boringssl-LICENSE.txt; Conscrypt’s license is alongside it. These notices are shipped as APK assets.

Upstream sources:

- Conscrypt: https://github.com/google/conscrypt/blob/master/LICENSE
- BoringSSL: https://boringssl.googlesource.com/boringssl/+/refs/heads/main/LICENSE
- AndroidX: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt
- Kotlin/coroutines: https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt

JVM-only test dependencies: JUnit (EPL-1.0), BouncyCastle (MIT), org.json (JSON License) and Turbine (Apache-2.0). They are excluded from release runtime dependencies. Toolchain artifacts and ktfmt are development tools, not APK runtime libraries. See docs/dependencies.md for versions and verification boundaries.

Native source/license provenance must be revisited whenever Conscrypt is upgraded. This notice does not substitute for reviewing every transitive license before distribution.

## Read-only design reference

The native notification pairing workflow was independently implemented after inspecting RikkaApps/Shizuku (Apache-2.0), commit `b844bc491f1790c72328e1a8e5b2349f8978f0ea`, and its official setup guide. No Shizuku source code, assets, native library or branding is incorporated. See `docs/notification-pairing-redesign.md` for source links and engineering differences.
