<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  adoptAutoRegisteredDevice,
  createBarrierGlobal,
  deleteAutoRegisteredDevice,
  deleteBarrierGlobal,
  enqueueDeviceCommand,
  listAllBarriers,
  listAutoRegisteredDevices,
  listDeviceCommands,
  listDriverFactories,
  listLanes,
  updateBarrierGlobal,
  type AutoRegisteredDeviceView,
  type BarrierView,
  type DriverFactoryView,
  type LaneView,
} from '@/api/client'
import { getUser } from '@/auth/session'
import { useSiteTime } from '@/composables/useSiteTime'

const { t, locale } = useI18n()
const { formatTime } = useSiteTime()

const isAdmin = computed(() => getUser()?.role === 'ADMIN')
const loading = ref(false)
const devices = ref<BarrierView[]>([])
const autoDevices = ref<AutoRegisteredDeviceView[]>([])
const autoBusyId = ref<string | null>(null)
const lanes = ref<LaneView[]>([])
const searchQuery = ref('')
const errorMessage = ref('')

// 「同步主板时间」行操作：入队后轮询指令状态，等待设备取走（DELIVERED 即时间帧已随响应下发）
const syncBusyId = ref<string | null>(null)
const syncNotice = ref<{ kind: 'ok' | 'error'; text: string } | null>(null)
let syncNoticeTimer: ReturnType<typeof setTimeout> | null = null

// 已接入驱动目录（来自驱动模块自动发现，多个驱动一并展示）
const drivers = ref<DriverFactoryView[]>([])
const driversLoading = ref(false)
const driverBrands = computed(() => {
  const set = new Set<string>()
  for (const d of drivers.value) {
    if (d.brand) set.add(d.brand)
  }
  return [...set]
})

/** 当前所选品牌下、驱动上报的具体型号清单；空数组表示整条产品线通配。 */
const modelOptions = computed(() => {
  const brand = formBrand.value.trim().toUpperCase()
  if (!brand) return []
  const set = new Set<string>()
  for (const d of drivers.value) {
    if (d.brand.trim().toUpperCase() === brand) {
      for (const m of d.supportedModels) set.add(m)
    }
  }
  return [...set]
})

const showForm = ref(false)
const editingId = ref<string | null>(null)
const formName = ref('')
const formCode = ref('')
const formBrand = ref('')
const formModel = ref('')
/** 型号搜索建议是否展开（聚焦/输入时打开，选中、失焦或按 Esc 时收起）。 */
const modelSuggestOpen = ref(false)
/** 按已输入关键字过滤后的型号建议（大小写不敏感包含匹配）。 */
const modelSuggestions = computed(() => {
  const keyword = formModel.value.trim().toLowerCase()
  if (!keyword) return modelOptions.value
  return modelOptions.value.filter((m) => m.toLowerCase().includes(keyword))
})
const formHost = ref('')
const formPortText = ref('')
const formEnabled = ref(true)
const formError = ref('')
const saving = ref(false)

// 删除识别一体机确认弹窗
const deletingDevice = ref<BarrierView | null>(null)
const deletingBusy = ref(false)
const deleteError = ref('')

function confirmDeleteDevice(device: BarrierView): void {
  if (!isAdmin.value) return
  deleteError.value = ''
  deletingDevice.value = device
}

function cancelDelete(): void {
  deletingDevice.value = null
  deletingBusy.value = false
  deleteError.value = ''
}

async function confirmSubmitDelete(): Promise<void> {
  const target = deletingDevice.value
  if (!target) return
  deletingBusy.value = true
  deleteError.value = ''
  try {
    await deleteBarrierGlobal(target.id, locale.value)
    devices.value = devices.value.filter((item) => item.id !== target.id)
    if (editingId.value === target.id) closeForm()
    deletingDevice.value = null
  } catch (error) {
    deleteError.value = error instanceof ApiError ? error.message : t('barriers.saveFailed')
  } finally {
    deletingBusy.value = false
  }
}

const isEditing = computed(() => editingId.value !== null)
const serverOrigin = computed(() => window.location.origin)

async function loadDevices(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await listAllBarriers(locale.value)
    devices.value = result.data
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('barriers.loadFailed')
  } finally {
    loading.value = false
  }
}

// 自动发现的设备：收录（转正式对接列表）或移除
async function loadAutoDevices(): Promise<void> {
  try {
    const result = await listAutoRegisteredDevices(locale.value)
    autoDevices.value = result.data
  } catch {
    autoDevices.value = []
  }
}

// 页面停留期间定时刷新设备状态（静默，不置 loading），让在线/离线随心跳实时可见
const STATUS_REFRESH_MS = 10_000
let statusTimer: ReturnType<typeof setInterval> | null = null

async function refreshDeviceStatus(): Promise<void> {
  try {
    const result = await listAllBarriers(locale.value)
    devices.value = result.data
  } catch {
    // 静默失败：保留上次数据等待下一轮
  }
  try {
    const result = await listAutoRegisteredDevices(locale.value)
    autoDevices.value = result.data
  } catch {
    // 静默失败：保留上次数据等待下一轮
  }
}

// 已接入驱动目录：来自当前构建引入的驱动模块
async function loadDrivers(): Promise<void> {
  driversLoading.value = true
  try {
    const result = await listDriverFactories(locale.value)
    drivers.value = result.data
  } catch {
    drivers.value = []
  } finally {
    driversLoading.value = false
  }
}

async function removeAutoDevice(device: AutoRegisteredDeviceView): Promise<void> {
  autoBusyId.value = device.id
  try {
    await deleteAutoRegisteredDevice(device.id, locale.value)
    autoDevices.value = autoDevices.value.filter((item) => item.id !== device.id)
  } catch {
    // 删除失败保持列表原样
  } finally {
    autoBusyId.value = null
  }
}

async function adoptAutoDevice(device: AutoRegisteredDeviceView): Promise<void> {
  // 后端已存在同 code 设备：只做收录标记，阻止重复发现
  if (devices.value.some((item) => item.code.toLowerCase() === device.code.toLowerCase())) {
    await markAdopted(device)
    autoDevices.value = autoDevices.value.filter((item) => item.id !== device.id)
    return
  }
  autoBusyId.value = device.id
  try {
    const result = await createBarrierGlobal(
      {
        name: device.name || device.code,
        code: device.code,
        enabled: true,
      },
      locale.value,
    )
    devices.value = [result.data, ...devices.value]
    await markAdopted(device)
    autoDevices.value = autoDevices.value.filter((item) => item.id !== device.id)
  } catch {
    autoDevices.value = autoDevices.value.filter((item) => item.id !== device.id)
  } finally {
    autoBusyId.value = null
  }
}

async function markAdopted(device: AutoRegisteredDeviceView): Promise<void> {
  await adoptAutoRegisteredDevice(device.id, locale.value)
}

const filteredDevices = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) {
    return devices.value
  }
  return devices.value.filter(
    (item) =>
      item.name.toLowerCase().includes(query) ||
      item.code.toLowerCase().includes(query) ||
      (item.laneName ?? '').toLowerCase().includes(query),
  )
})

onMounted(() => {
  void loadDevices()
  void loadAutoDevices()
  void loadDrivers()
  void listLanes(locale.value)
    .then((result) => {
      lanes.value = result.data
    })
    .catch(() => {
      lanes.value = []
    })
  statusTimer = setInterval(() => {
    void refreshDeviceStatus()
  }, STATUS_REFRESH_MS)
})

onUnmounted(() => {
  if (statusTimer !== null) {
    clearInterval(statusTimer)
    statusTimer = null
  }
})

function laneName(device: BarrierView): string {
  if (!device.laneId) {
    return t('barriers.unbound')
  }
  const lane = lanes.value.find((item) => item.id === device.laneId)
  const name = lane?.name ?? device.laneName ?? t('barriers.unbound')
  const direction =
    lane && lane.laneType !== 'BIDIRECTIONAL'
      ? lane.laneType === 'EXIT'
        ? 'EXIT'
        : 'ENTRANCE'
      : null
  if (!direction) {
    return name
  }
  const directionLabel =
    direction === 'EXIT' ? t('barriers.bindDirectionExit') : t('barriers.bindDirectionEntrance')
  return `${name} · ${directionLabel}`
}

function resetForm(): void {
  editingId.value = null
  formName.value = ''
  formCode.value = ''
  formBrand.value = ''
  formModel.value = ''
  modelSuggestOpen.value = false
  formHost.value = ''
  formPortText.value = ''
  formEnabled.value = true
  formError.value = ''
}

function openCreate(): void {
  resetForm()
  showForm.value = true
}

function openEdit(device: BarrierView): void {
  editingId.value = device.id
  formName.value = device.name
  formCode.value = device.code
  formBrand.value = device.brand ?? ''
  formModel.value = device.model ?? ''
  modelSuggestOpen.value = false
  formHost.value = device.host ?? ''
  formPortText.value = device.port != null ? String(device.port) : ''
  formEnabled.value = device.enabled
  formError.value = ''
  showForm.value = true
}

/** 切换品牌时型号不兼容，清空待选型号并收起建议。 */
function onBrandChange(): void {
  formModel.value = ''
  modelSuggestOpen.value = false
}

/** 点击/回车选中某条型号建议。 */
function pickModel(model: string): void {
  formModel.value = model
  modelSuggestOpen.value = false
}

/** 回车选中当前过滤结果的第一条建议。 */
function pickTopModel(): void {
  const first = modelSuggestions.value[0]
  if (modelSuggestOpen.value && first) pickModel(first)
}

function closeForm(): void {
  showForm.value = false
  resetForm()
}

async function onSubmit(): Promise<void> {
  formError.value = ''
  const name = formName.value.trim()
  const code = formCode.value.trim()
  if (!name || (!isEditing.value && !code)) {
    formError.value = t('barriers.formRequired')
    return
  }
  if (!isEditing.value && code.length < 2) {
    formError.value = t('barriers.codeTooShort')
    return
  }
  if (
    !isEditing.value &&
    devices.value.some((item) => item.code.toLowerCase() === code.toLowerCase())
  ) {
    formError.value = t('barriers.codeExists')
    return
  }
  const port = parsePort()
  if (port === 'invalid') {
    formError.value = t('barriers.portInvalid')
    return
  }
  // 品牌下有明确型号时必须选定具体型号
  if (modelOptions.value.length > 0 && !formModel.value.trim()) {
    formError.value = t('barriers.modelRequired')
    return
  }
  // 全量提交：清空输入即从档案清除品牌/型号/连接参数
  const payload = {
    name,
    enabled: formEnabled.value,
    brand: formBrand.value.trim() || null,
    model: formModel.value.trim() || null,
    host: formHost.value.trim() || null,
    port,
  }
  saving.value = true
  try {
    if (isEditing.value && editingId.value) {
      const result = await updateBarrierGlobal(editingId.value, payload, locale.value)
      devices.value = devices.value.map((item) => (item.id === result.data.id ? result.data : item))
    } else {
      const result = await createBarrierGlobal({ ...payload, code }, locale.value)
      devices.value = [result.data, ...devices.value]
    }
    closeForm()
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : t('barriers.saveFailed')
  } finally {
    saving.value = false
  }
}

/** 端口解析：空=null；非 1-65535 整数返回 'invalid'。 */
function parsePort(): number | null | 'invalid' {
  const raw = formPortText.value.trim()
  if (!raw) return null
  const value = Number(raw)
  if (!Number.isInteger(value) || value < 1 || value > 65535) {
    return 'invalid'
  }
  return value
}

/** 同步主板时间：轮询指令状态的间隔与总超时。服务端指令有效时限 5s（约 1~2 个轮询周期）。 */
const SYNC_POLL_INTERVAL_MS = 1000
const SYNC_POLL_TIMEOUT_MS = 12000

function showSyncNotice(kind: 'ok' | 'error', text: string): void {
  syncNotice.value = { kind, text }
  if (syncNoticeTimer !== null) {
    clearTimeout(syncNoticeTimer)
    syncNoticeTimer = null
  }
  syncNoticeTimer = setTimeout(() => {
    syncNotice.value = null
    syncNoticeTimer = null
  }, 6000)
}

/** 「同步主板时间」行操作：写入 SYNC_TIME 预排指令后轮询其状态。
 *  设备下次轮询/推送时，服务端在响应中附带 0x05 时间同步帧并标记 DELIVERED；
 *  EXPIRED / 超时说明设备未在有效时限内取走，多半离线或未接入识别网关。 */
async function syncDeviceTime(device: BarrierView): Promise<void> {
  if (syncBusyId.value !== null) return
  const name = device.name || device.code
  syncBusyId.value = device.id
  syncNotice.value = null
  try {
    const queued = await enqueueDeviceCommand(device.id, 'SYNC_TIME', 'sync-time:manual', locale.value)
    const commandId = queued.data.id
    let status = queued.data.status
    const deadline = Date.now() + SYNC_POLL_TIMEOUT_MS
    while (status === 'PENDING' && Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, SYNC_POLL_INTERVAL_MS))
      const recent = await listDeviceCommands(device.id, 20, locale.value)
      const found = recent.data.find((cmd) => cmd.id === commandId)
      if (found) {
        status = found.status
      }
    }
    if (status === 'DELIVERED') {
      showSyncNotice('ok', t('barriers.syncTimeOk', { name }))
    } else {
      showSyncNotice('error', t('barriers.syncTimeNotTaken', { name }))
    }
  } catch (error) {
    const reason = error instanceof ApiError ? error.message : String(error)
    showSyncNotice('error', t('barriers.syncTimeQueueFailed', { name, reason }))
  } finally {
    syncBusyId.value = null
  }
}
</script>

<template>
  <section class="page">
    <p class="banner planning">{{ t('barriers.planningHint') }}</p>
    <p v-if="errorMessage" class="banner error">{{ errorMessage }}</p>
    <p v-if="syncNotice" class="banner" :class="syncNotice.kind === 'ok' ? 'ok' : 'error'">
      {{ syncNotice.text }}
    </p>

    <section class="table-card driver-card">
      <div class="auto-head">
        <div>
          <h4>{{ t('barriers.driversTitle') }}</h4>
          <p class="field-hint">{{ t('barriers.driversHint') }}</p>
        </div>
      </div>
      <div v-if="drivers.length > 0" class="driver-grid">
        <div v-for="d in drivers" :key="`${d.brand}|${d.model}`" class="driver-item">
          <strong class="driver-name">{{ d.displayName }}</strong>
          <div v-if="d.supportedModels.length > 0" class="driver-models">
            <span class="field-hint">{{ t('barriers.driversModelsLabel') }}</span>
            <span v-for="m in d.supportedModels" :key="m" class="model-chip">{{ m }}</span>
          </div>
          <span v-else class="driver-models all">{{ t('barriers.driversAllModels') }}</span>
        </div>
      </div>
      <div v-else-if="driversLoading" class="empty">
        <p>{{ t('lanes.loading') }}</p>
      </div>
      <div v-else class="empty">
        <p>{{ t('barriers.driversEmpty') }}</p>
      </div>
    </section>

    <div class="toolbar">
      <label class="search">
        <span class="sr-only">{{ t('page.search') }}</span>
        <input v-model="searchQuery" type="search" :placeholder="t('barriers.searchPlaceholder')" />
      </label>
      <button v-if="isAdmin" type="button" @click="openCreate">{{ t('barriers.create') }}</button>
    </div>

    <div class="table-card">
      <table v-if="filteredDevices.length > 0">
        <thead>
          <tr>
            <th>{{ t('barriers.colName') }}</th>
            <th>{{ t('barriers.colCode') }}</th>
            <th>{{ t('page.colStatus') }}</th>
            <th>{{ t('barriers.colBoundLane') }}</th>
            <th>{{ t('page.colUpdated') }}</th>
            <th class="col-actions">{{ t('barriers.colActions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredDevices" :key="item.id">
            <td>{{ item.name }}</td>
            <td>{{ item.code }}</td>
            <td>
              <span v-if="!item.enabled" class="pill fail">
                {{ t('lanes.statusDisabled') }}
              </span>
              <span
                v-else
                class="pill"
                :class="item.online ? 'ok' : 'off'"
                :title="
                  item.lastPollAt
                    ? `${t('barriers.colLastPoll')}: ${formatTime(item.lastPollAt)}`
                    : undefined
                "
              >
                {{ item.online ? t('barriers.statusOnline') : t('barriers.statusOffline') }}
              </span>
            </td>
            <td>{{ laneName(item) }}</td>
            <td>{{ formatTime(item.updatedAt) }}</td>
            <td class="col-actions">
              <div class="action-group">
                <button v-if="isAdmin" type="button" class="link-btn" @click="openEdit(item)">
                  {{ t('barriers.edit') }}
                </button>
                <button
                  v-if="isAdmin && item.enabled"
                  type="button"
                  class="link-btn"
                  :disabled="syncBusyId !== null"
                  @click="syncDeviceTime(item)"
                >
                  {{ syncBusyId === item.id ? t('barriers.syncTimeSyncing') : t('barriers.syncTime') }}
                </button>
                <button
                  v-if="isAdmin"
                  type="button"
                  class="link-btn danger"
                  @click="confirmDeleteDevice(item)"
                >
                  {{ t('barriers.remove') }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else-if="loading" class="empty">
        <p>{{ t('lanes.loading') }}</p>
      </div>
      <div v-else class="empty">
        <strong>{{ t('barriers.empty') }}</strong>
        <p>{{ isAdmin ? t('barriers.emptyHintAdmin') : t('barriers.emptyHint') }}</p>
      </div>
    </div>

    <div v-if="autoDevices.length > 0" class="table-card auto-card">
      <div class="auto-head">
        <div>
          <h4>{{ t('barriers.autoTitle', { count: autoDevices.length }) }}</h4>
          <p class="field-hint">{{ t('barriers.autoHint') }}</p>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ t('barriers.colCode') }}</th>
            <th>{{ t('barriers.colBrand') }}</th>
            <th>{{ t('barriers.colLastPoll') }}</th>
            <th class="col-actions">{{ t('barriers.colActions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in autoDevices" :key="item.id">
            <td>
              <strong>{{ item.code }}</strong>
              <span class="field-hint"> · {{ item.name }}</span>
            </td>
            <td>{{ item.brand || t('barriers.unknownBrand') }}</td>
            <td>{{ formatTime(item.lastPollAt) }}</td>
            <td class="col-actions">
              <div class="action-group">
                <button
                  v-if="isAdmin"
                  type="button"
                  class="link-btn"
                  :disabled="autoBusyId !== null"
                  @click="adoptAutoDevice(item)"
                >
                  {{ autoBusyId === item.id ? t('barriers.adopting') : t('barriers.adopt') }}
                </button>
                <button
                  v-if="isAdmin"
                  type="button"
                  class="link-btn danger"
                  :disabled="autoBusyId !== null"
                  @click="removeAutoDevice(item)"
                >
                  {{ autoBusyId === item.id ? t('barriers.removing') : t('barriers.remove') }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="deletingDevice" class="modal-backdrop">
      <div class="modal">
        <h3>{{ t('barriers.removeTitle') }}</h3>
        <p class="hint">
          {{ t('barriers.removeHintName', { name: deletingDevice.name }) }}
        </p>
        <p class="remove-meta">
          <span><b>{{ t('barriers.code') }}:</b> {{ deletingDevice.code }}</span>
        </p>
        <p v-if="deletingDevice.laneId" class="form-error">
          {{ t('barriers.removeBoundHint') }}
        </p>
        <p v-if="deleteError" class="form-error">{{ deleteError }}</p>
        <div class="actions">
          <button type="button" class="ghost" :disabled="deletingBusy" @click="cancelDelete">
            {{ t('barriers.cancel') }}
          </button>
          <button
            type="button"
            class="danger"
            :disabled="deletingBusy"
            @click="void confirmSubmitDelete()"
          >
            {{ deletingBusy ? t('barriers.removing') : t('barriers.confirmRemove') }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="showForm" class="modal-backdrop">
      <form class="modal" @submit.prevent="onSubmit">
        <h3>{{ isEditing ? t('barriers.editTitle') : t('barriers.createTitle') }}</h3>
        <p class="hint">{{ t('barriers.createHint') }}</p>
        <label>
          <span>{{ t('barriers.name') }}</span>
          <input v-model="formName" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('barriers.code') }}</span>
          <input
            v-model="formCode"
            type="text"
            autocomplete="off"
            :readonly="isEditing"
            :class="{ locked: isEditing }"
          />
          <span v-if="isEditing" class="field-hint">{{ t('barriers.codeLocked') }}</span>
        </label>
        <label>
          <span>{{ t('barriers.brand') }}</span>
          <select v-model="formBrand" @change="onBrandChange">
            <option value="">{{ t('barriers.unknownBrand') }}</option>
            <option v-for="b in driverBrands" :key="b" :value="b">{{ b }}</option>
          </select>
          <span class="field-hint">{{ t('barriers.brandHint') }}</span>
        </label>
        <label v-if="modelOptions.length > 0">
          <span>{{ t('barriers.model') }}</span>
          <div class="suggest-wrap">
            <input
              v-model="formModel"
              type="text"
              autocomplete="off"
              :placeholder="t('barriers.modelPlaceholder')"
              @focus="modelSuggestOpen = true"
              @input="modelSuggestOpen = true"
              @keydown.enter.prevent="pickTopModel()"
              @keydown.esc="modelSuggestOpen = false"
              @blur="modelSuggestOpen = false"
            />
            <ul v-if="modelSuggestOpen && modelSuggestions.length > 0" class="suggest-list">
              <li v-for="m in modelSuggestions" :key="m" @mousedown.prevent="pickModel(m)">
                {{ m }}
              </li>
            </ul>
          </div>
          <span class="field-hint">{{ t('barriers.modelHint') }}</span>
        </label>
        <span v-else-if="formBrand.trim() !== ''" class="field-hint">
          {{ t('barriers.modelWildcardHint') }}
        </span>
        <label>
          <span>{{ t('barriers.host') }}</span>
          <input v-model="formHost" type="text" autocomplete="off" />
          <span class="field-hint">{{ t('barriers.hostHint') }}</span>
        </label>
        <label>
          <span>{{ t('barriers.port') }}</span>
          <input v-model="formPortText" type="number" min="1" max="65535" autocomplete="off" />
        </label>
        <div class="endpoint-guide">
          <p class="endpoint-title">{{ t('barriers.endpoints.title') }}</p>
          <p class="endpoint-server">{{ serverOrigin }}</p>
          <div class="endpoint-row">
            <div>
              <strong>{{ t('barriers.endpoints.push') }}</strong>
              <code>POST /api/v1/device-gateway/zhenshi/plate</code>
              <span class="field-hint">{{ t('barriers.endpoints.pushDesc') }}</span>
            </div>
          </div>
          <div class="endpoint-row">
            <div>
              <strong>{{ t('barriers.endpoints.poll') }}</strong>
              <code>GET /api/v1/device-gateway/{{ formCode || '{code}' }}/poll</code>
              <span class="field-hint">{{ t('barriers.endpoints.pollDesc') }}</span>
            </div>
          </div>
        </div>
        <label class="checkbox">
          <input v-model="formEnabled" type="checkbox" />
          <span>{{ t('barriers.enabled') }}</span>
        </label>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" :disabled="saving" @click="closeForm">
            {{ t('barriers.cancel') }}
          </button>
          <button type="submit" :disabled="saving">
            {{ saving ? t('lanes.saving') : isEditing ? t('barriers.save') : t('barriers.create') }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: grid;
  gap: 0.9rem;
}

.banner.planning {
  margin: 0;
  padding: 0.65rem 0.9rem;
  border-radius: 8px;
  color: #6b5a12;
  background: #fff6d8;
}

.banner.error {
  margin: 0;
  padding: 0.65rem 0.9rem;
  border-radius: 8px;
  color: var(--danger);
  background: #fdecec;
}

.banner.ok {
  margin: 0;
  padding: 0.65rem 0.9rem;
  border-radius: 8px;
  color: var(--ok);
  background: #e8f5ef;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
}

.search {
  flex: 1;
  max-width: 18rem;
}

.search input,
.toolbar button {
  border: 1px solid var(--border);
  border-radius: 8px;
  min-height: 2.25rem;
  padding: 0 0.8rem;
}

.search input {
  width: 100%;
  background: var(--surface);
  color: var(--text);
}

.toolbar button {
  background: var(--accent);
  color: #fff;
  font-weight: 600;
  border-color: transparent;
}

.table-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow);
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: start;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--border);
}

th {
  color: var(--muted);
  font-size: 0.8rem;
  font-weight: 600;
  background: #f7faf8;
}

.col-actions {
  width: 16rem;
  text-align: end;
}

.action-group {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 0.75rem;
}

tbody tr:last-child td {
  border-bottom: 0;
}

.link-btn {
  border: 0;
  background: none;
  color: var(--accent);
  font-weight: 600;
  padding: 0;
  cursor: pointer;
}

.link-btn.danger {
  color: var(--danger);
}

.link-btn:disabled {
  opacity: 0.55;
  cursor: default;
}

.pill {
  border-radius: 999px;
  padding: 0.15rem 0.6rem;
  font-size: 0.78rem;
  background: #f2f4f3;
}

.pill.ok {
  color: var(--ok);
  background: #e8f5ef;
}

.pill.fail {
  color: var(--danger);
  background: #fdecec;
}

.pill.off {
  color: #b45309;
  background: #fef3c7;
}

.empty {
  padding: 3rem 1.5rem;
  text-align: center;
}

.empty strong {
  display: block;
  margin-bottom: 0.35rem;
}

.empty p {
  margin: 0 auto;
  max-width: 28rem;
  color: var(--muted);
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 20, 0.45);
  display: grid;
  place-items: center;
  padding: 1rem;
  z-index: 20;
}

.modal {
  width: min(420px, 100%);
  max-height: calc(100dvh - 2rem);
  display: grid;
  align-content: start;
  gap: 0.75rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: var(--shadow);
  overflow-y: auto;
}

.modal h3 {
  margin: 0;
}

.hint {
  margin: -0.25rem 0 0;
  color: var(--muted);
  font-size: 0.9rem;
}

.field-hint {
  color: var(--muted);
  font-size: 0.82rem;
}

input.locked {
  background: #f4f6f5;
  color: var(--muted);
  cursor: not-allowed;
}

label {
  display: grid;
  gap: 0.35rem;
}

.checkbox {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.checkbox input {
  width: auto;
}

input,
select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
  font: inherit;
}

.endpoint-guide {
  display: grid;
  gap: 0.4rem;
  padding: 0.6rem 0.75rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #f7faf8;
}

.endpoint-title {
  margin: 0;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--muted);
}

.endpoint-server {
  margin: 0;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--accent);
  word-break: break-all;
}

.endpoint-row {
  display: grid;
  gap: 0.2rem;
  min-width: 0;
}

.endpoint-row code {
  font-size: 0.78rem;
  color: var(--text);
  word-break: break-all;
}

.form-error {
  margin: 0;
  color: var(--danger);
}

.actions {
  position: sticky;
  bottom: -1.25rem;
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin: 0 -1.25rem -1.25rem;
  padding: 0.75rem 1.25rem;
  background: var(--surface);
  border-top: 1px solid var(--border);
  border-radius: 0 0 12px 12px;
}

.actions button {
  border: 0;
  border-radius: 8px;
  padding: 0.55rem 0.85rem;
  font-weight: 600;
}

.actions button:not(.ghost) {
  color: #fff;
  background: var(--accent);
}

.actions button.danger {
  color: #fff;
  background: var(--danger);
}

.ghost {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
}

.remove-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  gap: 0.4rem 0.9rem;
  margin: 0;
  padding: 0.6rem 0.75rem;
  border-radius: 8px;
  border: 1px dashed var(--border);
  background: #fafbfc;
  font-size: 0.85rem;
  color: var(--muted);
}

.remove-meta b {
  color: var(--text);
  font-weight: 600;
}

.auto-card {
  border-style: dashed;
}

.auto-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.75rem 1rem 0.25rem;
}

.auto-head h4 {
  margin: 0 0 0.15rem;
}

.driver-card {
  padding-bottom: 0.4rem;
}

.driver-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
  gap: 0.65rem;
  padding: 0.5rem 1rem 0.95rem;
}

.driver-item {
  display: grid;
  gap: 0.45rem;
  padding: 0.7rem 0.85rem;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #fafbfc;
}

.driver-name {
  font-size: 0.92rem;
  color: var(--text);
}

.driver-models {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
}

.driver-models.all {
  color: var(--muted);
  font-size: 0.8rem;
}

.model-chip {
  border-radius: 999px;
  padding: 0.12rem 0.6rem;
  font-size: 0.78rem;
  color: var(--accent);
  background: #e6f2ec;
  border: 1px solid #d6e8dd;
}

/* 型号搜索框 + 建议面板 */
.suggest-wrap {
  position: relative;
}

.suggest-wrap input {
  width: 100%;
}

.suggest-list {
  position: absolute;
  top: calc(100% + 0.25rem);
  left: 0;
  right: 0;
  z-index: 30;
  max-height: 11rem;
  overflow-y: auto;
  margin: 0;
  padding: 0.25rem;
  list-style: none;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: var(--shadow);
}

.suggest-list li {
  padding: 0.45rem 0.6rem;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.9rem;
}

.suggest-list li:hover {
  background: #eef4f0;
  color: var(--accent);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
