# Temporary crash capture — 1.1.6

Scope: diagnostics only for the continuing agent crash. No claim of fixing its cause.

- Application-wide uncaught-exception handler writes and fsyncs the exception chain, Java source lines, device/build and last agent stage before chaining Android termination.
- A small durable stage journal can identify unfinished requests if native code or the system kills the process without a Java exception.
- Next launch shows a selectable report with Copy report and Dismiss. Dismiss acknowledges that report; the local copy stays in files/crash-survival/report.txt. No automatic upload.
- Android 11+ process exit reasons distinguish native crash, ANR, signal, memory kill and user stop when records exist. ANR text is bounded; unavailable native stack traces are not fabricated.
- Checkpoints use fixed labels, never prompt/command/provider-key text. Exceptions may contain their own messages; review a report before sharing it.
- Normal app launches without a new report do not show a popup.

Test: an isolated JVM actually throws an uncaught exception, saves the diagnostic, exits, and the parent reopens the report. Oversized diagnostic reads are bounded. Physical phone acceptance requires reproducing the reported agent crash after installation.

Build/publication pending at this checkpoint. Use local Gradle and the unchanged verified native library/bootstrap; no GitHub Actions needed. Existing 1.1.5 source and release remain available.
