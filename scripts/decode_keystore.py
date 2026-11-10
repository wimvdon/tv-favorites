#!/usr/bin/env python3
"""Decode the ANDROID_KEYSTORE_BASE64 secret into a keystore file.

Usage: decode_keystore.py OUTPUT_FILE [ENV_VAR_NAME]   (default ENV_VAR_NAME: ANDROID_KEYSTORE_BASE64)

The secret is read from the environment, never from the command line, and is NEVER printed.
It tolerates the usual copy/paste accidents:
  - line breaks, spaces, tabs and literal "\\n" sequences
  - surrounding quotes
  - -----BEGIN ...----- / -----END ...----- lines
  - URL-safe base64 ("-" and "_" instead of "+" and "/")
  - missing "=" padding
  - the keystore being base64-encoded twice
When something is wrong it explains what, using only lengths and positions.
"""

from __future__ import annotations

import base64
import binascii
import os
import re
import sys

_BASE64_CHARS = re.compile(r"^[A-Za-z0-9+/]*$")
_MAX_ROUNDS = 3


class KeystoreError(ValueError):
    pass


def keystore_type(data: bytes) -> str | None:
    """Returns the keystore format if the bytes look like one, otherwise None."""
    if data[:4] == b"\xfe\xed\xfe\xed":
        return "JKS"
    if data[:4] == b"\xce\xce\xce\xce":
        return "JCEKS"
    # PKCS12 is DER: a SEQUENCE (0x30) with a long-form length, then INTEGER version 3.
    if len(data) > 8 and data[0] == 0x30 and data[1] in (0x80, 0x81, 0x82, 0x83, 0x84):
        return "PKCS12"
    return None


def clean(text: str, notes: list[str]) -> str:
    """Strips copy/paste noise and returns standard, padded base64 text."""
    original_length = len(text)
    text = text.replace("\\r", "\n").replace("\\n", "\n").strip()

    while len(text) >= 2 and text[0] == text[-1] and text[0] in "'\"`":
        text = text[1:-1].strip()
        notes.append("removed surrounding quotes")

    lines = text.splitlines()
    kept = [line for line in lines if not line.strip().startswith("-----")]
    if len(kept) != len(lines):
        notes.append(f"removed {len(lines) - len(kept)} BEGIN/END line(s)")
    if len(kept) > 1:
        notes.append(f"joined {len(kept)} lines")

    text = re.sub(r"\s+", "", "".join(kept))
    if len(text) != original_length:
        notes.append(f"length {original_length} -> {len(text)} characters after cleaning")

    if "-" in text or "_" in text:
        text = text.replace("-", "+").replace("_", "/")
        notes.append("converted URL-safe base64")

    body = text.rstrip("=")
    if not _BASE64_CHARS.match(body):
        bad = [i for i, char in enumerate(body) if not re.match(r"[A-Za-z0-9+/]", char)]
        raise KeystoreError(
            f"the secret contains {len(bad)} character(s) that are not base64 "
            f"(first at position {bad[0] + 1} of {len(body)}). "
            "Copy the base64 text again, exactly as the command printed it."
        )
    if len(body) % 4 == 1:
        raise KeystoreError(
            f"the secret has {len(body)} base64 characters, which can never be valid: "
            "it looks cut off. Copy the whole text again."
        )
    if len(body) % 4 and body == text:
        notes.append("added missing '=' padding")
    return body + "=" * (-len(body) % 4)


def decode(text: str) -> tuple[bytes, list[str]]:
    """Decodes the secret text into keystore bytes. Returns (bytes, notes about what was fixed)."""
    notes: list[str] = []
    if not text or not text.strip():
        raise KeystoreError("the secret is empty or missing")

    current = text
    for round_number in range(1, _MAX_ROUNDS + 1):
        try:
            data = base64.b64decode(clean(current, notes), validate=True)
        except binascii.Error as error:
            raise KeystoreError(f"the secret is not valid base64 ({error})") from None

        kind = keystore_type(data)
        if kind:
            if round_number > 1:
                notes.append(f"the keystore was base64-encoded {round_number} times; decoded all layers")
            notes.append(f"found a {kind} keystore of {len(data)} bytes")
            return data, notes

        try:
            as_text = data.decode("ascii")
        except UnicodeDecodeError:
            break
        if re.search(r"(storePassword|keyAlias|keyPassword)\s*=", as_text):
            raise KeystoreError(
                "the secret decodes to a keystore.properties text file, not the keystore. "
                "Base64-encode the .jks/.keystore file itself."
            )
        if "BEGIN CERTIFICATE" in as_text or "BEGIN PRIVATE KEY" in as_text:
            raise KeystoreError(
                "the secret decodes to a PEM certificate/key, not a keystore. "
                "Base64-encode the .jks/.keystore file itself."
            )
        current = as_text  # Possibly encoded twice: try another round.

    raise KeystoreError(
        f"the secret decodes to {len(data)} bytes that are not a Java keystore (JKS/PKCS12). "
        "Did you encode the right file? It is usually called something like upload.jks."
    )


def main(argv: list[str]) -> int:
    if not 1 <= len(argv) <= 2:
        print(__doc__, file=sys.stderr)
        return 2
    output, variable = argv[0], (argv[1] if len(argv) == 2 else "ANDROID_KEYSTORE_BASE64")
    prefix = "::error title=Keystore secret::" if os.environ.get("GITHUB_ACTIONS") else "error: "
    try:
        data, notes = decode(os.environ.get(variable, ""))
    except KeystoreError as error:
        print(f"{prefix}{variable}: {error}", file=sys.stderr)
        return 1

    for note in notes:
        print(f"{variable}: {note}")
    descriptor = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "wb") as file:
        file.write(data)
    print(f"{variable}: keystore written to {output}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
