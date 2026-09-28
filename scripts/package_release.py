#!/usr/bin/env python3
"""Compatibility entry point for the verified current-source release builder.

Production releases require the existing secret keystore and a previously
published APK so Android update identity can be checked. Never rewrite or
re-sign an older APK as a new version.
"""
from build_release_apk import main

if __name__ == '__main__':
    main()
