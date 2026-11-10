#!/usr/bin/env python3
"""Version helpers for the release workflow.

Commands (all print a single line to stdout, or an error to stderr with exit code 1):

  parse-tag vX.Y.Z           -> X.Y.Z (fails if the tag is not exactly vX.Y.Z)
  next <latest-tag|""> BUMP  -> next X.Y.Z for BUMP in patch|minor|major
  code X.Y.Z                 -> Android versionCode = major*10000 + minor*100 + patch
  is-newer X.Y.Z A.B.C       -> exits 0 if X.Y.Z > A.B.C, otherwise 1

Kept free of GitHub/Gradle specifics so it is unit tested (scripts/tests/).
"""

from __future__ import annotations

import re
import sys

_VERSION = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
_TAG = re.compile(r"^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")

# Google Play's maximum versionCode.
MAX_VERSION_CODE = 2_100_000_000


class VersionError(ValueError):
    pass


def parse_version(text: str) -> tuple[int, int, int]:
    match = _VERSION.match(text.strip())
    if not match:
        raise VersionError(f"'{text}' is not a version like 1.2.3")
    major, minor, patch = (int(part) for part in match.groups())
    if minor > 99 or patch > 99:
        raise VersionError(f"'{text}': minor and patch must be at most 99 (versionCode uses two digits each)")
    return major, minor, patch


def parse_tag(tag: str) -> str:
    if not _TAG.match(tag.strip()):
        raise VersionError(f"tag '{tag}' must look like v1.2.3")
    version = tag.strip()[1:]
    parse_version(version)
    return version


def next_version(latest_tag: str, bump: str) -> str:
    major, minor, patch = parse_version(parse_tag(latest_tag)) if latest_tag.strip() else (0, 0, 0)
    if bump == "patch":
        patch += 1
    elif bump == "minor":
        minor, patch = minor + 1, 0
    elif bump == "major":
        major, minor, patch = major + 1, 0, 0
    else:
        raise VersionError(f"bump must be patch, minor or major, got '{bump}'")
    if patch > 99:
        raise VersionError("patch would exceed 99; release a minor version instead")
    if minor > 99:
        raise VersionError("minor would exceed 99; release a major version instead")
    return f"{major}.{minor}.{patch}"


def version_code(version: str) -> int:
    major, minor, patch = parse_version(version)
    code = major * 10000 + minor * 100 + patch
    if code <= 0:
        raise VersionError("version 0.0.0 cannot be released (versionCode must be positive)")
    if code > MAX_VERSION_CODE:
        raise VersionError(f"versionCode {code} is larger than Google Play allows")
    return code


def is_newer(version: str, than: str) -> bool:
    return parse_version(version) > parse_version(than)


def main(argv: list[str]) -> int:
    try:
        if len(argv) == 2 and argv[0] == "parse-tag":
            print(parse_tag(argv[1]))
        elif len(argv) == 3 and argv[0] == "next":
            print(next_version(argv[1], argv[2]))
        elif len(argv) == 2 and argv[0] == "code":
            print(version_code(argv[1]))
        elif len(argv) == 3 and argv[0] == "is-newer":
            return 0 if is_newer(argv[1], argv[2]) else 1
        else:
            print(__doc__, file=sys.stderr)
            return 2
    except VersionError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
