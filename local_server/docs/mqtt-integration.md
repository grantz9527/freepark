# FreePark 场端服务（local_server）MQTT 对接文档

> 本文档描述 `local_server`（本地/场端服务）的 MQTT 对接契约。
> 面向人群：需要把 local_server 接入自有云端 Broker、或用模拟端（含 AI Agent）驱动/测试该链路的开发人员。
> 所有主题、负载字段、时序与限制均直接来源于代码实现，字段名与枚举值请以文档为准（JSON 中未列出的字段会被忽略）。
>
> **范围**：本文档仅描述 local_server（边缘节点）与云端之间的 MQTT 对接（心跳上报 `edge.heartbeat/1`、配置同步下发 `edge.config.sync/3`、停车流水上报/下发 `edge.parking.session/1`、缴费开闸指令 `edge.gate.command/1`）；本地 AI 识别事件、设备/道闸 HTTP 对接等本地内部链路不属于本文档范围。

---

## 1. 一句话结论（TL;DR）

- local_server 在 **EDGE 模式**下会以 **四个独立 MQTT 客户端** 连到“云端 Broker”：
  - **心跳上报**：clientId = `{mqttClientId}`（默认 `freepark-local-edge`），每 10 秒向 `{mqttTopicPrefix}/{nodeCode}` 发布 `edge.heartbeat/1`（默认主题 `parking/heartbeat/{nodeCode}`），QoS 1、不 retain。
  - **配置同步订阅**：clientId = `{mqttClientId}-cfg`（默认 `freepark-local-edge-cfg`），订阅 `{configSyncTopicPrefix}/{nodeCode}`，接收云端下发的 `edge.config.sync/3` 全量/增量分帧，QoS 1。
  - **停车流水上报**：clientId = `{mqttClientId}-rec`（默认 `freepark-local-edge-rec`），每 10 秒把待同步的停车流水向 `{reportTopicPrefix}/{nodeCode}` 发布完整快照 `edge.parking.session/1`（默认主题 `parking/report/{nodeCode}`），QoS 1、不 retain，见 §10 协议三。
  - **指令订阅**：clientId = `{mqttClientId}-cmd`（默认 `freepark-local-edge-cmd`），订阅 `{commandTopicPrefix}/{nodeCode}`（默认 `parking/command/{nodeCode}`），接收缴费开闸 `edge.gate.command/1`（§11）以及云端改流水后的下行快照 `edge.parking.session/1` origin=`CLOUD`（§10.1），QoS 1。
- **本文档只描述“边缘节点 ↔ 云端”的 MQTT 对接**（心跳上报 + 配置同步下发 + 停车流水上报/下发 + 缴费开闸）；本地 AI 识别事件、设备/道闸 HTTP 对接等本地内部链路不在本文档范围。
- 四个客户端统一使用 Eclipse Paho v1.2.5，连接参数：`cleanSession=true`、`automaticReconnect=true`、`connectionTimeout=8s`、`keepAliveInterval=30s`。
- 所有 MQTT 配置都**没有环境变量**，只能通过 local_server 的 REST 接口运行时写入 MySQL（`node_settings` 单行 `id='default'`）。

---

## 2. 术语与角色

| 术语 | 含义 |
|---|---|
| `local_server` | 场端（本地）Java 服务，本仓库 `_workspace_freepark/local_server`，默认 HTTP 端口 8081 |
| 云端 / Cloud | 管理端后端，负责下发配置、接收心跳（本仓库 `freepark-cloud-simple-backend`） |
| 云端 Broker | 云端与边缘通信用的 MQTT Broker（对 local_server 而言是"上游"） |
| 边缘节点 / node | 一个 local_server 实例（EDGE 模式），以 `nodeCode` 标识 |
| `nodeCode` | 云端"边缘节点管理"里创建的节点编号；只允许 `[A-Za-z0-9_-]`，长度 ≤ 64；用于拼主题末段 |

---

## 3. 架构与数据流总览

```
             ┌────────────────────┐          MQTT（云端 Broker；不在本仓库场端 Docker 中部署）
             │      云端后端        │
             │ （心跳监控 + 配置下发 │
             │   + 停车流水上下行   │
             │   + 缴费开闸下发）    │
             └─────────▲──────────┘
                       │
                       │ ① 心跳 edge.heartbeat/1（QoS 1，edge 每 10s 发布）
                       │    主题 parking/heartbeat/{nodeCode}
                       │ ② 配置同步 edge.config.sync/3（QoS 1，云端发布、edge 订阅）
                       │    主题 {configSyncTopicPrefix}/{nodeCode}（full/delta 分帧）
                       │ ③ 停车流水上报 edge.parking.session/1（QoS 1，edge 周期补推）
                       │    主题 parking/report/{nodeCode}（每 10s 清一批待同步）
                       │ ④ 开闸指令 edge.gate.command/1（QoS 1，云端缴费成功后发布）
                       │    主题 parking/command/{nodeCode}
                       │ ⑤ 云端流水下发 edge.parking.session/1 origin=CLOUD（QoS 1）
                       │    主题 parking/command/{nodeCode}（与 ④ 共用订阅）
                       │
             ┌─────────┴──────────┐
             │  local_server（EDGE）│
             │  ├ EdgeHeartbeatReporter（发 ①）
             │  ├ CloudConfigSyncSubscriber（收 ②）
             │  ├ ParkingSessionSyncReporter（发 ③）
             │  └ CloudGateCommandSubscriber（收 ④⑤）
             └────────────────────┘
```

四条 MQTT 数据流（注意方向，都是相对 local_server）：

1. **edge → cloud（心跳）**：证明节点在线，云端据此判定节点及其管辖车场在线/离线。
2. **cloud → edge（配置同步）**：车场配置（车场、通道、黑白名单、放行规则、内部车、车位）由云端下发生效。
3. **edge → cloud（停车流水上报）**：入场/出场/作废等流水状态变化在本地事务内打上“待同步”标记，上报器周期补推完整快照，云端幂等 upsert 到 `parking_session`。
4. **cloud → edge（缴费开闸）**：道闸欠费拦截后用户在云端缴清，云端向该车场绑定节点下发 `OPEN`，边缘匹配闸前拦截记录并主动开闸。
5. **cloud → edge（停车流水下发）**：管理端新增/编辑/作废/算费/收退款写入云端流水后，向该车场绑定节点下发完整快照；边缘按 `sessionId` 或 `cloudId` upsert，并清待同步标记，避免过期上报把云端改动盖回去。

心跳、配置同步、停车流水上报与指令订阅（开闸 + 流水下发）连**同一条云端 Broker**：local_server 只保存一个"云端 Broker 地址"，四个客户端用不同 clientId（见 §6），避免互踢。

---

## 4. 云端 Broker

local_server 对接的 MQTT 服务器是**云端 Broker**，由云侧部署，**本仓库场端 Docker 不含 Mosquitto**。EDGE 模式下在「节点配置」填写 Broker 地址与账号。

---

## 5. 给 local_server 配置 MQTT（REST）

所有 MQTT 相关设置都保存在 MySQL 单例行，**不支持环境变量**。HTTP 前缀 `/api/v1`，除登录等白名单外均需 JWT（`Authorization: Bearer <token>`）；`node-settings` 的写接口还要求 **ADMIN** 角色。本地默认登录：`admin / admin123`，登录接口 `POST /api/v1/auth/login`。

### 云端链路设置：`/api/v1/node-settings`

- `GET /api/v1/node-settings` —— 查询（密码不回传，只给 `mqttPasswordSet` 布尔）。
- `PUT /api/v1/node-settings` —— 更新（JWT + ADMIN）。

请求体字段（`mode` 必填；EDGE 模式下 `mqttHost`、`nodeCode` 必填，其余有默认值）：

| JSON 字段 | 类型 | 必填 | 默认/取值说明 |
|---|---|---|---|
| `mode` | string | 是 | `OFFLINE` \| `EDGE`；非 EDGE 时 MQTT 链路不启用 |
| `mqttHost` | string | EDGE 必填 | 云端 Broker 主机，如 `127.0.0.1` |
| `mqttPort` | int | 否 | 默认 `1883`；范围 1–65535 |
| `mqttClientId` | string | 否 | 默认 `freepark-local-edge`（≤128） |
| `mqttUsername` | string | 否 | Broker 用户名（可空） |
| `mqttPassword` | string | 否 | **仅在非空时覆盖保存**；查询/响应不回传密码 |
| `mqttTopicPrefix` | string | 否 | 心跳主题前缀，默认 `parking/heartbeat`（保存时会去掉尾部 `/`）。心跳主题=`{前缀}/{nodeCode}`，故前缀应含 `heartbeat` 段以匹配云端订阅 `{前缀}/#` |
| `configSyncTopicPrefix` | string | 否 | 配置同步订阅前缀；**为空 = 不订阅云端配置同步**。非空时订阅主题=`{前缀}/{nodeCode}` |
| `reportTopicPrefix` | string | 否 | 停车流水上报主题前缀，默认 `parking/report`（保存时会去掉尾部 `/`）。上报主题=`{前缀}/{nodeCode}`，故前缀应含 `report` 段以匹配云端订阅 `{前缀}/#`；**为空 = 不上报流水** |
| `commandTopicPrefix` | string | 否 | 指令订阅前缀（开闸 + 云端流水下发共用），默认 `parking/command`。订阅主题=`{前缀}/{nodeCode}`；请求体省略时保留已有值，从未配置则落默认值 |
| `nodeCode` | string | EDGE 必填 | 云端节点编号；仅 `[A-Za-z0-9_-]`，≤64 |
| `feeApiUrl` / `feeMockEnabled` / `feeMockAmount` | - | 否 | 算费相关，与 MQTT 无关 |

保存示例：

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
  "commandTopicPrefix": "parking/command",
  "nodeCode": "node-001"
}
```

> 典型成功响应外壳（本项目统一响应包）：`{ "success": true, "code": "ok", "message": "...", "data": { …NodeSettingsView } }`。`data` 中 `mqttPassword` 以 `mqttPasswordSet: true` 代替。

改动生效方式：`EdgeHeartbeatReporter` / `CloudConfigSyncSubscriber` / `ParkingSessionSyncReporter` / `CloudGateCommandSubscriber` 各自每 10 秒自检一次期望参数（host/port/clientId/username/topic），参数变化会自动断旧连新；切回 `OFFLINE` 或清空必填项会自动断开。**无需重启进程。**

---

## 6. 客户端连接约定（本地实现必读）

四个客户端共用同一套 Paho 行为，模拟/自研对端时必须理解：

| 连接项 | 值 | 影响 |
|---|---|---|
| `cleanSession` | `true` | **Broker 不保存会话与订阅**。断线重连后必须重新 SUBSCRIBE（本地实现通过 `MqttCallbackExtended.connectComplete` 自动补订阅）。**不能依赖 retain/离线消息兜底** |
| `automaticReconnect` | `true` | 网络抖动自动重连；重连成功回调里补订阅 |
| `keepAliveInterval` | 30s | 心跳保活 |
| `connectionTimeout` | 8s | TCP 连接超时 |
| 持久化 | `MemoryPersistence` | 无磁盘队列 |

clientId 使用规则（同 Broker 下不得冲突，否则互踢）：

| 客户端 | clientId | 用途 |
|---|---|---|
| 心跳 | `{mqttClientId}`（默认 `freepark-local-edge`） | 只 PUBLISH，无回调 |
| 配置同步 | `{mqttClientId}-cfg`（默认 `freepark-local-edge-cfg`） | 只 SUBSCRIBE |
| 停车流水上报 | `{mqttClientId}-rec`（默认 `freepark-local-edge-rec`） | 只 PUBLISH，无回调 |
| 指令订阅（开闸 + 流水下发） | `{mqttClientId}-cmd`（默认 `freepark-local-edge-cmd`） | 只 SUBSCRIBE |

---

## 7. Topic 总表

| Topic 模式 | 方向（相对 local_server） | 协议/负载 | QoS | Retain | 说明 |
|---|---|---|---|---|---|
| `{mqttTopicPrefix}/{nodeCode}`（默认 `parking/heartbeat/{nodeCode}`） | 发（edge→cloud） | `edge.heartbeat/1` | 1 | 否 | 每 10s 一条心跳 |
| `{reportTopicPrefix}/{nodeCode}`（默认 `parking/report/{nodeCode}`） | 发（edge→cloud） | `edge.parking.session/1` | 1 | 否 | 停车流水变化后周期补推完整快照，见 §10 |
| `{configSyncTopicPrefix}/{nodeCode}`（如 `parking/config-sync/{nodeCode}`） | 收（cloud→edge） | `edge.config.sync/3`（full/delta 分帧） | 1 | 帧不 retain | 云端每次下发前会先发一条零字节 retain 清理消息 |
| `{commandTopicPrefix}/{nodeCode}`（默认 `parking/command/{nodeCode}`） | 收（cloud→edge） | `edge.gate.command/1` 或 `edge.parking.session/1`（origin=`CLOUD`） | 1 | 否 | 缴费开闸见 §11；云端改流水下发见 §10.1 |

帧内 `edgeCode` 必须与订阅主题末段 `nodeCode` 一致，否则丢弃。

---

## 8. 协议一：心跳 `edge.heartbeat/1`（edge → cloud）

实现类：`EdgeHeartbeatReporter`（`src/main/java/com/freepark/local/edge/service/`）。

- 触发：应用就绪后启动定时线程，启动 5s 后首次 tick，此后**每 10s** tick；已连接时每次 tick 都发布一条心跳。
- 空闲条件（满足任一则不发、并断开已有连接）：非 EDGE 模式、`mqttHost`/`mqttPort` 非法、`nodeCode` 为空或含非法字符。
- 主题：`stripTrailingSlash(mqttTopicPrefix) + "/" + nodeCode`。
- QoS=1，retained=false。

负载（UTF-8 JSON，仅 3 个字段）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `schema` | string | 恒为 `edge.heartbeat/1` |
| `edgeCode` | string | 节点编号（同主题末段） |
| `reportedAt` | string | ISO-8601 UTC 时间，如 `2026-09-08T01:23:45.678Z` |

示例：

```json
{"schema":"edge.heartbeat/1","edgeCode":"node-001","reportedAt":"2026-09-08T01:23:45.678Z"}
```

**对端（云端）对接约定**：用 `{mqttTopicPrefix}/#` 订阅（如 `parking/heartbeat/#`）即可收到全部节点心跳；以最近心跳时间判断在线，离线判定阈值建议 ≥ 90 秒（本地实现未消费 heartbeat，该语义完全由云端掌握）。

---

## 9. 协议二：配置同步 `edge.config.sync/3`（cloud → edge）

实现类：`CloudConfigSyncSubscriber`（收帧/聚合）、`ConfigSyncApplyService`（应用落库）。

### 9.1 帧信封（full 与 delta 共用同一信封）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `schema` | string | 是 | 恒为 `edge.config.sync/3` |
| `edgeCode` | string | 是 | 目标节点编号，**必须等于订阅主题末段**，否则丢弃 |
| `snapshotId` | string | 是 | 一次下发批次的唯一 ID（UUID）；同批所有帧共享 |
| `version` | int | 是 | 协议版本，恒为 `3` |
| `generatedAt` | string | 是 | ISO-8601 UTC 时间（Instant） |
| `kind` | string | 是 | `full` 或 `delta` |
| `seq` | int | 是 | 帧序号，从 1 开始 |
| `total` | int | 是 | 本批次总帧数 |
| `lot` | string | full 必填 / delta 必填 | 车场编码（`code`）。整节点空快照帧（节点无车场）时**省略**，`domain=lot`、`items=[]` |
| `domain` | string | 是 | 业务域，见 §9.3 |
| `items` | array | 是 | full：该域条目数组；delta：`{op,…}` 变更条目数组 |

**分帧规则**：一次下发的帧按 `seq` 1..total 编号、同一 `snapshotId`、同一 topic（同一条 TCP 长连接内 MQTT 保证有序）。单帧最多 1000 条条目，超出自动切片。

**边缘聚合与应用**：
1. 收到一帧 → 校验信封（schema/edgeCode/kind/seq/total）。
2. 按 `snapshotId` 缓存入内存，`seq` 去重。
3. 收齐（`frames.size()==total` 且含 `seq=1` 与 `seq=total`）后**按 seq 排序整批**交给应用器。
4. 缺帧时**不会**部分应用，等下一轮全量补齐；缓存 10 分钟超时清空（上限 8 个批次）。
5. 零字节（空 payload）消息直接忽略——那是云端清理历史 retained 用的，见 §9.5。

### 9.2 kind = `full`（整域替换快照）

一组 full 帧构成**该节点的权威快照**，语义（重要）：

- 帧按 `lot`（车场代码）分组；出现在快照里的车场即为“云端托管车场”清单。
- 对每个车场、每个业务域，**整域替换**：
  1. 先删除本地该车场该域中“不在本次保留集（快照携带的 `id`）内的行”——包括本地自建（`cloud_id` 为空）的行；
  2. 再按 `id`（作为本地 `cloud_id`）逐条 upsert。
- 快照中**缺失的域视为云端为空** → 本地对应域被清空。
- 快照中不存在的车场：仅当它曾被云端托管（任一行带过 `cloud_id`）才会整体清理（视为被摘除）；纯本地自建车场保留。
- 帧内 `id` 即云端主键，本地以其作为 `cloud_id` 做幂等 upsert/delete，本地自己的 UUID 主键与外部引用不变。
- 车场 `lot` 帧无数字 id，以 `code` 定位，直接覆盖配置（同 §9.3 的 lot 条目）。
- 节点名下**无任何车场**时，云端仍发 1 条空快照帧（无 `lot` 字段、`domain=lot`、`items=[]`），用于让边缘清理已被摘除的车场配置。

**下发时机**（由云端控制）：
- 周期全量：默认 **86400 秒（24 小时）** 一轮（配置 `configSyncIntervalSeconds`），非 30 秒；
- 心跳由离线转在线：立即补一次全量；
- 节点↔车场绑定变更：立即补全量（摘除则发空快照清理）；
- 手动“立即同步”；
- 业务数据变更：走 kind=`delta`（§9.4）。

### 9.3 七个业务域（domain）与条目字段

枚举（domain）：`lot`、`lane`、`blacklist`、`pattern`、`whitelist`、`internal`、`space`。

> 通用序列化规则：可空字符串/时间为空时**省略字段**（缺省即空）；枚举输出其 `name()`；时间为“无时区后缀的本地时刻文本”（见下方时间说明）。`items[]` 内元素为条目对象，`id` 为云端主键（number）。

**时间格式**：业务时间（`startTime`/`endTime`）以 `LocalDateTime.toString()` 形式下发，例如 `2026-09-08T10:30` 或 `2026-09-08T10:30:00`，**无时区后缀**；边缘按自身配置的时区解析为本地时间再转 Instant（解析失败会回退尝试 ISO-8601 带偏移格式）。

#### lot —— 车场（无数字 id，用 `code` 定位；全量里每车场一个 lot 帧，无 delete）

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | string | 车场编码 |
| `name` | string | 车场名称 |
| `lotType` | string | `INTERNAL` \| `PUBLIC` |
| `enabled` | bool | 启用 |
| `entryInterceptArrears` / `entryInterceptBlacklist` | bool | 入场拦截开关 |
| `exitInterceptArrears` / `exitInterceptBlacklist` | bool | 出场拦截开关 |
| `judgmentOrder` | array\<string\> | 通行判定顺序，元素取 `PATTERN_ALLOWLIST` \| `BLACKLIST` \| `WHITELIST` |
| `updatedAt` | string | 忽略即可 |

#### blacklist —— 黑名单

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | **云端主键**（upsert/delete 定位依据） |
| `plateNumber` | string | 车牌号（必填，空串兜底） |
| `plateColor` | string | 车牌颜色枚举名（见 §9.6），缺失/非法回退 `BLUE` |
| `ownerName` | string | 车主姓名 |
| `phone` `department` `remark` | string? | 可空，空则省略 |
| `startTime` `endTime` | string? | 有效区间，格式见上 |
| `enabled` | bool | 默认 `true` |

#### pattern —— 车牌号段放行规则（正则名单）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 云端主键 |
| `name` | string | 规则名 |
| `pattern` | string | 匹配模式（正则/号段） |
| `remark` | string? | 备注 |
| `enabled` | bool | 默认 `true` |

#### whitelist —— 白名单（停车卡）

字段：`id`、`plateNumber`、`plateColor`、`ownerName`、`type`（VehicleType 枚举，见 §9.6）、`phone?`、`department?`、`remark?`、`startTime?`、`endTime?`、`enabled`（默认 true）。

#### internal —— 内部车辆

同 whitelist，另加 `batchId?`（string，UUID 文本，空则省略）；无时间区间字段。

#### lane —— 通道（出入口）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | **云端主键**（upsert/delete 定位依据） |
| `name` | string | 通道名称 |
| `code` | string | 通道编码（全局唯一，新增时必填；云端更新不改编码） |
| `laneType` | string | `ENTRANCE` \| `EXIT` \| `BIDIRECTIONAL`（缺失/非法回退 `ENTRANCE`） |
| `linkedLotCode` | string? | 关联对向车场编码（双向通道通往的车场）；本地不存在该车场或为空则不建立关联 |
| `enabled` | bool | 默认 `true` |

#### space —— 车位管理（位置/区域/车位三层扁平条目）

每层条目都带 `type` 区分：

| type | 字段 | 说明 |
|---|---|---|
| `location` | `type`=`location`, `id`, `name` | 位置 |
| `area` | `type`=`area`, `id`, `name`, `locationId` | 区域，`locationId`=父位置云端 id |
| `space` | `type`=`space`, `id`, `code`, `enabled`, `areaId` | 车位，`areaId`=父区域云端 id |

应用顺序：location → area → space（父必须先存在；父级缺失的条目跳过，等下一轮全量）。

### 9.4 kind = `delta`（变更增量）

一组 delta 帧在同一信封里（同一 snapshotId、total 帧）。每帧 `items[]` 为变更条目，两种 op：

| op | 负载形状 | 语义 |
|---|---|---|
| `upsert` | `{"op":"upsert","item":{…条目同 §9.3…}}` | 按条目自带 `id` 整条覆盖（无则新增） |
| `delete` | `{"op":"delete","id":<云端主键>}` | 按 `id`（cloud_id）删除该域行；**lot 域不使用 delete** |

应用时按 `frame.lot` 找本地车场：本地还没有该车场（增量先于全量到达，如边缘重启后第一帧就是增量）→ 丢弃该帧增量并等待下一轮全量。

### 9.5 retained 与"清理消息"

- v3 帧本身**不 retain**。
- 云端在每轮 full 下发前，会先向该节点主题发布一条**零字节、retain=true** 的消息，用于清掉历史上 v2 遗留的 retained 快照。
- 订阅/重连时 Broker 会把这条空 retained 消息回放给边缘——**必须忽略空 payload**（边缘实现已如此处理）。

### 9.6 公共枚举值

| 枚举 | 取值 |
|---|---|
| `lotType` | `INTERNAL`、`PUBLIC` |
| 车辆 `type`（whitelist/internal） | `TEMPORARY`、`RESERVED`、`VIP`、`OWNER`、`MONTHLY`、`OTHER`（未知回退 `OTHER`） |
| `judgmentOrder` 元素 | `PATTERN_ALLOWLIST`、`BLACKLIST`、`WHITELIST` |
| `plateColor` | 边缘按本地 `PlateColor` 枚举名解析：`BLUE`、`YELLOW`、`GREEN`、`YELLOW_GREEN`、`BLACK`、`WHITE` 等（`valueOf` 失败回退 `BLUE`） |
| `domain` | `lot`、`lane`、`blacklist`、`pattern`、`whitelist`、`internal`、`space` |

### 9.7 一条全量下发的完整报文样例

车场 `P001`、黑名单 1 条、帧切分 total=3 的示意（实际帧数=车场数×域数×分片数）：

帧 1/3（lot 域）：
```json
{
  "schema": "edge.config.sync/3", "edgeCode": "node-001",
  "snapshotId": "8f1c…", "version": 3, "generatedAt": "2026-09-08T01:00:00Z",
  "kind": "full", "seq": 1, "total": 3, "lot": "P001",
  "domain": "lot",
  "items": [
    { "code": "P001", "name": "示范车场", "lotType": "INTERNAL", "enabled": true,
      "entryInterceptArrears": false, "entryInterceptBlacklist": true,
      "exitInterceptArrears": false, "exitInterceptBlacklist": true,
      "judgmentOrder": ["BLACKLIST", "WHITELIST", "PATTERN_ALLOWLIST"] }
  ]
}
```

帧 2/3（blacklist 域）：
```json
{
  "schema": "edge.config.sync/3", "edgeCode": "node-001",
  "snapshotId": "8f1c…", "version": 3, "generatedAt": "2026-09-08T01:00:00Z",
  "kind": "full", "seq": 2, "total": 3, "lot": "P001",
  "domain": "blacklist",
  "items": [
    { "id": 3001, "plateNumber": "浙B12345", "plateColor": "BLUE",
      "ownerName": "张三", "phone": "13800000000",
      "startTime": "2026-09-01T00:00", "endTime": "2027-09-01T00:00", "enabled": true }
  ]
}
```

帧 3/3（space 域，三层同帧混合展示）：
```json
{
  "schema": "edge.config.sync/3", "edgeCode": "node-001",
  "snapshotId": "8f1c…", "version": 3, "generatedAt": "2026-09-08T01:00:00Z",
  "kind": "full", "seq": 3, "total": 3, "lot": "P001",
  "domain": "space",
  "items": [
    { "type": "location", "id": 10, "name": "A 座" },
    { "type": "area",     "id": 20, "name": "A-1 区", "locationId": 10 },
    { "type": "space",    "id": 30, "code": "A101", "enabled": true, "areaId": 20 }
  ]
}
```

delta 增量示例（新增一条白名单）：
```json
{
  "schema": "edge.config.sync/3", "edgeCode": "node-001",
  "snapshotId": "9a2f…", "version": 3, "generatedAt": "2026-09-08T02:00:00Z",
  "kind": "delta", "seq": 1, "total": 1, "lot": "P001", "domain": "whitelist",
  "items": [
    { "op": "upsert", "item": { "id": 4001, "plateNumber": "浙B66666", "plateColor": "BLUE",
        "ownerName": "李四", "type": "MONTHLY", "enabled": true } }
  ]
}
```

删除示例（按云端主键删内部车）：
```json
{
  "schema": "edge.config.sync/3", "edgeCode": "node-001",
  "snapshotId": "9a2f…", "version": 3, "generatedAt": "2026-09-08T02:05:00Z",
  "kind": "delta", "seq": 1, "total": 1, "lot": "P001", "domain": "internal",
  "items": [ { "op": "delete", "id": 5001 } ]
}
```

---

## 10. 协议三：停车流水上报 `edge.parking.session/1`（edge → cloud）

实现类：`ParkingSessionSyncReporter`（`src/main/java/com/freepark/local/edge/service/`）；云端对端接收：`EdgeParkingSessionReceiver`（`freepark-cloud-simple-backend`，`…/parking/edge/`）。

- 用途：把 local_server 本地停车流水（入场创建 `OPEN` / 出场关闭 `CLOSED` / 作废 `VOIDED`）的状态变化同步到云端 `parking_session`。
- 上报时机与可靠性（重要）：
  - 流水每次状态变化（入场/出场/作废）都会**在同一本地事务内**把该流水置为“待同步”（`sync_pending=true`）。
  - 上报器启动 5s 后首次 tick，此后**每 10s** tick：已连接时最多补推 200 条待同步流水（每轮），逐条发布完整快照。
  - 每条以 **QoS 1、retained=false** 发布到 `stripTrailingSlash(reportTopicPrefix) + "/" + nodeCode`（默认 `parking/report/{nodeCode}`）。
  - 发布成功（Broker PUBACK）后，仅当流水当前状态/时间与刚发布快照完全一致（期间未被并发改动）才清除待同步标记；断网/云端不可用期间的变更保持待同步，连上后自动补推，**不丢单**。
  - 同一流水后续变化会发布“覆盖全量最新状态”的快照，云端按幂等键 upsert，重复补推不会产生重复记录。
- 空闲条件（满足任一则不发、并断开已有连接）：非 EDGE 模式、`mqttHost`/`mqttPort` 非法、`nodeCode` 为空或含非法字符、`reportTopicPrefix` 为空。
- 负载（UTF-8 JSON；可空字符串/时间为空时**省略字段**）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `schema` | string | 是 | 恒为 `edge.parking.session/1` |
| `edgeCode` | string | 是 | 节点编号，**必须等于发布主题末段 nodeCode**，否则云端丢弃 |
| `sessionId` | string | 是 | 边缘本地流水 UUID 文本；与 `edgeCode` 组成云端幂等键 |
| `cloudId` | number | 否 | 已绑定云端主键时回传，供云端按云端 ID 命中同一行 |
| `cloudRevision` | number | 否 | 最近一次已应用的云端写修订号；云端本地修订更大则丢弃本快照 |
| `lotCode` | string | 是 | 车场编码（本地流水关联车场的 `code`） |
| `lotName` | string | 否 | 车场名称（可为空省略） |
| `plateNumber` | string | 是 | 车牌号 |
| `plateColor` | string | 否 | 车牌颜色枚举名（如 `BLUE`/`GREEN`；可空省略） |
| `status` | string | 是 | `OPEN` \| `CLOSED` \| `VOIDED`（枚举 `name()`） |
| `entryTime` | string | 是 | ISO-8601 UTC（Instant 文本，带 `Z` 后缀） |
| `exitTime` | string | 否 | ISO-8601 UTC；未出场省略 |
| `entryLaneName` | string | 否 | 入场通道名称（可空省略） |
| `exitLaneName` | string | 否 | 出场通道名称（可空省略） |
| `reportedAt` | string | 是 | 上报时间，ISO-8601 UTC |

示例（一条已出场的完整快照）：
```json
{"schema":"edge.parking.session/1","edgeCode":"node-001","sessionId":"c9a0f7a1-…","lotCode":"P001","lotName":"示范车场","plateNumber":"浙B12345","plateColor":"BLUE","status":"CLOSED","entryTime":"2026-09-08T01:00:00Z","exitTime":"2026-09-08T08:23:45Z","entryLaneName":"入口1","exitLaneName":"出口1","reportedAt":"2026-09-08T08:24:01Z"}
```

**对端（云端）对接约定**：
1. 用 `{reportTopicPrefix}/#` 订阅（默认 `parking/report/#`），收到报文校验：`schema=edge.parking.session/1`、负载 `edgeCode` 等于主题末段节点编号，否则忽略。
2. 按 `lotCode` 定位云端车场，且该车场必须已绑定在该节点名下，否则丢弃（防止张冠李戴）。
3. 先按 `cloudId` 命中云端行，否则以 `edgeCode + sessionId` 为幂等键 upsert：无则新增、有则整体覆盖为快照最新状态（`CLOSED` 且无支付登记时默认置 `UNPAID`；`VOIDED` 清空支付状态/时间）。若云端已有 `cloudRevision` 且大于上报值：过期的在场快照丢弃；若边缘生命周期已前进（在场→出场/作废）则仍合并出场信息，保留云端已改的车牌/入场。
4. 边缘时间统一为 ISO-8601 UTC（带 `Z`），云端按 UTC 落库；v1 负载**不含图片**与通道/识别内部 ID（云端无可对应主键，仅落名称快照）。
5. 可靠性边界：边缘在 Broker PUBACK 后即清除待同步标记，云端落库失败只能靠日志/运维补数据，无端到端重试。

### 10.1 云端下发停车流水 `edge.parking.session/1`（cloud → edge，origin=`CLOUD`）

实现类：云端 `EdgeSessionPushPublisher`；边缘 `CloudGateCommandSubscriber` + `CloudSessionApplyService`。

- 用途：云端管理端新增/编辑（含关场）/作废/重新算费/收款入账/退款回冲停车流水后，把完整快照下发到该流水所属车场绑定的边缘节点，使本地 OPEN/CLOSED/VOIDED 与车牌、时间与云端一致。
- 主题：与开闸指令相同，`{commandTopicPrefix}/{nodeCode}`（默认 `parking/command/{nodeCode}`），QoS 1、不 retain。
- 触发：云端写流水时 `cloudRevision` +1，事务提交后发布。车场未绑定 `edgeNodeCode` 或 MQTT 未连接则跳过。
- 边缘匹配：先按负载 `sessionId`（本地 UUID）找行，找不到再按 `cloudId`；都没有则新建本地流水。应用后置 `sync_pending=false`，避免立刻回推把云端改动盖掉。若本地已经出场/作废而云端快照仍是在场，保留本地关场并保持待同步，用新修订号把出场补报上去。若本地 `cloudRevision` 已大于本次下发值则忽略。
- 作废：状态变为 `VOIDED` 时联动把关联入场/出场识别记录标为 voided（与本地作废一致）。本地流水无费用字段，负载中的金额可忽略。
- `cleanSession=true`：节点离线期间的下发会丢；连上后靠下一次云端改写再推，或边缘本地再变更后带 `cloudId`/`cloudRevision` 上报。

负载在协议三字段基础上增加：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `origin` | string | 是 | 恒为 `CLOUD`；缺省或其它值边缘丢弃 |
| `cloudId` | number | 是 | 云端 `parking_session` 主键 |
| `cloudRevision` | number | 是 | 本次云端写修订号 |
| `sessionId` | string | 否 | 已知边缘 UUID 时带上；云端自建流水可能暂无 |
| `issuedAt` | string | 否 | ISO-8601 UTC |
| `reportedAt` | - | 否 | 下行不使用 |

示例：

```json
{"schema":"edge.parking.session/1","origin":"CLOUD","edgeCode":"node-001","cloudId":1001,"cloudRevision":3,"sessionId":"c9a0f7a1-…","lotCode":"P001","lotName":"示范车场","plateNumber":"浙B12345","plateColor":"BLUE","status":"VOIDED","entryTime":"2026-09-08T01:00:00Z","issuedAt":"2026-09-12T13:00:00Z"}
```

---

## 11. 协议四：开闸指令 `edge.gate.command/1`（cloud → edge）

实现类：`CloudGateCommandSubscriber`、`CloudGateCommandHandler`、`PendingGateOpenService`（`src/main/java/com/freepark/local/edge/service/`）。

- 用途：车辆在道闸口因欠费被拦截并提示缴费后，用户在云端用户端完成支付；云端入账事务提交后向该车场绑定的边缘节点发布开闸指令，边缘匹配闸前拦截记录并向识别一体机主动 HTTP 下发开闸（无驱动/地址时入队 `OPEN` 供轮询设备取走）。
- 空闲条件（满足任一则不订、并断开已有连接）：非 EDGE 模式、`mqttHost`/`mqttPort` 非法、`nodeCode` 为空或含非法字符。
- 主题：`stripTrailingSlash(commandTopicPrefix 或默认 parking/command) + "/" + nodeCode`。
- QoS=1，retained=false。
- 闸前登记：欠费拦截（remark=`fee_pending`）时把设备 ID + 车牌 + 车场编码记入内存，有效期 20 分钟；进程重启后回落到同期 `fee_pending` 识别记录。

负载（UTF-8 JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `schema` | string | 是 | 恒为 `edge.gate.command/1` |
| `edgeCode` | string | 是 | 目标节点编号，**必须等于订阅主题末段 nodeCode**，否则丢弃 |
| `commandId` | string | 否 | UUID；边缘 10 分钟内去重 |
| `command` | string | 是 | 目前仅 `OPEN` |
| `reason` | string | 否 | 目前仅处理 `PAYMENT`（缺省也按缴费开闸）；其它原因丢弃 |
| `plate` | string | 是 | 车牌（云端已大写） |
| `plateColor` | string | 否 | 车牌颜色枚举名，如 `BLUE` |
| `lotCode` | string | 否 | 车场编码，用于匹配闸前拦截 |
| `payNo` | string | 否 | 云端缴款单号，仅日志追溯 |
| `issuedAt` | string | 否 | ISO-8601 UTC |

示例：

```json
{"schema":"edge.gate.command/1","edgeCode":"node-001","commandId":"7c2e…","command":"OPEN","reason":"PAYMENT","plate":"浙B12345","plateColor":"BLUE","lotCode":"P001","payNo":"PY202609121200000011234","issuedAt":"2026-09-12T12:00:01.000Z"}
```

**对端（云端）对接约定**：
1. 发布主题 `{commandPublishPrefix}/{nodeCode}`，默认前缀 `parking/command`，与边缘订阅前缀必须一致。
2. 仅在 C 端缴款单入账成功且事务提交后发布；按停车订单关联车场的 `edgeNodeCode` 定位节点，未绑定节点的车场跳过。
3. 边缘收到后按车牌（及可选颜色/车场）匹配闸前欠费拦截设备并开闸；找不到拦截记录则只打日志，不开闸。车辆可倒车再次识别：此时欠费已清零，会按正常放行开闸。

---

## 12. 联调验证手册（AI/测试端可直接照做）

前置：云端 Broker 已启动（本仓库场端不部署 Mosquitto）。在能访问该 Broker 的机器上执行 `mosquitto_sub` / `mosquitto_pub`，把 `<broker-host>` 换成节点配置里的 `mqttHost`。

1) **收心跳**（应每 10s 一条）：
```
mosquitto_sub -h <broker-host> -t "parking/heartbeat/#" -u freepark -P freepark -v
```

2) **人工模拟云端下发一条配置同步帧**（节点 `node-001`、订阅前缀 `parking/config-sync` 时主题=`parking/config-sync/node-001`）：
```
mosquitto_pub -h <broker-host> -t "parking/config-sync/node-001" -u freepark -P freepark \
  -m '{"schema":"edge.config.sync/3","edgeCode":"node-001","snapshotId":"test-1","version":3,"generatedAt":"2026-09-08T01:00:00Z","kind":"full","seq":1,"total":1,"lot":"P001","domain":"lot","items":[{"code":"P001","name":"测试场","lotType":"INTERNAL","enabled":true,"judgmentOrder":["BLACKLIST","WHITELIST","PATTERN_ALLOWLIST"]}]}'
```
验证：GET `/api/v1/node-settings` 无关，直接查本地库或 local_server 日志（`配置同步帧已收齐…`）；全量请确保 `seq..total` 发齐，否则边缘会等下一轮。

3) **收停车流水上报**：先在 local_server 完成一次入场（或直接改库把某条流水置待同步），上报器每 10s 补推，应能收到完整快照：
```
mosquitto_sub -h <broker-host> -t "parking/report/#" -u freepark -P freepark -v
```
验证：日志出现 `已上报停车流水并清除待同步标记`；云端侧应能在 `parking_session` 查到该流水（`edge_node_code`/`edge_session_id` 有值）。

4) **模拟云端缴费开闸**（节点 `node-001`、前缀 `parking/command`；先在该节点对某车牌做一次欠费拦截，再发）：
```
mosquitto_pub -h <broker-host> -t "parking/command/node-001" -u freepark -P freepark \
  -m '{"schema":"edge.gate.command/1","edgeCode":"node-001","commandId":"test-open-1","command":"OPEN","reason":"PAYMENT","plate":"浙B12345","plateColor":"BLUE","lotCode":"P001","issuedAt":"2026-09-12T12:00:00Z"}'
```
验证：local_server 日志出现 `缴费开闸完成` 或 `已下发`/`推送了开闸指令`；道闸应抬杆。若日志为 `未找到闸前拦截记录`，说明该车牌 20 分钟内没有 `fee_pending` 拦截。

5) **模拟云端下发停车流水**（节点 `node-001`、车场 `P001` 须已存在于本地）：
```
mosquitto_pub -h <broker-host> -t "parking/command/node-001" -u freepark -P freepark \
  -m '{"schema":"edge.parking.session/1","origin":"CLOUD","edgeCode":"node-001","cloudId":1001,"cloudRevision":1,"lotCode":"P001","plateNumber":"浙B12345","plateColor":"BLUE","status":"OPEN","entryTime":"2026-09-12T01:00:00Z","issuedAt":"2026-09-12T13:00:00Z"}'
```
验证：local_server 日志出现 `已应用云端停车流水`；本地 `parking_session` 出现对应车牌且 `sync_pending` 为 false。

6) **链路不生效的常规排查**：确认 `mode=EDGE`、`mqttHost/mqttPort`、`nodeCode`、`configSyncTopicPrefix`/`reportTopicPrefix`/`commandTopicPrefix` 已保存（GET node-settings）；Broker 日志看客户端是否成功登录（`allow_anonymous false` 下凭据错误会连接失败）；确认心跳/订阅/上报/开闸的 clientId 未与其它客户端冲突。

---

## 13. 关键约束清单（实现/模拟对端前必读）

1. **方向别搞反**：local_server 心跳是"发"，配置同步是"收"，停车流水上报是"发"、云端改流水是"收"，开闸指令是"收"；云端做反方向（心跳收、配置发、流水收+下发、开闸发）。
2. **配置同步帧必须按批次整批到达**：seq 1..total、同一 snapshotId；边缘收到不完整批次会静默等下一轮，10 分钟后丢弃。想单独验证一条 delta：`total=1`。
3. **`edgeCode` 必须等于订阅主题末段的 nodeCode**，否则丢弃。
4. **零字节 retained 清理消息要忽略**（订阅/重连时会回放）。
5. **cleanSession=true**：断线重连后边缘会重新 SUBSCRIBE（无需云端重发 retain），但任何"离线期间发给订阅端"的消息都会丢——云端模型里配置靠"恢复后补全量"来兜底，开闸指令离线会丢，车辆再次识别时若已缴清会按正常放行开闸。
6. **同 Broker 下 heartbeat / config-sync / parking-report / gate-command 是四个不同 clientId**，模拟对端时别复用 `freepark-local-edge` / `freepark-local-edge-cfg` / `freepark-local-edge-rec` / `freepark-local-edge-cmd`。
7. **业务时间字段无时区后缀**（协议二），按边缘本地时区解释；`generatedAt`/`reportedAt` 与协议三流水时间是带 `Z` 的 ISO-8601（UTC）。
8. **`nodeCode` 字符集** `[A-Za-z0-9_-]`、≤64；含 `/`、通配符、空白的配置会被拒绝（REST 返回校验错误）。
9. 心跳 topic 前缀默认 `parking/heartbeat`（含 `heartbeat` 段），云端订阅面是 `parking/heartbeat/#`；改动前缀需云端订阅面同步改。
10. `configSyncTopicPrefix` 留空 = 完全不订阅配置同步（用于纯心跳节点）。
11. `reportTopicPrefix` 留空 = 不上报停车流水；上报链路是"本地事务置待同步 + 周期补推 + QoS 1"，边缘在 PUBACK 后即清待同步标记，**云端必须按 `cloudId` 优先、否则 `edgeCode+sessionId` 幂等 upsert**，并接受重复/乱序快照覆盖；过期 `cloudRevision` 必须丢弃。
12. 指令前缀默认 `parking/command`，须与云端「开闸指令发布主题前缀」一致（开闸与流水下发共用）；闸前拦截登记有效期 20 分钟。云端下发流水须带 `origin=CLOUD`。

---

## 14. 相关代码位置（便于溯源）

| 关注点 | 文件 |
|---|---|
| 心跳发布（edge→cloud） | `local_server/…/edge/service/EdgeHeartbeatReporter.java` |
| 停车流水上报（edge→cloud） | `local_server/…/edge/service/ParkingSessionSyncReporter.java`；云端接收 `freepark-cloud-simple-backend/…/parking/edge/EdgeParkingSessionReceiver.java` |
| 停车流水下发（cloud→edge） | 云端 `…/parking/edge/EdgeSessionPushPublisher.java`；边缘 `CloudGateCommandSubscriber.java`、`CloudSessionApplyService.java` |
| 配置同步订阅/帧聚合（cloud→edge） | `local_server/…/configsync/CloudConfigSyncSubscriber.java` |
| 缴费开闸订阅/执行（cloud→edge） | `local_server/…/edge/service/CloudGateCommandSubscriber.java`、`CloudGateCommandHandler.java`；云端发布 `freepark-cloud-simple-backend/…/parking/edge/EdgeGateCommandPublisher.java` |
| 配置同步应用（full/delta → 各域落库） | `local_server/…/configsync/ConfigSyncApplyService.java` |
| 节点设置实体/默认值 | `local_server/…/domain/NodeSettings.java` |
| 节点设置 REST/服务 | `local_server/…/nodeconfig/controller/NodeConfigController.java`、`…/service/NodeConfigService.java` |
| 云端协议常量/下发（对端参照） | `freepark-cloud-simple-backend/…/settings/runtime/EdgeConfigSyncProtocol.java`、`EdgeConfigSyncDispatcher.java` |
| 云端条目字段形状（对端参照） | `freepark-cloud-simple-backend/…/parking/edge/EdgeDomainItems.java` |
| 云端流水幂等键/唯一约束 | `freepark-cloud-simple-backend/…/parking/entity/ParkingSession.java`（`uk_parking_session_edge`） |
| 云端 Broker | 由云侧部署，场端只填写地址；本仓库不包含 Mosquitto |
