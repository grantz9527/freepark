<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  createLot,
  fullResyncLotCameraWhitelist,
  listLots,
  updateLot,
  type LotOpenTimeRule,
  type LotType,
  type LotView,
} from '@/api/client'
import { getUser } from '@/auth/session'
import { useSiteTime } from '@/composables/useSiteTime'

const { t, locale } = useI18n()
const router = useRouter()
const { formatTime } = useSiteTime()

const loading = ref(false)
const submitting = ref(false)
const lots = ref<LotView[]>([])
const errorMessage = ref('')
const showForm = ref(false)
const editingLotId = ref<string | null>(null)
const formName = ref('')
const formCode = ref('')
const formAddress = ref('')
const formTotalSpaces = ref('')
const formLotType = ref<LotType>('INTERNAL')
const formEnabled = ref(true)
const formOpenTimeRules = ref<LotOpenTimeRule[]>([])
const formError = ref('')
const searchQuery = ref('')
const fullResyncLotId = ref<string | null>(null)
const successMessage = ref('')

const WEEKDAYS = [1, 2, 3, 4, 5, 6, 7]

const lotTypeOptions: LotType[] = ['INTERNAL', 'PUBLIC']

const isAdmin = computed(() => getUser()?.role === 'ADMIN')
const isEditing = computed(() => editingLotId.value !== null)
const filteredLots = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) {
    return lots.value
  }
  return lots.value.filter(
    (lot) => lot.name.toLowerCase().includes(query) || lot.code.toLowerCase().includes(query),
  )
})
const isSearching = computed(() => searchQuery.value.trim().length > 0)

/** 某天的开放时段（元素与 formOpenTimeRules 同一引用，可直接双向绑定）。 */
function openTimeRulesOfDay(day: number): LotOpenTimeRule[] {
  return formOpenTimeRules.value.filter((rule) => rule.day === day)
}

function addOpenTimeWindow(day: number): void {
  formOpenTimeRules.value.push({ day, start: '', end: '' })
}

function removeOpenTimeWindow(target: LotOpenTimeRule): void {
  const index = formOpenTimeRules.value.indexOf(target)
  if (index >= 0) {
    formOpenTimeRules.value.splice(index, 1)
  }
}

function weekdayLabel(day: number): string {
  const key = `lots.weekday${day}`
  const label = t(key)
  return label === key ? String(day) : label
}

async function loadLots(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await listLots(locale.value)
    lots.value = result.data
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('lots.loadFailed')
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  editingLotId.value = null
  formName.value = ''
  formCode.value = ''
  formAddress.value = ''
  formTotalSpaces.value = ''
  formLotType.value = 'INTERNAL'
  formEnabled.value = true
  formOpenTimeRules.value = []
  formError.value = ''
}

function openCreateForm(): void {
  resetForm()
  showForm.value = true
}

function openEditForm(lot: LotView): void {
  editingLotId.value = lot.id
  formName.value = lot.name
  formCode.value = lot.code
  formAddress.value = lot.address ?? ''
  formTotalSpaces.value = String(lot.totalSpaces)
  formLotType.value = lot.lotType
  formEnabled.value = lot.enabled
  formOpenTimeRules.value = (lot.openTimeRules ?? []).map((rule) => ({ ...rule }))
  formError.value = ''
  showForm.value = true
}

function closeForm(): void {
  showForm.value = false
  resetForm()
}

function parseTotalSpaces(value: string | number | null | undefined): number | undefined | null {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  if (typeof value === 'number') {
    if (!Number.isInteger(value) || value < 0) {
      return null
    }
    return value
  }
  const trimmed = value.trim()
  if (!trimmed) {
    return undefined
  }
  const parsed = Number(trimmed)
  if (!Number.isInteger(parsed) || parsed < 0) {
    return null
  }
  return parsed
}

async function onSubmit(): Promise<void> {
  formError.value = ''
  if (!formName.value.trim()) {
    formError.value = t('lots.formRequired')
    return
  }

  if (!isEditing.value) {
    if (!formCode.value.trim()) {
      formError.value = t('lots.formRequired')
      return
    }
    if (formCode.value.trim().length < 2) {
      formError.value = t('lots.codeTooShort')
      return
    }
  }

  const totalSpaces = parseTotalSpaces(formTotalSpaces.value)
  if (totalSpaces === null) {
    formError.value = t('lots.totalSpacesInvalid')
    return
  }

  const isInternal = formLotType.value === 'INTERNAL'
  if (
    isInternal &&
    formOpenTimeRules.value.some((rule) => !rule.start || !rule.end || rule.start >= rule.end)
  ) {
    formError.value = t('lots.openTimeInvalid')
    return
  }
  const openTimeRules = isInternal
    ? formOpenTimeRules.value.map((rule) => ({ day: rule.day, start: rule.start, end: rule.end }))
    : undefined

  submitting.value = true
  try {
    if (isEditing.value && editingLotId.value) {
      const result = await updateLot(
        editingLotId.value,
        {
          name: formName.value.trim(),
          address: formAddress.value.trim() || undefined,
          totalSpaces,
          lotType: formLotType.value,
          enabled: formEnabled.value,
          openTimeRules,
        },
        locale.value,
      )
      lots.value = lots.value.map((item) => (item.id === result.data.id ? result.data : item))
    } else {
      const result = await createLot(
        {
          name: formName.value.trim(),
          code: formCode.value.trim(),
          lotType: formLotType.value,
          address: formAddress.value.trim() || undefined,
          totalSpaces,
          enabled: formEnabled.value,
          openTimeRules,
        },
        locale.value,
      )
      lots.value = [result.data, ...lots.value]
    }
    closeForm()
  } catch (error) {
    formError.value =
      error instanceof ApiError
        ? error.message
        : isEditing.value
          ? t('lots.updateFailed')
          : t('lots.createFailed')
  } finally {
    submitting.value = false
  }
}

function openInterceptConfig(lot: LotView): void {
  void router.push({ name: 'lotIntercept', params: { lotId: lot.id } })
}

function openLaneConfig(lot: LotView): void {
  void router.push({ name: 'lanes', query: { lotId: lot.id } })
}

function openMap(lot: LotView): void {
  void router.push({ name: 'lotMap', params: { lotId: lot.id } })
}

async function runFullResync(lot: LotView): Promise<void> {
  if (fullResyncLotId.value !== null) return
  if (!window.confirm(t('lots.fullResyncConfirm'))) return
  errorMessage.value = ''
  successMessage.value = ''
  fullResyncLotId.value = lot.id
  try {
    const res = await fullResyncLotCameraWhitelist(lot.id, locale.value)
    const cameras = res.data?.cameras ?? 0
    const plates = res.data?.plates ?? 0
    successMessage.value =
      cameras === 0
        ? t('lots.fullResyncNone')
        : t('lots.fullResyncDone', { cameras, plates })
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('lots.fullResyncFailed')
  } finally {
    fullResyncLotId.value = null
  }
}

function lotTypeLabel(type: LotType): string {
  const key = `lotTypes.${type}`
  const label = t(key)
  return label === key ? type : label
}

onMounted(loadLots)
</script>

<template>
  <section class="page">
    <div class="toolbar">
      <label class="search">
        <span class="sr-only">{{ t('page.search') }}</span>
        <input
          v-model="searchQuery"
          type="search"
          :placeholder="t('lots.searchPlaceholder')"
        />
      </label>
      <button v-if="isAdmin" type="button" @click="openCreateForm">{{ t('lots.create') }}</button>
    </div>

    <p v-if="errorMessage" class="banner error">{{ errorMessage }}</p>
    <p v-if="successMessage" class="banner ok">{{ successMessage }}</p>

    <div class="table-card">
      <table v-if="filteredLots.length > 0">
        <thead>
          <tr>
            <th>{{ t('lots.colName') }}</th>
            <th>{{ t('lots.colCode') }}</th>
            <th>{{ t('lots.colLotType') }}</th>
            <th>{{ t('lots.colAddress') }}</th>
            <th>{{ t('lots.colTotalSpaces') }}</th>
            <th>{{ t('page.colStatus') }}</th>
            <th>{{ t('page.colUpdated') }}</th>
            <th v-if="isAdmin" class="col-actions">{{ t('lots.colActions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredLots" :key="item.id">
            <td>{{ item.name }}</td>
            <td>{{ item.code }}</td>
            <td>{{ lotTypeLabel(item.lotType) }}</td>
            <td>{{ item.address || '—' }}</td>
            <td>{{ item.totalSpaces }}</td>
            <td>
              <span class="pill" :class="item.enabled ? 'ok' : 'fail'">
                {{ item.enabled ? t('lots.statusActive') : t('lots.statusDisabled') }}
              </span>
            </td>
            <td>{{ formatTime(item.updatedAt) }}</td>
            <td v-if="isAdmin" class="col-actions">
              <div class="action-group">
                <button type="button" class="link-btn" @click="openEditForm(item)">
                  {{ t('lots.edit') }}
                </button>
                <button type="button" class="link-btn" @click="openMap(item)">
                  {{ t('lots.map') }}
                </button>
                <button type="button" class="link-btn" @click="openInterceptConfig(item)">
                  {{ t('lots.interceptConfig') }}
                </button>
                <button type="button" class="link-btn" @click="openLaneConfig(item)">
                  {{ t('lots.laneConfig') }}
                </button>
                <button
                  type="button"
                  class="link-btn"
                  :disabled="fullResyncLotId !== null"
                  @click="runFullResync(item)"
                >
                  {{
                    fullResyncLotId === item.id
                      ? t('lots.fullResyncing')
                      : t('lots.fullResync')
                  }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else-if="loading" class="empty">
        <p>{{ t('lots.loading') }}</p>
      </div>
      <div v-else-if="isSearching && lots.length > 0" class="empty">
        <strong>{{ t('lots.searchNoResults') }}</strong>
      </div>
      <div v-else class="empty">
        <strong>{{ t('lots.empty') }}</strong>
        <p>{{ isAdmin ? t('lots.emptyHintAdmin') : t('lots.emptyHint') }}</p>
      </div>
    </div>

    <div v-if="showForm" class="modal-backdrop">
      <form class="modal" @submit.prevent="onSubmit">
        <h3>{{ isEditing ? t('lots.editTitle') : t('lots.createTitle') }}</h3>
        <p class="hint">{{ isEditing ? t('lots.editHint') : t('lots.createHint') }}</p>
        <label>
          <span>{{ t('lots.name') }}</span>
          <input v-model="formName" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('lots.code') }}</span>
          <input
            v-model="formCode"
            type="text"
            autocomplete="off"
            :readonly="isEditing"
            :class="{ locked: isEditing }"
          />
          <span v-if="isEditing" class="field-hint">{{ t('lots.codeLocked') }}</span>
        </label>
        <label>
          <span>{{ t('lots.lotType') }}</span>
          <select v-model="formLotType">
            <option v-for="option in lotTypeOptions" :key="option" :value="option">
              {{ lotTypeLabel(option) }}
            </option>
          </select>
        </label>
        <label>
          <span>{{ t('lots.address') }}</span>
          <input v-model="formAddress" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('lots.totalSpaces') }}</span>
          <input v-model="formTotalSpaces" type="text" inputmode="numeric" autocomplete="off" />
        </label>
        <label class="checkbox">
          <input v-model="formEnabled" type="checkbox" />
          <span>{{ t('lots.enabled') }}</span>
        </label>
        <div v-if="formLotType === 'INTERNAL'" class="open-time-editor">
          <span class="open-time-title">{{ t('lots.openTime') }}</span>
          <div v-for="day in WEEKDAYS" :key="day" class="open-time-row">
            <span class="open-time-day">{{ weekdayLabel(day) }}</span>
            <div class="open-time-windows">
              <div
                v-for="(rule, index) in openTimeRulesOfDay(day)"
                :key="index"
                class="open-time-window"
              >
                <input v-model="rule.start" type="time" class="open-time-input" />
                <span class="open-time-sep">-</span>
                <input v-model="rule.end" type="time" class="open-time-input" />
                <button type="button" class="link-btn" @click="removeOpenTimeWindow(rule)">
                  {{ t('lots.removeOpenTime') }}
                </button>
              </div>
              <button type="button" class="link-btn open-time-add" @click="addOpenTimeWindow(day)">
                + {{ t('lots.addOpenTime') }}
              </button>
            </div>
          </div>
          <span class="field-hint">{{ t('lots.openTimeHint') }}</span>
        </div>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" @click="closeForm">{{ t('lots.cancel') }}</button>
          <button type="submit" :disabled="submitting">
            {{
              submitting
                ? isEditing
                  ? t('lots.saving')
                  : t('lots.creating')
                : isEditing
                  ? t('lots.save')
                  : t('lots.create')
            }}
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

.toolbar {
  display: flex;
  justify-content: space-between;
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
  width: 22rem;
  text-align: end;
}

.action-group {
  display: flex;
  justify-content: flex-end;
  gap: 0.65rem;
  flex-wrap: wrap;
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

.link-btn:hover {
  color: var(--accent-dark);
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
  color: #0f5132;
  background: #d1e7dd;
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
  width: min(520px, 100%);
  display: grid;
  gap: 0.75rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: var(--shadow);
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

select {
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

.ghost {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
}

.open-time-editor {
  display: grid;
  gap: 0.5rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.75rem;
}

.open-time-title {
  font-weight: 600;
}

.open-time-row {
  display: grid;
  grid-template-columns: 3.5rem 1fr;
  gap: 0.5rem;
  align-items: start;
}

.open-time-day {
  color: var(--muted);
  font-size: 0.85rem;
  padding-top: 0.5rem;
}

.open-time-windows {
  display: grid;
  gap: 0.35rem;
}

.open-time-window {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.4rem;
}

.open-time-input {
  width: 100%;
  min-width: 0;
  padding: 0.35rem 0.5rem;
  min-height: 2rem;
}

.open-time-add {
  justify-self: start;
}

.open-time-editor .link-btn {
  white-space: nowrap;
}

.open-time-sep {
  color: var(--muted);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
