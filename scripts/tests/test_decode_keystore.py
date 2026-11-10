import base64
import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from decode_keystore import KeystoreError, decode, keystore_type  # noqa: E402

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "decode_keystore.py")

# Fake keystores: only the header matters for format detection. Bytes chosen so that
# the base64 contains "+" and "/" (to exercise URL-safe handling) and needs padding.
PKCS12 = b"\x30\x82\x09\xf4\x02\x01\x03" + bytes(range(256)) * 3 + b"\xfb\xff\xfe\xfd"
JKS = b"\xfe\xed\xfe\xed\x00\x00\x00\x02" + bytes(range(200))


def b64(data: bytes) -> str:
    return base64.b64encode(data).decode()


class DecodeTest(unittest.TestCase):
    def assertDecodes(self, text: str, expected: bytes = PKCS12):
        data, _ = decode(text)
        self.assertEqual(data, expected)

    def test_clean_input(self):
        self.assertDecodes(b64(PKCS12))
        self.assertDecodes(b64(JKS), JKS)

    def test_line_breaks_and_spaces(self):
        text = b64(PKCS12)
        wrapped = "\n".join(text[i:i + 76] for i in range(0, len(text), 76))
        self.assertDecodes(wrapped)
        self.assertDecodes("  " + wrapped.replace("\n", "\r\n ") + "\n\n")
        self.assertDecodes(wrapped.replace("\n", "\\n"))  # literal \n from JSON

    def test_quotes(self):
        self.assertDecodes('"' + b64(PKCS12) + '"')
        self.assertDecodes("'" + b64(PKCS12) + "'\n")

    def test_begin_end_lines(self):
        self.assertDecodes("-----BEGIN KEYSTORE-----\n" + b64(PKCS12) + "\n-----END KEYSTORE-----\n")

    def test_url_safe(self):
        text = base64.urlsafe_b64encode(PKCS12).decode()
        self.assertTrue("-" in text or "_" in text)
        self.assertDecodes(text)

    def test_missing_padding(self):
        text = b64(PKCS12)
        self.assertTrue(text.endswith("="))
        self.assertDecodes(text.rstrip("="))

    def test_double_encoded(self):
        self.assertDecodes(base64.b64encode(b64(PKCS12).encode()).decode())

    def test_double_encoded_with_line_breaks(self):
        # e.g. `base64 file | base64` on Linux, which wraps both layers.
        inner = base64.encodebytes(PKCS12)
        self.assertDecodes(base64.encodebytes(inner).decode())

    def test_empty(self):
        for text in ["", "   \n", '""']:
            with self.subTest(text=text), self.assertRaises(KeystoreError):
                decode(text)

    def test_invalid_characters(self):
        with self.assertRaisesRegex(KeystoreError, "not base64"):
            decode(b64(PKCS12)[:40] + "!*" + b64(PKCS12)[40:])

    def test_truncated(self):
        with self.assertRaisesRegex(KeystoreError, "cut off"):
            decode(b64(PKCS12)[:401])

    def test_wrong_file(self):
        with self.assertRaisesRegex(KeystoreError, "keystore.properties"):
            decode(b64(b"storePassword=x\nkeyAlias=upload\n"))
        with self.assertRaisesRegex(KeystoreError, "PEM"):
            decode(b64(b"-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n"))
        with self.assertRaisesRegex(KeystoreError, "not a Java keystore"):
            decode(b64(b"\x89PNG\r\n\x1a\n" + bytes(100)))

    def test_detects_types(self):
        self.assertEqual(keystore_type(PKCS12), "PKCS12")
        self.assertEqual(keystore_type(JKS), "JKS")
        self.assertIsNone(keystore_type(b"hello world"))


class CommandLineTest(unittest.TestCase):
    SECRET_MARKER = "U0VDUkVU"  # must never appear in output

    def run_script(self, value: str):
        with tempfile.TemporaryDirectory() as directory:
            output = os.path.join(directory, "release.jks")
            env = dict(os.environ, ANDROID_KEYSTORE_BASE64=value)
            env.pop("GITHUB_ACTIONS", None)
            result = subprocess.run(
                [sys.executable, SCRIPT, output], env=env, capture_output=True, text=True, check=False
            )
            written = open(output, "rb").read() if os.path.exists(output) else None
            return result, written

    def test_writes_keystore(self):
        result, written = self.run_script(b64(PKCS12))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(written, PKCS12)

    def test_never_prints_the_secret(self):
        secret = b64(PKCS12)
        for value in [secret, secret + "!" + self.SECRET_MARKER, self.SECRET_MARKER * 3]:
            result, _ = self.run_script(value)
            output = result.stdout + result.stderr
            self.assertNotIn(secret[:20], output)
            self.assertNotIn(self.SECRET_MARKER, output)

    def test_fails_without_writing_on_bad_input(self):
        result, written = self.run_script("not a keystore")
        self.assertEqual(result.returncode, 1)
        self.assertIsNone(written)


if __name__ == "__main__":
    unittest.main()
