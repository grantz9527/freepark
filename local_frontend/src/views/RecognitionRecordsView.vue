<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { ApiError, listLots, type LotView } from '@/api/client'
import PlateBadge from '@/components/PlateBadge.vue'
import { useSiteTime } from '@/composables/useSiteTime'
import {
  listRecognitionRecords,
  type RecognitionEventType,
  type RecognitionRecord,
} from '@/hardware/recognitionRecords'

const LOT_STORAGE_KEY = 'freepark.recognitionRecords.lotId'

const { t, locale } = useI18n()
const { formatTime } = useSiteTime()

const loading = ref(false)
const errorMessage = ref('')
const lots = ref<LotView[]>([])
const selectedLotId = ref('')
const searchInput = ref('')
const appliedKeyword = ref('')
const eventTypeFilter = ref<RecognitionEventType | ''>('')
const abnormalOnly = ref(false)
const records = ref<RecognitionRecord[]>([])
const previewImage = ref<string | null>(null)

const filteredRecords = computed(() =>
  listRecognitionRecords({
    lotId: selectedLotId.value || undefined,
    keyword: appliedKeyword.value || undefined,
    eventType: eventTypeFilter.value || undefined,
    abnormalOnly: abnormalOnly.value || undefined,
  }),
)

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

function refreshRecords(): void {
  records.value = filteredRecords.value
}

async function reload(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadLots()
    refreshRecords()
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : t('recognitionRecords.loadFailed')
  } finally {
    loading.value = false
  }
}

function onLotChange(): void {
  sessionStorage.setItem(LOT_STORAGE_KEY, selectedLotId.value)
  appliedKeyword.value = ''
  searchInput.value = ''
  eventTypeFilter.value = ''
  abnormalOnly.value = false
  refreshRecords()
}

function onSearch(): void {
  appliedKeyword.value = searchInput.value.trim()
  refreshRecords()
}

function onResetSearch(): void {
  searchInput.value = ''
  appliedKeyword.value = ''
  eventTypeFilter.value = ''
  abnormalOnly.value = false
  refreshRecords()
}

function eventTypeLabel(type: RecognitionEventType): string {
  return type === 'MANUAL'
    ? t('recognitionRecords.eventTypeManual')
    : t('recognitionRecords.eventTypeDevice')
}

function directionLabel(direction: RecognitionRecord['direction']): string {
  if (direction === 'ENTRANCE') {
    return t('recognitionRecords.directionEntrance')
  }
  if (direction === 'EXIT') {
    return t('recognitionRecords.directionExit')
  }
  return '—'
}

function abnormalLabel(item: RecognitionRecord): string {
  if (!item.abnormal) {
    return t('recognitionRecords.abnormalNo')
  }
  if (item.abnormalReason === 'exit_unmatched') {
    return t('recognitionRecords.abnormalExitUnmatched')
  }
  if (item.abnormalReason === 'not_internal_vehicle') {
    return t('recognitionRecords.abnormalNotInternal')
  }
  return t('recognitionRecords.abnormalYes')
}

function openPreview(url: string | null): void {
  if (!url) {
    return
  }
  previewImage.value = url
}

function closePreview(): void {
  previewImage.value = null
}

onMounted(reload)
</script>

<template>
  <section class="page">
    <p class="banner planning">{{ t('recognitionRecords.planningHint') }}</p>

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
            :placeholder="t('recognitionRecords.searchPlaceholder')"
            @keyup.enter="onSearch"
          />
        </label>
        <label class="filter-field">
          <span>{{ t('recognitionRecords.colEventType') }}</span>
          <select v-model="eventTypeFilter" @change="refreshRecords">
            <option value="">{{ t('recognitionRecords.eventTypeAll') }}</option>
            <option value="DEVICE">{{ t('recognitionRecords.eventTypeDevice') }}</option>
            <option value="MANUAL">{{ t('recognitionRecords.eventTypeManual') }}</option>
          </select>
        </label>
        <label class="checkbox-inline">
          <input v-model="abnormalOnly" type="checkbox" @change="refreshRecords" />
          <span>{{ t('recognitionRecords.filterAbnormal') }}</span>
        </label>
        <div class="filter-actions">
          <button type="button" class="ghost" @click="onResetSearch">{{ t('spaces.reset') }}</button>
          <button type="button" class="primary" @click="onSearch">{{ t('spaces.query') }}</button>
        </div>
      </div>

      <div class="table-card">
        <table v-if="records.length > 0">
          <thead>
            <tr>
              <th>{{ t('recognitionRecords.colIndex') }}</th>
              <th>{{ t('recognitionRecords.colPlate') }}</th>
              <th>{{ t('recognitionRecords.colEventTime') }}</th>
              <th>{{ t('recognitionRecords.colEventImage') }}</th>
              <th>{{ t('recognitionRecords.colEventType') }}</th>
              <th>{{ t('recognitionRecords.colAbnormal') }}</th>
              <th>{{ t('recognitionRecords.colDirection') }}</th>
              <th>{{ t('recognitionRecords.colLane') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in records" :key="item.id">
              <td>{{ index + 1 }}</td>
              <td>
                <PlateBadge :plate-number="item.plateNumber" :plate-color="item.plateColor" />
              </td>
              <td>{{ formatTime(item.eventTime) }}</td>
              <td>
                <button
                  v-if="item.eventImage"
                  type="button"
                  class="thumb-btn"
                  @click="openPreview(item.eventImage)"
                >
                  <img :src="item.eventImage" :alt="item.plateNumber" class="thumb" />
                </button>
                <span v-else class="muted">{{ t('recognitionRecords.noImage') }}</span>
              </td>
              <td>
                <span class="pill" :class="item.eventType === 'DEVICE' ? 'device' : 'manual'">
                  {{ eventTypeLabel(item.eventType) }}
                </span>
              </td>
              <td>
                <span class="pill" :class="item.abnormal ? 'fail' : 'ok'">
                  {{ abnormalLabel(item) }}
                </span>
              </td>
              <td>{{ directionLabel(item.direction) }}</td>
              <td>{{ item.laneName || '—' }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else-if="loading" class="empty">
          <p>{{ t('recognitionRecords.loading') }}</p>
        </div>
        <div v-else class="empty">
          <strong>{{ t('recognitionRecords.empty') }}</strong>
          <p>{{ t('recognitionRecords.emptyHint') }}</p>
        </div>
      </div>
    </template>

    <div v-if="previewImage" class="modal-backdrop" @click="closePreview">
      <div class="preview-modal" @click.stop>
        <img :src="previewImage" alt="" class="preview-img" />
        <div class="actions">
          <button type="button" class="ghost" @click="closePreview">
            {{ t('recognitionRecords.closePreview') }}
          </button>
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
  min-width: 12rem;
}

.filter-field input,
.filter-field select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.5rem 0.65rem;
  background: #fff;
}

.filter-actions {
  display: flex;
  gap: 0.5rem;
}

.checkbox-inline {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-height: 2.25rem;
  font-size: 0.9rem;
}

.checkbox-inline input {
  width: auto;
}

.primary,
.filter-actions .primary {
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
  vertical-align: middle;
}

th {
  color: var(--muted);
  font-size: 0.8rem;
  font-weight: 600;
  background: #f7faf8;
}

.thumb-btn {
  border: 0;
  padding: 0;
  background: none;
  cursor: pointer;
}

.thumb {
  width: 72px;
  height: 40px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--border);
  display: block;
}

.muted {
  color: var(--muted);
}

.pill {
  border-radius: 999px;
  padding: 0.15rem 0.6rem;
  font-size: 0.78rem;
  background: #f2f4f3;
}

.pill.device {
  color: var(--accent);
  background: #e8f0ec;
}

.pill.manual {
  color: #7a5c00;
  background: #fff4d6;
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

.banner {
  margin: 0;
  padding: 0.65rem 0.9rem;
  border-radius: 8px;
}

.banner.error {
  color: var(--danger);
  background: #fdecec;
}

.banner.planning {
  color: #7a5c00;
  background: #fff7e6;
  border: 1px solid #f0d9a8;
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

.preview-modal {
  width: min(560px, 100%);
  display: grid;
  gap: 0.75rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1rem;
  box-shadow: var(--shadow);
}

.preview-img {
  width: 100%;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: #111;
}

.actions {
  display: flex;
  justify-content: flex-end;
}
</style>
