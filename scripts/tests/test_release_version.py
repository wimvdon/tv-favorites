import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from release_version import VersionError, is_newer, next_version, parse_tag, version_code  # noqa: E402


class ParseTagTest(unittest.TestCase):
    def test_valid_tag(self):
        self.assertEqual(parse_tag("v1.2.3"), "1.2.3")

    def test_rejects_bad_tags(self):
        for tag in ["1.2.3", "v1.2", "v1.2.3-beta", "v01.2.3", "release", ""]:
            with self.subTest(tag=tag), self.assertRaises(VersionError):
                parse_tag(tag)


class NextVersionTest(unittest.TestCase):
    def test_first_release(self):
        self.assertEqual(next_version("", "patch"), "0.0.1")
        self.assertEqual(next_version("", "minor"), "0.1.0")
        self.assertEqual(next_version("", "major"), "1.0.0")

    def test_bumps(self):
        self.assertEqual(next_version("v1.2.3", "patch"), "1.2.4")
        self.assertEqual(next_version("v1.2.3", "minor"), "1.3.0")
        self.assertEqual(next_version("v1.2.3", "major"), "2.0.0")

    def test_rejects_unknown_bump(self):
        with self.assertRaises(VersionError):
            next_version("v1.0.0", "none")

    def test_two_digit_limits(self):
        with self.assertRaises(VersionError):
            next_version("v1.2.99", "patch")
        with self.assertRaises(VersionError):
            next_version("v1.99.0", "minor")
        self.assertEqual(next_version("v1.2.99", "minor"), "1.3.0")


class VersionCodeTest(unittest.TestCase):
    def test_formula(self):
        self.assertEqual(version_code("1.2.3"), 10203)
        self.assertEqual(version_code("0.1.0"), 100)
        self.assertEqual(version_code("12.34.56"), 123456)

    def test_rejects_zero_and_out_of_range(self):
        for version in ["0.0.0", "1.100.0", "1.0.100", "abc"]:
            with self.subTest(version=version), self.assertRaises(VersionError):
                version_code(version)

    def test_codes_always_increase(self):
        self.assertLess(version_code("1.99.99"), version_code("2.0.0"))
        self.assertLess(version_code("1.2.99"), version_code("1.3.0"))


class IsNewerTest(unittest.TestCase):
    def test_compares_numerically(self):
        self.assertTrue(is_newer("1.10.0", "1.9.0"))
        self.assertFalse(is_newer("1.2.3", "1.2.3"))
        self.assertFalse(is_newer("1.2.3", "1.3.0"))


if __name__ == "__main__":
    unittest.main()
