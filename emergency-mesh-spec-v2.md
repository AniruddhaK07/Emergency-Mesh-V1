# MODEL-AGNOSTIC EXECUTION RULES (read first, apply regardless of model)
These rules override any model's default instincts about "improving" or
"modernizing" this spec. They exist because this document will be executed
across multiple models (Opus, Gemini, etc.) in the same project, and quality
must not vary by which model happens to be active in a given session.

1. **Build exactly what's specified — nothing extra.** Do not add animations,
   loading screens, onboarding flows, extra UI polish, additional libraries,
   "nice to have" features, or alternate implementations that weren't asked
   for. If you think something is missing, say so in plain text and ask —
   do not just add it.
2. **No unsolicited framework/library upgrades.** Section 2's pinned stack is
   final. Do not swap in a "better" or "more modern" library, state manager,
   styling approach, or animation library on your own initiative.
3. **Match existing code style, not your own default style.** If a file
   already exists with a pattern (naming, structure, comment density), follow
   it. Do not rewrite working code to match a different convention you prefer.
4. **Utilitarian over impressive.** This is emergency-response software. A
   plain, boring, correct implementation is always preferred over one that
   is visually elaborate, "creative," or showcases capability. If a choice
   exists between simple-and-obviously-correct and clever-and-impressive,
   choose simple.
5. **Read `/docs/PROGRESS.md` and `/docs/WIRING.md` before writing anything.**
   Do not restate, re-derive, or re-propose decisions already logged there.
   If you disagree with a past decision, say so explicitly and ask before
   changing it — do not silently override it.
6. **When in doubt, do less, not more.** An incomplete-but-correct piece of
   work beats a "complete" one that quietly added scope beyond this spec.

# SYSTEM_DIRECTIVE: EMERGENCY_MESH_COPILOT_v2
# ROLE: Senior Systems Architect & Edge Compute Engineer
# TARGET MODEL: Claude Opus 4.8 (via Antigravity CLI, agy)
# EXECUTION_MODE: Phase-gated, decisions-locked. Every design question in this
# spec has already been resolved below — do not re-open them, do not present
# alternatives, do not hedge with "you could also consider X." If something
# genuinely can't be decided from this spec, stop and ask; don't default to
# the most impressive-sounding option.

## 0. MANDATORY META-FILES (create these before writing any other code)

This project runs across multiple pair-programming sessions. Two files exist
purely to make re-entry into the project fast and lossless. Create them NOW,
before Phase 1 work begins, and treat updating them as part of finishing any
unit of work — not an afterthought at the end of a session.

### 0.1 `/docs/WIRING.md`
A living systems diagram document. Must contain, using Mermaid diagram syntax
(renders natively in most Markdown viewers and in Antigravity's artifact view):
- One **system-level diagram** showing the three hardware tiers (Reporter
  phone → Relay node → Command node) and the protocol used at each hop
  (BLE → LoRa → Satellite/internet).
- One **sequence diagram** per phase showing data flow through that phase's
  components (e.g. Phase 2: `IncidentReport → serialize → LZ4 → Noise encrypt
  → BLE payload`).
- One **state diagram** for the power-tier state machine (Section 4 below).
- A **wire format table** (byte offset, field name, width, type) for the
  binary payload — this is the single source of truth for the struct layout;
  if the struct changes, this file changes in the same commit.
Update this file whenever a data flow, protocol boundary, or state machine
changes. Do not let code and diagram drift apart.

### 0.2 `/docs/PROGRESS.md`
The project's persistent memory across sessions. Structure:
```markdown
# Project Progress

## Current State (update this section every session)
- Last worked on: <date>
- Active phase: <phase name>
- Immediate next step: <one concrete sentence>

## Phase Status
| Phase | Status | Notes |
|---|---|---|
| 0 — Gateway/Power Architecture | Not started / In progress / Done | |
| 1 — Reporter UI + local intent capture | ... | |
| 2 — Payload compression & crypto | ... | |
| 3 — BLE mesh transport | ... | |
| 3.5 — LoRa relay bridge | ... | |
| 4 — Command dashboard | ... | |

## Decisions Log (append-only — never delete past entries)
- <date>: <decision made, and why, in 1-2 sentences>

## Known Issues / Open Questions
- <anything genuinely unresolved, with enough context to pick back up cold>
```
At the start of every session, read this file first, out loud (i.e., summarize
it back) before touching code, to confirm shared context. At the end of every
session — or every time a phase's status changes — update it. This file is the
answer to "where did we leave off," so it must always be true, not aspirational.

### 0.3 `/docs/TESTING.md`
Testing this system as one end-to-end mesh on day one is a trap — most of it
is testable in isolation, in dependency order, well before all hardware
exists. Create this file with the following fixed test plan, and check off
stages as they pass (do not skip ahead and claim a later stage works if an
earlier one hasn't been verified):

```markdown
# Testing Plan

## Stage 1 — Dashboard (no hardware needed)
`npm run dev`, feed hand-written JSON matching the decrypted payload shape.
Validates: sort weighting, UI, ingest server — independent of everything else.

## Stage 2 — Phone app, solo (needs 1 phone)
`adb install` onto a real device — Android emulators do not support real
BLE radio between instances, so this cannot be emulator-only past this stage.
Validates: tap-first UI, GPS capture, local queueing, power-tier state machine
(mock battery levels for CONSERVE/CRITICAL transitions).

## Stage 3 — Phone-to-laptop BLE (needs 1 phone + laptop's built-in BLE)
Python script on laptop using `bleak` (cross-platform BLE lib) scans for and
decrypts the phone's advertised payload — laptop temporarily holds the Tier 3
key material for this test only.
Validates: wire format, LZ4 round-trip, Noise handshake + encryption — the
whole Phase 2 pipeline — without waiting on any relay hardware.

## Stage 4 — Phone-to-phone BLE mesh (needs 2 phones)
Validates: actual store-and-forward, TTL decrement, dedup logic from Phase 3.5,
all in the same room, no relay node involved yet.

## Stage 5 — ESP32 LoRa bench test (needs 2× ESP32 + RFM95)
Modules a foot apart. Confirms bytes sent from one arrive intact at the other.
Validates: LoRa hop correctness — NOT range. Range is a separate, later,
outdoor test and is not a blocker for anything else in this plan.

## Stage 6 — Full chain
Phone → ESP32 relay (BLE in, LoRa out) → second ESP32 (LoRa in, USB-serial out)
→ laptop command node (decrypt, dashboard). Only attempt once Stages 1-5 each
pass independently — this stage should mostly confirm wiring, not surface new
logic bugs, because the logic was already proven per-hop above.
```
Update the checkboxes/status in this file as stages pass — same append-only
spirit as PROGRESS.md's Decisions Log. When PROGRESS.md's "Immediate next
step" references a testing milestone, it should name the stage number here.
---

## 1. PROBLEM STATEMENT (locked)
Provide two-way emergency communication during total cellular/ISP collapse,
using only: citizen phones, pre-positioned cheap relay hardware, and one
satellite choke point at the command post. No design element may assume
internet reachability except at that single choke point.

## 2. RESOLVED ARCHITECTURE — THREE HARDWARE TIERS

```
TIER 1: Reporter phones        TIER 2: Relay nodes           TIER 3: Command node
(citizens, unlimited count) →  (pre-positioned, ~$60/unit) →  (single, at command post)
BLE mesh, last-hop only         LoRa backhaul, 2-15km range    Satellite/Starlink uplink
                                 Raspberry Pi + BLE + LoRa HAT   → React dashboard
```

- **Tier 1 → Tier 2:** BLE store-and-forward, as originally scoped. Range
  ~10-100m. This is the only tier where "no infrastructure" is a hard
  requirement, because this is the tier civilians actually carry.
- **Tier 2 relay nodes:** placed at fire stations, schools, community
  centers — decided and physically installed *before* disaster season, not
  improvised during one. Job: drain BLE queue within range, forward over
  LoRa. Nothing else. No UI, no dashboard, no cloud dependency.
- **Tier 2 → Tier 3:** LoRa, chosen specifically because it needs no carrier
  infrastructure and covers km-scale range at sub-1W power — this is the
  actual bridge across the "collapse" gap that the original spec never closed.
- **Tier 3:** exactly one command node per operational area. This is the
  only place satellite (Starlink terminal or a serial-mode satellite
  messenger) is required, and the only place the React dashboard connects
  to anything.

**Consequence for the payload protocol:** the wire format (Phase 2) must
survive a BLE→LoRa hop without modification — same encrypted blob, no
re-encoding at the relay. Relay nodes forward bytes; they do not decrypt.
Only the command node holds the key material to decrypt (see Phase 2 auth
model below).

---

## PHASE 0 — GATEWAY & POWER ARCHITECTURE (build this first)
1. Implement the relay node firmware in C++ (ESP32/Arduino framework): BLE
   central role draining queued payloads, LoRa TX of raw encrypted blobs via
   an RFM95 (SX1276) module, no decryption, no logic beyond TTL decrement
   and dedup-forwarding (see Phase 3.5). ESP32 chosen over Raspberry Pi
   because it draws ~0.1-0.5W vs. the Pi's ~2-5W — these units run
   unattended on solar/battery for the duration of a disaster.
   
2. Implement the power-tier state machine (phone-side), locked as follows:
   ```
   NORMAL   (battery > 40%): 3s scan / 7s advertise duty cycle, GPS on-demand
   CONSERVE (battery 15-40%): advertise-only every 30s, GPS at last-known-cached
   CRITICAL (battery < 15%): single advertise burst every 5 min, last unsent
                              report only, then radio off, screen stays locked
   ```
   Implement as a `BatteryManager`-driven state machine checked on every
   service wake. This is not configurable at runtime — these thresholds are
   fixed and documented in `/docs/WIRING.md`'s state diagram.

**Acceptance criteria:** relay firmware forwards an opaque encrypted blob
end-to-end without touching its contents; power state machine transitions
are unit-testable against mocked battery levels.

---
## TESTING PLAN (create as /docs/TESTING.md)

Test in this order — do not attempt a later stage until the one before it passes:

1. **Dashboard** — `npm run dev`, feed hand-written fake JSON. No hardware needed.
2. **Phone app solo** — `adb install` onto a real phone (emulators can't do real BLE).
3. **Phone-to-laptop BLE** — Python `bleak` script on laptop reads the phone's
   encrypted payload directly. Validates the whole crypto pipeline with zero
   relay hardware.
4. **Phone-to-phone BLE mesh** — needs a second phone. Validates store-and-forward.
5. **ESP32 LoRa bench test** — two ESP32+RFM95 units a foot apart, confirm bytes
   arrive intact. This tests correctness, not range.
6. **Full chain** — only once 1-5 all pass individually.

---
## PHASE 1 — REPORTER UI (tap-first, decided)
1. Primary input: four fixed buttons (Trapped / Injured / Fire / Need
   Evacuation) + numeric casualty stepper + GPS auto-capture. This is the
   entire critical path — no ML inference sits between a tap and a queued
   report.
2. Voice is a **secondary, optional dictation layer**: fills a free-text
   notes field, always shown back to the user for confirmation before send.
   Never auto-submits from inference confidence alone.
3. `IncidentReport` data class (unchanged from v1, still the wire contract):
   ```kotlin
   data class IncidentReport(
       val emergencyType: EmergencyType,
       val casualtyCount: Int,
       val severity: Severity,
       val notes: String,       // optional, from dictation or typed
       val timestamp: Long,
       val corroborationCount: Int = 1   // set by relay/command node, not phone
   )
   ```

**Acceptance criteria:** a report can be fully composed and queued with zero
mic permission granted; voice, if used, requires explicit confirm-tap before
the report leaves the "draft" state.

---

## PHASE 2 — PAYLOAD COMPRESSION & CRYPTOGRAPHY
1. Fixed binary struct layout, documented in `/docs/WIRING.md`'s wire-format
   table (byte offset, field, width) — that table is the source of truth,
   not this prose.
2. LZ4 compression via `org.lz4:lz4-java`.
3. Noise Protocol: `Noise_XX_25519_ChaChaPoly_SHA256`.
   - **Key trust model, locked:** Tier 1 (reporter) nodes use
     trust-on-first-encounter for peer keys — no pre-shared secrets needed
     to enter the mesh, matching the "any citizen can report" requirement.
     Tier 3 (command node) keys are pre-provisioned and signed before
     deployment, since there's exactly one and it's known in advance.
   - Relay nodes (Tier 2) hold no decryption keys at all — they forward
     ciphertext blindly. Only the Tier 3 command node can decrypt. This is
     what makes "relay nodes need no security hardening" true — they never
     see plaintext.

**Acceptance criteria:** two reports from the same phone produce distinct
ciphertexts; relay firmware from Phase 0 can forward the blob without any
key material; wire format table in WIRING.md matches the actual struct.

---

## PHASE 3 — BLE MESH TRANSPORT (Tier 1 → Tier 2)
1. `BluetoothLeScanner`, duty-cycled per the power-tier state machine from
   Phase 0 — do not define a separate duty cycle here, reference Phase 0's.
2. Foreground `Service` + battery-optimization exemption request. (Corrected
   from the original spec's non-existent "CPU-to-daemon handoff.")
3. TTL as hop-count, not wall-clock.

## PHASE 3.5 — DEDUP & ANTI-FLOODING (locked algorithm)
Applied at relay nodes (Tier 2), before LoRa forwarding — this is where
duplicate reports from multiple bystanders get collapsed, and where the
mesh's bandwidth-scarce LoRa hop is protected from redundant traffic.

- Cluster key: `round(lat, 3) + round(lon, 3) + emergencyType + floor(timestamp / 300)`
  — same ~100m grid cell, same category, same 5-minute bucket.
- On match: increment `corroborationCount` on the existing forwarded record
  instead of creating a new LoRa transmission.
- Sort/priority weight at the command node: `baseSeverity * log(1 + corroborationCount)`
  — many independent reports of one incident outrank a single higher-labeled
  one, but one spoofed "critical" report can't game the queue alone.

**Acceptance criteria:** given N reports matching the same cluster key, exactly
one LoRa transmission occurs with `corroborationCount = N`.

---

## PHASE 4 — COMMAND DASHBOARD (Tier 3 only)
1. React + Vite + Tailwind, dark high-contrast operational theme, single
   accent color reserved for critical-severity items.
2. Ingest layer: a small standalone Express server (not Vite dev middleware —
   this one needs to run persistently at the command post, independent of
   the frontend dev server) receiving decrypted, deduplicated reports from
   the Tier 3 LoRa/satellite gateway process.
3. Sort: stable sort, primary key `baseSeverity * log(1 + corroborationCount)`
   descending, secondary key TTL-remaining ascending.

**Acceptance criteria:** dashboard never receives raw ciphertext (decryption
happens in the Tier 3 gateway process, not in the browser); sort order
matches Phase 3.5's weighting exactly.

---

## END-OF-SESSION CHECKLIST (do this before ending any work session)
1. Update `/docs/PROGRESS.md`: Current State section, Phase Status table row(s)
   touched, append any new Decisions Log entries.
2. Update `/docs/WIRING.md` if any data flow, protocol, or state machine
   changed this session.
3. State explicitly, in your final message of the session, which phase is
   next and what the immediate next step is — this should match what you
   just wrote into PROGRESS.md, so the next session starts from a single
   source of truth instead of chat history.
