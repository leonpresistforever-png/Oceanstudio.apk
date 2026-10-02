---
id: data-pipeline
name: Data Pipeline & State Streaming
description: Reactive data streams, state persistence, SQLite schema migrations, and event processing.
version: 2.0.0
required_tools:
  - run_command
  - view_file
  - replace_file_content
optional_tools:
  - search_web
---

# Data Pipeline & State Streaming

## 1. Mission and Scope
Design, maintain, and optimize data ingestion, reactive event streams, database persistence, and schema migrations across Ocean subsystems. Guarantee transactional integrity, zero state corruption, efficient backpressure handling, and clean separation between storage engines and application presentation layers.

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Designing or modifying SQLite databases, Room entities, or schema migration scripts.
  - Implementing Server-Sent Events (SSE), WebSocket streams, or reactive Flow/Rx pipelines.
  - Optimizing data serialization, JSON/protobuf parsing, or caching tiers.
  - Resolving data corruption, lock contention, or slow database query performance.
- **Do NOT Invoke When**:
  - Writing visual UI views or styles (use UX Design).
  - Configuring remote provider OAuth authentications (use API Integration).

## 3. Inputs to Gather
1. Data schema definitions, table schemas, and indexing strategies.
2. Stream velocity, expected data throughput, and memory constraints.
3. Database version, target migration path (e.g., v1 -> v2), and migration scripts.
4. Transaction boundaries and consistency requirements (ACID vs eventual consistency).

## 4. Tool Policy for This Domain
- Inspect schema definitions and SQL queries using `view_file`.
- Verify database migrations and schema consistency via automated test suites.
- Never execute unparameterized SQL queries constructed via raw string concatenation.

## 5. Step-by-Step Operating Procedure
1. **Schema Design & Versioning**: Model data entities with explicit primary keys, foreign key constraints, and indices for frequently queried columns.
2. **Migration Scripting**: When modifying existing tables, write automated migration scripts (`ALTER TABLE`, column defaults) with roll-forward verification.
3. **Reactive Stream Pipeline**: Construct streaming pipelines with explicit backpressure strategies (buffering, dropping oldest, or backpressure suspension).
4. **Transaction Management**: Enclose multi-statement writes in atomic database transactions (`beginTransaction()` / `endTransaction()`).
5. **Serialization Optimization**: Use streaming JSON parsers (Jackson/Gson streaming or kotlinx.serialization) to prevent large memory spikes on large payloads.
6. **Integrity & Index Verification**: Test queries with `EXPLAIN QUERY PLAN` to ensure index coverage and prevent full table scans on critical paths.

## 6. Domain-Specific Heuristics and Algorithms
- **WAL Mode Preference**: Always enable Write-Ahead Logging (`PRAGMA journal_mode=WAL;`) for concurrent read/write throughput without blocking UI readers.
- **Batched Inserts**: Batch multiple inserts into chunks (e.g., 100-500 rows per transaction) to minimize disk sync overhead.
- **Backpressure Buffer Sizing**: Cap memory buffers for real-time SSE streams to avoid OOM when consumer threads fall behind fast producers.

## 7. Evidence Requirements
- Database migration test logs confirming schema upgrade without data loss.
- `EXPLAIN QUERY PLAN` output demonstrating indexed lookups.
- Stream processing benchmarks confirming zero dropped events and stable memory footprint.

## 8. Failure Modes and Recovery
- *SQLite Database Locked (`SQLITE_BUSY`)*: Ensure long-running queries do not hold write locks; utilize WAL mode and appropriate timeout pragmas.
- *Schema Migration Exception*: Catch migration failures, roll back transaction, preserve existing database backup, and report actionable schema mismatch.
- *Stream Backpressure Buffer Overflow*: Apply bounded ring buffers and notify user of dropped frames rather than exhausting application heap.

## 9. Security and Permission Boundaries
- Store sensitive tokens, credentials, and cryptographic keys in encrypted storage, never in plaintext SQLite tables.
- Confine database files strictly to the application's private app directory (`context.getDatabasePath()`).

## 10. Acceptance Tests
1. Database schema migrations upgrade cleanly from all previous versions without crashes.
2. Streaming pipelines process high-frequency event streams without memory leaks or ANRs.
3. SQL injection vulnerabilities are eliminated through 100% parameterized queries.

## 11. Handoff Format
- **Pipeline Component**: Storage engine, table names, or stream channel.
- **Schema Delta**: Table migration definitions and index modifications.
- **Performance Verification**: Benchmark metrics and query plan validation.

## 12. Small Worked Examples
- *Example*: Streaming MCP events: Implemented SSE chunk parser with bounded 64KB buffer, validated JSON-RPC payload parsing, emitted events to UI Flow, and confirmed zero memory growth during long-lived server streaming sessions.
