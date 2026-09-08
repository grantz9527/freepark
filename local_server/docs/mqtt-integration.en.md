# FreePark Site Service (local_server) MQTT Integration Guide

> This document describes the MQTT integration contract of `local_server` (the local / on-premise site service).
> Audience: developers who want to connect `local_server` to their own cloud MQTT broker, or who want to drive / test the chain with a simulated peer (including an AI agent).
> Every topic, payload field, timing rule and limitation below is taken directly from the implementation. Treat this document as the source of truth for field names and enum values (JSON fields not listed here are ignored by the edge).
>
> **Scope**: this document only covers the MQTT integration between `local_server` (the edge node) and the cloud (heartbeat reporting `edge.heartbeat/1`, config-sync dispatch `edge.config.sync/3`, and parking-session reporting `edge.parking.session/1`). Local internal links — AI recognition events, device/barrier HTTP, etc. — are out of scope.

---

## 1. TL;DR

- In **EDGE mode**, `local_server` connects to the "cloud broker" with **three independent MQTT clients**:
  - **Heartbeat (publisher)**: clientId = `{mqttClientId}` (default `freepark-local-edge`). Every 10 seconds it publishes `edge.heartbeat/1` to `{mqttTopicPrefix}/{nodeCode}` (default topic `parking/heartbeat/{nodeCode}`), QoS 1, not retained.
  - **Config-sync (subscriber)**: clientId = `{mqttClientId}-cfg` (default `freepark-local-edge-cfg`). It subscribes to `{configSyncTopicPrefix}/{nodeCode}` to receive `edge.config.sync/3` full/delta framed snapshots, QoS 1.
  - **Parking-session (publisher)**: clientId = `{mqttClientId}-rec` (default `freepark-local-edge-rec`). Every 10 seconds it publishes full snapshots of pending parking sessions `edge.parking.session/1` to `{reportTopicPrefix}/{nodeCode}` (default topic `parking/report/{nodeCode}`), QoS 1, not retained, see §10 Protocol 3.
- **This document only describes the MQTT integration between the edge node (`local_server`) and the cloud** (heartbeat reporting + config-sync dispatch + parking-session reporting). Local internal links (AI recognition events, device/barrier HTTP, etc.) are out of scope.
- All three clients use Eclipse Paho v1.2.5 with: `cleanSession=true`, `automaticReconnect=true`, `connectionTimeout=8s`, `keepAliveInterval=30s`.
- All MQTT settings are persisted at runtime into the MySQL singleton row (`node_settings`, `id='default'`). **There are no environment variables for MQTT.**

---

## 2. Roles & Terminology

| Term | Meaning |
|---|---|
| `local_server` | The site (local) Java service, i.e. `_workspace_freepark/local_server`, default HTTP port 8081 |
| Cloud | The management backend that pushes configuration and monitors heartbeats (`freepark-cloud-simple-backend`) |
| Cloud broker | The MQTT broker between cloud and edge (upstream from the perspective of `local_server`) |
| Edge node / node | A `local_server` instance running in EDGE mode, identified by `nodeCode` |
| `nodeCode` | Node code created in the cloud "edge node management"; only `[A-Za-z0-9_-]`, length ≤ 64; used as the last topic segment |

---

## 3. Architecture & Data Flows

```
             ┌────────────────────┐          MQTT (one cloud broker; the `freepark-mosquitto` container in dev/staging)
             │   Cloud backend    │
             │ (heartbeat monitor │
             │  + config dispatch │
             │  + session ingest) │
             └─────────▲──────────┘
                       │
                       │ ① heartbeat edge.heartbeat/1 (QoS 1, edge publishes every 10s)
                       │    topic parking/heartbeat/{nodeCode}
                       │ ② config sync edge.config.sync/3 (QoS 1, cloud publishes, edge subscribes)
                       │    topic {configSyncTopicPrefix}/{nodeCode} (full/delta frames)
                       │ ③ parking session edge.parking.session/1 (QoS 1, edge periodic backfill)
                       │    topic parking/report/{nodeCode} (drains pending rows every 10s)
                       │
             ┌─────────┴──────────┐
             │  local_server (EDGE)│
             │  ├ EdgeHeartbeatReporter (sends ①)
             │  ├ CloudConfigSyncSubscriber (receives ②)
             │  └ ParkingSessionSyncReporter (sends ③)
             └────────────────────┘
```

Three MQTT data flows (directions are relative to `local_server`):

1. **edge → cloud (heartbeat)**: proves the node is online; the cloud uses it to decide whether the node (and the lots it manages) is online/offline.
2. **cloud → edge (config sync)**: lot configuration (lot, lanes, blacklist, whitelist, plate patterns, internal vehicles, spaces) is pushed by the cloud and applied locally.
3. **edge → cloud (parking-session report)**: entry/exit/void session changes are flagged "pending" in the local transaction; the reporter periodically backfills a full snapshot and the cloud idempotently upserts it into `parking_session`.

All three links use **the same cloud broker**: `local_server` stores a single "cloud broker" address; the three clients use different clientIds (see §6) so they do not kick each other.

---

## 4. Cloud Broker (Mosquitto) Deployment & Credentials

The MQTT server that `local_server` talks to is the "cloud broker". In dev/staging the Mosquitto container from `_workspace_freepark/docker-compose.yml` (NOT `local_server/docker-compose.yml`) plays that role (both the cloud backend and `local_server` connect to it):

| Item | Value |
|---|---|
| Service | `mosquitto`, image `eclipse-mosquitto:2`, container `freepark-mosquitto` |
| Ports | `1883:1883` (MQTT); `9001:9001` is mapped but **no websocket listener is configured** — do not rely on it |
| Auth | `allow_anonymous false`, password file `/mosquitto/config/pwfile` |
| Account | Single user: `freepark` / `freepark` |
| Config | `./mosquitto/config/mosquitto.conf`; data/log persisted under `./mosquitto/{data,log}` |

Start with: `docker compose up -d` (run inside `_workspace_freepark`).

---

## 5. Configuring local_server MQTT over REST

All MQTT-related settings live in MySQL singleton rows; **no environment variables**. HTTP prefix is `/api/v1`. Except for the allow-listed endpoints (login, etc.) every request requires a JWT (`Authorization: Bearer <token>`); the write endpoints of `node-settings` additionally require the **ADMIN** role. Default local login: `admin` / `admin123` via `POST /api/v1/auth/login`.

### Cloud-link settings: `/api/v1/node-settings`

- `GET /api/v1/node-settings` — read (password is never returned; only the boolean `mqttPasswordSet`).
- `PUT /api/v1/node-settings` — update (JWT + ADMIN).

Request body fields (`mode` required; in EDGE mode `mqttHost` and `nodeCode` are required, the rest have defaults):

| JSON field | Type | Required | Default / notes |
|---|---|---|---|
| `mode` | string | yes | `OFFLINE` \| `EDGE`; MQTT links only start in EDGE mode |
| `mqttHost` | string | required in EDGE | Cloud broker host, e.g. `127.0.0.1` |
| `mqttPort` | int | no | default `1883`; range 1–65535 |
| `mqttClientId` | string | no | default `freepark-local-edge` (≤128) |
| `mqttUsername` | string | no | Broker username (may be empty) |
| `mqttPassword` | string | no | **Only overwritten when non-empty**; never returned by queries/responses |
| `mqttTopicPrefix` | string | no | Heartbeat topic prefix, default `parking/heartbeat` (trailing `/` stripped on save). Heartbeat topic = `{prefix}/{nodeCode}`, so the prefix must contain a `heartbeat` segment to match the cloud subscription `{prefix}/#` |
| `configSyncTopicPrefix` | string | no | Config-sync subscription prefix; **empty = do not subscribe to config sync**. When non-empty the subscribed topic is `{prefix}/{nodeCode}` |
| `reportTopicPrefix` | string | no | Parking-session report topic prefix, default `parking/report` (trailing `/` stripped on save). Report topic = `{prefix}/{nodeCode}`, so the prefix must contain a `report` segment to match the cloud subscription `{prefix}/#`; **empty = do not report sessions** |
| `nodeCode` | string | required in EDGE | Cloud node code; only `[A-Za-z0-9_-]`, ≤64 |
| `feeApiUrl` / `feeMockEnabled` / `feeMockAmount` | - | no | Fee-quote feature, unrelated to MQTT |

Save example:

```
curl -X PUT http://localhost:8081/api/v1/node-settings \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @body.json
```

```json
{
  "mode": "EDGE",
  "mqttHost": "127.0.0.1",
  "mqttPort": 1883,
  "mqttClientId": "freepark-local-edge",
  "mqttUsername": "freepark",
  "mqttPassword": "freepark",
  "mqttTopicPrefix": "parking/heartbeat",
  "configSyncTopicPrefix": "parking/config-sync",
  "reportTopicPrefix": "parking/report",
  "nodeCode": "node-001"
}
```

> Typical success envelope (project-wide response wrapper): `{ "success": true, "code": "ok", "message": "...", "data": { …NodeSettingsView } }`. Inside `data`, `mqttPassword` is represented by `mqttPasswordSet: true`.

Changes take effect without restart: `EdgeHeartbeatReporter` / `CloudConfigSyncSubscriber` / `ParkingSessionSyncReporter` each re-check their desired connection parameters (host/port/clientId/username/topic) every 10 seconds and reconnect automatically when parameters change; switching back to `OFFLINE` or clearing required fields disconnects them.

---

## 6. Client Connection Contract (must-read for implementers)

All three clients share the same Paho behaviour. A simulating/self-built peer must understand:

| Item | Value | Impact |
|---|---|---|
| `cleanSession` | `true` | **The broker stores no session or subscriptions.** After a reconnect the client must SUBSCRIBE again (the edge does this automatically in `MqttCallbackExtended.connectComplete`). Do **not** rely on retained messages / offline queues |
| `automaticReconnect` | `true` | Auto-reconnects on network failure; subscriptions are re-established in the reconnect callback |
| `keepAliveInterval` | 30s | Keep-alive |
| `connectionTimeout` | 8s | TCP connect timeout |
| Persistence | `MemoryPersistence` | No on-disk queue |

ClientId rules (must not collide on the same broker, otherwise clients kick each other):

| Client | clientId | Role |
|---|---|---|
| Heartbeat | `{mqttClientId}` (default `freepark-local-edge`) | PUBLISH only, no callback |
| Config sync | `{mqttClientId}-cfg` (default `freepark-local-edge-cfg`) | SUBSCRIBE only |
| Parking-session report | `{mqttClientId}-rec` (default `freepark-local-edge-rec`) | PUBLISH only, no callback |

---

## 7. Topic Summary

| Topic pattern | Direction (relative to local_server) | Protocol / payload | QoS | Retain | Notes |
|---|---|---|---|---|---|
| `{mqttTopicPrefix}/{nodeCode}` (default `parking/heartbeat/{nodeCode}`) | out (edge→cloud) | `edge.heartbeat/1` | 1 | no | one heartbeat every 10s |
| `{reportTopicPrefix}/{nodeCode}` (default `parking/report/{nodeCode}`) | out (edge→cloud) | `edge.parking.session/1` | 1 | no | full snapshot backfill after session changes, see §10 |
| `{configSyncTopicPrefix}/{nodeCode}` (e.g. `parking/config-sync/{nodeCode}`) | in (cloud→edge) | `edge.config.sync/3` (full/delta frames) | 1 | frames not retained | before every full push the cloud first sends a zero-byte retained clear message |

Within frames, `edgeCode` must equal the `nodeCode` in the last topic segment, otherwise the frame is dropped.

---

## 8. Protocol 1: Heartbeat `edge.heartbeat/1` (edge → cloud)

Implementation: `EdgeHeartbeatReporter` (`src/main/java/com/freepark/local/edge/service/`).

- Trigger: after application-ready a single-thread scheduler starts; first tick after 5s, then **every 10s**; whenever connected, one heartbeat is published per tick.
- Idle conditions (if any holds, no message is sent and an existing connection is dropped): not EDGE mode; `mqttHost`/`mqttPort` invalid; `nodeCode` empty or containing illegal characters.
- Topic: `stripTrailingSlash(mqttTopicPrefix) + "/" + nodeCode`.
- QoS = 1, retained = false.

Payload (UTF-8 JSON, exactly 3 fields):

| Field | Type | Notes |
|---|---|---|
| `schema` | string | always `edge.heartbeat/1` |
| `edgeCode` | string | node code (same as the last topic segment) |
| `reportedAt` | string | ISO-8601 UTC instant, e.g. `2026-09-08T01:23:45.678Z` |

Example:

```json
{"schema":"edge.heartbeat/1","edgeCode":"node-001","reportedAt":"2026-09-08T01:23:45.678Z"}
```

**Peer (cloud-side) contract**: subscribe to `{mqttTopicPrefix}/#` (e.g. `parking/heartbeat/#`) to receive heartbeats from every node. Online state is derived from the latest heartbeat; a timeout threshold of ≥ 90 seconds is suggested (the edge itself does not consume heartbeats — this semantics belongs entirely to the cloud).

---

## 9. Protocol 2: Config Sync `edge.config.sync/3` (cloud → edge)

Implementation: `CloudConfigSyncSubscriber` (frame reception / aggregation), `ConfigSyncApplyService` (apply & persist).

### 9.1 Frame envelope (shared by full and delta)

| Field | Type | Required | Notes |
|---|---|---|---|
| `schema` | string | yes | always `edge.config.sync/3` |
| `edgeCode` | string | yes | target node code; **must equal the last topic segment**, otherwise dropped |
| `snapshotId` | string | yes | unique ID (UUID) of one dispatch batch; all frames of the batch share it |
| `version` | int | yes | protocol version, always `3` |
| `generatedAt` | string | yes | ISO-8601 UTC instant |
| `kind` | string | yes | `full` or `delta` |
| `seq` | int | yes | frame sequence, starting at 1 |
| `total` | int | yes | total frames in this batch |
| `lot` | string | yes for full/delta | lot code. For an empty node snapshot (node owns no lots) it is **omitted**, with `domain=lot` and `items=[]` |
| `domain` | string | yes | business domain, see §9.3 |
| `items` | array | yes | full: array of domain items; delta: array of `{op,…}` change entries |

**Framing rule**: frames of one dispatch are numbered `seq` 1..total, share one `snapshotId`, and go to the same topic (MQTT guarantees ordering over one TCP connection). A frame carries at most 1000 items; larger domains are automatically split.

**Edge aggregation & apply**:
1. On each frame → validate the envelope (schema/edgeCode/kind/seq/total).
2. Buffer in memory keyed by `snapshotId`, dedup by `seq`.
3. When complete (`frames.size()==total` and contains both `seq=1` and `seq=total`) the batch is sorted by `seq` and handed to the applier as a whole.
4. Incomplete batches are **never partially applied**; the edge waits for the next full round. Buffers expire after 10 minutes (max 8 batches).
5. Zero-byte (empty payload) messages are ignored — they are the retained-clear messages from the cloud, see §9.5.

### 9.2 kind = `full` (per-domain replacement snapshot)

A set of full frames is the **authoritative snapshot** for the node. Semantics (important):

- Frames are grouped by `lot` (lot code); lots present in the snapshot are the "cloud-managed lots".
- For each lot and each business domain, the domain is **fully replaced**:
  1. delete local rows of that lot/domain that are NOT in the retained set (the `id`s carried by the snapshot) — including locally-created rows (`cloud_id` is null);
  2. then upsert every item by `id` (stored locally as `cloud_id`).
- A domain **absent from the snapshot is treated as empty on the cloud** → the local domain is cleared.
- Lots absent from the snapshot are only removed if they were ever cloud-managed (at least one row carried a `cloud_id`); purely local lots are kept.
- The `id` inside items is the cloud PK and is used by the edge as `cloud_id` for idempotent upsert/delete; local UUID PKs and external references are preserved.
- The `lot` frame has no numeric id; the lot is located by `code` and its config is overwritten directly (item shape in §9.3).
- When a node owns **no lots**, the cloud still publishes 1 empty snapshot frame (no `lot` field, `domain=lot`, `items=[]`) so the edge can clean up configuration of detached lots.

**Dispatch timing** (controlled by the cloud):
- Periodic full: default every **86400 seconds (24 h)** (`configSyncIntervalSeconds`) — not 30 seconds;
- heartbeat offline→online transition: one full snapshot immediately;
- node↔lot binding change: full snapshot immediately (detached nodes receive an empty snapshot to clear);
- manual "sync now";
- business data changes: kind=`delta` (§9.4).

### 9.3 The seven domains and item fields

Domains (enum): `lot`, `lane`, `blacklist`, `pattern`, `whitelist`, `internal`, `space`.

> General serialization rules: nullable strings/times are **omitted when empty** (absent = empty); enums are serialized as `name()`; time is a "local date-time text without timezone suffix" (see below). `items[]` holds item objects; `id` is the cloud PK (number).

**Time format**: business times (`startTime`/`endTime`) are serialized with `LocalDateTime.toString()`, e.g. `2026-09-08T10:30` or `2026-09-08T10:30:00`, **no timezone suffix**; the edge parses them as local time in its configured timezone and converts to an Instant (on failure it falls back to ISO-8601 with offset).

#### lot — the lot itself (no numeric id; located by `code`; one lot frame per lot in full; no delete)

| Field | Type | Notes |
|---|---|---|
| `code` | string | lot code |
| `name` | string | lot name |
| `lotType` | string | `INTERNAL` \| `PUBLIC` |
| `enabled` | bool | enabled |
| `entryInterceptArrears` / `entryInterceptBlacklist` | bool | entry interception switches |
| `exitInterceptArrears` / `exitInterceptBlacklist` | bool | exit interception switches |
| `judgmentOrder` | array\<string\> | access-judgment order; elements: `PATTERN_ALLOWLIST` \| `BLACKLIST` \| `WHITELIST` |
| `updatedAt` | string | ignore |

#### blacklist

| Field | Type | Notes |
|---|---|---|
| `id` | number | **cloud PK** (used to locate upsert/delete) |
| `plateNumber` | string | plate number (required; empty string as fallback) |
| `plateColor` | string | plate-color enum name (see §9.6); missing/invalid falls back to `BLUE` |
| `ownerName` | string | owner name |
| `phone` `department` `remark` | string? | optional; omitted when empty |
| `startTime` `endTime` | string? | validity window, format above |
| `enabled` | bool | default `true` |

#### pattern — plate-segment allow rules (regex lists)

| Field | Type | Notes |
|---|---|---|
| `id` | number | cloud PK |
| `name` | string | rule name |
| `pattern` | string | match pattern (regex / segment) |
| `remark` | string? | remark |
| `enabled` | bool | default `true` |

#### whitelist (parking passes)

Fields: `id`, `plateNumber`, `plateColor`, `ownerName`, `type` (VehicleType enum, see §9.6), `phone?`, `department?`, `remark?`, `startTime?`, `endTime?`, `enabled` (default true).

#### internal — internal vehicles

Same as whitelist plus optional `batchId?` (string, UUID text, omitted when empty); no time-window fields.

#### lane — lanes (gates / entry-exit points)

| Field | Type | Notes |
|---|---|---|
| `id` | number | **cloud PK** (used to locate upsert/delete) |
| `name` | string | lane name |
| `code` | string | lane code (globally unique; required on insert; never changed by cloud update) |
| `laneType` | string | `ENTRANCE` \| `EXIT` \| `BIDIRECTIONAL` (missing/invalid falls back to `ENTRANCE`) |
| `linkedLotCode` | string? | code of the linked opposite lot (for bidirectional lanes); no link is created when the local lot is absent or empty |
| `enabled` | bool | default `true` |

#### space — space management (three flat layers: location / area / space)

Every layer item carries a `type` discriminator:

| type | Fields | Notes |
|---|---|---|
| `location` | `type`=`location`, `id`, `name` | location |
| `area` | `type`=`area`, `id`, `name`, `locationId` | area; `locationId` = parent location cloud id |
| `space` | `type`=`space`, `id`, `code`, `enabled`, `areaId` | space; `areaId` = parent area cloud id |

Apply order: location → area → space (parents must exist first; items whose parent is missing are skipped and picked up by the next full round).

### 9.4 kind = `delta` (incremental changes)

A delta batch shares one envelope (same snapshotId, total frames). Each frame's `items[]` holds change entries with two ops:

| op | Payload shape | Semantics |
|---|---|---|
| `upsert` | `{"op":"upsert","item":{…same item shape as §9.3…}}` | overwrite the row entirely by its own `id` (insert if missing) |
| `delete` | `{"op":"delete","id":<cloud PK>}` | delete the row by `id` (cloud_id) for that domain; **never used for the `lot` domain** |

Applying resolves the local lot via `frame.lot`; if the lot is not present locally (delta arrived before the first full, e.g. right after an edge restart), that delta frame is dropped and the edge waits for the next full round.

### 9.5 Retained messages and the "clear message"

- v3 frames themselves are **not retained**.
- Before every full dispatch the cloud first publishes a **zero-byte, retain=true** message on the node topic to clear any legacy v2 retained snapshot.
- On subscribe/reconnect the broker replays this empty retained message — **empty payloads must be ignored** (the edge already does).

### 9.6 Shared enums

| Enum | Values |
|---|---|
| `lotType` | `INTERNAL`, `PUBLIC` |
| vehicle `type` (whitelist/internal) | `TEMPORARY`, `RESERVED`, `VIP`, `OWNER`, `MONTHLY`, `OTHER` (unknown falls back to `OTHER`) |
| `judgmentOrder` elements | `PATTERN_ALLOWLIST`, `BLACKLIST`, `WHITELIST` |
| `plateColor` | parsed by the edge as a local `PlateColor` enum name: `BLUE`, `YELLOW`, `GREEN`, `YELLOW_GREEN`, `BLACK`, `WHITE`, etc. (`valueOf` failure falls back to `BLUE`) |
| `domain` | `lot`, `lane`, `blacklist`, `pattern`, `whitelist`, `internal`, `space` |

### 9.7 Complete wire samples

For lot `P001` with one blacklist row, split into total=3 (in practice frames = lots × domains × splits):

Frame 1/3 (lot domain):
```json
{
  "schema": "edge.config.sync/3", "edgeCode": "node-001",
  "snapshotId": "8f1c…", "version": 3, "generatedAt": "2026-09-08T01:00:00Z",
  "kind": "full", "seq": 1, "total": 3, "lot": "P001",
  "domain": "lot",
  "items": [
    { "code": "P001", "name": "Demo Lot", "lotType": "INTERNAL", "enabled": true,
      "entryInterceptArrears": false, "entryInterceptBlacklist": true,
      "exitInterceptArrears": false, "exitInterceptBlacklist": true,
      "judgmentOrder": ["BLACKLIST", "WHITELIST", "PATTERN_ALLOWLIST"] }
  ]
}
```

Frame 2/3 (blacklist domain):
```json
{
  "schema": "edge.config.sync/3", "edgeCode": "node-001",
  "snapshotId": "8f1c…", "version": 3, "generatedAt": "2026-09-08T01:00:00Z",
  "kind": "full", "seq": 2, "total": 3, "lot": "P001",
  "domain": "blacklist",
  "items": [
    { "id": 3001, "plateNumber": "ZHE B12345", "plateColor": "BLUE",
      "ownerName": "Zhang San", "phone": "13800000000",
      "startTime": "2026-09-01T00:00", "endTime": "2027-09-01T00:00", "enabled": true }
  ]
}
```

Frame 3/3 (space domain, all three layers in one frame):
```json
{
  "schema": "edge.config.sync/3", "edgeCode": "node-001",
  "snapshotId": "8f1c…", "version": 3, "generatedAt": "2026-09-08T01:00:00Z",
  "kind": "full", "seq": 3, "total": 3, "lot": "P001",
  "domain": "space",
  "items": [
    { "type": "location", "id": 10, "name": "Building A" },
    { "type": "area",     "id": 20, "name": "Zone A-1", "locationId": 10 },
    { "type": "space",    "id": 30, "code": "A101", "enabled": true, "areaId": 20 }
  ]
}
```

Delta example (upsert one whitelist row):
```json
{
  "schema": "edge.config.sync/3", "edgeCode": "node-001",
  "snapshotId": "9a2f…", "version": 3, "generatedAt": "2026-09-08T02:00:00Z",
  "kind": "delta", "seq": 1, "total": 1, "lot": "P001", "domain": "whitelist",
  "items": [
    { "op": "upsert", "item": { "id": 4001, "plateNumber": "ZHE B66666", "plateColor": "BLUE",
        "ownerName": "Li Si", "type": "MONTHLY", "enabled": true } }
  ]
}
```

Delete example (delete an internal vehicle by cloud PK):
```json
{
  "schema": "edge.config.sync/3", "edgeCode": "node-001",
  "snapshotId": "9a2f…", "version": 3, "generatedAt": "2026-09-08T02:05:00Z",
  "kind": "delta", "seq": 1, "total": 1, "lot": "P001", "domain": "internal",
  "items": [ { "op": "delete", "id": 5001 } ]
}
```

---

## 10. Protocol 3: Parking-Session Report `edge.parking.session/1` (edge → cloud)

Implementation: `ParkingSessionSyncReporter` (`src/main/java/com/freepark/local/edge/service/`); cloud peer receiver: `EdgeParkingSessionReceiver` (`freepark-cloud-simple-backend`, `…/parking/edge/`).

- Purpose: sync local parking-session state changes (entry creates `OPEN`, exit closes `CLOSED`, void `VOIDED`) into the cloud `parking_session` table.
- Timing & reliability (important):
  - Every session state change (entry/exit/void) flags the row as "pending" (`sync_pending=true`) **inside the same local transaction**.
  - The reporter's first tick runs 5s after startup, then **every 10s**: while connected it drains at most 200 pending sessions per round, publishing one full snapshot each.
  - Each snapshot is published with **QoS 1, retained=false** to `stripTrailingSlash(reportTopicPrefix) + "/" + nodeCode` (default `parking/report/{nodeCode}`).
  - After a successful publish (broker PUBACK), the pending flag is cleared **only if** the session still matches the just-published snapshot (state/times unchanged, i.e. not modified concurrently). Changes made while offline / broker unavailable stay pending and are backfilled automatically after reconnecting — **nothing is lost**.
  - Later changes to the same session publish a fresh "full latest state" snapshot; the cloud upserts idempotently by key, so duplicate backfills never create duplicate rows.
- Idle conditions (if any holds, no publish and any existing connection is dropped): not EDGE mode; `mqttHost`/`mqttPort` invalid; `nodeCode` empty or containing illegal characters; `reportTopicPrefix` empty.
- Payload (UTF-8 JSON; nullable strings/times are **omitted when empty**):

| Field | Type | Required | Notes |
|---|---|---|---|
| `schema` | string | yes | always `edge.parking.session/1` |
| `edgeCode` | string | yes | node code; **must equal the last topic segment**, otherwise the cloud drops it |
| `sessionId` | string | yes | edge-local session UUID text; with `edgeCode` it forms the cloud idempotency key |
| `lotCode` | string | yes | lot code (the local session's linked lot `code`) |
| `lotName` | string | no | lot name (omitted when empty) |
| `plateNumber` | string | yes | plate number |
| `plateColor` | string | no | plate-color enum name (e.g. `BLUE`/`GREEN`; omitted when empty) |
| `status` | string | yes | `OPEN` \| `CLOSED` \| `VOIDED` (enum `name()`) |
| `entryTime` | string | yes | ISO-8601 UTC (Instant text with `Z` suffix) |
| `exitTime` | string | no | ISO-8601 UTC; omitted while the session is still open |
| `entryLaneName` | string | no | entry lane name (omitted when empty) |
| `exitLaneName` | string | no | exit lane name (omitted when empty) |
| `reportedAt` | string | yes | report time, ISO-8601 UTC |

Example (a closed full snapshot):
```json
{"schema":"edge.parking.session/1","edgeCode":"node-001","sessionId":"c9a0f7a1-…","lotCode":"P001","lotName":"Demo Lot","plateNumber":"ZHE B12345","plateColor":"BLUE","status":"CLOSED","entryTime":"2026-09-08T01:00:00Z","exitTime":"2026-09-08T08:23:45Z","entryLaneName":"Entry 1","exitLaneName":"Exit 1","reportedAt":"2026-09-08T08:24:01Z"}
```

**Peer (cloud-side) contract**:
1. Subscribe to `{reportTopicPrefix}/#` (default `parking/report/#`); on arrival verify `schema=edge.parking.session/1` and that the payload `edgeCode` equals the node code in the last topic segment, otherwise ignore.
2. Locate the cloud lot by `lotCode`; the lot must be bound to that node, otherwise drop (prevents cross-node contamination).
3. Upsert by the idempotency key `edgeCode + sessionId`: insert if missing, otherwise overwrite entirely with the snapshot's latest state (`CLOSED` without a payment record defaults to `UNPAID`; `VOIDED` clears pay status/time).
4. Edge times are ISO-8601 UTC (with `Z`) and stored as UTC on the cloud. The v1 payload carries **no images** and no lane/recognition internal IDs (the cloud has no matching PKs; only name snapshots are stored).
5. Reliability boundary: the edge clears its pending flag as soon as the broker PUBACKs; a cloud DB failure is only recovered via logs/ops — there is no end-to-end retry.

---

## 11. Integration Verification Handbook (executable by AI / testers)

Prerequisites: broker up; login `freepark` / `freepark`.

1) **Receive heartbeats** (one every ~10s):
```
docker exec freepark-mosquitto mosquitto_sub -t "parking/heartbeat/#" -u freepark -P freepark -v
```

2) **Manually emulate a cloud config-sync frame** (node `node-001`, subscription prefix `parking/config-sync` → topic `parking/config-sync/node-001`):
```
docker exec freepark-mosquitto mosquitto_pub -t "parking/config-sync/node-001" -u freepark -P freepark \
  -m '{"schema":"edge.config.sync/3","edgeCode":"node-001","snapshotId":"test-1","version":3,"generatedAt":"2026-09-08T01:00:00Z","kind":"full","seq":1,"total":1,"lot":"P001","domain":"lot","items":[{"code":"P001","name":"Test Lot","lotType":"INTERNAL","enabled":true,"judgmentOrder":["BLACKLIST","WHITELIST","PATTERN_ALLOWLIST"]}]}'
```
Verify: look at the local DB or the local_server log (`config sync frames received, applying snapshot…`). For a full snapshot make sure all `seq..total` frames arrive — otherwise the edge waits for the next round.

3) **Receive parking-session reports**: first complete an entry on `local_server` (or mark a row pending directly in the DB); the reporter backfills every 10s, and you should receive full snapshots:
```
docker exec freepark-mosquitto mosquitto_sub -t "parking/report/#" -u freepark -P freepark -v
```
Verify: the log shows `reported parking session and cleared pending flag`; the cloud `parking_session` table should contain that session (with `edge_node_code`/`edge_session_id` filled).

4) **Common checks when the link does not work**: confirm `mode=EDGE`, `mqttHost/mqttPort`, `nodeCode`, `configSyncTopicPrefix`/`reportTopicPrefix` are saved (GET node-settings); check the broker log to see whether the client authenticated (`allow_anonymous false` rejects wrong credentials); confirm heartbeat/config-sync/report clientIds do not collide with other clients.

---

## 12. Critical Constraints Checklist (read before implementing/simulating)

1. **Do not invert the directions**: heartbeats are *published* by local_server; config sync is *subscribed* by local_server; parking sessions are *published* by local_server. The cloud does the opposite (receive heartbeats, send config, receive sessions).
2. **Config-sync batches must arrive whole**: seq 1..total with one snapshotId; the edge silently waits for the next round on incomplete batches and discards them after 10 minutes. To test a single delta use `total=1`.
3. **`edgeCode` must equal the nodeCode in the last topic segment**, otherwise the frame is dropped.
4. **Ignore zero-byte retained clear messages** (replayed on subscribe/reconnect).
5. **cleanSession=true**: after a reconnect the edge re-SUBSCRIBEs (no retained re-delivery from the cloud needed), but any message published while offline is lost — the cloud model compensates with a "full snapshot on recovery" rather than QoS 1 offline queues.
6. Heartbeat, config sync and parking-session report use **different clientIds** on the same broker; don't reuse `freepark-local-edge` / `freepark-local-edge-cfg` / `freepark-local-edge-rec` when simulating.
7. **Business time fields carry no timezone suffix** (Protocol 2) and are interpreted in the edge's local timezone; `generatedAt`/`reportedAt` and Protocol 3 session times are ISO-8601 with `Z` (UTC).
8. **`nodeCode` charset** `[A-Za-z0-9_-]`, ≤64; configs containing `/`, wildcards or whitespace are rejected by REST validation.
9. Heartbeat topic prefix defaults to `parking/heartbeat` (contains the `heartbeat` segment); the cloud subscription surface is `parking/heartbeat/#`. Changing the prefix requires updating the cloud subscription accordingly.
10. Leaving `configSyncTopicPrefix` empty = the node never subscribes to config sync (for heartbeat-only nodes).
11. Leaving `reportTopicPrefix` empty = no parking sessions are reported. The report chain is "flag pending in the local transaction + periodic backfill + QoS 1"; the edge clears the pending flag right after PUBACK, so **the cloud must upsert idempotently by `edgeCode + sessionId`** and tolerate duplicate / out-of-order snapshot overwrites.

---

## 13. Reference Code Locations

| Concern | File |
|---|---|
| Heartbeat publisher (edge→cloud) | `local_server/…/edge/service/EdgeHeartbeatReporter.java` |
| Parking-session publisher (edge→cloud) | `local_server/…/edge/service/ParkingSessionSyncReporter.java`; cloud receiver `freepark-cloud-simple-backend/…/parking/edge/EdgeParkingSessionReceiver.java` |
| Config-sync subscriber / frame aggregation (cloud→edge) | `local_server/…/configsync/CloudConfigSyncSubscriber.java` |
| Config-sync apply (full/delta → all domains) | `local_server/…/configsync/ConfigSyncApplyService.java` |
| Node-settings entity / defaults | `local_server/…/domain/NodeSettings.java` |
| Node-settings REST/service | `local_server/…/nodeconfig/controller/NodeConfigController.java`, `…/service/NodeConfigService.java` |
| Cloud protocol constants / dispatcher (peer reference) | `freepark-cloud-simple-backend/…/settings/runtime/EdgeConfigSyncProtocol.java`, `EdgeConfigSyncDispatcher.java` |
| Cloud item shapes (peer reference) | `freepark-cloud-simple-backend/…/parking/edge/EdgeDomainItems.java` |
| Cloud session idempotency key / unique constraint | `freepark-cloud-simple-backend/…/parking/entity/ParkingSession.java` (`uk_parking_session_edge`) |
| Cloud broker deployment (dev/staging) | `_workspace_freepark/docker-compose.yml`, `_workspace_freepark/mosquitto/config/mosquitto.conf` |
