<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { listLanes, type LaneView } from '@/api/client'
import { getUser } from '@/auth/session'
import { useSiteTime } from '@/composables/useSiteTime'
import {
  BARRIER_CAMERA_TYPES,
  boardProfile,
  boardsForCamera,
  commandsForBoard,
  defaultBoardId,
  listBarrierDevices,
  saveBarrierDevice,
  setBarrierLinkStatus,
  wait,
  type BarrierBindDirection,
  type BarrierBoardId,
  type BarrierCameraType,
  type BarrierCommand,
  type BarrierDevice,
  type BarrierLinkStatus,
} from '@/hardware/barrierDevices'

interface DebugLog {
  at: string
  message: string
}

const { t, locale } = useI18n()
const { formatTime } = useSiteTime()

const isAdmin = computed(() => getUser()?.role === 'ADMIN')
const devices = ref<BarrierDevice[]>([])
const lanes = ref<LaneView[]>([])
const searchQuery = ref('')
const showForm = ref(false)
const editingId = ref<string | null>(null)
const formName = ref('')
const formCode = ref('')
const formCameraType = ref<BarrierCameraType>('ZHENSHI')
const formBoardId = ref<BarrierBoardId>('ZS_IO')
const formHost = ref('')
const formPort = ref('80')
const formEnabled = ref(true)
const formError = ref('')
const debugDevice = ref<BarrierDevice | null>(null)
const debugBusy = ref(false)
const debugLogs = ref<DebugLog[]>([])

const isEditing = computed(() => editingId.value !== null)
const availableBoards = computed(() => boardsForCamera(formCameraType.value))
const selectedBoardHint = computed(() => {
  const board = availableBoards.value.find((item) => item.id === formBoardId.value)
  return board ? t(board.hintKey) : ''
})
const debugCommands = computed(() =>
  debugDevice.value ? commandsForBoard(debugDevice.value.boardId) : [],
)
const debugBoardHint = computed(() => {
  if (!debugDevice.value) {
    return ''
  }
  const board = boardProfile(debugDevice.value.boardId)
  return board ? t(board.hintKey) : ''
})
const filteredDevices = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) {
    return devices.value
  }
  return devices.value.filter((item) => {
    const camera = t(`barriers.cameras.${item.cameraType}`).toLowerCase()
    const board = t(`barriers.boards.${item.boardId}`).toLowerCase()
    return (
      item.name.toLowerCase().includes(query) ||
      item.code.toLowerCase().includes(query) ||
      item.host.toLowerCase().includes(query) ||
      camera.includes(query) ||
      board.includes(query)
    )
  })
})

onMounted(async () => {
  devices.value = listBarrierDevices()
  try {
    const result = await listLanes(locale.value)
    lanes.value = result.data
  } catch {
    lanes.value = []
  }
})

function laneName(device: { laneId: string | null; bindDirection: BarrierBindDirection | null }): string {
  if (!device.laneId) {
    return t('barriers.unbound')
  }
  const lane = lanes.value.find((item) => item.id === device.laneId)
  const name = lane?.name ?? t('barriers.unbound')
  const direction =
    lane && lane.laneType !== 'BIDIRECTIONAL'
      ? lane.laneType === 'EXIT'
        ? 'EXIT'
        : 'ENTRANCE'
      : device.bindDirection
  if (!direction) {
    return name
  }
  const directionLabel =
    direction === 'EXIT' ? t('barriers.bindDirectionExit') : t('barriers.bindDirectionEntrance')
  return `${name} · ${directionLabel}`
}

function statusLabel(status: BarrierLinkStatus): string {
  if (status === 'CONNECTED') {
    return t('barriers.linkConnected')
  }
  if (status === 'FAILED') {
    return t('barriers.linkFailed')
  }
  return t('barriers.linkDisconnected')
}

function statusClass(status: BarrierLinkStatus): string {
  if (status === 'CONNECTED') {
    return 'ok'
  }
  if (status === 'FAILED') {
    return 'fail'
  }
  return ''
}

function resetForm(): void {
  editingId.value = null
  formName.value = ''
  formCode.value = ''
  formCameraType.value = 'ZHENSHI'
  formBoardId.value = defaultBoardId('ZHENSHI')
  formHost.value = ''
  formPort.value = '80'
  formEnabled.value = true
  formError.value = ''
}

function openCreate(): void {
  resetForm()
  showForm.value = true
}

function openEdit(device: BarrierDevice): void {
  editingId.value = device.id
  formName.value = device.name
  formCode.value = device.code
  formCameraType.value = device.cameraType
  formBoardId.value = device.boardId
  formHost.value = device.host
  formPort.value = String(device.port)
  formEnabled.value = device.enabled
  formError.value = ''
  showForm.value = true
}

function onCameraTypeChange(): void {
  const boards = boardsForCamera(formCameraType.value)
  if (!boards.some((board) => board.id === formBoardId.value)) {
    formBoardId.value = defaultBoardId(formCameraType.value)
  }
}

function closeForm(): void {
  showForm.value = false
  resetForm()
}

function onSubmit(): void {
  formError.value = ''
  const name = formName.value.trim()
  const code = formCode.value.trim()
  const host = formHost.value.trim()
  const port = Number(formPort.value)
  if (!name || !host || (!isEditing.value && !code)) {
    formError.value = t('barriers.formRequired')
    return
  }
  if (!isEditing.value && code.length < 2) {
    formError.value = t('barriers.codeTooShort')
    return
  }
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    formError.value = t('barriers.portInvalid')
    return
  }
  if (
    !isEditing.value &&
    devices.value.some((item) => item.code.toLowerCase() === code.toLowerCase())
  ) {
    formError.value = t('barriers.codeExists')
    return
  }
  devices.value = saveBarrierDevice(
    {
      id: editingId.value ?? undefined,
      name,
      code,
      cameraType: formCameraType.value,
      boardId: formBoardId.value,
      host,
      port,
      enabled: formEnabled.value,
    },
    devices.value,
  )
  closeForm()
}

function openDebug(device: BarrierDevice): void {
  debugDevice.value = device
  debugLogs.value = []
}

function closeDebug(): void {
  debugDevice.value = null
  debugBusy.value = false
  debugLogs.value = []
}

function pushLog(message: string): void {
  debugLogs.value = [{ at: new Date().toISOString(), message }, ...debugLogs.value].slice(0, 20)
}

async function testConnection(): Promise<void> {
  if (!debugDevice.value) {
    return
  }
  debugBusy.value = true
  pushLog(
    t('barriers.logTesting', {
      camera: t(`barriers.cameras.${debugDevice.value.cameraType}`),
      board: t(`barriers.boards.${debugDevice.value.boardId}`),
      host: debugDevice.value.host,
      port: debugDevice.value.port,
    }),
  )
  await wait(700)
  devices.value = setBarrierLinkStatus(debugDevice.value.id, 'CONNECTED', devices.value)
  debugDevice.value = devices.value.find((item) => item.id === debugDevice.value?.id) ?? null
  pushLog(t('barriers.logConnected'))
  debugBusy.value = false
}

async function sendCommand(command: BarrierCommand): Promise<void> {
  if (!debugDevice.value) {
    return
  }
  if (debugDevice.value.linkStatus !== 'CONNECTED') {
    pushLog(t('barriers.needConnected'))
    return
  }
  debugBusy.value = true
  pushLog(
    t('barriers.logCommand', {
      board: t(`barriers.boards.${debugDevice.value.boardId}`),
      command: t(command.labelKey),
      payload: command.payload,
    }),
  )
  await wait(400)
  pushLog(t('barriers.logCommandDone', { command: t(command.labelKey) }))
  debugBusy.value = false
}
</script>

<template>
  <section class="page">
    <p class="banner planning">{{ t('barriers.planningHint') }}</p>

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
            <th>{{ t('barriers.colCamera') }}</th>
            <th>{{ t('barriers.colBoard') }}</th>
            <th>{{ t('barriers.colHost') }}</th>
            <th>{{ t('barriers.colLink') }}</th>
            <th>{{ t('barriers.colBoundLane') }}</th>
            <th>{{ t('page.colUpdated') }}</th>
            <th class="col-actions">{{ t('barriers.colActions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredDevices" :key="item.id">
            <td>{{ item.name }}</td>
            <td>{{ item.code }}</td>
            <td>{{ t(`barriers.cameras.${item.cameraType}`) }}</td>
            <td>{{ t(`barriers.boards.${item.boardId}`) }}</td>
            <td>{{ item.host }}:{{ item.port }}</td>
            <td>
              <span class="pill" :class="statusClass(item.linkStatus)">
                {{ statusLabel(item.linkStatus) }}
              </span>
            </td>
            <td>{{ laneName(item) }}</td>
            <td>{{ formatTime(item.updatedAt) }}</td>
            <td class="col-actions">
              <div class="action-group">
                <button type="button" class="link-btn" @click="openDebug(item)">
                  {{ t('barriers.debug') }}
                </button>
                <button v-if="isAdmin" type="button" class="link-btn" @click="openEdit(item)">
                  {{ t('barriers.edit') }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">
        <strong>{{ t('barriers.empty') }}</strong>
        <p>{{ isAdmin ? t('barriers.emptyHintAdmin') : t('barriers.emptyHint') }}</p>
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
          <span>{{ t('barriers.cameraType') }}</span>
          <select v-model="formCameraType" @change="onCameraTypeChange">
            <option v-for="camera in BARRIER_CAMERA_TYPES" :key="camera" :value="camera">
              {{ t(`barriers.cameras.${camera}`) }}
            </option>
          </select>
        </label>
        <label>
          <span>{{ t('barriers.boardType') }}</span>
          <select v-model="formBoardId">
            <option v-for="board in availableBoards" :key="board.id" :value="board.id">
              {{ t(`barriers.boards.${board.id}`) }}
            </option>
          </select>
          <span class="field-hint">{{ selectedBoardHint }}</span>
        </label>
        <label>
          <span>{{ t('barriers.host') }}</span>
          <input v-model="formHost" type="text" autocomplete="off" placeholder="192.168.1.50" />
        </label>
        <label>
          <span>{{ t('barriers.port') }}</span>
          <input v-model="formPort" type="number" min="1" max="65535" />
        </label>
        <label class="checkbox">
          <input v-model="formEnabled" type="checkbox" />
          <span>{{ t('barriers.enabled') }}</span>
        </label>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" @click="closeForm">{{ t('barriers.cancel') }}</button>
          <button type="submit">{{ isEditing ? t('barriers.save') : t('barriers.create') }}</button>
        </div>
      </form>
    </div>

    <div v-if="debugDevice" class="modal-backdrop">
      <div class="modal wide">
        <h3>{{ t('barriers.debugTitle') }} · {{ debugDevice.name }}</h3>
        <p class="hint">{{ t('barriers.debugHint') }}</p>
        <p class="field-hint">
          {{ t(`barriers.cameras.${debugDevice.cameraType}`) }} ·
          {{ t(`barriers.boards.${debugDevice.boardId}`) }} ·
          {{ debugDevice.host }}:{{ debugDevice.port }}
        </p>
        <p class="field-hint">{{ debugBoardHint }}</p>
        <p>
          <span class="pill" :class="statusClass(debugDevice.linkStatus)">
            {{ statusLabel(debugDevice.linkStatus) }}
          </span>
        </p>
        <div class="debug-actions">
          <button type="button" :disabled="debugBusy" @click="testConnection">
            {{ debugBusy ? t('barriers.testing') : t('barriers.testConnection') }}
          </button>
        </div>
        <div class="command-list">
          <p class="command-title">{{ t('barriers.commandSet') }}</p>
          <div v-for="command in debugCommands" :key="command.id" class="command-row">
            <div>
              <strong>{{ t(command.labelKey) }}</strong>
              <code>{{ command.payload }}</code>
            </div>
            <button
              type="button"
              class="ghost"
              :disabled="debugBusy || debugDevice.linkStatus !== 'CONNECTED'"
              @click="sendCommand(command)"
            >
              {{ t('barriers.sendCommand') }}
            </button>
          </div>
        </div>
        <div class="log">
          <p v-if="debugLogs.length === 0" class="field-hint">{{ t('barriers.logEmpty') }}</p>
          <p v-for="(item, index) in debugLogs" :key="index">
            {{ formatTime(item.at) }} · {{ item.message }}
          </p>
        </div>
        <div class="actions">
          <button type="button" class="ghost" @click="closeDebug">{{ t('barriers.cancel') }}</button>
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

.modal select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
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

input {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
}

.debug-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.command-list {
  display: grid;
  gap: 0.5rem;
}

.command-title {
  margin: 0;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--muted);
}

.command-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
}

.command-row div {
  display: grid;
  gap: 0.2rem;
  min-width: 0;
}

.command-row code {
  font-size: 0.78rem;
  color: var(--muted);
  word-break: break-all;
}

.command-row button {
  flex-shrink: 0;
}

.debug-actions button.ghost,
.ghost {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
}

.log {
  max-height: 12rem;
  overflow: auto;
  padding: 0.75rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #f7faf8;
  font-size: 0.85rem;
}

.log p {
  margin: 0 0 0.35rem;
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

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
