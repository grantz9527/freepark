<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  adoptAutoRegisteredDevice,
  createBarrierGlobal,
  deleteAutoRegisteredDevice,
  deleteBarrierGlobal,
  listAllBarriers,
  listAutoRegisteredDevices,
  listLanes,
  updateBarrierGlobal,
  type AutoRegisteredDeviceView,
  type BarrierView,
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

const showForm = ref(false)
const editingId = ref<string | null>(null)
const formName = ref('')
const formCode = ref('')
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
  void listLanes(locale.value)
    .then((result) => {
      lanes.value = result.data
    })
    .catch(() => {
      lanes.value = []
    })
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
  formEnabled.value = device.enabled
  formError.value = ''
  showForm.value = true
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
  saving.value = true
  try {
    if (isEditing.value && editingId.value) {
      const result = await updateBarrierGlobal(
        editingId.value,
        { name, enabled: formEnabled.value },
        locale.value,
      )
      devices.value = devices.value.map((item) => (item.id === result.data.id ? result.data : item))
    } else {
      const result = await createBarrierGlobal(
        { name, code, enabled: formEnabled.value },
        locale.value,
      )
      devices.value = [result.data, ...devices.value]
    }
    closeForm()
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : t('barriers.saveFailed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="page">
    <p class="banner planning">{{ t('barriers.planningHint') }}</p>
    <p v-if="errorMessage" class="banner error">{{ errorMessage }}</p>

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
              <span class="pill" :class="item.enabled ? 'ok' : 'fail'">
                {{ item.enabled ? t('lanes.statusActive') : t('lanes.statusDisabled') }}
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

.link-btn.danger {
  color: var(--danger);
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

input {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
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

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
