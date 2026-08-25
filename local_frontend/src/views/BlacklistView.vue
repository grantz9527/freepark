<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  createBlacklistVehicle,
  deleteBlacklistVehicle,
  downloadBlacklistImportTemplate,
  importBlacklistVehicles,
  listBlacklistVehicles,
  listLots,
  updateBlacklistVehicle,
  type BlacklistVehicleView,
  type LotView,
  type PlateColor,
} from '@/api/client'
import VehicleListImportModal from '@/components/VehicleListImportModal.vue'
import { getUser } from '@/auth/session'
import { usePlateColorLabel } from '@/composables/usePlateColorLabel'
import { useSiteTime } from '@/composables/useSiteTime'
import { siteAllowedPlateColors, siteDefaultPlateColor } from '@/site/settings'

const LOT_STORAGE_KEY = 'freepark.blacklist.lotId'

const { t, locale } = useI18n()
const { formatTime, toDateTimeLocal, fromDateTimeLocal, defaultDateTimeLocal } = useSiteTime()
const { plateColorLabel } = usePlateColorLabel()

const plateColorOptions = computed(() => siteAllowedPlateColors.value)

const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const lots = ref<LotView[]>([])
const selectedLotId = ref('')
const vehicles = ref<BlacklistVehicleView[]>([])
const total = ref(0)
const page = ref(0)
const pageSize = ref(10)

const searchInput = ref('')
const appliedPlate = ref('')

const showForm = ref(false)
const editingId = ref<string | null>(null)
const formPlate = ref('')
const formPlateColor = ref<PlateColor>('BLUE')
const formOwnerName = ref('')
const formPhone = ref('')
const formDepartment = ref('')
const formRemark = ref('')
const formStartTime = ref('')
const formEndTime = ref('')
const formEnabled = ref(true)
const formError = ref('')

const showImport = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)
const downloadingTemplate = ref(false)
const importError = ref('')

const isAdmin = computed(() => getUser()?.role === 'ADMIN')
const isEditing = computed(() => editingId.value !== null)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const pageStart = computed(() => (total.value === 0 ? 0 : page.value * pageSize.value + 1))

async function loadLots(): Promise<void> {
  const result = await listLots(locale.value)
  lots.value = result.data
  if (lots.value.length === 0) {
    selectedLotId.value = ''
    return
  }
  const stored = sessionStorage.getItem(LOT_STORAGE_KEY)
  const match = lots.value.find((lot) => lot.id === stored)
  selectedLotId.value = match?.id ?? lots.value[0]?.id ?? ''
}

async function loadVehicles(): Promise<void> {
  if (!selectedLotId.value) {
    vehicles.value = []
    total.value = 0
    return
  }
  const result = await listBlacklistVehicles(selectedLotId.value, locale.value, {
    plate: appliedPlate.value || undefined,
    page: page.value,
    size: pageSize.value,
  })
  vehicles.value = result.data.items
  total.value = result.data.total
  page.value = result.data.page
  pageSize.value = result.data.size
}

async function reload(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadLots()
    page.value = 0
    await loadVehicles()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('blacklist.loadFailed')
  } finally {
    loading.value = false
  }
}

async function onLotChange(): Promise<void> {
  sessionStorage.setItem(LOT_STORAGE_KEY, selectedLotId.value)
  appliedPlate.value = ''
  searchInput.value = ''
  page.value = 0
  loading.value = true
  try {
    await loadVehicles()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('blacklist.loadFailed')
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  appliedPlate.value = searchInput.value.trim()
  page.value = 0
  void loadVehiclesOnly()
}

function onResetSearch(): void {
  searchInput.value = ''
  appliedPlate.value = ''
  page.value = 0
  void loadVehiclesOnly()
}

async function loadVehiclesOnly(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadVehicles()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('blacklist.loadFailed')
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  editingId.value = null
  formPlate.value = ''
  formPlateColor.value = siteDefaultPlateColor.value
  formOwnerName.value = ''
  formPhone.value = ''
  formDepartment.value = ''
  formRemark.value = ''
  formStartTime.value = defaultDateTimeLocal()
  formEndTime.value = defaultDateTimeLocal(365 * 24 * 60 * 60 * 1000)
  formEnabled.value = true
  formError.value = ''
}

function openCreateForm(): void {
  resetForm()
  showForm.value = true
}

function openEditForm(vehicle: BlacklistVehicleView): void {
  editingId.value = vehicle.id
  formPlate.value = vehicle.plateNumber
  formPlateColor.value = vehicle.plateColor
  formOwnerName.value = vehicle.ownerName
  formPhone.value = vehicle.phone ?? ''
  formDepartment.value = vehicle.department ?? ''
  formRemark.value = vehicle.remark ?? ''
  formStartTime.value = toDateTimeLocal(vehicle.startTime)
  formEndTime.value = toDateTimeLocal(vehicle.endTime)
  formEnabled.value = vehicle.enabled
  formError.value = ''
  showForm.value = true
}

function closeForm(): void {
  showForm.value = false
  resetForm()
}

async function onSubmit(): Promise<void> {
  if (!selectedLotId.value) {
    return
  }
  if (!formPlate.value.trim() || !formOwnerName.value.trim()) {
    formError.value = t('blacklist.formRequired')
    return
  }
  if (!formStartTime.value || !formEndTime.value) {
    formError.value = t('blacklist.formTimeRequired')
    return
  }
  const startTime = fromDateTimeLocal(formStartTime.value)
  const endTime = fromDateTimeLocal(formEndTime.value)
  if (!startTime || !endTime || Date.parse(endTime) <= Date.parse(startTime)) {
    formError.value = t('blacklist.formInvalidTimeRange')
    return
  }
  submitting.value = true
  formError.value = ''
  const payload = {
    plateNumber: formPlate.value.trim(),
    plateColor: formPlateColor.value,
    ownerName: formOwnerName.value.trim(),
    phone: formPhone.value.trim() || undefined,
    department: formDepartment.value.trim() || undefined,
    remark: formRemark.value.trim() || undefined,
    startTime,
    endTime,
    enabled: formEnabled.value,
  }
  try {
    if (isEditing.value && editingId.value) {
      await updateBlacklistVehicle(selectedLotId.value, editingId.value, payload, locale.value)
    } else {
      await createBlacklistVehicle(selectedLotId.value, payload, locale.value)
    }
    closeForm()
    successMessage.value = t('blacklist.saveSuccess')
    await loadVehiclesOnly()
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : t('blacklist.saveFailed')
  } finally {
    submitting.value = false
  }
}

async function onDelete(vehicle: BlacklistVehicleView): Promise<void> {
  if (!selectedLotId.value) {
    return
  }
  submitting.value = true
  errorMessage.value = ''
  try {
    await deleteBlacklistVehicle(selectedLotId.value, vehicle.id, locale.value)
    successMessage.value = t('blacklist.deleteSuccess')
    await loadVehiclesOnly()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('blacklist.deleteFailed')
  } finally {
    submitting.value = false
  }
}

function openImportForm(): void {
  if (!selectedLotId.value) {
    return
  }
  importFile.value = null
  importError.value = ''
  successMessage.value = ''
  showImport.value = true
}

function closeImport(): void {
  if (importing.value) {
    return
  }
  showImport.value = false
  importFile.value = null
  importError.value = ''
}

function onImportFileChange(file: File | null): void {
  importFile.value = file
  importError.value = ''
}

async function downloadImportTemplate(): Promise<void> {
  if (!selectedLotId.value) {
    return
  }
  downloadingTemplate.value = true
  importError.value = ''
  try {
    await downloadBlacklistImportTemplate(selectedLotId.value, locale.value)
  } catch (error) {
    importError.value = error instanceof ApiError ? error.message : t('blacklist.downloadTemplateFailed')
  } finally {
    downloadingTemplate.value = false
  }
}

async function onSubmitImport(): Promise<void> {
  if (!selectedLotId.value || !importFile.value) {
    importError.value = t('blacklist.importFileRequired')
    return
  }
  importing.value = true
  importError.value = ''
  try {
    const result = await importBlacklistVehicles(selectedLotId.value, importFile.value, locale.value)
    successMessage.value = t('blacklist.importSuccess', {
      imported: result.data.imported,
      skipped: result.data.skipped,
    })
    closeImport()
    await loadVehiclesOnly()
  } catch (error) {
    importError.value = error instanceof ApiError ? error.message : t('blacklist.importFailed')
  } finally {
    importing.value = false
  }
}

function goToPage(target: number): void {
  const next = Math.min(Math.max(target, 0), pageCount.value - 1)
  if (next === page.value) {
    return
  }
  page.value = next
  void loadVehiclesOnly()
}

function onPageSizeChange(): void {
  page.value = 0
  void loadVehiclesOnly()
}

onMounted(reload)
</script>

<template>
  <section class="page">
    <div class="lot-bar">
      <label class="lot-select">
        <span>{{ t('spaces.lotLabel') }}</span>
        <select v-model="selectedLotId" @change="onLotChange">
          <option v-if="lots.length === 0" value="">{{ t('spaces.noLot') }}</option>
          <option v-for="lot in lots" :key="lot.id" :value="lot.id">{{ lot.name }}</option>
        </select>
      </label>
    </div>

    <p v-if="errorMessage" class="banner error">{{ errorMessage }}</p>
    <p v-if="successMessage" class="banner ok">{{ successMessage }}</p>

    <div v-if="!selectedLotId" class="table-card empty-card">
      <div class="empty">
        <strong>{{ t('spaces.noLot') }}</strong>
        <p>{{ t('spaces.noLotHint') }}</p>
      </div>
    </div>

    <template v-else>
      <div class="filter-bar">
        <label class="filter-field">
          <span>{{ t('blacklist.colPlate') }}</span>
          <input
            v-model="searchInput"
            type="search"
            :placeholder="t('blacklist.searchPlaceholder')"
            @keyup.enter="onSearch"
          />
        </label>
        <div class="filter-actions">
          <button type="button" class="ghost" @click="onResetSearch">{{ t('spaces.reset') }}</button>
          <button type="button" class="primary" @click="onSearch">{{ t('spaces.query') }}</button>
        </div>
      </div>

      <div v-if="isAdmin" class="action-bar">
        <button type="button" class="primary" @click="openCreateForm">{{ t('blacklist.create') }}</button>
        <button type="button" class="outline" @click="openImportForm">{{ t('blacklist.import') }}</button>
      </div>

      <div class="table-card">
        <table v-if="vehicles.length > 0">
          <thead>
            <tr>
              <th>{{ t('blacklist.colIndex') }}</th>
              <th>{{ t('blacklist.colPlate') }}</th>
              <th>{{ t('blacklist.colPlateColor') }}</th>
              <th>{{ t('blacklist.colOwner') }}</th>
              <th>{{ t('blacklist.colPhone') }}</th>
              <th>{{ t('blacklist.colDepartment') }}</th>
              <th>{{ t('blacklist.colStartTime') }}</th>
              <th>{{ t('blacklist.colEndTime') }}</th>
              <th>{{ t('page.colStatus') }}</th>
              <th>{{ t('page.colUpdated') }}</th>
              <th v-if="isAdmin" class="col-actions">{{ t('blacklist.colActions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in vehicles" :key="item.id">
              <td>{{ pageStart + index }}</td>
              <td>{{ item.plateNumber }}</td>
              <td>{{ plateColorLabel(item.plateColor) }}</td>
              <td>{{ item.ownerName }}</td>
              <td>{{ item.phone || '—' }}</td>
              <td>{{ item.department || '—' }}</td>
              <td>{{ formatTime(item.startTime) || '—' }}</td>
              <td>{{ formatTime(item.endTime) || '—' }}</td>
              <td>
                <span class="pill" :class="item.enabled ? 'ok' : 'fail'">
                  {{ item.enabled ? t('blacklist.statusActive') : t('blacklist.statusDisabled') }}
                </span>
              </td>
              <td>{{ formatTime(item.updatedAt) }}</td>
              <td v-if="isAdmin" class="col-actions">
                <button type="button" class="link-btn" @click="openEditForm(item)">
                  {{ t('blacklist.edit') }}
                </button>
                <button type="button" class="link-btn danger" @click="onDelete(item)">
                  {{ t('blacklist.delete') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else-if="loading" class="empty">
          <p>{{ t('blacklist.loading') }}</p>
        </div>
        <div v-else class="empty">
          <strong>{{ t('blacklist.empty') }}</strong>
          <p>{{ isAdmin ? t('blacklist.emptyHintAdmin') : t('blacklist.emptyHint') }}</p>
        </div>

        <footer v-if="total > 0" class="pagination">
          <span>{{ t('spaces.paginationTotal', { total }) }}</span>
          <label class="page-size">
            <select v-model.number="pageSize" @change="onPageSizeChange">
              <option :value="10">{{ t('spaces.pageSize10') }}</option>
              <option :value="20">{{ t('spaces.pageSize20') }}</option>
              <option :value="50">{{ t('spaces.pageSize50') }}</option>
            </select>
          </label>
          <div class="page-nav">
            <button type="button" :disabled="page <= 0" @click="goToPage(page - 1)">‹</button>
            <span>{{ page + 1 }}</span>
            <button type="button" :disabled="page + 1 >= pageCount" @click="goToPage(page + 1)">›</button>
          </div>
        </footer>
      </div>
    </template>

    <div v-if="showForm" class="modal-backdrop">
      <form class="modal" @submit.prevent="onSubmit">
        <h3>{{ isEditing ? t('blacklist.editTitle') : t('blacklist.createTitle') }}</h3>
        <label>
          <span>{{ t('blacklist.colPlate') }}</span>
          <input v-model="formPlate" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('blacklist.colPlateColor') }}</span>
          <select v-model="formPlateColor">
            <option v-for="color in plateColorOptions" :key="color" :value="color">
              {{ plateColorLabel(color) }}
            </option>
          </select>
        </label>
        <label>
          <span>{{ t('blacklist.colOwner') }}</span>
          <input v-model="formOwnerName" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('blacklist.colPhone') }}</span>
          <input v-model="formPhone" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('blacklist.colDepartment') }}</span>
          <input v-model="formDepartment" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('blacklist.colRemark') }}</span>
          <input v-model="formRemark" type="text" autocomplete="off" />
        </label>
        <div class="time-row">
          <label>
            <span>{{ t('blacklist.colStartTime') }}</span>
            <input v-model="formStartTime" type="datetime-local" />
          </label>
          <label>
            <span>{{ t('blacklist.colEndTime') }}</span>
            <input v-model="formEndTime" type="datetime-local" />
          </label>
        </div>
        <label class="checkbox">
          <input v-model="formEnabled" type="checkbox" />
          <span>{{ t('blacklist.statusActive') }}</span>
        </label>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" @click="closeForm">{{ t('blacklist.cancel') }}</button>
          <button type="submit" :disabled="submitting">
            {{ submitting ? t('blacklist.saving') : t('blacklist.save') }}
          </button>
        </div>
      </form>
    </div>

    <VehicleListImportModal
      scope="blacklist"
      :open="showImport"
      :importing="importing"
      :downloading-template="downloadingTemplate"
      :import-error="importError"
      :import-file="importFile"
      @close="closeImport"
      @submit="onSubmitImport"
      @download-template="downloadImportTemplate"
      @file-change="onImportFileChange"
    />
  </section>
</template>

<style scoped>
.page {
  display: grid;
  gap: 0.9rem;
}

.lot-bar {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 0.85rem 1rem;
  box-shadow: var(--shadow);
}

.lot-select {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-weight: 600;
}

.lot-select select {
  min-width: 12rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.45rem 0.65rem;
  background: #fff;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: flex-end;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 0.85rem 1rem;
  box-shadow: var(--shadow);
}

.filter-field {
  display: grid;
  gap: 0.35rem;
  min-width: 14rem;
}

.filter-field input {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.5rem 0.65rem;
  background: #fff;
}

.filter-actions {
  display: flex;
  gap: 0.5rem;
}

.action-bar {
  display: flex;
  gap: 0.5rem;
}

.outline {
  border: 1px solid var(--accent);
  border-radius: 8px;
  padding: 0.5rem 0.85rem;
  font-weight: 600;
  background: #fff;
  color: var(--accent);
  cursor: pointer;
}

.outline:hover {
  background: #f2f8f5;
}

.primary,
.filter-actions .primary,
.action-bar .primary,
.actions button:not(.ghost) {
  border: 0;
  border-radius: 8px;
  padding: 0.5rem 0.85rem;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
  cursor: pointer;
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

.table-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow);
}

.empty-card {
  padding: 3rem 1.5rem;
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
  width: 9rem;
  text-align: end;
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
  margin-left: 0.65rem;
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
  padding: 2.5rem 1.5rem;
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

.pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-top: 1px solid var(--border);
  font-size: 0.88rem;
  color: var(--muted);
}

.page-size select {
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 0.25rem 0.45rem;
  background: #fff;
}

.page-nav {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.page-nav button {
  border: 1px solid var(--border);
  border-radius: 6px;
  width: 1.75rem;
  height: 1.75rem;
  background: #fff;
  cursor: pointer;
}

.page-nav button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.banner {
  margin: 0;
  padding: 0.65rem 0.9rem;
  border-radius: 8px;
}

.banner.error {
  color: var(--danger);
  background: #fdecec;
}

.banner.ok {
  color: var(--ok);
  background: #e8f5ef;
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

.time-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

@media (max-width: 560px) {
  .time-row {
    grid-template-columns: 1fr;
  }
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

input,
select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
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
</style>
