<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  createPatternAllowlistEntry,
  deletePatternAllowlistEntry,
  listPatternAllowlist,
  listLots,
  updatePatternAllowlistEntry,
  type PatternAllowlistView,
  type LotView,
} from '@/api/client'
import { getUser } from '@/auth/session'
import { useSiteTime } from '@/composables/useSiteTime'
import {
  buildAllowPattern,
  testAllowPattern,
  type PatternMatchMode,
} from '@/lib/patternAllowRule'

const LOT_STORAGE_KEY = 'freepark.patternAllowlist.lotId'

const { t, locale } = useI18n()
const { formatTime } = useSiteTime()

const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const lots = ref<LotView[]>([])
const selectedLotId = ref('')
const entries = ref<PatternAllowlistView[]>([])
const total = ref(0)
const page = ref(0)
const pageSize = ref(10)

const searchInput = ref('')
const appliedKeyword = ref('')

const showForm = ref(false)
const editingId = ref<string | null>(null)
const formName = ref('')
const formPattern = ref('')
const formRemark = ref('')
const formEnabled = ref(true)
const formError = ref('')

const helperMode = ref<PatternMatchMode>('endsWith')
const helperKeyword = ref('')
const helperTestPlate = ref('')
const helperError = ref('')

const isAdmin = computed(() => getUser()?.role === 'ADMIN')
const isEditing = computed(() => editingId.value !== null)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const pageStart = computed(() => (total.value === 0 ? 0 : page.value * pageSize.value + 1))

const helperPreview = computed(() => buildAllowPattern(helperMode.value, helperKeyword.value))

const helperTestResult = computed(() => {
  const pattern = formPattern.value.trim() || helperPreview.value
  const plate = helperTestPlate.value.trim()
  if (!pattern || !plate) {
    return null
  }
  if (!isValidPattern(pattern)) {
    return null
  }
  return testAllowPattern(pattern, plate)
})

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

async function loadEntries(): Promise<void> {
  if (!selectedLotId.value) {
    entries.value = []
    total.value = 0
    return
  }
  const result = await listPatternAllowlist(selectedLotId.value, locale.value, {
    keyword: appliedKeyword.value || undefined,
    page: page.value,
    size: pageSize.value,
  })
  entries.value = result.data.items
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
    await loadEntries()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('patternAllowlist.loadFailed')
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
    await loadEntries()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('patternAllowlist.loadFailed')
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  appliedKeyword.value = searchInput.value.trim()
  page.value = 0
  void loadEntriesOnly()
}

function onResetSearch(): void {
  searchInput.value = ''
  appliedKeyword.value = ''
  page.value = 0
  void loadEntriesOnly()
}

async function loadEntriesOnly(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadEntries()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('patternAllowlist.loadFailed')
  } finally {
    loading.value = false
  }
}

function resetHelper(): void {
  helperMode.value = 'endsWith'
  helperKeyword.value = ''
  helperTestPlate.value = ''
  helperError.value = ''
}

function resetForm(): void {
  editingId.value = null
  formName.value = ''
  formPattern.value = ''
  formRemark.value = ''
  formEnabled.value = true
  formError.value = ''
  resetHelper()
}

function openCreateForm(): void {
  resetForm()
  showForm.value = true
}

function openEditForm(entry: PatternAllowlistView): void {
  editingId.value = entry.id
  formName.value = entry.name
  formPattern.value = entry.pattern
  formRemark.value = entry.remark ?? ''
  formEnabled.value = entry.enabled
  formError.value = ''
  resetHelper()
  showForm.value = true
}

function closeForm(): void {
  showForm.value = false
  resetForm()
}

function isValidPattern(value: string): boolean {
  try {
    new RegExp(value)
    return true
  } catch {
    return false
  }
}

function applyHelperPattern(): void {
  const pattern = buildAllowPattern(helperMode.value, helperKeyword.value)
  if (!pattern) {
    helperError.value = t('patternAllowlist.helperKeywordRequired')
    return
  }
  helperError.value = ''
  formPattern.value = pattern
  formError.value = ''
}

async function onSubmit(): Promise<void> {
  if (!selectedLotId.value) {
    return
  }
  if (!formName.value.trim() || !formPattern.value.trim()) {
    formError.value = t('patternAllowlist.formRequired')
    return
  }
  if (!isValidPattern(formPattern.value.trim())) {
    formError.value = t('patternAllowlist.formInvalidPattern')
    return
  }
  submitting.value = true
  formError.value = ''
  const payload = {
    name: formName.value.trim(),
    pattern: formPattern.value.trim(),
    remark: formRemark.value.trim() || undefined,
    enabled: formEnabled.value,
  }
  try {
    if (isEditing.value && editingId.value) {
      await updatePatternAllowlistEntry(selectedLotId.value, editingId.value, payload, locale.value)
    } else {
      await createPatternAllowlistEntry(selectedLotId.value, payload, locale.value)
    }
    closeForm()
    successMessage.value = t('patternAllowlist.saveSuccess')
    await loadEntriesOnly()
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : t('patternAllowlist.saveFailed')
  } finally {
    submitting.value = false
  }
}

async function onDelete(entry: PatternAllowlistView): Promise<void> {
  if (!selectedLotId.value) {
    return
  }
  submitting.value = true
  errorMessage.value = ''
  try {
    await deletePatternAllowlistEntry(selectedLotId.value, entry.id, locale.value)
    successMessage.value = t('patternAllowlist.deleteSuccess')
    await loadEntriesOnly()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('patternAllowlist.deleteFailed')
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
  void loadEntriesOnly()
}

function onPageSizeChange(): void {
  page.value = 0
  void loadEntriesOnly()
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
          <span>{{ t('page.search') }}</span>
          <input
            v-model="searchInput"
            type="search"
            :placeholder="t('patternAllowlist.searchPlaceholder')"
            @keyup.enter="onSearch"
          />
        </label>
        <div class="filter-actions">
          <button type="button" class="ghost" @click="onResetSearch">{{ t('spaces.reset') }}</button>
          <button type="button" class="primary" @click="onSearch">{{ t('spaces.query') }}</button>
        </div>
      </div>

      <div v-if="isAdmin" class="action-bar">
        <button type="button" class="primary" @click="openCreateForm">{{ t('patternAllowlist.create') }}</button>
      </div>

      <div class="table-card">
        <table v-if="entries.length > 0">
          <thead>
            <tr>
              <th>{{ t('patternAllowlist.colIndex') }}</th>
              <th>{{ t('patternAllowlist.colName') }}</th>
              <th>{{ t('patternAllowlist.colPattern') }}</th>
              <th>{{ t('patternAllowlist.colRemark') }}</th>
              <th>{{ t('page.colStatus') }}</th>
              <th>{{ t('page.colUpdated') }}</th>
              <th v-if="isAdmin" class="col-actions">{{ t('patternAllowlist.colActions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in entries" :key="item.id">
              <td>{{ pageStart + index }}</td>
              <td>{{ item.name }}</td>
              <td><code class="pattern">{{ item.pattern }}</code></td>
              <td>{{ item.remark || '—' }}</td>
              <td>
                <span class="pill" :class="item.enabled ? 'ok' : 'fail'">
                  {{ item.enabled ? t('patternAllowlist.statusActive') : t('patternAllowlist.statusDisabled') }}
                </span>
              </td>
              <td>{{ formatTime(item.updatedAt) }}</td>
              <td v-if="isAdmin" class="col-actions">
                <button type="button" class="link-btn" @click="openEditForm(item)">
                  {{ t('patternAllowlist.edit') }}
                </button>
                <button type="button" class="link-btn danger" @click="onDelete(item)">
                  {{ t('patternAllowlist.delete') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else-if="loading" class="empty">
          <p>{{ t('patternAllowlist.loading') }}</p>
        </div>
        <div v-else class="empty">
          <strong>{{ t('patternAllowlist.empty') }}</strong>
          <p>{{ isAdmin ? t('patternAllowlist.emptyHintAdmin') : t('patternAllowlist.emptyHint') }}</p>
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
        <h3>{{ isEditing ? t('patternAllowlist.editTitle') : t('patternAllowlist.createTitle') }}</h3>
        <p class="form-hint">{{ t('patternAllowlist.formHint') }}</p>
        <label>
          <span>{{ t('patternAllowlist.colName') }}</span>
          <input
            v-model="formName"
            type="text"
            autocomplete="off"
            :placeholder="t('patternAllowlist.namePlaceholder')"
          />
        </label>

        <div class="helper-box">
          <div class="helper-head">
            <strong>{{ t('patternAllowlist.helperTitle') }}</strong>
            <p>{{ t('patternAllowlist.helperHint') }}</p>
          </div>
          <fieldset class="helper-modes">
            <legend class="sr-only">{{ t('patternAllowlist.helperTitle') }}</legend>
            <label class="mode-option">
              <input v-model="helperMode" type="radio" value="startsWith" />
              <span>{{ t('patternAllowlist.helperModeStartsWith') }}</span>
            </label>
            <label class="mode-option">
              <input v-model="helperMode" type="radio" value="endsWith" />
              <span>{{ t('patternAllowlist.helperModeEndsWith') }}</span>
            </label>
            <label class="mode-option">
              <input v-model="helperMode" type="radio" value="contains" />
              <span>{{ t('patternAllowlist.helperModeContains') }}</span>
            </label>
          </fieldset>
          <div class="helper-row">
            <label class="helper-field grow">
              <span>{{ t('patternAllowlist.helperKeyword') }}</span>
              <input
                v-model="helperKeyword"
                type="text"
                autocomplete="off"
                :placeholder="t('patternAllowlist.helperKeywordPlaceholder')"
                @keyup.enter.prevent="applyHelperPattern"
              />
            </label>
            <button type="button" class="primary helper-apply" @click="applyHelperPattern">
              {{ t('patternAllowlist.helperApply') }}
            </button>
          </div>
          <p v-if="helperPreview" class="helper-preview">
            <span>{{ t('patternAllowlist.helperPreview') }}</span>
            <code class="pattern">{{ helperPreview }}</code>
          </p>
          <label class="helper-field">
            <span>{{ t('patternAllowlist.helperTestPlate') }}</span>
            <input
              v-model="helperTestPlate"
              type="text"
              autocomplete="off"
              :placeholder="t('patternAllowlist.helperTestPlaceholder')"
            />
          </label>
          <p v-if="helperTestResult === true" class="helper-test ok">
            {{ t('patternAllowlist.helperTestMatch') }}
          </p>
          <p v-else-if="helperTestResult === false" class="helper-test fail">
            {{ t('patternAllowlist.helperTestNoMatch') }}
          </p>
          <p v-if="helperError" class="form-error">{{ helperError }}</p>
        </div>

        <label>
          <span>{{ t('patternAllowlist.colPattern') }}</span>
          <input
            v-model="formPattern"
            type="text"
            autocomplete="off"
            :placeholder="t('patternAllowlist.patternPlaceholder')"
          />
        </label>
        <label>
          <span>{{ t('patternAllowlist.colRemark') }}</span>
          <input v-model="formRemark" type="text" autocomplete="off" />
        </label>
        <label class="checkbox">
          <input v-model="formEnabled" type="checkbox" />
          <span>{{ t('patternAllowlist.statusActive') }}</span>
        </label>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" @click="closeForm">{{ t('patternAllowlist.cancel') }}</button>
          <button type="submit" :disabled="submitting">
            {{ submitting ? t('patternAllowlist.saving') : t('patternAllowlist.save') }}
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

.pattern {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.88rem;
  background: #f2f4f3;
  padding: 0.15rem 0.4rem;
  border-radius: 4px;
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
  width: min(560px, 100%);
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

.modal h3 {
  margin: 0;
}

.form-hint {
  margin: -0.25rem 0 0;
  color: var(--muted);
  font-size: 0.88rem;
}

.helper-box {
  display: grid;
  gap: 0.65rem;
  padding: 0.85rem 0.9rem;
  border: 1px dashed var(--border);
  border-radius: 10px;
  background: #f7faf8;
}

.helper-head strong {
  display: block;
  font-size: 0.92rem;
}

.helper-head p {
  margin: 0.25rem 0 0;
  color: var(--muted);
  font-size: 0.82rem;
}

.helper-modes {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem 1rem;
  margin: 0;
  padding: 0;
  border: 0;
}

.mode-option {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.9rem;
}

.mode-option input {
  width: auto;
  padding: 0;
}

.helper-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: flex-end;
}

.helper-field {
  display: grid;
  gap: 0.35rem;
}

.helper-field.grow {
  flex: 1 1 12rem;
}

.helper-apply {
  flex: 0 0 auto;
  white-space: nowrap;
}

.helper-preview {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  margin: 0;
  font-size: 0.85rem;
  color: var(--muted);
}

.helper-test {
  margin: 0;
  font-size: 0.85rem;
  font-weight: 600;
}

.helper-test.ok {
  color: var(--ok);
}

.helper-test.fail {
  color: var(--danger);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
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
