<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { ApiError, getNodeSettings, quoteFee, updateNodeSettings, type NodeMode } from '@/api/client'
import { formatSiteTime } from '@/composables/useSiteTime'

const { t, locale } = useI18n()

const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const mode = ref<NodeMode>('OFFLINE')
const mqttHost = ref('')
const mqttPort = ref(1883)
const mqttClientId = ref('')
const mqttUsername = ref('')
const mqttPassword = ref('')
const mqttPasswordSet = ref(false)
const mqttNodeCode = ref('')
const feeApiUrl = ref('')
const feeMockEnabled = ref(false)
const feeMockAmount = ref<number | null>(null)
const quotePlate = ref('')
const quoteColor = ref('')
const quoting = ref(false)
const quoteError = ref(false)
const quoteMessage = ref('')
const updatedAt = ref('')

const isEdge = computed(() => mode.value === 'EDGE')
const feeDocOpen = ref(false)

type FeeDocRow = { name: string; required: string; type: string; desc: string }

interface FeeDoc {
  method: string
  endpointLabel: string
  endpoint: string
  endpointNote: string
  colField: string
  colRequired: string
  colType: string
  colDesc: string
  reqTitle: string
  reqFields: FeeDocRow[]
  reqExampleTitle: string
  reqExample: string
  resTitle: string
  resFields: FeeDocRow[]
  resExampleTitle: string
  resExample: string
  notesTitle: string
  notes: string[]
}

/** 算费请求接口标准文档：契约与本机 FeeQuoteClient 及云端 /api/edge/arrears-quote 保持一致 */
const feeDocZh: FeeDoc = {
  method: 'POST',
  endpointLabel: '请求地址',
  endpoint: '您在「接口地址」中填写的完整 URL，本机将直接向该地址发起请求。',
  endpointNote: '配套云端示例：POST /api/edge/arrears-quote（对边缘节点开放，无需登录令牌）。',
  colField: '字段',
  colRequired: '必填',
  colType: '类型',
  colDesc: '说明',
  reqTitle: '调用入参（请求体 · application/json）',
  reqFields: [
    {
      name: 'plateNumber',
      required: '必填',
      type: 'string',
      desc: '车牌号，例如 粤R888G8；为空时返回 400。',
    },
    {
      name: 'plateColor',
      required: '可选',
      type: 'string',
      desc: '车牌颜色代码，例如 BLUE；仅用于对齐边缘契约，云端统计不按颜色区分。',
    },
    {
      name: 'lotCode',
      required: '可选',
      type: 'string',
      desc: '车场编码，用于按该车场的欠费范围统计；不传则按全部车场统计，找不到该车场时金额返回 0。',
    },
  ],
  reqExampleTitle: '请求示例',
  reqExample:
    '{\n  "plateNumber": "粤R888G8",\n  "plateColor": "BLUE",\n  "lotCode": "XHW001"\n}',
  resTitle: '返回参数（HTTP 200）',
  resFields: [
    {
      name: 'amount',
      required: '必填',
      type: 'number',
      desc: '当前车辆欠费（应收）金额合计，单位元，例如 12.5；无欠费时为 0。',
    },
  ],
  resExampleTitle: '返回示例',
  resExample: '{\n  "amount": 12.5\n}',
  notesTitle: '补充说明',
  notes: [
    '响应格式：顶层 amount 为标准格式；也兼容响应体直接为金额数字，例如 12.5。',
    '超时：连接超时 5 秒、请求超时 10 秒，超时按算费失败处理。',
    '失败：非 2xx 状态码、缺少 amount 或金额无法解析，均提示「算费请求失败」。',
    '页面「算费」试算会先经本机 /api/v1/node-settings/fee-quote 转发到该地址，契约与上述一致。',
    '启用「模拟金额」后不再请求该地址，直接返回设置的固定金额。',
  ],
}

const feeDocEn: FeeDoc = {
  method: 'POST',
  endpointLabel: 'Request endpoint',
  endpoint:
    'The full URL configured in “API URL”. This node sends the request directly to that address.',
  endpointNote:
    'Cloud reference: POST /api/edge/arrears-quote (open to edge nodes, no auth token required).',
  colField: 'Field',
  colRequired: 'Required',
  colType: 'Type',
  colDesc: 'Description',
  reqTitle: 'Request parameters (body · application/json)',
  reqFields: [
    {
      name: 'plateNumber',
      required: 'Required',
      type: 'string',
      desc: 'Plate number, e.g. 粤R888G8. An empty value returns 400.',
    },
    {
      name: 'plateColor',
      required: 'Optional',
      type: 'string',
      desc: 'Plate color code, e.g. BLUE. Kept for edge-contract alignment only; the cloud quote does not distinguish colors.',
    },
    {
      name: 'lotCode',
      required: 'Optional',
      type: 'string',
      desc: 'Lot code used to quote within that lot’s arrears scope. Omit to quote across all lots; unknown lot codes return 0.',
    },
  ],
  reqExampleTitle: 'Request example',
  reqExample:
    '{\n  "plateNumber": "粤R888G8",\n  "plateColor": "BLUE",\n  "lotCode": "XHW001"\n}',
  resTitle: 'Response parameters (HTTP 200)',
  resFields: [
    {
      name: 'amount',
      required: 'Required',
      type: 'number',
      desc: 'Total arrears (fee payable) of the vehicle in yuan, e.g. 12.5; 0 when nothing is due.',
    },
  ],
  resExampleTitle: 'Response example',
  resExample: '{\n  "amount": 12.5\n}',
  notesTitle: 'Notes',
  notes: [
    'Response format: top-level amount is the standard shape; a bare number body such as 12.5 is also accepted.',
    'Timeouts: connect 5s, request 10s. A timeout is treated as a failed quote.',
    'Failures: non-2xx status, missing amount, or an unparsable amount all report “Fee request failed”.',
    'The “Quote” tester goes through the local proxy /api/v1/node-settings/fee-quote first, with the same contract.',
    'When “Mock amount” is enabled, no request is sent and the configured fixed amount is returned.',
  ],
}

const feeDoc = computed<FeeDoc>(() =>
  locale.value.toLowerCase().startsWith('zh') ? feeDocZh : feeDocEn,
)
const mqttDocOpen = ref(false)

/** 文档通用块：overview=概述 text；table=字段/链路表格；code=报文示例；notes=对接要点 */
interface MqttDocBlock {
  type: 'overview' | 'table' | 'code' | 'notes'
  title?: string
  text?: string
  header?: string[]
  rows?: (string | { code: string })[][]
  label?: string
  code?: string
  items?: string[]
}

const mqttDocZh: MqttDocBlock[] = [
  {
    type: 'overview',
    title: '对接说明',
    text: '模式为「云端直连(EDGE)」时，本机以三个独立 MQTT 客户端连接上方配置的云端 Broker：心跳上报、配置同步订阅、停车流水上报。保存后每 10 秒自动自检一次，连接参数变化会自动断旧连新，无需重启进程。',
  },
  {
    type: 'table',
    title: '连接参数（页面字段 → MQTT 客户端）',
    header: ['页面字段', '作用'],
    rows: [
      [{ code: 'mqttHost' }, { code: 'mqttPort' }, '云端 Broker 主机与端口（端口默认 1883），三条链路共用。'],
      [{ code: 'mqttUsername' }, { code: 'mqttPassword' }, 'Broker 登录账号；留空则按 Broker 的匿名/访问策略。'],
      [{ code: 'mqttClientId' }, '心跳客户端 clientId（默认 freepark-local-edge）；配置同步 / 停车流水上报分别追加后缀 -cfg / -rec。'],
      [{ code: 'nodeCode' }, '节点编号，用作心跳主题末段；仅允许字母、数字、_ 与 -，长度 ≤ 64。'],
    ],
  },
  {
    type: 'table',
    title: '数据链路与主题',
    header: ['链路', '方向', '主题（默认）', '负载与频率'],
    rows: [
      [
        '心跳上报',
        'edge → cloud',
        { code: 'parking/heartbeat/{nodeCode}' },
        'edge.heartbeat/1 · QoS 1 · 每 10 秒一条',
      ],
      [
        '配置同步订阅',
        'cloud → edge',
        { code: '{configSyncTopicPrefix}/{nodeCode}' },
        'edge.config.sync/3 · QoS 1 · 云端下发 full / delta 分帧',
      ],
      [
        '停车流水上报',
        'edge → cloud',
        { code: 'parking/report/{nodeCode}' },
        'edge.parking.session/1 · QoS 1 · 有变更后每 10 秒补推（每轮最多 200 条）',
      ],
    ],
  },
  {
    type: 'code',
    label: '心跳报文示例',
    code: '{"schema":"edge.heartbeat/1","edgeCode":"node-001","reportedAt":"2026-09-08T01:23:45.678Z"}',
  },
  {
    type: 'code',
    label: '停车流水报文示例（完整快照）',
    code: '{"schema":"edge.parking.session/1","edgeCode":"node-001","sessionId":"c9a0f7a1-…","lotCode":"P001","lotName":"示范车场","plateNumber":"浙B12345","plateColor":"BLUE","status":"CLOSED","entryTime":"2026-09-08T01:00:00Z","exitTime":"2026-09-08T08:23:45Z","entryLaneName":"入口1","exitLaneName":"出口1","reportedAt":"2026-09-08T08:24:01Z"}',
  },
  {
    type: 'notes',
    title: '对接要点',
    items: [
      '主题与订阅：心跳固定发布到 parking/heartbeat/{nodeCode}，云端用 parking/heartbeat/# 订阅即可收到全部节点；停车流水默认上报到 parking/report/{nodeCode}（云端订阅 parking/report/#）；报文内 edgeCode 必须与主题末段 nodeCode 一致，否则云端丢弃。',
      '连接约定：cleanSession=true、自动重连，断线重连后会自动重新订阅；Broker 不保留离线消息，云端以“恢复后补全量”兜底。',
      '停车流水按 edgeCode + sessionId 幂等 upsert，云端可容忍重复/乱序快照覆盖；本地在 Broker PUBACK 后即清除待同步标记。',
      '注意：本页保存为整表覆盖，config-sync 前缀不在本页表单中，未提供即按“不订阅云端配置同步”保存；如需要请通过 REST /api/v1/node-settings 传入 configSyncTopicPrefix。',
      '三个客户端使用不同 clientId（{mqttClientId}、{mqttClientId}-cfg、{mqttClientId}-rec），请勿与同 Broker 下其它客户端冲突，否则会互踢。',
      '完整协议字段与 full / delta 帧格式详见本机源码仓库 docs/mqtt-integration.md。',
    ],
  },
]

const mqttDocEn: MqttDocBlock[] = [
  {
    type: 'overview',
    title: 'Overview',
    text: 'In “Cloud direct (EDGE)” mode this node connects to the configured cloud MQTT broker with three independent MQTT clients: heartbeat reporting, config-sync subscription, and parking-session reporting. Settings are self-checked every 10 s; changed parameters reconnect automatically without a restart.',
  },
  {
    type: 'table',
    title: 'Connection parameters (page fields → MQTT clients)',
    header: ['Page field', 'Purpose'],
    rows: [
      [
        { code: 'mqttHost' },
        { code: 'mqttPort' },
        'Cloud broker host and port (port defaults to 1883), shared by all three links.',
      ],
      [
        { code: 'mqttUsername' },
        { code: 'mqttPassword' },
        'Broker credentials; leave blank to rely on the broker anonymous/access policy.',
      ],
      [
        { code: 'mqttClientId' },
        'clientId of the heartbeat client (default freepark-local-edge); the config-sync / report clients append -cfg / -rec.',
      ],
      [
        { code: 'nodeCode' },
        'Node code used as the last topic segment; only letters, digits, _ and -, max 64 chars.',
      ],
    ],
  },
  {
    type: 'table',
    title: 'Links and topics',
    header: ['Link', 'Direction', 'Topic (default)', 'Payload & frequency'],
    rows: [
      [
        'Heartbeat',
        'edge → cloud',
        { code: 'parking/heartbeat/{nodeCode}' },
        'edge.heartbeat/1 · QoS 1 · every 10 s',
      ],
      [
        'Config sync',
        'cloud → edge',
        { code: '{configSyncTopicPrefix}/{nodeCode}' },
        'edge.config.sync/3 · QoS 1 · full/delta frames from cloud',
      ],
      [
        'Parking session report',
        'edge → cloud',
        { code: 'parking/report/{nodeCode}' },
        'edge.parking.session/1 · QoS 1 · retries pending sessions every 10 s (max 200 per round)',
      ],
    ],
  },
  {
    type: 'code',
    label: 'Heartbeat message example',
    code: '{"schema":"edge.heartbeat/1","edgeCode":"node-001","reportedAt":"2026-09-08T01:23:45.678Z"}',
  },
  {
    type: 'code',
    label: 'Parking session message example (full snapshot)',
    code: '{"schema":"edge.parking.session/1","edgeCode":"node-001","sessionId":"c9a0f7a1-…","lotCode":"P001","lotName":"Demo lot","plateNumber":"浙B12345","plateColor":"BLUE","status":"CLOSED","entryTime":"2026-09-08T01:00:00Z","exitTime":"2026-09-08T08:23:45Z","entryLaneName":"Entry 1","exitLaneName":"Exit 1","reportedAt":"2026-09-08T08:24:01Z"}',
  },
  {
    type: 'notes',
    title: 'Integration notes',
    items: [
      'Topics & subscriptions: heartbeats are published to parking/heartbeat/{nodeCode} — subscribe with parking/heartbeat/# to receive all nodes; parking sessions go to parking/report/{nodeCode} by default (subscribe parking/report/#). edgeCode inside a payload must equal the last topic segment or the message is dropped.',
      'Connection: cleanSession=true with auto-reconnect; subscriptions are restored after reconnect. The broker keeps no offline queue — the cloud compensates with a full resync after a node comes back online.',
      'Parking sessions are upserted idempotently by edgeCode + sessionId; repeated/out-of-order snapshots are tolerated. The edge clears the pending flag only after broker PUBACK.',
      'Note: saving this page overwrites the whole settings row. The config-sync prefix is not on this form, so an omitted value disables cloud config sync; pass configSyncTopicPrefix via REST /api/v1/node-settings to enable it.',
      'Three clients use distinct clientIds ({mqttClientId}, {mqttClientId}-cfg, {mqttClientId}-rec); never reuse them elsewhere on the same broker to avoid kicking each other off.',
      'Full protocol fields and full/delta frame format: see docs/mqtt-integration.md in the local_server source repo.',
    ],
  },
]

const mqttDoc = computed<MqttDocBlock[]>(() =>
  locale.value.toLowerCase().startsWith('zh') ? mqttDocZh : mqttDocEn,
)

/** 心跳主题前缀固定默认，与后端 NodeSettings.DEFAULT_MQTT_TOPIC_PREFIX 保持一致 */
const DEFAULT_HEARTBEAT_PREFIX = 'parking/heartbeat'

/** 心跳主题预览：本机发布主题 = parking/heartbeat/节点编号，云端订阅主题 = parking/heartbeat/# */
const heartbeatPreview = computed<{ publish: string; subscribe: string } | null>(() => {
  if (!isEdge.value) return null
  const code = mqttNodeCode.value.trim()
  if (!code) return null
  return { publish: `${DEFAULT_HEARTBEAT_PREFIX}/${code}`, subscribe: `${DEFAULT_HEARTBEAT_PREFIX}/#` }
})

const passwordPlaceholder = computed(() =>
  mqttPasswordSet.value
    ? t('nodeConfig.mqttPasswordKeepPlaceholder')
    : t('nodeConfig.mqttPasswordEmptyPlaceholder'),
)

function formatUpdatedAt(iso: string): string {
  return formatSiteTime(iso)
}

function mockAmountOrNull(): number | null {
  const raw = String(feeMockAmount.value ?? '').trim()
  if (!raw) return null
  const n = Number(raw)
  return Number.isNaN(n) ? null : n
}

async function loadSettings(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getNodeSettings(locale.value)
    applySettings(response.data)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('nodeConfig.loadFailed')
  } finally {
    loading.value = false
  }
}

function applySettings(data: {
  mode: NodeMode
  mqttHost: string
  mqttPort: number
  mqttClientId: string
  mqttUsername: string
  mqttPasswordSet: boolean
  mqttTopicPrefix: string
  nodeCode: string
  feeApiUrl: string
  feeMockEnabled: boolean
  feeMockAmount: number | null
  updatedAt: string
}): void {
  mode.value = data.mode
  mqttHost.value = data.mqttHost || ''
  mqttPort.value = data.mqttPort || 1883
  mqttClientId.value = data.mqttClientId || ''
  mqttUsername.value = data.mqttUsername || ''
  mqttPasswordSet.value = data.mqttPasswordSet
  mqttPassword.value = ''
  mqttNodeCode.value = data.nodeCode || ''
  feeApiUrl.value = data.feeApiUrl || ''
  feeMockEnabled.value = data.feeMockEnabled
  feeMockAmount.value = data.feeMockAmount
  updatedAt.value = data.updatedAt
}

async function onSubmit(): Promise<void> {
  errorMessage.value = ''
  successMessage.value = ''
  if (mode.value === 'EDGE' && !mqttHost.value.trim()) {
    errorMessage.value = t('nodeConfig.hostRequired')
    return
  }
  if (mode.value === 'EDGE' && !mqttNodeCode.value.trim()) {
    errorMessage.value = t('nodeConfig.nodeCodeRequired')
    return
  }
  if (mode.value === 'EDGE' && !/^[A-Za-z0-9_-]+$/.test(mqttNodeCode.value.trim())) {
    errorMessage.value = t('nodeConfig.nodeCodeInvalid')
    return
  }
  const port = Number(mqttPort.value)
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    errorMessage.value = t('nodeConfig.portInvalid')
    return
  }
  const mockAmount = mockAmountOrNull()
  if (feeMockEnabled.value && (mockAmount === null || mockAmount < 0)) {
    errorMessage.value = t('nodeConfig.feeMockAmountInvalid')
    return
  }
  submitting.value = true
  try {
    const response = await updateNodeSettings(
      {
        mode: mode.value,
        mqttHost: mqttHost.value.trim(),
        mqttPort: port,
        mqttClientId: mqttClientId.value.trim(),
        mqttUsername: mqttUsername.value.trim(),
        mqttPassword: mqttPassword.value,
        nodeCode: mqttNodeCode.value.trim(),
        feeApiUrl: feeApiUrl.value.trim(),
        feeMockEnabled: feeMockEnabled.value,
        feeMockAmount: feeMockEnabled.value ? mockAmount : null,
      },
      locale.value,
    )
    applySettings(response.data)
    successMessage.value = t('nodeConfig.saved')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('nodeConfig.saveFailed')
  } finally {
    submitting.value = false
  }
}

async function onQuote(): Promise<void> {
  if (!quotePlate.value.trim() || quoting.value) return
  quoting.value = true
  quoteError.value = false
  quoteMessage.value = ''
  try {
    const response = await quoteFee(
      { plateNumber: quotePlate.value.trim(), plateColor: quoteColor.value.trim() },
      locale.value,
    )
    quoteMessage.value = t('nodeConfig.feeQuoteResult', { amount: response.data.amount })
  } catch (error) {
    quoteError.value = true
    quoteMessage.value = error instanceof ApiError ? error.message : t('nodeConfig.feeQuoteFailed')
  } finally {
    quoting.value = false
  }
}

onMounted(() => {
  void loadSettings()
})
</script>

<template>
  <section class="page">
    <p v-if="loading" class="hint">{{ t('nodeConfig.loading') }}</p>
    <form v-else class="page-form" @submit.prevent="onSubmit">
      <article class="card card-mode">
        <h3>{{ t('nodeConfig.mode') }}</h3>
        <p class="hint">{{ t('nodeConfig.modeHint') }}</p>
        <div class="mode-grid">
          <label class="mode-option" :class="{ active: mode === 'OFFLINE' }">
            <input v-model="mode" type="radio" value="OFFLINE" />
            <strong>{{ t('nodeConfig.modeOffline') }}</strong>
            <span>{{ t('nodeConfig.modeOfflineHint') }}</span>
          </label>
          <label class="mode-option" :class="{ active: mode === 'EDGE' }">
            <input v-model="mode" type="radio" value="EDGE" />
            <strong>{{ t('nodeConfig.modeEdge') }}</strong>
            <span>{{ t('nodeConfig.modeEdgeHint') }}</span>
          </label>
        </div>
      </article>

      <article v-if="isEdge" class="card card-mqtt">
        <div class="card-head">
          <h3>{{ t('nodeConfig.mqtt') }}</h3>
          <button type="button" class="doc-link" @click="mqttDocOpen = true">
            {{ t('nodeConfig.feeDocOpen') }}
          </button>
        </div>
        <p class="hint">{{ t('nodeConfig.mqttHint') }}</p>
        <div class="form form-row">
          <label>
            <span>{{ t('nodeConfig.mqttHost') }}</span>
            <input v-model="mqttHost" type="text" :placeholder="t('nodeConfig.mqttHostPlaceholder')" />
          </label>
          <label>
            <span>{{ t('nodeConfig.mqttPort') }}</span>
            <input v-model.number="mqttPort" type="number" min="1" max="65535" />
          </label>
        </div>
        <div class="form form-row">
          <label>
            <span>{{ t('nodeConfig.mqttClientId') }}</span>
            <input
              v-model="mqttClientId"
              type="text"
              :placeholder="t('nodeConfig.mqttClientIdPlaceholder')"
            />
          </label>
          <label>
            <span>{{ t('nodeConfig.mqttUsername') }}</span>
            <input v-model="mqttUsername" type="text" autocomplete="off" />
          </label>
        </div>
        <div class="form">
          <label>
            <span>{{ t('nodeConfig.mqttPassword') }}</span>
            <input
              v-model="mqttPassword"
              type="password"
              autocomplete="new-password"
              :placeholder="passwordPlaceholder"
            />
          </label>
          <label>
            <span>{{ t('nodeConfig.nodeCode') }}</span>
            <input
              v-model="mqttNodeCode"
              type="text"
              :placeholder="t('nodeConfig.nodeCodePlaceholder')"
            />
          </label>
          <p class="hint node-code-hint">{{ t('nodeConfig.nodeCodeHint') }}</p>
          <div v-if="heartbeatPreview" class="topic-preview">
            <p class="hint">{{ t('nodeConfig.mqttTopicPrefixHint') }}</p>
            <div class="preview-line">
              <span class="preview-label">{{ t('nodeConfig.heartbeatPublishLabel') }}</span>
              <code class="preview-topic">{{ heartbeatPreview.publish }}</code>
            </div>
            <div class="preview-line">
              <span class="preview-label">{{ t('nodeConfig.cloudSubscribeLabel') }}</span>
              <code class="preview-topic">{{ heartbeatPreview.subscribe }}</code>
            </div>
          </div>
        </div>
      </article>

      <article v-if="isEdge" class="card">
        <div class="card-head">
          <h3>{{ t('nodeConfig.feeApi') }}</h3>
          <button type="button" class="doc-link" @click="feeDocOpen = true">
            {{ t('nodeConfig.feeDocOpen') }}
          </button>
        </div>
        <p class="hint">{{ t('nodeConfig.feeApiHint') }}</p>
        <div class="form">
          <label>
            <span>{{ t('nodeConfig.feeApiUrl') }}</span>
            <input v-model="feeApiUrl" type="text" :placeholder="t('nodeConfig.feeApiUrlPlaceholder')" />
          </label>
          <label class="mock-toggle">
            <input v-model="feeMockEnabled" type="checkbox" />
            <span class="mock-toggle-text">
              <strong>{{ t('nodeConfig.feeMockEnabled') }}</strong>
              <em>{{ t('nodeConfig.feeMockEnabledHint') }}</em>
            </span>
          </label>
          <label v-if="feeMockEnabled">
            <span>{{ t('nodeConfig.feeMockAmount') }}</span>
            <input
              v-model="feeMockAmount"
              type="number"
              min="0"
              step="0.01"
              :placeholder="t('nodeConfig.feeMockAmountPlaceholder')"
            />
          </label>
        </div>
        <div class="quote-panel">
          <div class="form form-row">
            <label>
              <span>{{ t('nodeConfig.feeQuotePlate') }}</span>
              <input
                v-model="quotePlate"
                type="text"
                :placeholder="t('nodeConfig.feeQuotePlatePlaceholder')"
              />
            </label>
            <label>
              <span>{{ t('nodeConfig.feeQuoteColor') }}</span>
              <input
                v-model="quoteColor"
                type="text"
                :placeholder="t('nodeConfig.feeQuoteColorPlaceholder')"
              />
            </label>
          </div>
          <div class="quote-footer">
            <p v-if="quoteMessage" class="quote-result" :class="quoteError ? 'error' : 'ok'">
              {{ quoteMessage }}
            </p>
            <button
              type="button"
              class="quote-btn"
              :disabled="quoting || !quotePlate.trim()"
              @click="onQuote"
            >
              {{ quoting ? t('nodeConfig.feeQuoting') : t('nodeConfig.feeQuote') }}
            </button>
          </div>
        </div>
      </article>

      <div class="form-footer">
        <div class="footer-meta">
          <p v-if="updatedAt" class="meta">
            {{ t('nodeConfig.lastUpdated') }}: {{ formatUpdatedAt(updatedAt) }}
          </p>
          <p v-if="errorMessage" class="message error">{{ errorMessage }}</p>
          <p v-if="successMessage" class="message ok">{{ successMessage }}</p>
        </div>
        <button type="submit" :disabled="submitting">
          {{ submitting ? t('nodeConfig.saving') : t('nodeConfig.save') }}
        </button>
      </div>
    </form>

    <div v-if="feeDocOpen" class="doc-backdrop" @click.self="feeDocOpen = false">
      <div class="doc-modal">
        <div class="doc-head">
          <h3>{{ t('nodeConfig.feeApi') }} · {{ t('nodeConfig.feeDocOpen') }}</h3>
          <button type="button" class="doc-close-btn" @click="feeDocOpen = false">
            {{ t('nodeConfig.feeDocClose') }}
          </button>
        </div>
        <div class="doc-scroll">
          <div class="doc-section">
            <p class="doc-label">{{ feeDoc.endpointLabel }}</p>
            <div class="endpoint-box">
              <span class="method-tag">{{ feeDoc.method }}</span>
              <p class="endpoint-desc">{{ feeDoc.endpoint }}</p>
            </div>
            <p class="doc-note">{{ feeDoc.endpointNote }}</p>
          </div>

          <div class="doc-section">
            <p class="doc-label">{{ feeDoc.reqTitle }}</p>
            <table class="doc-table">
              <thead>
                <tr>
                  <th>{{ feeDoc.colField }}</th>
                  <th>{{ feeDoc.colRequired }}</th>
                  <th>{{ feeDoc.colType }}</th>
                  <th>{{ feeDoc.colDesc }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in feeDoc.reqFields" :key="row.name">
                  <td><code>{{ row.name }}</code></td>
                  <td>{{ row.required }}</td>
                  <td>{{ row.type }}</td>
                  <td>{{ row.desc }}</td>
                </tr>
              </tbody>
            </table>
            <p class="doc-label">{{ feeDoc.reqExampleTitle }}</p>
            <pre class="doc-code">{{ feeDoc.reqExample }}</pre>
          </div>

          <div class="doc-section">
            <p class="doc-label">{{ feeDoc.resTitle }}</p>
            <table class="doc-table">
              <thead>
                <tr>
                  <th>{{ feeDoc.colField }}</th>
                  <th>{{ feeDoc.colRequired }}</th>
                  <th>{{ feeDoc.colType }}</th>
                  <th>{{ feeDoc.colDesc }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in feeDoc.resFields" :key="row.name">
                  <td><code>{{ row.name }}</code></td>
                  <td>{{ row.required }}</td>
                  <td>{{ row.type }}</td>
                  <td>{{ row.desc }}</td>
                </tr>
              </tbody>
            </table>
            <p class="doc-label">{{ feeDoc.resExampleTitle }}</p>
            <pre class="doc-code">{{ feeDoc.resExample }}</pre>
          </div>

          <div class="doc-section">
            <p class="doc-label">{{ feeDoc.notesTitle }}</p>
            <ul class="doc-notes">
              <li v-for="(note, index) in feeDoc.notes" :key="index">{{ note }}</li>
            </ul>
          </div>
        </div>
      </div>
    </div>

    <div v-if="mqttDocOpen" class="doc-backdrop" @click.self="mqttDocOpen = false">
      <div class="doc-modal">
        <div class="doc-head">
          <h3>{{ t('nodeConfig.mqtt') }} · {{ t('nodeConfig.feeDocOpen') }}</h3>
          <button type="button" class="doc-close-btn" @click="mqttDocOpen = false">
            {{ t('nodeConfig.feeDocClose') }}
          </button>
        </div>
        <div class="doc-scroll">
          <template v-for="(block, index) in mqttDoc" :key="index">
            <div v-if="block.type === 'overview'" class="doc-section">
              <p v-if="block.title" class="doc-label">{{ block.title }}</p>
              <p class="doc-note">{{ block.text }}</p>
            </div>
            <div v-else-if="block.type === 'table'" class="doc-section">
              <p class="doc-label">{{ block.title }}</p>
              <table class="doc-table">
                <thead>
                  <tr>
                    <th v-for="(header, hi) in block.header" :key="hi">{{ header }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, ri) in block.rows" :key="ri">
                    <td v-for="(cell, ci) in row" :key="ci">
                      <code v-if="typeof cell === 'object' && 'code' in cell">{{ cell.code }}</code>
                      <template v-else>{{ cell }}</template>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else-if="block.type === 'code'" class="doc-section">
              <p class="doc-label">{{ block.label }}</p>
              <pre class="doc-code">{{ block.code }}</pre>
            </div>
            <div v-else class="doc-section">
              <p class="doc-label">{{ block.title }}</p>
              <ul class="doc-notes">
                <li v-for="(item, ii) in block.items" :key="ii">{{ item }}</li>
              </ul>
            </div>
          </template>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: grid;
  gap: 0.9rem;
  width: 100%;
}

.page-form {
  display: grid;
  gap: 0.9rem;
  max-width: 52rem;
}

.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.2rem 1.25rem;
  box-shadow: var(--shadow);
}

.card h3 {
  margin: 0 0 0.75rem;
}

.hint {
  margin: -0.35rem 0 0.75rem;
  color: var(--muted);
  font-size: 0.9rem;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.9rem;
}

.mode-option {
  display: grid;
  gap: 0.35rem;
  padding: 0.9rem 1rem;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.mode-option input {
  width: auto;
  justify-self: start;
}

.mode-option strong {
  font-size: 0.98rem;
}

.mode-option span {
  color: var(--muted);
  font-size: 0.88rem;
  line-height: 1.5;
}

.mode-option.active {
  border-color: var(--accent);
  background: #f2faf6;
}

.form {
  display: grid;
  gap: 0.75rem;
}

.form-row {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

label {
  display: grid;
  gap: 0.35rem;
}

.mock-toggle {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  cursor: pointer;
}

.mock-toggle input {
  width: auto;
}

.mock-toggle-text {
  display: grid;
  gap: 0.15rem;
}

.mock-toggle-text strong {
  font-size: 0.95rem;
  font-weight: 600;
}

.mock-toggle-text em {
  color: var(--muted);
  font-size: 0.85rem;
  font-style: normal;
  line-height: 1.4;
}

select,
input[type='text'],
input[type='password'],
input[type='number'] {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
  font: inherit;
  box-sizing: border-box;
}

.form-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem 1rem;
  padding: 0.25rem 0.1rem 0;
}

.footer-meta {
  display: grid;
  gap: 0.55rem;
  min-width: min(100%, 20rem);
  flex: 1;
}

.meta {
  margin: 0;
  color: var(--muted);
  font-size: 0.85rem;
}

.message {
  margin: 0;
  padding: 0.55rem 0.75rem;
  border-radius: 8px;
  font-size: 0.9rem;
}

.message.error {
  color: var(--danger);
  background: #fdecec;
}

.message.ok {
  color: var(--ok);
  background: #e8f5ef;
}

button {
  border: 0;
  border-radius: 8px;
  padding: 0.65rem 1.2rem;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
  justify-self: end;
}

button:disabled {
  opacity: 0.7;
}

@media (max-width: 720px) {
  .mode-grid,
  .form-row {
    grid-template-columns: 1fr;
  }

  .form-footer {
    flex-direction: column;
    align-items: stretch;
  }

  button {
    width: 100%;
  }
}

.quote-panel {
  margin-top: 1rem;
  border-top: 1px dashed var(--border);
  padding-top: 1rem;
  display: grid;
  gap: 0.75rem;
}

.quote-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem 1rem;
}

.quote-result {
  margin: 0;
  padding: 0.45rem 0.75rem;
  border-radius: 8px;
  font-size: 0.9rem;
}

.quote-result.error {
  color: var(--danger);
  background: #fdecec;
}

.quote-result.ok {
  color: var(--ok);
  background: #e8f5ef;
}

.quote-btn {
  justify-self: end;
  min-width: 7rem;
}

.topic-preview {
  display: grid;
  gap: 0.4rem;
  margin-top: 0.25rem;
  padding: 0.7rem 0.8rem;
  border: 1px dashed var(--border);
  border-radius: 8px;
  background: #fafcfa;
}

.topic-preview .hint {
  margin: 0;
}

.preview-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem 0.6rem;
  font-size: 0.9rem;
}

.preview-label {
  color: var(--muted);
}

.preview-topic {
  padding: 0.15rem 0.5rem;
  border-radius: 5px;
  background: var(--surface);
  border: 1px solid var(--border);
  color: var(--accent);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 0.85rem;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin: 0 0 0.75rem;
}

.card-head h3 {
  margin: 0;
}

.doc-link {
  flex: none;
  justify-self: auto;
  padding: 0.4rem 0.85rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--accent);
  background: #eef7f2;
  border: 1px solid var(--accent);
}

.doc-link:hover {
  background: #e0f1e8;
}

.doc-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 20, 0.45);
  display: grid;
  place-items: center;
  padding: 1rem;
  z-index: 30;
}

.doc-modal {
  display: flex;
  flex-direction: column;
  width: min(900px, 100%);
  max-height: min(86vh, 920px);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow);
  overflow: hidden;
}

.doc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid var(--border);
}

.doc-head h3 {
  margin: 0;
}

.doc-close-btn {
  flex: none;
  justify-self: auto;
  padding: 0.35rem 0.85rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--muted);
  background: transparent;
  border: 1px solid var(--border);
}

.doc-close-btn:hover {
  color: var(--text);
  border-color: var(--muted);
}

.doc-scroll {
  display: grid;
  gap: 1.1rem;
  padding: 1rem 1.25rem 1.25rem;
  overflow: auto;
}

.doc-section {
  display: grid;
  gap: 0.5rem;
}

.doc-label {
  margin: 0;
  font-size: 0.92rem;
  font-weight: 600;
}

.endpoint-box {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  padding: 0.7rem 0.8rem;
  border: 1px dashed var(--border);
  border-radius: 8px;
  background: #fafcfa;
}

.method-tag {
  flex: none;
  padding: 0.15rem 0.5rem;
  border-radius: 6px;
  background: var(--accent);
  color: #fff;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 0.8rem;
  font-weight: 700;
  line-height: 1.4;
}

.endpoint-desc {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.5;
}

.doc-note {
  margin: 0;
  color: var(--muted);
  font-size: 0.85rem;
  line-height: 1.5;
}

.doc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
}

.doc-table th {
  padding: 0.45rem 0.6rem;
  border-bottom: 1px solid var(--border);
  color: var(--muted);
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}

.doc-table td {
  padding: 0.45rem 0.6rem;
  border-bottom: 1px solid #eef2ee;
  vertical-align: top;
  line-height: 1.55;
}

.doc-table td code {
  padding: 0.05rem 0.35rem;
  border-radius: 5px;
  background: #eef7f2;
  color: var(--accent);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 0.85rem;
  white-space: nowrap;
}

.doc-code {
  margin: 0;
  padding: 0.7rem 0.85rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fafcfa;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 0.85rem;
  line-height: 1.5;
  overflow-x: auto;
  white-space: pre;
}

.doc-notes {
  margin: 0;
  padding-left: 1.2rem;
  display: grid;
  gap: 0.4rem;
  color: var(--muted);
  font-size: 0.88rem;
  line-height: 1.6;
}
</style>
