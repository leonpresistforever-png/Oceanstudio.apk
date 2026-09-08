# Ocean terminal emulation

Ocean Terminal renders its local PTY with the `emulatorview` engine from
[Android Terminal Emulator](https://github.com/jackpal/Android-Terminal-Emulator),
vendored under `android/terminal-emulator/` under the Apache License 2.0.

The engine supplies VT/ANSI parsing, cursor addressing, erase operations,
scroll regions, the alternate screen, UTF-8/wide-character handling, scrollback,
selection, IME input, xterm 256 colors, and terminal resize behavior. Ocean adds
dynamic RGB SGR (`38;2` / `48;2`) colors and connects the view directly to the
service-owned Ocean PTY. It does not use the Termux application, Termux paths,
or a remote terminal.

The integration deliberately does not strip escape sequences. PTY bytes enter
the emulator state machine and only the resulting screen cells are rendered.
