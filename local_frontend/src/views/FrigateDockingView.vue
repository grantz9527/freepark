<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  createFrigateCameraApi,
  frigateEventTopic,
  getFrigateSettings,
  listFrigateCamerasApi,
  listLanes,
  simulateFrigateEventApi,
  testFrigateCameraApi,
  testFrigateSettings,
  updateFrigateCameraApi,
  updateFrigateSettings,
  type FrigateBindDirection,
  type FrigateCameraView,
  type FrigateLinkStatus,
  type FrigateSettingsView,
  type LaneView,
} from '@/api/client'
import { getUser } from '@/auth/session'
import { useSiteTime } from '@/composables/useSiteTime'
import { usePlateColorLabel } from '@/composables/usePlateColorLabel'
import { listBarrierDevices, type BarrierDevice } from '@/hardware/barrierDevices'
import type { RecognitionDirection } from '@/hardware/recognitionRecords'
import type { PlateColor } from '@/api/client'

interface EventLog {
  at: string
  message: string
}

const { t, locale } = useI18n()
const { formatTime } = useSiteTime()
const { plateColorLabel } = usePlateColorLabel()

const commonPlateColors: PlateColor[] = [
  'BLUE',
  'YELLOW',
  'GREEN',
  'YELLOW_GREEN',
  'BLACK',
  'WHITE',
  'OTHER',
]

const isAdmin = computed(() => getUser()?.role === 'ADMIN')
const loading = ref(true)
const server = ref<FrigateSettingsView | null>(null)
const mqttPassword = ref('')
const mqttPasswordSet = ref(false)
const cameras = ref<FrigateCameraView[]>([])
const barriers = ref<BarrierDevice[]>([])
const lanes = ref<LaneView[]>([])
const searchQuery = ref('')
const showForm = ref(false)
const editingId = ref<string | null>(null)
const formName = ref('')
const formCameraName = ref('')
const formEnabled = ref(true)
const formError = ref('')
const formBusy = ref(false)
const serverError = ref('')
const serverBusy = ref(false)
const debugCamera = ref<FrigateCameraView | null>(null)
const debugBusy = ref(false)
const debugLogs = ref<EventLog[]>([])
const simulatePlate = ref('沪A12345')
const simulatePlateColor = ref<PlateColor | ''>('BLUE')
const simulateDirection = ref<RecognitionDirection>('ENTRANCE')

const debugCameraLane = computed<LaneView | null>(() => {
  if (!debugCamera.value?.laneId) return null
  return lanes.value.find((l) => l.id === debugCamera.value!.laneId) ?? null
})
const needDirectionSelect = computed(
  () => !debugCameraLane.value || debugCameraLane.value.laneType === 'BIDIRECTIONAL',
)

const isEditing = computed(() => editingId.value !== null)
const passwordPlaceholder = computed(() =>
  mqttPasswordSet.value
    ? t('frigate.mqttPasswordKeepPlaceholder')
    : t('frigate.mqttPasswordEmptyPlaceholder'),
)
const filteredCameras = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) {
    return cameras.value
  }
  const prefix = server.value?.topicPrefix ?? 'frigate'
  return cameras.value.filter((item) => {
    const lane = laneName(item).toLowerCase()
    return (
      item.name.toLowerCase().includes(query) ||
      item.cameraName.toLowerCase().includes(query) ||
      frigateEventTopic(item.cameraName, prefix).toLowerCase().includes(query) ||
      lane.includes(query)
    )
  })
})

onMounted(async () => {
  barriers.value = listBarrierDevices()
  await Promise.all([loadSettings(), loadCameras(), loadLanes()])
  loading.value = false
})

async function loadSettings(): Promise<void> {
  try {
    const result = await getFrigateSettings(locale.value)
    applySettings(result.data)
  } catch (error) {
    serverError.value = error instanceof ApiError ? error.message : t('frigate.loadFailed')
  }
}

async function loadCameras(): Promise<void> {
  try {
    const result = await listFrigateCamerasApi(locale.value)
    cameras.value = result.data
  } catch (error) {
    serverError.value = error instanceof ApiError ? error.message : t('frigate.loadFailed')
  }
}

async function loadLanes(): Promise<void> {
  try {
    const result = await listLanes(locale.value)
    lanes.value = result.data
  } catch {
    lanes.value = []
  }
}

function applySettings(data: FrigateSettingsView): void {
  server.value = data
  mqttPasswordSet.value = data.mqttPasswordSet
  mqttPassword.value = ''
}

function laneName(camera: {
  laneId: string | null
  bindDirection: FrigateBindDirection | null
}): string {
  if (!camera.laneId) {
    return t('frigate.unbound')
  }
  const lane = lanes.value.find((item) => item.id === camera.laneId)
  const name = lane?.name ?? t('frigate.unbound')
  const direction =
    lane && lane.laneType !== 'BIDIRECTIONAL'
      ? lane.laneType === 'EXIT'
        ? 'EXIT'
        : 'ENTRANCE'
      : camera.bindDirection
  if (!direction) {
    return name
  }
  const directionLabel =
    direction === 'EXIT' ? t('frigate.bindDirectionExit') : t('frigate.bindDirectionEntrance')
  return `${name} · ${directionLabel}`
}

function statusLabel(status: FrigateLinkStatus): string {
  if (status === 'CONNECTED') {
    return t('frigate.linkConnected')
  }
  if (status === 'FAILED') {
    return t('frigate.linkFailed')
  }
  return t('frigate.linkDisconnected')
}

function statusClass(status: FrigateLinkStatus): string {
  if (status === 'CONNECTED') {
    return 'ok'
  }
  if (status === 'FAILED') {
    return 'fail'
  }
  return ''
}

function lastEventText(camera: FrigateCameraView): string {
  if (!camera.lastPlate || !camera.lastEventAt) {
    return t('frigate.noEvent')
  }
  return `${camera.lastPlate} · ${formatTime(camera.lastEventAt)}`
}

function resetForm(): void {
  editingId.value = null
  formName.value = ''
  formCameraName.value = ''
  formEnabled.value = true
  formError.value = ''
}

function openCreate(): void {
  resetForm()
  showForm.value = true
}

function openEdit(camera: FrigateCameraView): void {
  editingId.value = camera.id
  formName.value = camera.name
  formCameraName.value = camera.cameraName
  formEnabled.value = camera.enabled
  formError.value = ''
  showForm.value = true
}

function closeForm(): void {
  showForm.value = false
  resetForm()
}

async function onSaveServer(): Promise<void> {
  if (!server.value) {
    return
  }
  serverError.value = ''
  const apiHost = server.value.apiHost.trim()
  const mqttHost = server.value.mqttHost.trim()
  const topicPrefix = server.value.topicPrefix.trim()
  if (!apiHost || !mqttHost || !topicPrefix) {
    serverError.value = t('frigate.serverRequired')
    return
  }
  if (
    server.value.apiPort < 1 ||
    server.value.apiPort > 65535 ||
    server.value.mqttPort < 1 ||
    server.value.mqttPort > 65535
  ) {
    serverError.value = t('frigate.portInvalid')
    return
  }
  serverBusy.value = true
  try {
    const result = await updateFrigateSettings(
      {
        apiHost,
        apiPort: server.value.apiPort,
        mqttHost,
        mqttPort: server.value.mqttPort,
        topicPrefix,
        mqttUsername: server.value.mqttUsername.trim(),
        mqttPassword: mqttPassword.value,
        enabled: server.value.enabled,
      },
      locale.value,
    )
    applySettings(result.data)
  } catch (error) {
    serverError.value = error instanceof ApiError ? error.message : t('frigate.saveFailed')
  } finally {
    serverBusy.value = false
  }
}

async function testServer(): Promise<void> {
  await onSaveServer()
  if (serverError.value) {
    return
  }
  serverBusy.value = true
  try {
    const result = await testFrigateSettings(locale.value)
    applySettings(result.data)
  } catch (error) {
    serverError.value = error instanceof ApiError ? error.message : t('frigate.testFailed')
    await loadSettings()
  } finally {
    serverBusy.value = false
  }
}

async function onSubmit(): Promise<void> {
  formError.value = ''
  const name = formName.value.trim()
  const cameraName = formCameraName.value.trim()
  if (!name || !cameraName) {
    formError.value = t('frigate.formRequired')
    return
  }
  formBusy.value = true
  try {
    if (editingId.value) {
      await updateFrigateCameraApi(
        editingId.value,
        { name, cameraName, enabled: formEnabled.value },
        locale.value,
      )
    } else {
      await createFrigateCameraApi(
        { name, cameraName, enabled: formEnabled.value },
        locale.value,
      )
    }
    await loadCameras()
    closeForm()
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : t('frigate.saveFailed')
  } finally {
    formBusy.value = false
  }
}

function openDebug(camera: FrigateCameraView): void {
  debugCamera.value = camera
  debugLogs.value = []
  simulatePlate.value = camera.lastPlate || '沪A12345'
  simulatePlateColor.value = (camera.lastPlateColor as PlateColor) || 'BLUE'
}

function closeDebug(): void {
  debugCamera.value = null
  debugBusy.value = false
  debugLogs.value = []
}

function pushLog(message: string): void {
  debugLogs.value = [{ at: new Date().toISOString(), message }, ...debugLogs.value].slice(0, 20)
}

function upsertCamera(camera: FrigateCameraView): void {
  const index = cameras.value.findIndex((item) => item.id === camera.id)
  if (index < 0) {
    cameras.value = [camera, ...cameras.value]
    return
  }
  const next = [...cameras.value]
  next[index] = camera
  cameras.value = next
}

async function testCamera(): Promise<void> {
  if (!debugCamera.value || !server.value) {
    return
  }
  if (server.value.linkStatus !== 'CONNECTED') {
    pushLog(t('frigate.needServerConnected'))
    return
  }
  debugBusy.value = true
  pushLog(
    t('frigate.logTesting', {
      camera: debugCamera.value.cameraName,
      topic: frigateEventTopic(debugCamera.value.cameraName, server.value.topicPrefix),
      host: server.value.mqttHost,
      port: server.value.mqttPort,
    }),
  )
  try {
    const result = await testFrigateCameraApi(debugCamera.value.id, locale.value)
    upsertCamera(result.data)
    debugCamera.value = result.data
    pushLog(t('frigate.logConnected'))
  } catch (error) {
    pushLog(error instanceof ApiError ? error.message : t('frigate.testFailed'))
  } finally {
    debugBusy.value = false
  }
}

function linkedBarriers(camera: FrigateCameraView): BarrierDevice[] {
  if (!camera.laneId || !camera.linkageEnabled) {
    return []
  }
  return barriers.value.filter((item) => {
    if (item.laneId !== camera.laneId || item.linkStatus !== 'CONNECTED') {
      return false
    }
    if (!camera.bindDirection) {
      return true
    }
    return !item.bindDirection || item.bindDirection === camera.bindDirection
  })
}

async function simulateRecognition(): Promise<void> {
  if (!debugCamera.value || !server.value) {
    return
  }
  if (debugCamera.value.linkStatus !== 'CONNECTED') {
    pushLog(t('frigate.needCameraConnected'))
    return
  }
  const plate = simulatePlate.value.trim().toUpperCase()
  if (!plate) {
    pushLog(t('frigate.plateRequired'))
    return
  }
  const plateColor = simulatePlateColor.value ? (simulatePlateColor.value as PlateColor) : null
  if (!plateColor) {
    pushLog(t('systemSettings.plateColors') + ' -')
    return
  }

  debugBusy.value = true
  barriers.value = listBarrierDevices()
  pushLog(
    t('frigate.logEvent', {
      camera: debugCamera.value.cameraName,
      plate,
      topic: frigateEventTopic(debugCamera.value.cameraName, server.value.topicPrefix),
    }),
  )
  try {
    const result = await simulateFrigateEventApi(
      debugCamera.value.id,
      { plate, plateColor },
      locale.value,
    )
    upsertCamera(result.data)
    debugCamera.value = result.data

    // 识别记录与停车流水联动已由后端在事件链路中完成
    pushLog(t('frigate.logRecognition', { plate }))

    if (!debugCamera.value.laneId) {
      pushLog(t('frigate.logUnbound'))
      return
    }
    if (!debugCamera.value.linkageEnabled) {
      pushLog(t('frigate.logNotifyOnly', { plate }))
      return
    }
    const devices = linkedBarriers(debugCamera.value)
    if (devices.length === 0) {
      pushLog(t('frigate.logNoBarrier'))
      return
    }
    const names = devices.map((item) => item.name).join(' · ')
    pushLog(t('frigate.logLinkage', { plate, devices: names }))
  } catch (error) {
    pushLog(error instanceof ApiError ? error.message : t('frigate.saveFailed'))
  } finally {
    debugBusy.value = false
  }
}
</script>

<template>
  <section class="page">
    <p class="banner ok-hint">{{ t('frigate.planningHint') }}</p>
    <p v-if="loading" class="hint">{{ t('page.loading') }}</p>

    <div v-if="server" class="card">
      <h3>{{ t('frigate.serverTitle') }}</h3>
      <p class="hint">{{ t('frigate.serverHint') }}</p>
      <div class="grid two">
        <label>
          <span>{{ t('frigate.apiHost') }}</span>
          <input v-model="server.apiHost" type="text" autocomplete="off" :disabled="!isAdmin" />
        </label>
        <label>
          <span>{{ t('frigate.apiPort') }}</span>
          <input v-model.number="server.apiPort" type="number" min="1" max="65535" :disabled="!isAdmin" />
        </label>
        <label>
          <span>{{ t('frigate.mqttHost') }}</span>
          <input v-model="server.mqttHost" type="text" autocomplete="off" :disabled="!isAdmin" />
        </label>
        <label>
          <span>{{ t('frigate.mqttPort') }}</span>
          <input v-model.number="server.mqttPort" type="number" min="1" max="65535" :disabled="!isAdmin" />
        </label>
        <label>
          <span>{{ t('frigate.topicPrefix') }}</span>
          <input v-model="server.topicPrefix" type="text" autocomplete="off" :disabled="!isAdmin" />
        </label>
        <label>
          <span>{{ t('frigate.mqttUsername') }}</span>
          <input v-model="server.mqttUsername" type="text" autocomplete="off" :disabled="!isAdmin" />
        </label>
        <label>
          <span>{{ t('frigate.mqttPassword') }}</span>
          <input
            v-model="mqttPassword"
            type="password"
            autocomplete="new-password"
            :placeholder="passwordPlaceholder"
            :disabled="!isAdmin"
          />
        </label>
      </div>
      <label class="checkbox">
        <input v-model="server.enabled" type="checkbox" :disabled="!isAdmin" />
        <span>{{ t('frigate.serverEnabled') }}</span>
      </label>
      <p class="field-hint">
        {{ t('frigate.serverStatus') }}：
        <span class="pill" :class="statusClass(server.linkStatus)">{{ statusLabel(server.linkStatus) }}</span>
        <span v-if="server.lastTestAt"> · {{ formatTime(server.lastTestAt) }}</span>
      </p>
      <p v-if="serverError" class="form-error">{{ serverError }}</p>
      <div v-if="isAdmin" class="actions start">
        <button type="button" class="ghost" :disabled="serverBusy" @click="onSaveServer">
          {{ t('frigate.saveServer') }}
        </button>
        <button type="button" :disabled="serverBusy" @click="testServer">
          {{ serverBusy ? t('frigate.testing') : t('frigate.testServer') }}
        </button>
      </div>
    </div>

    <div class="toolbar">
      <label class="search">
        <span class="sr-only">{{ t('page.search') }}</span>
        <input v-model="searchQuery" type="search" :placeholder="t('frigate.searchPlaceholder')" />
      </label>
      <button v-if="isAdmin" type="button" @click="openCreate">{{ t('frigate.create') }}</button>
    </div>

    <div class="table-card">
      <table v-if="filteredCameras.length > 0">
        <thead>
          <tr>
            <th>{{ t('frigate.colName') }}</th>
            <th>{{ t('frigate.colCameraName') }}</th>
            <th>{{ t('frigate.colTopic') }}</th>
            <th>{{ t('frigate.colLink') }}</th>
            <th>{{ t('frigate.colBoundLane') }}</th>
            <th>{{ t('frigate.colLastEvent') }}</th>
            <th class="col-actions">{{ t('frigate.colActions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredCameras" :key="item.id">
            <td>{{ item.name }}</td>
            <td>{{ item.cameraName }}</td>
            <td>{{ frigateEventTopic(item.cameraName, server?.topicPrefix ?? 'frigate') }}</td>
            <td>
              <span class="pill" :class="statusClass(item.linkStatus)">{{ statusLabel(item.linkStatus) }}</span>
            </td>
            <td>{{ laneName(item) }}</td>
            <td>{{ lastEventText(item) }}</td>
            <td class="col-actions">
              <div class="action-group">
                <button type="button" class="link-btn" @click="openDebug(item)">
                  {{ t('frigate.debug') }}
                </button>
                <button v-if="isAdmin" type="button" class="link-btn" @click="openEdit(item)">
                  {{ t('frigate.edit') }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">
        <strong>{{ t('frigate.empty') }}</strong>
        <p>{{ isAdmin ? t('frigate.emptyHintAdmin') : t('frigate.emptyHint') }}</p>
      </div>
    </div>

    <div v-if="showForm" class="modal-backdrop">
      <form class="modal" @submit.prevent="onSubmit">
        <h3>{{ isEditing ? t('frigate.editTitle') : t('frigate.createTitle') }}</h3>
        <p class="hint">{{ t('frigate.createHint') }}</p>
        <label>
          <span>{{ t('frigate.name') }}</span>
          <input v-model="formName" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('frigate.cameraName') }}</span>
          <input v-model="formCameraName" type="text" autocomplete="off" />
          <span class="field-hint">{{ t('frigate.cameraNameHint') }}</span>
        </label>
        <label class="checkbox">
          <input v-model="formEnabled" type="checkbox" />
          <span>{{ t('frigate.enabled') }}</span>
        </label>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" :disabled="formBusy" @click="closeForm">
            {{ t('frigate.cancel') }}
          </button>
          <button type="submit" :disabled="formBusy">
            {{ isEditing ? t('frigate.save') : t('frigate.create') }}
          </button>
        </div>
      </form>
    </div>

    <div v-if="debugCamera && server" class="modal-backdrop">
      <div class="modal wide">
        <h3>{{ t('frigate.debugTitle') }} · {{ debugCamera.name }}</h3>
        <p class="hint">{{ t('frigate.debugHint') }}</p>
        <p class="field-hint">
          {{ debugCamera.cameraName }} ·
          {{ frigateEventTopic(debugCamera.cameraName, server.topicPrefix) }}
        </p>
        <p>
          <span class="pill" :class="statusClass(debugCamera.linkStatus)">
            {{ statusLabel(debugCamera.linkStatus) }}
          </span>
          · {{ laneName(debugCamera) }}
        </p>
        <div class="debug-actions">
          <button type="button" :disabled="debugBusy" @click="testCamera">
            {{ debugBusy ? t('frigate.testing') : t('frigate.testCamera') }}
          </button>
        </div>
        <label>
          <span>{{ t('frigate.simulatePlate') }}</span>
          <input v-model="simulatePlate" type="text" autocomplete="off" />
        </label>
        <label v-if="needDirectionSelect">
          <span>{{ t('frigate.simulateDirection') }}</span>
          <select v-model="simulateDirection">
            <option value="ENTRANCE">{{ t('recognitionRecords.directionEntrance') }}</option>
            <option value="EXIT">{{ t('recognitionRecords.directionExit') }}</option>
          </select>
        </label>
        <label>
          <span>{{ t('systemSettings.plateColors') }}</span>
          <select v-model="simulatePlateColor">
            <option value="">-</option>
            <option v-for="color in commonPlateColors" :key="color" :value="color">
              {{ plateColorLabel(color) }}
            </option>
          </select>
        </label>
        <div class="debug-actions">
          <button type="button" :disabled="debugBusy" @click="simulateRecognition">
            {{ t('frigate.simulateEvent') }}
          </button>
        </div>
        <p class="field-hint">{{ t('frigate.simulateHint') }}</p>
        <div class="log">
          <p v-if="debugLogs.length === 0" class="field-hint">{{ t('frigate.logEmpty') }}</p>
          <p v-for="(item, index) in debugLogs" :key="index">
            {{ formatTime(item.at) }} · {{ item.message }}
          </p>
        </div>
        <div class="actions">
          <button type="button" class="ghost" @click="closeDebug">{{ t('frigate.cancel') }}</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: grid;
  gap: 0.9rem;
}

.banner.ok-hint {
  margin: 0;
  padding: 0.65rem 0.9rem;
  border-radius: 8px;
  color: #0f5132;
  background: #d1e7dd;
}

.card,
.table-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow);
}

.card {
  display: grid;
  gap: 0.75rem;
  padding: 1rem 1.1rem;
}

.card h3 {
  margin: 0;
}

.hint {
  margin: 0;
  color: var(--muted);
  font-size: 0.9rem;
}

.grid.two {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
}

label {
  display: grid;
  gap: 0.35rem;
}

input[type='text'],
input[type='password'],
input[type='number'],
input[type='search'],
select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.55rem 0.7rem;
  background: #fff;
}

select {
  appearance: none;
  background-image:
    linear-gradient(45deg, transparent 50%, var(--muted) 50%),
    linear-gradient(135deg, var(--muted) 50%, transparent 50%);
  background-position:
    calc(100% - 18px) 50%,
    calc(100% - 12px) 50%;
  background-size: 6px 6px, 6px 6px;
  background-repeat: no-repeat;
  padding-right: 32px;
}

.checkbox {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.checkbox input {
  width: auto;
}

.field-hint {
  margin: 0;
  color: var(--muted);
  font-size: 0.82rem;
}

.form-error {
  margin: 0;
  color: var(--danger);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.actions.start {
  justify-content: flex-start;
}

.toolbar {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  justify-content: space-between;
}

.search {
  flex: 1;
}

.table-card {
  overflow: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 0.7rem 0.85rem;
  border-bottom: 1px solid var(--border);
  text-align: left;
  font-size: 0.92rem;
}

th {
  color: var(--muted);
  font-weight: 600;
}

.col-actions {
  white-space: nowrap;
}

.action-group {
  display: flex;
  gap: 0.45rem;
}

.link-btn {
  border: 0;
  background: transparent;
  color: var(--accent);
  font-weight: 600;
  cursor: pointer;
  padding: 0;
}

.pill {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 0.1rem 0.55rem;
  font-size: 0.78rem;
  font-weight: 600;
  background: #eef1f0;
  color: var(--muted);
}

.pill.ok {
  background: #e8f5ef;
  color: var(--ok);
}

.pill.fail {
  background: #fdecec;
  color: var(--danger);
}

.empty {
  padding: 2.5rem 1.5rem;
  text-align: center;
}

.empty strong {
  display: block;
  margin-bottom: 0.35rem;
}

.empty p {
  margin: 0;
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
  display: grid;
  gap: 0.75rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: var(--shadow);
}

.modal.wide {
  width: min(560px, 100%);
}

.modal h3 {
  margin: 0;
}

.debug-actions {
  display: flex;
  gap: 0.5rem;
}

.log {
  max-height: 12rem;
  overflow: auto;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.65rem 0.75rem;
  background: #f8faf9;
}

.log p {
  margin: 0 0 0.35rem;
  font-size: 0.85rem;
}

.ghost {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.5rem 0.85rem;
  font-weight: 600;
  background: #fff;
  color: var(--text);
  cursor: pointer;
}

button {
  border: 0;
  border-radius: 8px;
  padding: 0.5rem 0.85rem;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
  cursor: pointer;
}

button:disabled,
.ghost:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
}

@media (max-width: 760px) {
  .grid.two {
    grid-template-columns: 1fr;
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
