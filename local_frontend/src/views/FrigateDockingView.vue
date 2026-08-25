<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { listLanes, type LaneView } from '@/api/client'
import { getUser } from '@/auth/session'
import { useSiteTime } from '@/composables/useSiteTime'
import { listBarrierDevices, type BarrierDevice } from '@/hardware/barrierDevices'
import {
  eventTopicOf,
  listFrigateCameras,
  loadFrigateServer,
  recordFrigateRecognition,
  saveFrigateCamera,
  saveFrigateServer,
  setFrigateCameraLinkStatus,
  setFrigateServerLinkStatus,
  wait,
  type FrigateBindDirection,
  type FrigateCamera,
  type FrigateLinkStatus,
  type FrigateServer,
} from '@/hardware/frigateCameras'

interface EventLog {
  at: string
  message: string
}

const { t, locale } = useI18n()
const { formatTime } = useSiteTime()

const isAdmin = computed(() => getUser()?.role === 'ADMIN')
const server = ref<FrigateServer>(loadFrigateServer())
const cameras = ref<FrigateCamera[]>([])
const barriers = ref<BarrierDevice[]>([])
const lanes = ref<LaneView[]>([])
const searchQuery = ref('')
const showForm = ref(false)
const editingId = ref<string | null>(null)
const formName = ref('')
const formCameraName = ref('')
const formEnabled = ref(true)
const formError = ref('')
const serverError = ref('')
const serverBusy = ref(false)
const debugCamera = ref<FrigateCamera | null>(null)
const debugBusy = ref(false)
const debugLogs = ref<EventLog[]>([])
const simulatePlate = ref('沪A12345')

const isEditing = computed(() => editingId.value !== null)
const filteredCameras = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) {
    return cameras.value
  }
  return cameras.value.filter((item) => {
    const lane = laneName(item).toLowerCase()
    return (
      item.name.toLowerCase().includes(query) ||
      item.cameraName.toLowerCase().includes(query) ||
      eventTopicOf(item.cameraName, server.value).toLowerCase().includes(query) ||
      lane.includes(query)
    )
  })
})

onMounted(async () => {
  cameras.value = listFrigateCameras()
  barriers.value = listBarrierDevices()
  try {
    const result = await listLanes(locale.value)
    lanes.value = result.data
  } catch {
    lanes.value = []
  }
})

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

function lastEventText(camera: FrigateCamera): string {
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

function openEdit(camera: FrigateCamera): void {
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

function onSaveServer(): void {
  serverError.value = ''
  const apiHost = server.value.apiHost.trim()
  const mqttHost = server.value.mqttHost.trim()
  const topicPrefix = server.value.topicPrefix.trim()
  if (!apiHost || !mqttHost || !topicPrefix) {
    serverError.value = t('frigate.serverRequired')
    return
  }
  if (server.value.apiPort < 1 || server.value.apiPort > 65535 || server.value.mqttPort < 1 || server.value.mqttPort > 65535) {
    serverError.value = t('frigate.portInvalid')
    return
  }
  server.value = saveFrigateServer({
    apiHost,
    apiPort: server.value.apiPort,
    mqttHost,
    mqttPort: server.value.mqttPort,
    topicPrefix,
    mqttUsername: server.value.mqttUsername.trim(),
    enabled: server.value.enabled,
    linkStatus: server.value.linkStatus,
    lastTestAt: server.value.lastTestAt,
  })
}

async function testServer(): Promise<void> {
  onSaveServer()
  if (serverError.value) {
    return
  }
  serverBusy.value = true
  await wait(700)
  server.value = setFrigateServerLinkStatus('CONNECTED', server.value)
  serverBusy.value = false
}

function onSubmit(): void {
  formError.value = ''
  const name = formName.value.trim()
  const cameraName = formCameraName.value.trim()
  if (!name || !cameraName) {
    formError.value = t('frigate.formRequired')
    return
  }
  const duplicated = cameras.value.some(
    (item) => item.cameraName.toLowerCase() === cameraName.toLowerCase() && item.id !== editingId.value,
  )
  if (duplicated) {
    formError.value = t('frigate.cameraNameExists')
    return
  }
  cameras.value = saveFrigateCamera(
    {
      id: editingId.value ?? undefined,
      name,
      cameraName,
      enabled: formEnabled.value,
    },
    cameras.value,
  )
  closeForm()
}

function openDebug(camera: FrigateCamera): void {
  debugCamera.value = camera
  debugLogs.value = []
  simulatePlate.value = camera.lastPlate || '沪A12345'
}

function closeDebug(): void {
  debugCamera.value = null
  debugBusy.value = false
  debugLogs.value = []
}

function pushLog(message: string): void {
  debugLogs.value = [{ at: new Date().toISOString(), message }, ...debugLogs.value].slice(0, 20)
}

async function testCamera(): Promise<void> {
  if (!debugCamera.value) {
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
      topic: eventTopicOf(debugCamera.value.cameraName, server.value),
      host: server.value.mqttHost,
      port: server.value.mqttPort,
    }),
  )
  await wait(700)
  cameras.value = setFrigateCameraLinkStatus(debugCamera.value.id, 'CONNECTED', cameras.value)
  debugCamera.value = cameras.value.find((item) => item.id === debugCamera.value?.id) ?? null
  pushLog(t('frigate.logConnected'))
  debugBusy.value = false
}

function linkedBarriers(camera: FrigateCamera): BarrierDevice[] {
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
  if (!debugCamera.value) {
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
  debugBusy.value = true
  barriers.value = listBarrierDevices()
  pushLog(
    t('frigate.logEvent', {
      camera: debugCamera.value.cameraName,
      plate,
      topic: eventTopicOf(debugCamera.value.cameraName, server.value),
    }),
  )
  await wait(400)
  cameras.value = recordFrigateRecognition(debugCamera.value.id, plate, cameras.value)
  debugCamera.value = cameras.value.find((item) => item.id === debugCamera.value?.id) ?? null
  if (!debugCamera.value?.laneId) {
    pushLog(t('frigate.logUnbound'))
    debugBusy.value = false
    return
  }
  if (!debugCamera.value.linkageEnabled) {
    pushLog(t('frigate.logNotifyOnly', { plate }))
    debugBusy.value = false
    return
  }
  const devices = linkedBarriers(debugCamera.value)
  if (devices.length === 0) {
    pushLog(t('frigate.logNoBarrier'))
    debugBusy.value = false
    return
  }
  const names = devices.map((item) => item.name).join(' · ')
  pushLog(t('frigate.logLinkage', { plate, devices: names }))
  debugBusy.value = false
}
</script>

<template>
  <section class="page">
    <p class="banner planning">{{ t('frigate.planningHint') }}</p>

    <div class="card">
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
            <td>{{ eventTopicOf(item.cameraName, server) }}</td>
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
          <button type="button" class="ghost" @click="closeForm">{{ t('frigate.cancel') }}</button>
          <button type="submit">{{ isEditing ? t('frigate.save') : t('frigate.create') }}</button>
        </div>
      </form>
    </div>

    <div v-if="debugCamera" class="modal-backdrop">
      <div class="modal wide">
        <h3>{{ t('frigate.debugTitle') }} · {{ debugCamera.name }}</h3>
        <p class="hint">{{ t('frigate.debugHint') }}</p>
        <p class="field-hint">
          {{ debugCamera.cameraName }} · {{ eventTopicOf(debugCamera.cameraName, server) }}
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

.banner.planning {
  margin: 0;
  padding: 0.65rem 0.9rem;
  border-radius: 8px;
  color: #6b5a12;
  background: #fff6d8;
}

.card,
.card,
.table-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow);
}

.card,
.card {
  display: grid;
  gap: 0.75rem;
  padding: 1.1rem 1.2rem;
}

.card h3 {
  margin: 0;
}

.grid.two {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
  gap: 0.75rem;
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
.toolbar button,
.debug-actions button {
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

.toolbar button,
.debug-actions button:not(.ghost) {
  background: var(--accent);
  color: #fff;
  font-weight: 600;
  border-color: transparent;
}

.table-card {
  overflow: hidden;
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
  width: 10rem;
  text-align: end;
}

.action-group {
  display: flex;
  justify-content: flex-end;
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

.empty {
  padding: 3rem 1.5rem;
  text-align: center;
}

.empty strong {
  display: block;
  margin-bottom: 0.35rem;
}

.empty p,
.hint,
.field-hint {
  margin: 0;
  color: var(--muted);
}

.hint {
  font-size: 0.9rem;
}

.field-hint {
  font-size: 0.82rem;
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
  width: min(480px, 100%);
  max-height: calc(100vh - 2rem);
  overflow: auto;
  display: grid;
  gap: 0.75rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: var(--shadow);
}

.modal.wide {
  width: min(720px, 100%);
}

.modal h3 {
  margin: 0;
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

input {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
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

.actions button,
.debug-actions button {
  border: 0;
  border-radius: 8px;
  padding: 0.55rem 0.85rem;
  font-weight: 600;
}

.actions button:not(.ghost) {
  color: #fff;
  background: var(--accent);
}

.ghost {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
}

.debug-actions {
  display: flex;
  gap: 0.5rem;
}

.log {
  display: grid;
  gap: 0.35rem;
  max-height: 12rem;
  overflow: auto;
  padding: 0.75rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #f7faf8;
  font-size: 0.85rem;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
