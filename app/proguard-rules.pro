# Project-specific R8 / ProGuard rules for the release build.
#
# Compose, AndroidX and Kotlin ship their own rules inside their libraries, so this
# file is intentionally (almost) empty. Add rules here ONLY when a release build
# breaks because R8 removed or renamed something that is used via reflection
# (for example a class loaded by name or a serialization model), and explain why.
#
# Release crash stack traces are obfuscated. Each GitHub Release has the matching
# mapping file attached so they can be decoded (see docs/testing.md).

# Keep line numbers in stack traces, but hide the original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
