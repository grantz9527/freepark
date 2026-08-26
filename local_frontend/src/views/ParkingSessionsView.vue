<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { ApiError, listLots, type LotView } from '@/api/client'
import PlateBadge from '@/components/PlateBadge.vue'
import { useSiteTime } from '@/composables/useSiteTime'
import {
  listParkingSessions,
  type ParkingSession,
  type ParkingSessionStatus,
} from '@/hardware/parkingSessions'

const LOT_STORAGE_KEY = 'freepark.parkingSessions.lotId'

const { t, locale } = useI18n()
const { formatTime } = useSiteTime()

const loading = ref(false)
const errorMessage = ref('')
const lots = ref<LotView[]>([])
const selectedLotId = ref('')
const searchInput = ref('')
const appliedKeyword = ref('')
const statusFilter = ref<ParkingSessionStatus | ''>('')
const sessions = ref<ParkingSession[]>([])
const previewImage = ref<string | null>(null)

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

function refreshSessions(): void {
  sessions.value = listParkingSessions({
    lotId: selectedLotId.value || undefined,
    keyword: appliedKeyword.value || undefined,
    status: statusFilter.value || undefined,
  })
}

async function reload(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadLots()
    refreshSessions()
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : t('parkingSessions.loadFailed')
  } finally {
    loading.value = false
  }
}

function onLotChange(): void {
  sessionStorage.setItem(LOT_STORAGE_KEY, selectedLotId.value)
  appliedKeyword.value = ''
  searchInput.value = ''
  statusFilter.value = ''
  refreshSessions()
}

function onSearch(): void {
  appliedKeyword.value = searchInput.value.trim()
  refreshSessions()
}

function onResetSearch(): void {
  searchInput.value = ''
  appliedKeyword.value = ''
  statusFilter.value = ''
  refreshSessions()
}

function statusLabel(status: ParkingSessionStatus): string {
  return status === 'OPEN' ? t('parkingSessions.statusOpen') : t('parkingSessions.statusClosed')
}

function durationText(session: ParkingSession): string {
  if (!session.exitTime) {
    return '—'
  }
  const start = Date.parse(session.entryTime)
  const end = Date.parse(session.exitTime)
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) {
    return '—'
  }
  const minutes = Math.round((end - start) / 60000)
  if (minutes < 60) {
    return t('parkingSessions.durationMinutes', { minutes })
  }
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return t('parkingSessions.durationHours', { hours, minutes: rest })
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
    <p class="banner planning">{{ t('parkingSessions.planningHint') }}</p>

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
            :placeholder="t('parkingSessions.searchPlaceholder')"
            @keyup.enter="onSearch"
          />
        </label>
        <label class="filter-field">
          <span>{{ t('parkingSessions.colStatus') }}</span>
          <select v-model="statusFilter" @change="refreshSessions">
            <option value="">{{ t('parkingSessions.statusAll') }}</option>
            <option value="OPEN">{{ t('parkingSessions.statusOpen') }}</option>
            <option value="CLOSED">{{ t('parkingSessions.statusClosed') }}</option>
          </select>
        </label>
        <div class="filter-actions">
          <button type="button" class="ghost" @click="onResetSearch">{{ t('spaces.reset') }}</button>
          <button type="button" class="primary" @click="onSearch">{{ t('spaces.query') }}</button>
        </div>
      </div>

      <div class="table-card">
        <table v-if="sessions.length > 0">
          <thead>
            <tr>
              <th>{{ t('parkingSessions.colIndex') }}</th>
              <th>{{ t('parkingSessions.colPlate') }}</th>
              <th>{{ t('parkingSessions.colEntryTime') }}</th>
              <th>{{ t('parkingSessions.colEntryLane') }}</th>
              <th>{{ t('parkingSessions.colEntryImage') }}</th>
              <th>{{ t('parkingSessions.colExitTime') }}</th>
              <th>{{ t('parkingSessions.colExitLane') }}</th>
              <th>{{ t('parkingSessions.colExitImage') }}</th>
              <th>{{ t('parkingSessions.colDuration') }}</th>
              <th>{{ t('parkingSessions.colStatus') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in sessions" :key="item.id">
              <td>{{ index + 1 }}</td>
              <td>
                <PlateBadge :plate-number="item.plateNumber" :plate-color="item.plateColor" />
              </td>
              <td>{{ formatTime(item.entryTime) }}</td>
              <td>{{ item.entryLaneName || '—' }}</td>
              <td>
                <button
                  v-if="item.entryImage"
                  type="button"
                  class="thumb-btn"
                  @click="openPreview(item.entryImage)"
                >
                  <img :src="item.entryImage" :alt="item.plateNumber" class="thumb" />
                </button>
                <span v-else class="muted">{{ t('parkingSessions.noImage') }}</span>
              </td>
              <td>{{ item.exitTime ? formatTime(item.exitTime) : '—' }}</td>
              <td>{{ item.exitLaneName || '—' }}</td>
              <td>
                <button
                  v-if="item.exitImage"
                  type="button"
                  class="thumb-btn"
                  @click="openPreview(item.exitImage)"
                >
                  <img :src="item.exitImage" :alt="item.plateNumber" class="thumb" />
                </button>
                <span v-else class="muted">{{ t('parkingSessions.noImage') }}</span>
              </td>
              <td>{{ durationText(item) }}</td>
              <td>
                <span class="pill" :class="item.status === 'OPEN' ? 'ok' : 'closed'">
                  {{ statusLabel(item.status) }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else-if="loading" class="empty">
          <p>{{ t('parkingSessions.loading') }}</p>
        </div>
        <div v-else class="empty">
          <strong>{{ t('parkingSessions.empty') }}</strong>
          <p>{{ t('parkingSessions.emptyHint') }}</p>
        </div>
      </div>
    </template>

    <div v-if="previewImage" class="modal-backdrop" @click="closePreview">
      <div class="preview-modal" @click.stop>
        <img :src="previewImage" alt="" class="preview-img" />
        <div class="actions">
          <button type="button" class="ghost" @click="closePreview">
            {{ t('parkingSessions.closePreview') }}
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
  overflow: auto;
  box-shadow: var(--shadow);
}

.empty-card {
  padding: 3rem 1.5rem;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 960px;
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

.pill.ok {
  color: var(--ok);
  background: #e8f5ef;
}

.pill.closed {
  color: var(--muted);
  background: #eef1f0;
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
