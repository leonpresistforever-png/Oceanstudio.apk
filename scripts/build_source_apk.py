#!/usr/bin/env python3
"""Compatibility entry point for the verified source release builder.

The old implementation depended on a foreign app's temporary jars, merged
previous APK DEX files and enabled authentication bypass. Release builds now
compile the current Gradle sources and require the existing signing identity.
"""
from build_release_apk import main

if __name__ == '__main__':
    main()
