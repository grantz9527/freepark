<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  createInternalVehicle,
  deleteInternalVehicle,
  deleteInternalVehicleBatch,
  downloadInternalVehicleImportTemplate,
  exportInternalVehicles,
  importInternalVehicles,
  listInternalVehicles,
  listLots,
  updateInternalVehicle,
  type InternalVehicleType,
  type InternalVehicleView,
  type LotView,
  type PlateColor,
} from '@/api/client'
import { getUser } from '@/auth/session'
import PlateBadge from '@/components/PlateBadge.vue'
import { usePlateColorLabel } from '@/composables/usePlateColorLabel'
import { useSiteTime } from '@/composables/useSiteTime'
import { siteAllowedPlateColors, siteDefaultPlateColor } from '@/site/settings'

const LOT_STORAGE_KEY = 'freepark.internalVehicles.lotId'

const { t, locale } = useI18n()
const { formatTime } = useSiteTime()
const { plateColorLabel } = usePlateColorLabel()

const plateColorOptions = computed(() => siteAllowedPlateColors.value)

const vehicleTypeOptions: InternalVehicleType[] = ['TENANT', 'OWNER', 'APPOINTMENT', 'VISITOR', 'OTHER']

function vehicleTypeLabel(type: InternalVehicleType): string {
  return t(`internalVehicles.type${type}`)
}

const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const lots = ref<LotView[]>([])
const selectedLotId = ref('')
const vehicles = ref<InternalVehicleView[]>([])
const total = ref(0)
const page = ref(0)
const pageSize = ref(10)

const searchInput = ref('')
const appliedPlate = ref('')

const showForm = ref(false)
const editingId = ref<string | null>(null)
const formPlate = ref('')
const formPlateColor = ref<PlateColor>('BLUE')
const formType = ref<InternalVehicleType>('OTHER')
const formOwnerName = ref('')
const formPhone = ref('')
const formDepartment = ref('')
const formRemark = ref('')
const formEnabled = ref(true)
const formError = ref('')

const showImport = ref(false)
const importInput = ref<HTMLInputElement | null>(null)
const importFile = ref<File | null>(null)
const importing = ref(false)
const downloadingTemplate = ref(false)
const importError = ref('')
const exporting = ref(false)

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
  const result = await listInternalVehicles(selectedLotId.value, locale.value, {
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
    errorMessage.value = error instanceof ApiError ? error.message : t('internalVehicles.loadFailed')
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
    errorMessage.value = error instanceof ApiError ? error.message : t('internalVehicles.loadFailed')
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
    errorMessage.value = error instanceof ApiError ? error.message : t('internalVehicles.loadFailed')
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  editingId.value = null
  formPlate.value = ''
  formPlateColor.value = siteDefaultPlateColor.value
  formType.value = 'OTHER'
  formOwnerName.value = ''
  formPhone.value = ''
  formDepartment.value = ''
  formRemark.value = ''
  formEnabled.value = true
  formError.value = ''
}

function openCreateForm(): void {
  resetForm()
  showForm.value = true
}

function openEditForm(vehicle: InternalVehicleView): void {
  editingId.value = vehicle.id
  formPlate.value = vehicle.plateNumber
  formPlateColor.value = vehicle.plateColor
  formType.value = vehicle.type
  formOwnerName.value = vehicle.ownerName
  formPhone.value = vehicle.phone ?? ''
  formDepartment.value = vehicle.department ?? ''
  formRemark.value = vehicle.remark ?? ''
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
    formError.value = t('internalVehicles.formRequired')
    return
  }
  submitting.value = true
  formError.value = ''
  const payload = {
    plateNumber: formPlate.value.trim(),
    plateColor: formPlateColor.value,
    ownerName: formOwnerName.value.trim(),
    type: formType.value,
    phone: formPhone.value.trim() || undefined,
    department: formDepartment.value.trim() || undefined,
    remark: formRemark.value.trim() || undefined,
    enabled: formEnabled.value,
  }
  try {
    if (isEditing.value && editingId.value) {
      await updateInternalVehicle(selectedLotId.value, editingId.value, payload, locale.value)
    } else {
      await createInternalVehicle(selectedLotId.value, payload, locale.value)
    }
    closeForm()
    successMessage.value = t('internalVehicles.saveSuccess')
    await loadVehiclesOnly()
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : t('internalVehicles.saveFailed')
  } finally {
    submitting.value = false
  }
}

async function onDelete(vehicle: InternalVehicleView): Promise<void> {
  if (!selectedLotId.value) {
    return
  }
  submitting.value = true
  errorMessage.value = ''
  try {
    await deleteInternalVehicle(selectedLotId.value, vehicle.id, locale.value)
    successMessage.value = t('internalVehicles.deleteSuccess')
    await loadVehiclesOnly()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('internalVehicles.deleteFailed')
  } finally {
    submitting.value = false
  }
}

async function onExport(): Promise<void> {
  if (!selectedLotId.value) {
    return
  }
  exporting.value = true
  errorMessage.value = ''
  try {
    await exportInternalVehicles(selectedLotId.value, locale.value, appliedPlate.value || undefined)
    successMessage.value = t('internalVehicles.exportSuccess')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('internalVehicles.exportFailed')
  } finally {
    exporting.value = false
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
  if (importInput.value) {
    importInput.value.value = ''
  }
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  importFile.value = input.files?.[0] ?? null
  importError.value = ''
}

async function downloadImportTemplate(): Promise<void> {
  if (!selectedLotId.value) {
    return
  }
  downloadingTemplate.value = true
  importError.value = ''
  try {
    await downloadInternalVehicleImportTemplate(selectedLotId.value, locale.value)
  } catch (error) {
    importError.value =
      error instanceof ApiError ? error.message : t('internalVehicles.downloadTemplateFailed')
  } finally {
    downloadingTemplate.value = false
  }
}

function openImportFilePicker(): void {
  importInput.value?.click()
}

async function onSubmitImport(): Promise<void> {
  if (!selectedLotId.value) {
    return
  }
  if (!importFile.value) {
    importError.value = t('internalVehicles.importFileRequired')
    return
  }
  importing.value = true
  importError.value = ''
  try {
    const result = await importInternalVehicles(selectedLotId.value, importFile.value, locale.value)
    successMessage.value = t('internalVehicles.importSuccess', {
      imported: result.data.imported,
      skipped: result.data.skipped,
    })
    closeImport()
    await loadVehiclesOnly()
  } catch (error) {
    importError.value = error instanceof ApiError ? error.message : t('internalVehicles.importFailed')
  } finally {
    importing.value = false
  }
}

function shortBatchId(batchId: string): string {
  return batchId.slice(0, 8)
}

async function onDeleteBatch(vehicle: InternalVehicleView): Promise<void> {
  if (!selectedLotId.value || !vehicle.batchId) {
    return
  }
  if (!window.confirm(t('internalVehicles.batchDeleteConfirm'))) {
    return
  }
  submitting.value = true
  errorMessage.value = ''
  try {
    const result = await deleteInternalVehicleBatch(selectedLotId.value, vehicle.batchId, locale.value)
    successMessage.value = t('internalVehicles.batchDeleteSuccess', { deleted: result.data })
    await loadVehiclesOnly()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('internalVehicles.batchDeleteFailed')
  } finally {
    submitting.value = false
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
          <span>{{ t('internalVehicles.colPlate') }}</span>
          <input
            v-model="searchInput"
            type="search"
            :placeholder="t('internalVehicles.searchPlaceholder')"
            @keyup.enter="onSearch"
          />
        </label>
        <div class="filter-actions">
          <button type="button" class="ghost" @click="onResetSearch">{{ t('spaces.reset') }}</button>
          <button type="button" class="primary" @click="onSearch">{{ t('spaces.query') }}</button>
        </div>
      </div>

      <div v-if="isAdmin" class="action-bar">
        <button type="button" class="primary" @click="openCreateForm">{{ t('internalVehicles.create') }}</button>
        <button type="button" class="outline" @click="openImportForm">{{ t('internalVehicles.import') }}</button>
        <button type="button" class="outline" :disabled="exporting" @click="onExport">
          {{ exporting ? t('internalVehicles.exporting') : t('internalVehicles.export') }}
        </button>
      </div>

      <div class="table-card">
        <table v-if="vehicles.length > 0">
          <thead>
            <tr>
              <th>{{ t('internalVehicles.colIndex') }}</th>
              <th>{{ t('internalVehicles.colPlate') }}</th>
              <th>{{ t('internalVehicles.colOwner') }}</th>
              <th>{{ t('internalVehicles.colType') }}</th>
              <th>{{ t('internalVehicles.colPhone') }}</th>
              <th>{{ t('internalVehicles.colDepartment') }}</th>
              <th>{{ t('internalVehicles.colBatch') }}</th>
              <th>{{ t('page.colStatus') }}</th>
              <th>{{ t('page.colUpdated') }}</th>
              <th v-if="isAdmin" class="col-actions">{{ t('internalVehicles.colActions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in vehicles" :key="item.id">
              <td>{{ pageStart + index }}</td>
              <td>
                <PlateBadge :plate-number="item.plateNumber" :plate-color="item.plateColor" />
              </td>
              <td>{{ item.ownerName }}</td>
              <td>{{ vehicleTypeLabel(item.type) }}</td>
              <td>{{ item.phone || '—' }}</td>
              <td>{{ item.department || '—' }}</td>
              <td>
                <span v-if="item.batchId" class="batch-tag" :title="item.batchId">
                  {{ shortBatchId(item.batchId) }}
                </span>
                <span v-else>—</span>
              </td>
              <td>
                <span class="pill" :class="item.enabled ? 'ok' : 'fail'">
                  {{ item.enabled ? t('internalVehicles.statusActive') : t('internalVehicles.statusDisabled') }}
                </span>
              </td>
              <td>{{ formatTime(item.updatedAt) }}</td>
              <td v-if="isAdmin" class="col-actions">
                <button type="button" class="link-btn" @click="openEditForm(item)">
                  {{ t('internalVehicles.edit') }}
                </button>
                <button v-if="item.batchId" type="button" class="link-btn danger" @click="onDeleteBatch(item)">
                  {{ t('internalVehicles.batchDelete') }}
                </button>
                <button type="button" class="link-btn danger" @click="onDelete(item)">
                  {{ t('internalVehicles.delete') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else-if="loading" class="empty">
          <p>{{ t('internalVehicles.loading') }}</p>
        </div>
        <div v-else class="empty">
          <strong>{{ t('internalVehicles.empty') }}</strong>
          <p>{{ isAdmin ? t('internalVehicles.emptyHintAdmin') : t('internalVehicles.emptyHint') }}</p>
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
        <h3>{{ isEditing ? t('internalVehicles.editTitle') : t('internalVehicles.createTitle') }}</h3>
        <label>
          <span>{{ t('internalVehicles.colPlate') }}</span>
          <input v-model="formPlate" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('internalVehicles.colPlateColor') }}</span>
          <select v-model="formPlateColor">
            <option v-for="color in plateColorOptions" :key="color" :value="color">
              {{ plateColorLabel(color) }}
            </option>
          </select>
        </label>
        <label>
          <span>{{ t('internalVehicles.colType') }}</span>
          <select v-model="formType">
            <option v-for="type in vehicleTypeOptions" :key="type" :value="type">
              {{ vehicleTypeLabel(type) }}
            </option>
          </select>
        </label>
        <label>
          <span>{{ t('internalVehicles.colOwner') }}</span>
          <input v-model="formOwnerName" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('internalVehicles.colPhone') }}</span>
          <input v-model="formPhone" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('internalVehicles.colDepartment') }}</span>
          <input v-model="formDepartment" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('internalVehicles.colRemark') }}</span>
          <input v-model="formRemark" type="text" autocomplete="off" />
        </label>
        <label class="checkbox">
          <input v-model="formEnabled" type="checkbox" />
          <span>{{ t('internalVehicles.statusActive') }}</span>
        </label>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" @click="closeForm">{{ t('internalVehicles.cancel') }}</button>
          <button type="submit" :disabled="submitting">
            {{ submitting ? t('internalVehicles.saving') : t('internalVehicles.save') }}
          </button>
        </div>
      </form>
    </div>

    <div v-if="showImport" class="modal-backdrop" @click.self="closeImport">
      <form class="modal import-modal" @submit.prevent="onSubmitImport">
        <div class="modal-header">
          <h3>{{ t('internalVehicles.importTitle') }}</h3>
          <button type="button" class="modal-close" :aria-label="t('internalVehicles.cancel')" @click="closeImport">
            ×
          </button>
        </div>
        <p class="hint">{{ t('internalVehicles.importHint') }}</p>
        <div class="import-toolbar">
          <button type="button" class="outline" :disabled="downloadingTemplate" @click="downloadImportTemplate">
            {{
              downloadingTemplate
                ? t('internalVehicles.downloadingTemplate')
                : t('internalVehicles.downloadTemplate')
            }}
          </button>
        </div>
        <label class="file-field">
          <span>{{ t('internalVehicles.importFileLabel') }}</span>
          <button type="button" class="ghost file-picker" @click="openImportFilePicker">
            {{ importFile ? t('internalVehicles.importChangeFile') : t('internalVehicles.importChooseFile') }}
          </button>
          <input
            ref="importInput"
            type="file"
            accept=".xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
            class="hidden-file"
            @change="onFileChange"
          />
          <p v-if="importFile" class="import-file">{{ importFile.name }}</p>
        </label>
        <p v-if="importError" class="form-error">{{ importError }}</p>
        <div class="actions">
          <button type="button" class="ghost" :disabled="importing" @click="closeImport">
            {{ t('internalVehicles.cancel') }}
          </button>
          <button type="submit" :disabled="importing || !importFile">
            {{ importing ? t('internalVehicles.importing') : t('internalVehicles.importSubmit') }}
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
  flex-wrap: wrap;
  align-items: center;
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

.outline:hover:not(:disabled) {
  background: #f2f8f5;
}

.outline:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.hidden-file {
  display: none;
}

.file-field {
  display: grid;
  gap: 0.5rem;
}

.file-picker {
  justify-self: start;
}

.import-file {
  margin: 0;
  color: var(--muted);
  font-size: 0.88rem;
  word-break: break-all;
}

.import-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
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
  width: 13rem;
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

.batch-tag {
  display: inline-block;
  border-radius: 6px;
  padding: 0.15rem 0.5rem;
  font-size: 0.78rem;
  font-family: ui-monospace, monospace;
  color: #6b7280;
  background: #f2f4f3;
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
  width: min(420px, 100%);
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

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.modal-close {
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 1.35rem;
  line-height: 1;
  cursor: pointer;
  padding: 0.15rem 0.35rem;
}

.modal-close:hover {
  color: var(--text);
}

.import-modal {
  width: min(560px, 100%);
}

.hint {
  margin: 0;
  color: var(--muted);
  font-size: 0.9rem;
  line-height: 1.45;
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
