# Local execution boundary

Ocean package CI is a compiler and static distribution system only. It does
not receive terminal input and has no command-execution API. The repository
contains Android/aarch64 `.deb` files and signed APT metadata.

On Android, `OceanTerminalRuntimeService` owns the PTY. JNI creates a local
PTY, forks under the OceanStudio application UID, and calls `execve()` on the
Ocean shell or another executable installed below `OCEAN_PREFIX`. Standard
input, output, error, signals, resize events, and exit status remain between
the local child process and local PTY. No root, remote shell, VM, container,
or PRoot is used for ordinary Ocean packages.

After download and installation, package executables are independent of the
network. Network access is needed only for repository metadata and package
archives. Installed Bash, Git, Python, Node, Clang, and user-built executables
run directly on the Android kernel and must continue to run while offline.

`LocalProcessDiagnostics` reads `/proc/<pid>` locally and exposes PID, parent
PID, UID, executable path, working directory, and Ocean prefix. A process is
reported as an Ocean-installed executable only when its kernel-reported
executable path is beneath the runtime prefix.
