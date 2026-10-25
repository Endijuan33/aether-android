#!/usr/bin/env python3
"""Execute the production release-summary shell script with controlled fixtures."""
import os
import pathlib
import subprocess
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name("release_summary.sh").resolve()


class ReleaseSummaryTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="aether-release-summary-")
        self.addCleanup(temporary.cleanup)
        self.root = pathlib.Path(temporary.name)
        (self.root / "dist-apk").mkdir()
        (self.root / "dist-sums").mkdir()
        (self.root / "dist-apk" / "app.apk").write_bytes(b"summary fixture")
        self.digest = "a" * 64
        (self.root / "dist-sums" / "checksums.txt").write_text(
            self.digest + "  app.apk\n", encoding="utf-8"
        )

    def render(self, *, build="success", signed="false", ref="v0.1.0"):
        output = self.root / "summary.md"
        output.write_text("", encoding="utf-8")
        environment = dict(
            os.environ,
            GITHUB_STEP_SUMMARY=str(output),
            RELEASE_REF_NAME=ref,
            RELEASE_EVENT_NAME="push",
            RELEASE_VERIFY_RESULT="success",
            RELEASE_BUILD_RESULT=build,
            RELEASE_SIGNED=signed,
        )
        subprocess.run(
            ["bash", str(SCRIPT)], cwd=self.root, env=environment,
            check=True, capture_output=True, text=True, timeout=10,
        )
        return output.read_text(encoding="utf-8")

    def test_tag_name_remains_data(self):
        for ref in ("v$(touch${IFS}injected)", "v`touch${IFS}injected`"):
            with self.subTest(ref=ref):
                result = self.render(ref=ref)
                self.assertIn(ref, result)
                self.assertFalse((self.root / "injected").exists())

    def test_successful_signed_build_reports_artifact(self):
        result = self.render(signed="true")
        self.assertIn("- Signing: signed with apksigner", result)
        self.assertIn("- APK: `app.apk`", result)
        self.assertIn("- SHA-256: `" + self.digest + "`", result)

    def test_successful_unsigned_build_is_explicit(self):
        result = self.render()
        self.assertIn("- Signing: unsigned", result)

    def test_failed_build_never_claims_signing_success(self):
        result = self.render(build="failure", signed="true")
        self.assertIn("- Signing: unverified", result)
        self.assertNotIn("- Signing: signed with apksigner", result)


if __name__ == "__main__":
    unittest.main()
