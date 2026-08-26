<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  createBooth,
  deleteBooth,
  listBooths,
  listLanes,
  listLots,
  updateBooth,
  type BoothView,
  type LaneView,
  type LotView,
} from '@/api/client'
import { getUser } from '@/auth/session'
import { useSiteTime } from '@/composables/useSiteTime'

const LOT_STORAGE_KEY = 'freepark.booths.lotId'

const { t, locale } = useI18n()
const { formatTime } = useSiteTime()

const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const lots = ref<LotView[]>([])
const selectedLotId = ref('')
const booths = ref<BoothView[]>([])
const laneOptions = ref<LaneView[]>([])
const total = ref(0)
const page = ref(0)
const pageSize = ref(10)

const searchInput = ref('')
const appliedKeyword = ref('')

const showForm = ref(false)
const editingId = ref<string | null>(null)
const formName = ref('')
const formCode = ref('')
const formLocation = ref('')
const formEnabled = ref(true)
const formLaneIds = ref<string[]>([])
const formError = ref('')

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

async function loadBooths(): Promise<void> {
  if (!selectedLotId.value) {
    booths.value = []
    total.value = 0
    return
  }
  const result = await listBooths(selectedLotId.value, locale.value, {
    keyword: appliedKeyword.value || undefined,
    page: page.value,
    size: pageSize.value,
  })
  booths.value = result.data.items
  total.value = result.data.total
  page.value = result.data.page
  pageSize.value = result.data.size
}

async function loadLanes(): Promise<void> {
  if (!selectedLotId.value) {
    laneOptions.value = []
    return
  }
  const result = await listLanes(locale.value, selectedLotId.value)
  laneOptions.value = result.data
}

async function reload(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadLots()
    page.value = 0
    await Promise.all([loadLanes(), loadBooths()])
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('booths.loadFailed')
  } finally {
    loading.value = false
  }
}

async function onLotChange(): Promise<void> {
  sessionStorage.setItem(LOT_STORAGE_KEY, selectedLotId.value)
  appliedKeyword.value = ''
  searchInput.value = ''
  page.value = 0
  loading.value = true
  try {
    await Promise.all([loadLanes(), loadBooths()])
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('booths.loadFailed')
  } finally {
    loading.value = false
  }
}

async function loadBoothsOnly(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadBooths()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('booths.loadFailed')
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  appliedKeyword.value = searchInput.value.trim()
  page.value = 0
  void loadBoothsOnly()
}

function onResetSearch(): void {
  searchInput.value = ''
  appliedKeyword.value = ''
  page.value = 0
  void loadBoothsOnly()
}

function resetForm(): void {
  editingId.value = null
  formName.value = ''
  formCode.value = ''
  formLocation.value = ''
  formEnabled.value = true
  formLaneIds.value = []
  formError.value = ''
}

function openCreateForm(): void {
  resetForm()
  showForm.value = true
}

function openEditForm(booth: BoothView): void {
  editingId.value = booth.id
  formName.value = booth.name
  formCode.value = booth.code ?? ''
  formLocation.value = booth.location ?? ''
  formEnabled.value = booth.enabled
  formLaneIds.value = (booth.lanes ?? []).map((lane) => lane.id)
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
  if (!formName.value.trim()) {
    formError.value = t('booths.formRequired')
    return
  }
  submitting.value = true
  formError.value = ''
  const payload = {
    name: formName.value.trim(),
    code: formCode.value.trim() || undefined,
    location: formLocation.value.trim() || undefined,
    enabled: formEnabled.value,
    laneIds: formLaneIds.value,
  }
  try {
    if (isEditing.value && editingId.value) {
      await updateBooth(selectedLotId.value, editingId.value, payload, locale.value)
    } else {
      await createBooth(selectedLotId.value, payload, locale.value)
    }
    closeForm()
    successMessage.value = t('booths.saveSuccess')
    await loadBoothsOnly()
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : t('booths.saveFailed')
  } finally {
    submitting.value = false
  }
}

async function onDelete(booth: BoothView): Promise<void> {
  if (!selectedLotId.value) {
    return
  }
  if (!window.confirm(t('booths.deleteConfirm'))) {
    return
  }
  submitting.value = true
  errorMessage.value = ''
  try {
    await deleteBooth(selectedLotId.value, booth.id, locale.value)
    successMessage.value = t('booths.deleteSuccess')
    await loadBoothsOnly()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('booths.deleteFailed')
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
  void loadBoothsOnly()
}

function onPageSizeChange(): void {
  page.value = 0
  void loadBoothsOnly()
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
          <span>{{ t('booths.colName') }}</span>
          <input
            v-model="searchInput"
            type="search"
            :placeholder="t('booths.searchPlaceholder')"
            @keyup.enter="onSearch"
          />
        </label>
        <div class="filter-actions">
          <button type="button" class="ghost" @click="onResetSearch">{{ t('spaces.reset') }}</button>
          <button type="button" class="primary" @click="onSearch">{{ t('spaces.query') }}</button>
        </div>
      </div>

      <div v-if="isAdmin" class="action-bar">
        <button type="button" class="primary" @click="openCreateForm">{{ t('booths.create') }}</button>
      </div>

      <div class="table-card">
        <table v-if="booths.length > 0">
          <thead>
            <tr>
              <th>{{ t('booths.colIndex') }}</th>
              <th>{{ t('booths.colName') }}</th>
              <th>{{ t('booths.colCode') }}</th>
              <th>{{ t('booths.colLocation') }}</th>
              <th>{{ t('booths.colLanes') }}</th>
              <th>{{ t('page.colStatus') }}</th>
              <th>{{ t('page.colUpdated') }}</th>
              <th class="col-actions">{{ t('booths.colActions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in booths" :key="item.id">
              <td>{{ pageStart + index }}</td>
              <td>{{ item.name }}</td>
              <td>{{ item.code || '—' }}</td>
              <td>{{ item.location || '—' }}</td>
              <td>
                <span v-if="item.lanes && item.lanes.length > 0" class="lane-tags">
                  <span v-for="lane in item.lanes" :key="lane.id" class="lane-tag">
                    {{ lane.name }}
                  </span>
                </span>
                <span v-else class="muted">—</span>
              </td>
              <td>
                <span class="pill" :class="item.enabled ? 'ok' : 'fail'">
                  {{ item.enabled ? t('internalVehicles.statusActive') : t('internalVehicles.statusDisabled') }}
                </span>
              </td>
              <td>{{ formatTime(item.updatedAt) }}</td>
              <td class="col-actions">
                <RouterLink :to="{ name: 'boothView', params: { boothId: item.id }, query: { lot: selectedLotId } }" class="link-btn">
                  {{ t('booths.view') }}
                </RouterLink>
                <template v-if="isAdmin">
                  <button type="button" class="link-btn" @click="openEditForm(item)">
                    {{ t('booths.edit') }}
                  </button>
                  <button type="button" class="link-btn danger" @click="onDelete(item)">
                    {{ t('booths.delete') }}
                  </button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else-if="loading" class="empty">
          <p>{{ t('booths.loading') }}</p>
        </div>
        <div v-else class="empty">
          <strong>{{ t('booths.empty') }}</strong>
          <p>{{ isAdmin ? t('booths.emptyHintAdmin') : t('booths.emptyHint') }}</p>
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
        <h3>{{ isEditing ? t('booths.editTitle') : t('booths.createTitle') }}</h3>
        <label>
          <span>{{ t('booths.colName') }}</span>
          <input v-model="formName" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('booths.colCode') }}</span>
          <input v-model="formCode" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('booths.colLocation') }}</span>
          <input v-model="formLocation" type="text" autocomplete="off" />
        </label>
        <label class="checkbox">
          <input v-model="formEnabled" type="checkbox" />
          <span>{{ t('internalVehicles.statusActive') }}</span>
        </label>
        <div class="lane-field">
          <span class="lane-label">{{ t('booths.colLanes') }}</span>
          <div v-if="laneOptions.length > 0" class="lane-options">
            <label v-for="lane in laneOptions" :key="lane.id" class="lane-option">
              <input v-model="formLaneIds" type="checkbox" :value="lane.id" />
              <span>{{ lane.name }}</span>
            </label>
          </div>
          <p v-else class="lane-empty">{{ t('booths.lanesEmpty') }}</p>
        </div>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" @click="closeForm">{{ t('booths.cancel') }}</button>
          <button type="submit" :disabled="submitting">
            {{ submitting ? t('booths.saving') : t('booths.save') }}
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
  width: 10rem;
  text-align: end;
}

.link-btn {
  border: 0;
  background: none;
  color: var(--accent);
  font-weight: 600;
  padding: 0;
  cursor: pointer;
  text-decoration: none;
}

.col-actions .link-btn {
  margin-left: 0.65rem;
}

.col-actions .link-btn:first-child {
  margin-left: 0;
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

.muted {
  color: var(--muted);
}

.lane-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.lane-tag {
  border-radius: 6px;
  padding: 0.1rem 0.5rem;
  font-size: 0.78rem;
  background: #eef3ff;
  color: #3b5bdb;
}

.lane-field {
  display: grid;
  gap: 0.4rem;
}

.lane-label {
  font-weight: 600;
  font-size: 0.9rem;
}

.lane-options {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.lane-option {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.35rem 0.6rem;
  background: #fff;
}

.lane-option input {
  width: auto;
}

.lane-empty {
  margin: 0;
  color: var(--muted);
  font-size: 0.85rem;
}
</style>
