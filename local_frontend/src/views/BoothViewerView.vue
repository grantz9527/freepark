<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  listBooths,
  type BoothLaneView,
  type BoothView,
} from '@/api/client'
import PlateBadge from '@/components/PlateBadge.vue'
import { useSiteTime } from '@/composables/useSiteTime'
import { listBarrierDevices, type BarrierDevice } from '@/hardware/barrierDevices'
import { listRecognitionRecords, type RecognitionRecord } from '@/hardware/recognitionRecords'

const LOT_STORAGE_KEY = 'freepark.booths.lotId'

const { t, locale } = useI18n()
const { formatTime } = useSiteTime()
const route = useRoute()

const loading = ref(false)
const errorMessage = ref('')
const booth = ref<BoothView | null>(null)
const logs = ref<string[]>([])

const boothId = computed(() => String(route.params.boothId ?? ''))
const lotId = computed(() => {
  const queryLot = route.query.lot
  if (typeof queryLot === 'string' && queryLot) {
    return queryLot
  }
  return sessionStorage.getItem(LOT_STORAGE_KEY) ?? ''
})

async function fetchBooth(): Promise<BoothView | null> {
  const size = 50
  let page = 0
  while (true) {
    const result = await listBooths(lotId.value, locale.value, { page, size })
    const found = result.data.items.find((item) => item.id === boothId.value)
    if (found) {
      return found
    }
    const lastPage = Math.max(1, Math.ceil(result.data.total / result.data.size))
    if (page + 1 >= lastPage) {
      return null
    }
    page += 1
  }
}

async function load(): Promise<void> {
  if (!lotId.value) {
    errorMessage.value = t('booths.viewNotFound')
    return
  }
  loading.value = true
  errorMessage.value = ''
  logs.value = []
  try {
    booth.value = await fetchBooth()
    if (!booth.value) {
      errorMessage.value = t('booths.viewNotFound')
    } else {
      await loadLatestRecognitions()
    }
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('booths.loadFailed')
  } finally {
    loading.value = false
  }
}

const laneRecognitions = ref<Record<string, RecognitionRecord | null>>({})

async function loadLatestRecognitions(): Promise<void> {
  const lanes = booth.value?.lanes ?? []
  const map: Record<string, RecognitionRecord | null> = {}
  if (lanes.length === 0) {
    laneRecognitions.value = map
    return
  }
  try {
    const records = await listRecognitionRecords(locale.value, { lotId: lotId.value })
    for (const lane of lanes) {
      const latest =
        records
          .filter((record) => record.laneId === lane.id && !record.voided)
          .sort((a, b) => b.eventTime.localeCompare(a.eventTime))[0] ?? null
      map[lane.id] = latest
    }
  } catch {
    // 识别记录加载失败不影响岗亭主界面展示
  }
  laneRecognitions.value = map
}

function laneTypeLabel(laneType: string): string {
  if (laneType === 'ENTRANCE') {
    return t('booths.laneEntrance')
  }
  if (laneType === 'EXIT') {
    return t('booths.laneExit')
  }
  return t('booths.laneBidirectional')
}

function gateDevicesFor(laneId: string): BarrierDevice[] {
  return listBarrierDevices().filter(
    (device) => device.laneId === laneId && device.linkStatus === 'CONNECTED',
  )
}

function onGateCommand(lane: BoothLaneView, command: 'open' | 'close'): void {
  const devices = gateDevicesFor(lane.id)
  if (devices.length === 0) {
    logs.value.push(
      command === 'open' ? t('booths.gateNoDeviceOpen') : t('booths.gateNoDeviceClose'),
    )
    return
  }
  logs.value.push(
    t('booths.gateLinked', {
      lane: lane.name,
      command: t(command === 'open' ? 'booths.gateOpen' : 'booths.gateClose'),
      devices: devices.map((device) => device.name).join(' · '),
    }),
  )
}

onMounted(load)
</script>

<template>
  <section class="page">
    <p v-if="errorMessage" class="banner error">{{ errorMessage }}</p>

    <div v-if="loading && !booth" class="table-card">
      <div class="empty">
        <p>{{ t('booths.loading') }}</p>
      </div>
    </div>

    <template v-if="booth">
      <div class="head-bar">
        <RouterLink :to="{ name: 'booths' }" class="back-link">{{ t('booths.viewBack') }}</RouterLink>
        <div class="head-info">
          <h2>{{ booth.name }}</h2>
          <p class="head-sub">
            {{ booth.lotName }}
            <template v-if="booth.code"> · {{ booth.code }}</template>
            <template v-if="booth.location"> · {{ booth.location }}</template>
          </p>
        </div>
        <span class="pill" :class="booth.enabled ? 'ok' : 'fail'">
          {{ booth.enabled ? t('internalVehicles.statusActive') : t('internalVehicles.statusDisabled') }}
        </span>
      </div>

      <p v-if="booth.lanes.length === 0" class="empty-card">
        {{ t('booths.noLanes') }}
      </p>

      <div v-else class="lane-grid">
        <div v-for="lane in booth.lanes" :key="lane.id" class="lane-card">
          <div class="lane-card-head">
            <strong>{{ lane.name }}</strong>
            <span class="pill">{{ laneTypeLabel(lane.laneType) }}</span>
            <span class="pill">{{ lane.code || '—' }}</span>
          </div>

          <div class="recognition">
            <template v-if="laneRecognitions[lane.id]">
              <div class="rec-main">
                <PlateBadge
                  show-color-label
                  :plate-number="laneRecognitions[lane.id]!.plateNumber"
                  :plate-color="laneRecognitions[lane.id]!.plateColor"
                />
                <span class="pill">{{
                  laneRecognitions[lane.id]!.direction === 'ENTRANCE'
                    ? t('booths.directionEntrance')
                    : t('booths.directionExit')
                }}</span>
                <span v-if="laneRecognitions[lane.id]!.abnormal" class="pill fail">
                  {{ t('booths.colAbnormal') }}
                </span>
              </div>
              <p class="rec-time">{{ formatTime(laneRecognitions[lane.id]!.eventTime) }}</p>
              <div class="rec-media">
                <img
                  v-if="laneRecognitions[lane.id]!.eventImage"
                  :src="laneRecognitions[lane.id]!.eventImage ?? undefined"
                  class="rec-img"
                  alt="recognition"
                />
                <div v-else class="rec-placeholder">
                  {{ t('booths.noImage') }}
                </div>
              </div>
            </template>
            <p v-else class="rec-empty">{{ t('booths.noRecognition') }}</p>
          </div>

          <div class="lane-actions">
            <button type="button" class="primary gate-open" @click="onGateCommand(lane, 'open')">
              {{ t('booths.gateOpen') }}
            </button>
            <button type="button" class="primary gate-close" @click="onGateCommand(lane, 'close')">
              {{ t('booths.gateClose') }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="logs.length > 0" class="gate-logs">
        <strong>{{ t('booths.gateLogTitle') }}</strong>
        <ul>
          <li v-for="(log, index) in logs" :key="index">{{ log }}</li>
        </ul>
      </div>
    </template>
  </section>
</template>

<style scoped>
.page {
  display: grid;
  gap: 0.9rem;
}

.head-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.9rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 0.9rem 1rem;
  box-shadow: var(--shadow);
}

.back-link {
  display: inline-flex;
  align-items: center;
  color: var(--accent);
  font-weight: 600;
  text-decoration: none;
  gap: 0.3rem;
  white-space: nowrap;
}

.head-info {
  flex: 1;
  min-width: 0;
}

.head-info h2 {
  margin: 0;
  font-size: 1.15rem;
}

.head-sub {
  margin: 0.2rem 0 0;
  color: var(--muted);
  font-size: 0.88rem;
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

.empty-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 2.5rem 1.5rem;
  text-align: center;
  color: var(--muted);
  box-shadow: var(--shadow);
  margin: 0;
}

.table-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow);
}

.empty {
  padding: 2.5rem 1.5rem;
  text-align: center;
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

.lane-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 0.9rem;
  align-items: start;
}

.lane-card {
  display: grid;
  gap: 0.6rem;
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 0.9rem;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.lane-card-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.lane-card-head strong {
  margin-right: auto;
}

.recognition {
  border: 1px dashed var(--border);
  border-radius: 8px;
  padding: 0.7rem;
  background: #fff;
}

.rec-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.rec-time {
  margin: 0.4rem 0 0;
  font-size: 0.82rem;
  color: var(--muted);
}

.rec-media {
  margin-top: 0.5rem;
}

.rec-img {
  display: block;
  width: 100%;
  max-height: 160px;
  object-fit: cover;
  border-radius: 6px;
}

.rec-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 120px;
  border: 1px dashed var(--border);
  border-radius: 6px;
  background: #f2f4f3;
  color: var(--muted);
  font-size: 0.85rem;
}

.rec-empty {
  margin: 0;
  color: var(--muted);
  font-size: 0.85rem;
  text-align: center;
  padding: 0.6rem 0;
}

.lane-actions {
  display: flex;
  gap: 0.5rem;
}

.lane-actions .primary {
  border: 0;
  border-radius: 8px;
  padding: 0.5rem 0.85rem;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
}

.gate-open {
  background: var(--ok);
}

.gate-close {
  background: var(--danger);
}

.gate-logs {
  display: grid;
  gap: 0.4rem;
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 0.85rem;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.gate-logs ul {
  margin: 0;
  padding-left: 1.1rem;
  font-size: 0.85rem;
  color: var(--muted);
}

.gate-logs li + li {
  margin-top: 0.25rem;
}
</style>
