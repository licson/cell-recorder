# Cell Recorder — Specification Index

This directory contains the authoritative behavioral specifications for the Cell Recorder Android application. Each file is a self-contained behavior contract. Where requirements touch multiple domains, a single spec is designated as the **authoritative source** and all others reference it.

---

## Spec Map

| Spec | Tier | Authoritative For | Summary |
|---|---|---|---|
| [`recording/`](recording/spec.md) | Capability | Recording lifecycle, triggers, indoor recording mode (high-level), speedtest lifecycle hooks | How outdoor and indoor recording sessions are created, started, stopped, and what triggers data capture. |
| [`cell-info/`](cell-info/spec.md) | Cross-cutting | Cell identity processing, serving cell detection, CA bands, 5G NSA handling, cell ID split | How raw modem data is interpreted into structured cell records. |
| [`connectivity/`](connectivity/spec.md) | Capability | ICMP ping measurement, latency, packet loss | Continuous network health measurement via ping. |
| [`speedtest/`](speedtest/spec.md) | Capability | Speedtest protocol, server discovery, throughput measurement | HTTP-based speedtest execution and data collection during recording. |
| [`analytics/`](analytics/spec.md) | Capability | Post-session analysis, anomaly detection, handoffs, mobility, coverage | Statistical and event detection on recorded sessions. |
| [`sessions/`](sessions/spec.md) | Capability | Session CRUD, detail view, replay, export/import triggers | User-facing session management and replay experience. |
| [`data/`](data/spec.md) | Capability | CSV/GeoJSON export/import, database schema evolution | Data formats, persistence rules, and migration guarantees. |
| [`ui/`](ui/spec.md) | Capability | Screens, navigation, controls, visual feedback | What the user sees and interacts with. |
| [`service/`](service/spec.md) | Platform | Foreground service lifecycle, notification, auto-stop | Background execution mechanics that keep recording alive. |
| [`permission-flow/`](permission-flow/spec.md) | Cross-cutting | Unified permission decision logic, rationale dialogs, settings fallback | How every runtime permission checkpoint behaves consistently. |
| [`indoor/`](indoor/spec.md) | Cross-cutting | IMU-based pedestrian dead reckoning, step detection, drift, origin reset | Indoor position tracking and sensor fallback. |
| [`thread-safety/`](thread-safety/spec.md) | Platform | Concurrent state snapshots, atomic updates | Coroutine-safe shared state during recording. |
| [`db-write-safety/`](db-write-safety/spec.md) | Platform | DB finalization on shutdown, idempotent stop | Database durability during service teardown. |
| [`process-cleanup/`](process-cleanup/spec.md) | Platform | Ping process termination, zombie prevention | Reliable cleanup of subprocesses on cancellation. |
| [`test-foundation/`](test-foundation/spec.md) | Quality | JVM unit test coverage, pure-logic extraction contracts | What must be tested at the JVM level. |
| [`instrumented-test-coverage/`](instrumented-test-coverage/spec.md) | Quality | androidTest coverage, migration verification, DAO round-trips | What must be tested with a device/emulator. |

---

## Cross-Reference Rules

- **If a requirement appears in multiple specs, the spec listed as *Authoritative For* in the table above is the source of truth.**
- All other specs **must not** restate the requirement; they **must** reference the authoritative spec.
- Platform and quality specs protect the capabilities. They do not define user-facing behavior, but they constrain how capabilities are implemented.
- Changes to the authoritative source propagate to all referencing specs. Review cross-references when archiving a change that modifies an authoritative spec.

---

## Dependency Graph

```
permission-flow ──────┐
                      ▼
indoor ──────┐    recording ◄──────┐
             │         │            │
             │         ▼            │
             └──► service ◄────────┤
                      │             │
                      ▼             │
              ┌────── cell-info ◄───┤
              │          │          │
              │          ▼          │
              │    data ◄─────────┘
              │      │
              │      ▼
              │   sessions ◄────── analytics
              │      │
              │      ▼
              └──► ui
                     │
                     ▼
              connectivity ◄──── speedtest
```

---

## How to Change These Specs

1. Identify the authoritative spec for the behavior you want to change.
2. If the change touches multiple domains, update the authoritative spec first, then verify that dependent specs still reference the correct version.
3. Do not restate requirements from other specs. Add or update cross-references instead.
