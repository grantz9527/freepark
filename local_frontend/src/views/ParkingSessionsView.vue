<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { ApiError, listLots, type LotView } from '@/api/client'
import PlateBadge from '@/components/PlateBadge.vue'
import { useSiteTime } from '@/composables/useSiteTime'
import {
  listParkingSessions,
  voidParkingSession,
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
const voidTarget = ref<ParkingSession | null>(null)
const voidSubmitting = ref(false)

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

async function refreshSessions(): Promise<void> {
  errorMessage.value = ''
  try {
    sessions.value = await listParkingSessions(locale.value, {
      lotId: selectedLotId.value || undefined,
      keyword: appliedKeyword.value || undefined,
      status: statusFilter.value || undefined,
    })
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : t('parkingSessions.loadFailed')
  }
}

async function reload(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadLots()
    await refreshSessions()
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : t('parkingSessions.loadFailed')
  } finally {
    loading.value = false
  }
}

async function onLotChange(): Promise<void> {
  sessionStorage.setItem(LOT_STORAGE_KEY, selectedLotId.value)
  appliedKeyword.value = ''
  searchInput.value = ''
  statusFilter.value = ''
  await refreshSessions()
}

async function onSearch(): Promise<void> {
  appliedKeyword.value = searchInput.value.trim()
  await refreshSessions()
}

async function onResetSearch(): Promise<void> {
  searchInput.value = ''
  appliedKeyword.value = ''
  statusFilter.value = ''
  await refreshSessions()
}

function statusLabel(status: ParkingSessionStatus): string {
  if (status === 'OPEN') {
    return t('parkingSessions.statusOpen')
  }
  if (status === 'CLOSED') {
    return t('parkingSessions.statusClosed')
  }
  return t('parkingSessions.statusVoided')
}

function openVoidModal(session: ParkingSession): void {
  voidTarget.value = session
}

function closeVoidModal(): void {
  if (voidSubmitting.value) {
    return
  }
  voidTarget.value = null
}

async function confirmVoid(): Promise<void> {
  const session = voidTarget.value
  if (!session || voidSubmitting.value) {
    return
  }
  voidSubmitting.value = true
  errorMessage.value = ''
  try {
    // 后端作废流水并联动标记关联识别记录为作废
    await voidParkingSession(session.id, locale.value)
    await refreshSessions()
    voidTarget.value = null
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : t('parkingSessions.loadFailed')
  } finally {
    voidSubmitting.value = false
  }
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

function onVoidKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    closeVoidModal()
  }
}

watch(voidTarget, (target) => {
  if (target) {
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onVoidKeydown)
  } else {
    document.body.style.overflow = ''
    window.removeEventListener('keydown', onVoidKeydown)
  }
})

onMounted(reload)
onUnmounted(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onVoidKeydown)
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
            <option value="VOIDED">{{ t('parkingSessions.statusVoided') }}</option>
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
              <th>{{ t('parkingSessions.colActions') }}</th>
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
                <span
                  class="pill"
                  :class="item.status === 'OPEN' ? 'ok' : item.status === 'CLOSED' ? 'closed' : 'voided'"
                >
                  {{ statusLabel(item.status) }}
                </span>
              </td>
              <td>
                <button
                  v-if="item.status !== 'VOIDED'"
                  type="button"
                  class="link-btn"
                  @click="openVoidModal(item)"
                >
                  {{ t('parkingSessions.void') }}
                </button>
                <span v-else class="muted">—</span>
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

    <Transition name="void-fade">
      <div v-if="voidTarget" class="modal-backdrop void-backdrop" @click.self="closeVoidModal">
        <div
          class="void-dialog"
          role="dialog"
          aria-modal="true"
          :aria-label="t('parkingSessions.voidTitle')"
          @click.stop
        >
          <div class="void-dialog-accent" aria-hidden="true" />

          <button
            type="button"
            class="void-dialog-close"
            :aria-label="t('parkingSessions.cancel')"
            :disabled="voidSubmitting"
            @click="closeVoidModal"
          >
            ×
          </button>

          <div class="void-dialog-header">
            <div class="void-dialog-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="9" />
                <path d="M8 12h8" stroke-linecap="round" />
              </svg>
            </div>
            <div>
              <h3>{{ t('parkingSessions.voidTitle') }}</h3>
              <p>{{ t('parkingSessions.voidHint') }}</p>
            </div>
          </div>

          <div class="void-plate-row">
            <span class="void-lot-name">{{ voidTarget.lotName }}</span>
            <PlateBadge :plate-number="voidTarget.plateNumber" :plate-color="voidTarget.plateColor" />
            <div class="void-plate-meta">
              <span
                class="pill"
                :class="voidTarget.status === 'OPEN' ? 'ok' : voidTarget.status === 'CLOSED' ? 'closed' : 'voided'"
              >
                {{ statusLabel(voidTarget.status) }}
              </span>
              <span class="void-duration-chip">{{ durationText(voidTarget) }}</span>
            </div>
          </div>

          <div class="void-timeline">
            <div class="void-leg entry">
              <span class="void-leg-tag">{{ t('parkingSessions.voidEntry') }}</span>
              <strong>{{ formatTime(voidTarget.entryTime) }}</strong>
              <span class="void-leg-lane">{{ voidTarget.entryLaneName || '—' }}</span>
              <button
                v-if="voidTarget.entryImage"
                type="button"
                class="void-thumb-btn"
                @click="openPreview(voidTarget.entryImage)"
              >
                <img
                  :src="voidTarget.entryImage"
                  :alt="voidTarget.plateNumber"
                  class="void-leg-thumb"
                />
              </button>
              <span v-else class="void-no-image">{{ t('parkingSessions.noImage') }}</span>
            </div>

            <div class="void-timeline-bridge" aria-hidden="true">
              <span class="void-bridge-line" />
              <span class="void-bridge-arrow">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
              <span class="void-bridge-line" />
            </div>

            <div class="void-leg exit" :class="{ pending: !voidTarget.exitTime }">
              <span class="void-leg-tag">{{ t('parkingSessions.voidExit') }}</span>
              <strong>{{ voidTarget.exitTime ? formatTime(voidTarget.exitTime) : t('parkingSessions.voidNotExited') }}</strong>
              <span class="void-leg-lane">{{ voidTarget.exitLaneName || '—' }}</span>
              <button
                v-if="voidTarget.exitImage"
                type="button"
                class="void-thumb-btn"
                @click="openPreview(voidTarget.exitImage)"
              >
                <img
                  :src="voidTarget.exitImage"
                  :alt="voidTarget.plateNumber"
                  class="void-leg-thumb"
                />
              </button>
              <span v-else class="void-no-image">{{ t('parkingSessions.noImage') }}</span>
            </div>
          </div>

          <div class="void-alert">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M12 9v4" stroke-linecap="round" />
              <circle cx="12" cy="16" r="0.5" fill="currentColor" stroke="none" />
              <path
                d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z"
                stroke-linejoin="round"
              />
            </svg>
            <p>{{ t('parkingSessions.voidConfirm') }}</p>
          </div>

          <div class="void-dialog-actions">
            <button type="button" class="ghost" :disabled="voidSubmitting" @click="closeVoidModal">
              {{ t('parkingSessions.cancel') }}
            </button>
            <button type="button" class="danger-btn" :disabled="voidSubmitting" @click="confirmVoid">
              <span v-if="voidSubmitting" class="void-spinner" aria-hidden="true" />
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <circle cx="12" cy="12" r="9" />
                <path d="M8 12h8" stroke-linecap="round" />
              </svg>
              {{ voidSubmitting ? t('parkingSessions.voidSubmitting') : t('parkingSessions.voidSubmit') }}
            </button>
          </div>
        </div>
      </div>
    </Transition>

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

.pill.voided {
  color: var(--danger);
  background: #fdecec;
}

.link-btn {
  border: 0;
  padding: 0;
  background: none;
  color: var(--danger);
  font-weight: 600;
  cursor: pointer;
}

.link-btn:hover {
  text-decoration: underline;
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

.void-backdrop {
  backdrop-filter: blur(6px);
}

.void-fade-enter-active,
.void-fade-leave-active {
  transition: opacity 0.2s ease;
}

.void-fade-enter-active .void-dialog,
.void-fade-leave-active .void-dialog {
  transition:
    transform 0.24s cubic-bezier(0.22, 1, 0.36, 1),
    opacity 0.24s ease;
}

.void-fade-enter-from,
.void-fade-leave-to {
  opacity: 0;
}

.void-fade-enter-from .void-dialog,
.void-fade-leave-to .void-dialog {
  transform: translateY(12px) scale(0.97);
  opacity: 0;
}

.void-dialog {
  position: relative;
  width: min(540px, 100%);
  display: grid;
  gap: 1rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 18px;
  padding: 0 1.35rem 1.25rem;
  box-shadow:
    0 24px 64px rgba(15, 23, 20, 0.18),
    0 0 0 1px rgba(255, 255, 255, 0.65) inset;
  overflow: hidden;
}

.void-dialog-accent {
  position: absolute;
  inset: 0 0 auto;
  height: 4px;
  background: linear-gradient(90deg, #f97066, var(--danger), #93370d);
}

.void-dialog-close {
  position: absolute;
  top: 0.85rem;
  inset-inline-end: 0.85rem;
  z-index: 1;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 1.35rem;
  line-height: 1;
  cursor: pointer;
  padding: 0.2rem 0.45rem;
  border-radius: 8px;
}

.void-dialog-close:hover:not(:disabled) {
  color: var(--text);
  background: #f2f4f3;
}

.void-dialog-close:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.void-dialog-header {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.85rem;
  align-items: start;
  padding-top: 1.35rem;
  padding-inline-end: 1.75rem;
}

.void-dialog-icon {
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: var(--danger);
  background: linear-gradient(145deg, #fff5f5, #fdecec);
  border: 1px solid #fecdca;
  box-shadow: 0 2px 8px rgba(180, 35, 24, 0.08);
}

.void-dialog-icon svg {
  width: 1.35rem;
  height: 1.35rem;
}

.void-dialog-header h3 {
  margin: 0 0 0.2rem;
  font-size: 1.08rem;
  letter-spacing: -0.01em;
}

.void-dialog-header p {
  margin: 0;
  color: var(--muted);
  font-size: 0.86rem;
  line-height: 1.45;
}

.void-plate-row {
  display: grid;
  justify-items: center;
  gap: 0.55rem;
  padding: 0.85rem 1rem;
  border-radius: 12px;
  background: linear-gradient(180deg, #f7faf8 0%, #fff 100%);
  border: 1px solid var(--border);
}

.void-lot-name {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--muted);
  letter-spacing: 0.02em;
}

.void-plate-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
}

.void-duration-chip {
  font-size: 0.84rem;
  color: var(--muted);
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--border);
}

.void-timeline {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 0.65rem;
  align-items: stretch;
}

.void-leg {
  display: grid;
  gap: 0.35rem;
  padding: 0.75rem 0.8rem;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: #fff;
  min-width: 0;
}

.void-leg.entry {
  border-color: #b7e4c7;
  background: linear-gradient(180deg, #f6fffa 0%, #fff 100%);
}

.void-leg.exit {
  border-color: #c7d7fe;
  background: linear-gradient(180deg, #f8faff 0%, #fff 100%);
}

.void-leg.exit.pending strong {
  color: var(--muted);
  font-weight: 600;
  font-size: 0.88rem;
}

.void-leg-tag {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--muted);
}

.void-leg.entry .void-leg-tag {
  color: var(--ok);
}

.void-leg.exit .void-leg-tag {
  color: #444ce7;
}

.void-leg strong {
  font-size: 0.92rem;
  line-height: 1.35;
}

.void-leg-lane {
  font-size: 0.82rem;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.void-thumb-btn {
  border: 0;
  padding: 0;
  background: none;
  cursor: pointer;
  justify-self: start;
}

.void-leg-thumb {
  width: 100%;
  max-width: 120px;
  height: 56px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--border);
  display: block;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.void-thumb-btn:hover .void-leg-thumb {
  transform: scale(1.02);
  box-shadow: 0 4px 12px rgba(15, 23, 20, 0.12);
}

.void-no-image {
  font-size: 0.78rem;
  color: var(--muted);
}

.void-timeline-bridge {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  place-items: center;
  align-content: center;
  gap: 0.35rem;
  color: #98a2b3;
  padding-top: 1.25rem;
  min-width: 2rem;
}

.void-bridge-line {
  display: block;
  width: 100%;
  height: 2px;
  background: linear-gradient(90deg, #b7e4c7, #c7d7fe);
  border-radius: 1px;
}

.void-bridge-arrow {
  width: 1.25rem;
  height: 1.25rem;
}

.void-alert {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.65rem;
  align-items: start;
  margin: 0;
  padding: 0.75rem 0.85rem;
  border-radius: 10px;
  font-size: 0.86rem;
  line-height: 1.5;
  color: #8a4b00;
  background: #fffaeb;
  border: 1px solid #fedf89;
}

.void-alert svg {
  width: 1.1rem;
  height: 1.1rem;
  margin-top: 0.1rem;
  flex-shrink: 0;
}

.void-alert p {
  margin: 0;
}

.void-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.55rem;
  padding-top: 0.15rem;
}

.danger-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  border: 0;
  border-radius: 10px;
  padding: 0.55rem 1rem;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(180deg, #d92d20 0%, var(--danger) 100%);
  box-shadow: 0 2px 8px rgba(180, 35, 24, 0.22);
  cursor: pointer;
}

.danger-btn svg {
  width: 1rem;
  height: 1rem;
}

.danger-btn:disabled,
.void-dialog-actions .ghost:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.danger-btn:hover:not(:disabled) {
  filter: brightness(0.96);
}

.void-spinner {
  width: 0.9rem;
  height: 0.9rem;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 999px;
  animation: void-spin 0.7s linear infinite;
}

@keyframes void-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 520px) {
  .void-timeline {
    grid-template-columns: 1fr;
  }

  .void-timeline-bridge {
    padding: 0;
    grid-auto-flow: column;
    grid-template-columns: 1fr auto 1fr;
  }

  .void-bridge-line {
    width: 100%;
    height: 1px;
  }

  .void-bridge-arrow {
    transform: rotate(90deg);
  }
}
</style>
