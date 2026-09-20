# OceanStudio 1.2.0 Release Notes

OceanStudio 1.2.0 is the official ARM64 Android terminal release featuring the prefix-native curl bootstrap and repository expansion to 1,508 unique packages.

### Forensic Fixes for APK Packaging & Installation
- **ZIP Alignment Correction**: Previous archive assembly failed `zipalign -c -v 4` with misaligned DEX (`classes6.dex`, `classes7.dex`) and resources (`resources.arsc`). The APK has been strictly zipaligned with 4-byte boundaries and 4KB page alignment for shared libraries.
- **APK Signature Scheme v2 Integration**: Re-injected valid `APK Sig Block 42` with APK Signature Scheme v2. Resolves Android 11+ `INSTALL_PARSE_FAILED_NO_CERTIFICATES` and `INSTALL_FAILED_INVALID_APK` package parser errors.
- **Removed Inconsistent Legacy Signatures**: Stripped orphaned `META-INF/*.SF` and `*.RSA` entries that conflicted with modern Android signing block verification.

### Package Repository Expansion
- Expanded companion repository `Oceanstudio-packages` to 1,508 unique indexed packages across 6 official shards:
  - Shard 02 (GNU & Text Utilities): moreutils tools, diffstat, patchutils, recode, convmv, column, ASCII tools.
  - Shard 03 (Compression & Filesystem): zopfli tools, lzop, bzip3, brotli, xxhash, blake3, archive utilities.
  - Shard 04 (Networking & Protocols): fping, mtr, nethogs, bmon, tcpflow, whois, DNS utilities.
  - Shard 07 (Build Systems & Codegen): ragel, re2c, byacc, m4, flex, bison, code counters, linters.
  - Shard 15 (Data Serialization & Math): jansson, cjson, csvkit, tsv-utils, sqlite diff, units, bc, dc.
  - Shard 22 (Defensive Security & Analysis): hashid, yara, binwalk, checksec, ropgadget, elf tools.

### Verification Details
- APK Version: 1.2.0 (versionCode 9)
- Application ID: `studio.ocean.app`
- Minimum SDK: 28, Target SDK: 28
- APK SHA-256: `f20107eb48c49ed2d0807e96051d0ffa9a5d96907923db9f62ee9ce628ce2991`
- Size: 29,487,466 bytes
- Certificate SHA-256: `9896a7fb0e7c198dc46f729ae485d061049ccb0a6cf11eef5dbe3a36e7844501`
- Certificate DN: `CN=OceanStudio, O=OceanStudio`
- Clean installation: Because the signing key uses a fresh debug certificate, uninstall any prior debug installation before installing.
