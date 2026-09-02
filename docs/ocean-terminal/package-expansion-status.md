# Ocean package expansion status

This milestone implements the dependency-correct **foundation** phase, not the
entire requested ecosystem.

## Preserved runtime boundary

The Android bootstrap installer, PTY/JNI, service/session ownership, Bash
startup, `filesDir` path derivation, target SDK 28 and terminal emulator are
unchanged. Native packages remain Android/bionic API 28 aarch64 artifacts built
for `/data/data/studio.ocean.app/files/usr`.

## Implemented by this phase

* versioned Ocean catalogue with bootstrap, foundation, network and
  source-control phase boundaries;
* missing-root incremental compilation over the pinned, source-only Android
  build recipes;
* minimal-bootstrap dependency closure independent of repository size;
* real `ocean-tools` Debian package with an on-device acceptance script;
* `.deb` inspection/report generation for architecture, runtime path identity,
  executable payloads, artifact sizes and SHA-256;
* atomic pkg transaction ownership using PID plus Linux process start time and
  a unique token, including stale-lock recovery;
* signed APT repository publication for foundation packages.

## Not yet claimed

Network, Git/GitHub CLI, Python/pip, Node/npm, compiler, database, media,
editor and PRoot phases remain unverified until their real artifacts build and
their binaries execute on arm64 Android. The catalogue declarations for the
next phases are build inputs, not claims that those packages are published.

The signed repository is published from the separate public
`foxerdude90-source/Oceanstudio-packages` repository. This keeps application
source private while making APT metadata and packages anonymously reachable at
`https://foxerdude90-source.github.io/Oceanstudio-packages/apt`.
