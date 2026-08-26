<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  createArea,
  createLocation,
  createSpace,
  deleteSpace,
  downloadSpaceImportTemplate,
  importSpaces,
  listAreas,
  listLocations,
  listLots,
  listSpaces,
  updateSpace,
  type AreaView,
  type LocationView,
  type LotView,
  type SpaceView,
} from '@/api/client'
import VehicleListImportModal from '@/components/VehicleListImportModal.vue'
import { getUser } from '@/auth/session'

const LOT_STORAGE_KEY = 'freepark.selectedLotId'

function locationStorageKey(lotId: string): string {
  return `freepark.lot.${lotId}.location`
}

function areaStorageKey(lotId: string): string {
  return `freepark.lot.${lotId}.area`
}

const { t, locale } = useI18n()

const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const lots = ref<LotView[]>([])
const selectedLotId = ref('')
const locations = ref<LocationView[]>([])
const areas = ref<AreaView[]>([])
const selectedLocationId = ref('')
const selectedAreaId = ref('')

const spaces = ref<SpaceView[]>([])
const totalSpaces = ref(0)
const page = ref(0)
const pageSize = ref(10)

const searchInput = ref('')
const appliedCode = ref('')

const showSpaceForm = ref(false)
const showLocationForm = ref(false)
const showAreaForm = ref(false)
const editingSpaceId = ref<string | null>(null)
const formCode = ref('')
const formEnabled = ref(true)
const formLocationName = ref('')
const formAreaName = ref('')
const formError = ref('')

const showImport = ref(false)
const importing = ref(false)
const downloadingTemplate = ref(false)
const importFile = ref<File | null>(null)
const importError = ref('')

const isAdmin = computed(() => getUser()?.role === 'ADMIN')
const isEditingSpace = computed(() => editingSpaceId.value !== null)
const selectedLot = computed(() => lots.value.find((lot) => lot.id === selectedLotId.value))
const selectedLocation = computed(() =>
  locations.value.find((item) => item.id === selectedLocationId.value),
)
const selectedArea = computed(() => areas.value.find((item) => item.id === selectedAreaId.value))
const canLoadSpaces = computed(
  () => selectedLotId.value.length > 0 && selectedLocationId.value.length > 0 && selectedAreaId.value.length > 0,
)
const pageCount = computed(() => Math.max(1, Math.ceil(totalSpaces.value / pageSize.value)))
const pageStart = computed(() => (totalSpaces.value === 0 ? 0 : page.value * pageSize.value + 1))
const importHintText = computed(() => {
  if (selectedLocation.value && selectedArea.value) {
    return t('spaces.importHintWithContext', {
      location: selectedLocation.value.name,
      area: selectedArea.value.name,
    })
  }
  return t('spaces.importHint')
})

function persistLotId(lotId: string): void {
  if (lotId) {
    sessionStorage.setItem(LOT_STORAGE_KEY, lotId)
  }
}

async function loadLots(): Promise<void> {
  const result = await listLots(locale.value)
  lots.value = result.data
  if (lots.value.length === 0) {
    selectedLotId.value = ''
    return
  }
  const stored = sessionStorage.getItem(LOT_STORAGE_KEY)
  const match = lots.value.find((lot) => lot.id === stored)
  const firstLot = lots.value[0]
  selectedLotId.value = match?.id ?? firstLot?.id ?? ''
  persistLotId(selectedLotId.value)
}

async function loadLocations(): Promise<void> {
  if (!selectedLotId.value) {
    locations.value = []
    selectedLocationId.value = ''
    return
  }
  const result = await listLocations(selectedLotId.value, locale.value)
  locations.value = result.data
  if (locations.value.length === 0) {
    selectedLocationId.value = ''
    return
  }
  const storedLocation = sessionStorage.getItem(locationStorageKey(selectedLotId.value))
  if (storedLocation && locations.value.some((item) => item.id === storedLocation)) {
    selectedLocationId.value = storedLocation
  } else if (!locations.value.some((item) => item.id === selectedLocationId.value)) {
    selectedLocationId.value = locations.value[0]?.id ?? ''
  }
  sessionStorage.setItem(locationStorageKey(selectedLotId.value), selectedLocationId.value)
}

async function loadAreas(): Promise<void> {
  if (!selectedLotId.value || !selectedLocationId.value) {
    areas.value = []
    selectedAreaId.value = ''
    return
  }
  const result = await listAreas(selectedLotId.value, locale.value, selectedLocationId.value)
  areas.value = result.data
  if (areas.value.length === 0) {
    selectedAreaId.value = ''
    return
  }
  const storedArea = sessionStorage.getItem(areaStorageKey(selectedLotId.value))
  if (storedArea && areas.value.some((item) => item.id === storedArea)) {
    selectedAreaId.value = storedArea
  } else if (!areas.value.some((item) => item.id === selectedAreaId.value)) {
    selectedAreaId.value = areas.value[0]?.id ?? ''
  }
  sessionStorage.setItem(areaStorageKey(selectedLotId.value), selectedAreaId.value)
}

async function loadSpaces(): Promise<void> {
  if (!canLoadSpaces.value) {
    spaces.value = []
    totalSpaces.value = 0
    return
  }
  const result = await listSpaces(selectedLotId.value, locale.value, {
    locationId: selectedLocationId.value || undefined,
    areaId: selectedAreaId.value || undefined,
    code: appliedCode.value || undefined,
    page: page.value,
    size: pageSize.value,
  })
  spaces.value = result.data.items
  totalSpaces.value = result.data.total
  page.value = result.data.page
  pageSize.value = result.data.size
}

async function reloadAll(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadLocations()
    await loadAreas()
    page.value = 0
    await loadSpaces()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('spaces.loadFailed')
  } finally {
    loading.value = false
  }
}

async function onLotChange(): Promise<void> {
  persistLotId(selectedLotId.value)
  selectedLocationId.value = ''
  selectedAreaId.value = ''
  appliedCode.value = ''
  searchInput.value = ''
  page.value = 0
  await reloadAll()
}

function selectLocation(locationId: string): void {
  if (selectedLocationId.value === locationId) {
    return
  }
  selectedLocationId.value = locationId
  sessionStorage.setItem(locationStorageKey(selectedLotId.value), locationId)
  selectedAreaId.value = ''
  page.value = 0
  void loadAreasAndSpaces()
}

function selectArea(areaId: string): void {
  if (selectedAreaId.value === areaId) {
    return
  }
  selectedAreaId.value = areaId
  sessionStorage.setItem(areaStorageKey(selectedLotId.value), areaId)
  page.value = 0
  void loadSpacesOnly()
}

async function loadAreasAndSpaces(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadAreas()
    await loadSpaces()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('spaces.loadFailed')
  } finally {
    loading.value = false
  }
}

async function loadSpacesOnly(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadSpaces()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('spaces.loadFailed')
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  appliedCode.value = searchInput.value.trim()
  page.value = 0
  void loadSpacesOnly()
}

function onResetSearch(): void {
  searchInput.value = ''
  appliedCode.value = ''
  page.value = 0
  void loadSpacesOnly()
}

function openCreateSpace(): void {
  if (!selectedAreaId.value) {
    errorMessage.value = t('spaces.needArea')
    return
  }
  editingSpaceId.value = null
  formCode.value = ''
  formEnabled.value = true
  formError.value = ''
  showSpaceForm.value = true
}

function openEditSpace(space: SpaceView): void {
  editingSpaceId.value = space.id
  formCode.value = space.code
  formEnabled.value = space.enabled
  formError.value = ''
  showSpaceForm.value = true
}

function closeSpaceForm(): void {
  showSpaceForm.value = false
  editingSpaceId.value = null
  formError.value = ''
}

async function onSubmitSpace(): Promise<void> {
  if (!selectedLotId.value || !selectedAreaId.value) {
    formError.value = t('spaces.needArea')
    return
  }
  if (!formCode.value.trim()) {
    formError.value = t('spaces.formRequired')
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    const payload = {
      areaId: selectedAreaId.value,
      code: formCode.value.trim(),
      enabled: formEnabled.value,
    }
    if (isEditingSpace.value && editingSpaceId.value) {
      await updateSpace(selectedLotId.value, editingSpaceId.value, payload, locale.value)
    } else {
      await createSpace(selectedLotId.value, payload, locale.value)
    }
    closeSpaceForm()
    successMessage.value = t('spaces.saveSuccess')
    await loadSpacesOnly()
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : t('spaces.saveFailed')
  } finally {
    submitting.value = false
  }
}

async function onDeleteSpace(space: SpaceView): Promise<void> {
  if (!selectedLotId.value) {
    return
  }
  submitting.value = true
  errorMessage.value = ''
  try {
    await deleteSpace(selectedLotId.value, space.id, locale.value)
    successMessage.value = t('spaces.deleteSuccess')
    await loadSpacesOnly()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('spaces.deleteFailed')
  } finally {
    submitting.value = false
  }
}

function openLocationForm(): void {
  formLocationName.value = ''
  formError.value = ''
  showLocationForm.value = true
}

function openAreaForm(): void {
  if (!selectedLocationId.value) {
    errorMessage.value = t('spaces.needLocation')
    return
  }
  formAreaName.value = ''
  formError.value = ''
  showAreaForm.value = true
}

async function onSubmitLocation(continueAdding = false): Promise<void> {
  if (!selectedLotId.value || !formLocationName.value.trim()) {
    formError.value = t('spaces.formRequired')
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    const result = await createLocation(selectedLotId.value, formLocationName.value.trim(), locale.value)
    selectedLocationId.value = result.data.id
    selectedAreaId.value = ''
    sessionStorage.setItem(locationStorageKey(selectedLotId.value), result.data.id)
    if (continueAdding) {
      formLocationName.value = ''
      successMessage.value = t('spaces.saveSuccess')
      await loadLocations()
    } else {
      showLocationForm.value = false
      await loadAreasAndSpaces()
    }
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : t('spaces.saveFailed')
  } finally {
    submitting.value = false
  }
}

async function onSubmitArea(continueAdding = false): Promise<void> {
  if (!selectedLotId.value || !selectedLocationId.value || !formAreaName.value.trim()) {
    formError.value = t('spaces.formRequired')
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    const result = await createArea(
      selectedLotId.value,
      { locationId: selectedLocationId.value, name: formAreaName.value.trim() },
      locale.value,
    )
    selectedAreaId.value = result.data.id
    sessionStorage.setItem(areaStorageKey(selectedLotId.value), result.data.id)
    if (continueAdding) {
      formAreaName.value = ''
      successMessage.value = t('spaces.saveSuccess')
      await loadAreas()
    } else {
      showAreaForm.value = false
      await loadAreasAndSpaces()
    }
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : t('spaces.saveFailed')
  } finally {
    submitting.value = false
  }
}

function openImportForm(): void {
  if (!selectedLotId.value) {
    return
  }
  if (!selectedAreaId.value) {
    errorMessage.value = t('spaces.needArea')
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
    await downloadSpaceImportTemplate(selectedLotId.value, locale.value)
  } catch (error) {
    importError.value = error instanceof ApiError ? error.message : t('spaces.downloadTemplateFailed')
  } finally {
    downloadingTemplate.value = false
  }
}

async function onSubmitImport(): Promise<void> {
  if (!selectedLotId.value || !selectedAreaId.value) {
    importError.value = t('spaces.needArea')
    return
  }
  if (!importFile.value) {
    importError.value = t('spaces.importFileRequired')
    return
  }
  importing.value = true
  importError.value = ''
  try {
    const result = await importSpaces(
      selectedLotId.value,
      selectedAreaId.value,
      importFile.value,
      locale.value,
    )
    successMessage.value = t('spaces.importSuccess', { count: result.data })
    closeImport()
    await loadSpacesOnly()
  } catch (error) {
    importError.value = error instanceof ApiError ? error.message : t('spaces.importFailed')
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
  void loadSpacesOnly()
}

function onPageSizeChange(): void {
  page.value = 0
  void loadSpacesOnly()
}

onMounted(async () => {
  loading.value = true
  try {
    await loadLots()
    if (selectedLotId.value) {
      await reloadAll()
    }
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('spaces.loadFailed')
  } finally {
    loading.value = false
  }
})

watch(successMessage, (value) => {
  if (value) {
    setTimeout(() => {
      successMessage.value = ''
    }, 3000)
  }
})
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

    <div v-else class="spaces-layout">
      <aside class="sidebar">
        <section class="side-block">
          <header>
            <strong>
              {{ t('spaces.locationInfo') }}
              <span v-if="locations.length > 0" class="count">({{ locations.length }})</span>
            </strong>
            <button v-if="isAdmin" type="button" class="manage-link" @click="openLocationForm">
              {{ t('spaces.addItem') }}
            </button>
          </header>
          <p class="side-hint">{{ t('spaces.locationInfoHint') }}</p>
          <div class="chip-list scrollable">
            <button
              v-for="location in locations"
              :key="location.id"
              type="button"
              class="chip location"
              :class="{ active: selectedLocationId === location.id }"
              @click="selectLocation(location.id)"
            >
              {{ location.name }}
            </button>
            <p v-if="locations.length === 0" class="side-empty">{{ t('spaces.noLocation') }}</p>
          </div>
        </section>

        <section class="side-block" :class="{ muted: !selectedLocationId }">
          <header>
            <strong>
              {{ t('spaces.areaInfo') }}
              <span v-if="areas.length > 0" class="count">({{ areas.length }})</span>
            </strong>
            <button
              v-if="isAdmin"
              type="button"
              class="manage-link"
              :disabled="!selectedLocationId"
              @click="openAreaForm"
            >
              {{ t('spaces.addItem') }}
            </button>
          </header>
          <p v-if="!selectedLocationId" class="side-hint">{{ t('spaces.areaBlockedHint') }}</p>
          <p v-else class="side-hint">{{ t('spaces.areaInfoHint') }}</p>
          <div v-if="selectedLocationId" class="chip-list scrollable">
            <button
              v-for="area in areas"
              :key="area.id"
              type="button"
              class="chip area"
              :class="{ active: selectedAreaId === area.id }"
              @click="selectArea(area.id)"
            >
              {{ area.name }}
            </button>
            <p v-if="areas.length === 0" class="side-empty">{{ t('spaces.noArea') }}</p>
          </div>
        </section>
      </aside>

      <div class="main-panel">
        <div v-if="selectedLocation && selectedArea" class="context-bar">
          <span class="context-label">{{ t('spaces.currentContext') }}</span>
          <span class="context-chip location">{{ selectedLocation.name }}</span>
          <span class="context-sep">/</span>
          <span class="context-chip area">{{ selectedArea.name }}</span>
        </div>
        <div v-else-if="selectedLocation" class="context-bar muted">
          <span>{{ t('spaces.selectAreaFirst') }}</span>
        </div>
        <div v-else class="context-bar muted">
          <span>{{ t('spaces.selectLocationFirst') }}</span>
        </div>

        <div v-if="!canLoadSpaces" class="table-card">
          <div class="empty">
            <strong>{{ selectedLocationId ? t('spaces.selectAreaFirst') : t('spaces.selectLocationFirst') }}</strong>
            <p>{{ selectedLocationId ? t('spaces.noArea') : t('spaces.noLocation') }}</p>
          </div>
        </div>

        <template v-else>
        <div class="filter-bar">
          <label class="filter-field">
            <span>{{ t('spaces.colCode') }}</span>
            <input
              v-model="searchInput"
              type="search"
              :placeholder="t('spaces.searchPlaceholder')"
              @keyup.enter="onSearch"
            />
          </label>
          <div class="filter-actions">
            <button type="button" class="ghost" @click="onResetSearch">{{ t('spaces.reset') }}</button>
            <button type="button" class="primary" @click="onSearch">{{ t('spaces.query') }}</button>
          </div>
        </div>

        <div v-if="isAdmin" class="action-bar">
          <button type="button" class="primary" @click="openCreateSpace">{{ t('spaces.add') }}</button>
          <button type="button" class="outline" @click="openImportForm">{{ t('spaces.import') }}</button>
        </div>

        <div class="table-card">
          <table v-if="spaces.length > 0">
            <thead>
              <tr>
                <th>{{ t('spaces.colIndex') }}</th>
                <th>{{ t('spaces.colCode') }}</th>
                <th>{{ t('page.colStatus') }}</th>
                <th v-if="isAdmin" class="col-actions">{{ t('spaces.colActions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in spaces" :key="item.id">
                <td>{{ pageStart + index }}</td>
                <td>{{ item.code }}</td>
                <td>
                  <span class="pill" :class="item.enabled ? 'ok' : 'fail'">
                    {{ item.enabled ? t('spaces.statusEnabled') : t('spaces.statusDisabled') }}
                  </span>
                </td>
                <td v-if="isAdmin" class="col-actions">
                  <button type="button" class="link-btn" @click="openEditSpace(item)">
                    {{ t('spaces.edit') }}
                  </button>
                  <button type="button" class="link-btn danger" @click="onDeleteSpace(item)">
                    {{ t('spaces.delete') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else-if="loading" class="empty">
            <p>{{ t('spaces.loading') }}</p>
          </div>
          <div v-else class="empty">
            <strong>{{ t('spaces.empty') }}</strong>
            <p>{{ isAdmin ? t('spaces.emptyHintAdmin') : t('spaces.emptyHint') }}</p>
          </div>

          <footer v-if="totalSpaces > 0" class="pagination">
            <span>{{ t('spaces.paginationTotal', { total: totalSpaces }) }}</span>
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
            <label class="goto">
              {{ t('spaces.paginationGoto') }}
              <input
                type="number"
                min="1"
                :max="pageCount"
                :value="page + 1"
                @change="goToPage(Number(($event.target as HTMLInputElement).value) - 1)"
              />
              {{ t('spaces.paginationPage') }}
            </label>
          </footer>
        </div>
        </template>
      </div>
    </div>

    <div v-if="showSpaceForm" class="modal-backdrop">
      <form class="modal" @submit.prevent="onSubmitSpace">
        <h3>{{ isEditingSpace ? t('spaces.editTitle') : t('spaces.createTitle') }}</h3>
        <p class="hint">{{ selectedLot?.name }} · {{ t('spaces.colCode') }}</p>
        <label>
          <span>{{ t('spaces.colCode') }}</span>
          <input v-model="formCode" type="text" autocomplete="off" />
        </label>
        <label class="checkbox">
          <input v-model="formEnabled" type="checkbox" />
          <span>{{ t('spaces.statusEnabled') }}</span>
        </label>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" @click="closeSpaceForm">{{ t('spaces.cancel') }}</button>
          <button type="submit" :disabled="submitting">
            {{ submitting ? t('spaces.saving') : t('spaces.save') }}
          </button>
        </div>
      </form>
    </div>

    <div v-if="showLocationForm" class="modal-backdrop">
      <form class="modal" @submit.prevent="onSubmitLocation(false)">
        <h3>{{ t('spaces.createLocationTitle') }}</h3>
        <p class="hint">{{ t('spaces.locationInfoHint') }}</p>
        <label>
          <span>{{ t('spaces.locationName') }}</span>
          <input
            v-model="formLocationName"
            type="text"
            autocomplete="off"
            :placeholder="t('spaces.locationNamePlaceholder')"
          />
        </label>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" @click="showLocationForm = false">{{ t('spaces.cancel') }}</button>
          <button type="button" class="ghost" :disabled="submitting" @click="onSubmitLocation(true)">
            {{ t('spaces.saveAndContinue') }}
          </button>
          <button type="submit" :disabled="submitting">{{ t('spaces.save') }}</button>
        </div>
      </form>
    </div>

    <div v-if="showAreaForm" class="modal-backdrop">
      <form class="modal" @submit.prevent="onSubmitArea(false)">
        <h3>{{ t('spaces.createAreaTitle') }}</h3>
        <p v-if="selectedLocation" class="hint">
          {{ t('spaces.createAreaHint', { location: selectedLocation.name }) }}
        </p>
        <p class="hint">{{ t('spaces.areaInfoHint') }}</p>
        <label>
          <span>{{ t('spaces.areaName') }}</span>
          <input
            v-model="formAreaName"
            type="text"
            autocomplete="off"
            :placeholder="t('spaces.areaNamePlaceholder')"
          />
        </label>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" @click="showAreaForm = false">{{ t('spaces.cancel') }}</button>
          <button type="button" class="ghost" :disabled="submitting" @click="onSubmitArea(true)">
            {{ t('spaces.saveAndContinue') }}
          </button>
          <button type="submit" :disabled="submitting">{{ t('spaces.save') }}</button>
        </div>
      </form>
    </div>

    <VehicleListImportModal
      scope="spaces"
      :open="showImport"
      :hint="importHintText"
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

.spaces-layout {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 0.9rem;
  align-items: start;
}

.sidebar {
  display: grid;
  gap: 0.75rem;
}

.side-block {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 0.75rem;
  box-shadow: var(--shadow);
}

.side-block header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.6rem;
}

.manage-link {
  border: 0;
  background: none;
  color: var(--accent);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
}

.manage-link:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.side-block.muted {
  opacity: 0.92;
}

.side-hint {
  margin: 0;
  color: var(--muted);
  font-size: 0.82rem;
  line-height: 1.4;
}

.context-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.45rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 0.65rem 1rem;
  box-shadow: var(--shadow);
  font-size: 0.9rem;
}

.context-bar.muted {
  color: var(--muted);
}

.context-label {
  color: var(--muted);
  font-weight: 600;
}

.context-chip {
  border-radius: 6px;
  padding: 0.2rem 0.55rem;
  font-weight: 600;
}

.context-chip.location {
  background: var(--accent);
  color: #fff;
}

.context-chip.area {
  background: #2f9e44;
  color: #fff;
}

.context-sep {
  color: var(--muted);
}

.count {
  color: var(--muted);
  font-weight: 600;
  font-size: 0.82rem;
}

.chip-list.scrollable {
  max-height: 11rem;
  overflow-y: auto;
}

.chip-list {
  display: grid;
  gap: 0.45rem;
}

.chip {
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 0.45rem 0.65rem;
  text-align: center;
  font-weight: 600;
  cursor: pointer;
  background: #fff;
}

.chip.location.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

.chip.area.active {
  background: #2f9e44;
  border-color: #2f9e44;
  color: #fff;
}

.side-empty {
  margin: 0;
  color: var(--muted);
  font-size: 0.85rem;
}

.main-panel {
  display: grid;
  gap: 0.75rem;
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

.outline {
  border: 1px solid var(--accent);
  border-radius: 8px;
  padding: 0.5rem 0.85rem;
  font-weight: 600;
  color: var(--accent);
  background: #fff;
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

.hidden-file {
  display: none;
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

.page-size select,
.goto input {
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

.goto {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.goto input {
  width: 3rem;
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

.hint {
  margin: -0.25rem 0 0;
  color: var(--muted);
  font-size: 0.9rem;
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

input[type='text'],
input[type='search'] {
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

@media (max-width: 900px) {
  .spaces-layout {
    grid-template-columns: 1fr;
  }
}
</style>
