<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  enqueueDeviceCommand,
  listBarriers,
  listBooths,
  listFrigateCamerasApi,
  type BarrierView,
  type BoothLaneView,
  type BoothView,
  type FrigateCameraView,
} from '@/api/client'
import LaneLivePlayer from '@/components/LaneLivePlayer.vue'
import PlateBadge from '@/components/PlateBadge.vue'
import { useSiteTime } from '@/composables/useSiteTime'
import { listRecognitionRecords, type RecognitionRecord } from '@/hardware/recognitionRecords'

const LOT_STORAGE_KEY = 'freepark.booths.lotId'

const { t, locale } = useI18n()
const { formatTime } = useSiteTime()
const route = useRoute()

const loading = ref(false)
const refreshing = ref(false)
const errorMessage = ref('')
const booth = ref<BoothView | null>(null)
const logs = ref<string[]>([])
const monitorMode = ref(false)
const frigateCameras = ref<FrigateCameraView[]>([])
const laneMonitorPick = ref<Record<string, string>>({})

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
      await Promise.all([loadLatestRecognitions(), loadLaneDevices(), loadFrigateCameras()])
    }
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('booths.loadFailed')
  } finally {
    loading.value = false
  }
}

/** 每通道后端真实绑定的一体机档案（parking_barrier）。 */
const laneDevices = ref<Record<string, BarrierView[]>>({})

async function loadLaneDevices(): Promise<void> {
  const lanes = booth.value?.lanes ?? []
  const map: Record<string, BarrierView[]> = {}
  for (const lane of lanes) {
    try {
      const result = await listBarriers(lane.id, locale.value)
      map[lane.id] = result.data
    } catch {
      map[lane.id] = []
    }
  }
  laneDevices.value = map
}

/** 该通道可下发的一体机：后端真实绑定且启用。 */
function gateDevicesFor(laneId: string): BarrierView[] {
  return (laneDevices.value[laneId] ?? []).filter((device) => device.enabled)
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

async function loadFrigateCameras(): Promise<void> {
  try {
    const result = await listFrigateCamerasApi(locale.value)
    frigateCameras.value = result.data ?? []
  } catch {
    frigateCameras.value = []
  }
}

type LaneMonitorSource = { key: string; label: string; url: string }

function monitorSourcesFor(laneId: string): LaneMonitorSource[] {
  const cameras = frigateCameras.value.filter(
    (camera) => camera.laneId === laneId && camera.enabled && camera.cameraName.trim(),
  )
  const devices = (laneDevices.value[laneId] ?? []).filter(
    (device) => device.enabled && device.streamUrl?.trim(),
  )
  const sources: LaneMonitorSource[] = [
    ...cameras.map((camera) => ({
      key: `frigate:${camera.id}`,
      label: t('booths.monitorSourceFrigate', { name: camera.name || camera.cameraName }),
      url: camera.cameraName.trim(),
    })),
    ...devices.map((device) => ({
      key: `aio:${device.id}`,
      label: t('booths.monitorSourceAio', { name: device.name || device.code }),
      url: device.streamUrl!.trim(),
    })),
  ]
  return sources
}

function monitorUrlFor(laneId: string): string | null {
  const sources = monitorSourcesFor(laneId)
  if (sources.length === 0) {
    return null
  }
  const picked = laneMonitorPick.value[laneId]
  return sources.find((item) => item.key === picked)?.url ?? sources[0].url
}

function onPickMonitorSource(laneId: string, event: Event): void {
  const value = (event.target as HTMLSelectElement).value
  laneMonitorPick.value = { ...laneMonitorPick.value, [laneId]: value }
}

async function refreshView(): Promise<void> {
  if (!lotId.value) {
    errorMessage.value = t('booths.viewNotFound')
    return
  }
  if (!booth.value) {
    await load()
    return
  }
  refreshing.value = true
  errorMessage.value = ''
  try {
    const next = await fetchBooth()
    if (!next) {
      booth.value = null
      errorMessage.value = t('booths.viewNotFound')
      return
    }
    booth.value = next
    await Promise.all([loadLatestRecognitions(), loadLaneDevices(), loadFrigateCameras()])
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('booths.loadFailed')
  } finally {
    refreshing.value = false
  }
}

async function toggleMonitorMode(): Promise<void> {
  if (monitorMode.value) {
    monitorMode.value = false
    return
  }
  await loadFrigateCameras()
  monitorMode.value = true
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

/** 开闸/落闸/常开：把指令写入设备 HTTP 轮询队列，由臻识设备下次轮询取走执行。
 *  OPEN → info=ok 开闸；CLOSE → ivs_ioctrl 落杆/解除常开；
 *  HOLD_OPEN → ivs_ioctrl 持续通电，闸杆保持抬起（常开）。 */
type GateCommand = 'open' | 'close' | 'holdOpen'

async function onGateCommand(lane: BoothLaneView, command: GateCommand): Promise<void> {
  const confirmText =
    command === 'open'
      ? t('booths.confirmGateOpen', { lane: lane.name })
      : command === 'holdOpen'
        ? t('booths.confirmGateHoldOpen', { lane: lane.name })
        : t('booths.confirmGateClose', { lane: lane.name })
  if (!window.confirm(confirmText)) {
    return
  }
  const action = command === 'holdOpen' ? 'HOLD_OPEN' : command.toUpperCase()
  let devices = gateDevicesFor(lane.id)
  if (devices.length === 0) {
    // 进入页面后可能刚完成绑定，兜底再拉一次
    try {
      const result = await listBarriers(lane.id, locale.value)
      devices = result.data
      laneDevices.value = { ...laneDevices.value, [lane.id]: result.data }
    } catch {
      devices = []
    }
  }
  if (devices.length === 0) {
    logs.value.push(
      t(
        command === 'open'
          ? 'booths.gateNoDeviceOpen'
          : command === 'holdOpen'
            ? 'booths.gateNoDeviceHoldOpen'
            : 'booths.gateNoDeviceClose',
        { lane: lane.name },
      ),
    )
    return
  }
  for (const device of devices) {
    if (!device.enabled) {
      continue
    }
    const label = device.name || device.code
    try {
      const queued = await enqueueDeviceCommand(device.id, action, `booth:${lane.name}`, locale.value)
      logs.value.push(
        t('booths.gateQueued', {
          lane: lane.name,
          name: label,
          queueStatus: queued.data.status,
        }),
      )
    } catch (error) {
      const queueReason = error instanceof ApiError ? error.message : String(error)
      logs.value.push(
        t('booths.gateQueueFailed', {
          lane: lane.name,
          name: label,
          queueReason,
        }),
      )
    }
  }
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
        <div class="head-actions">
          <button type="button" class="ghost" :disabled="refreshing || loading" @click="refreshView">
            {{ refreshing ? t('booths.refreshing') : t('booths.refresh') }}
          </button>
          <button
            type="button"
            class="ghost"
            :class="{ active: monitorMode }"
            @click="toggleMonitorMode"
          >
            {{ monitorMode ? t('booths.monitorModeExit') : t('booths.monitorMode') }}
          </button>
        </div>
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
            </template>
            <template v-if="monitorMode">
              <label v-if="monitorSourcesFor(lane.id).length > 1" class="monitor-source">
                <span>{{ t('booths.monitorSource') }}</span>
                <select
                  :value="laneMonitorPick[lane.id] || monitorSourcesFor(lane.id)[0]?.key"
                  @change="onPickMonitorSource(lane.id, $event)"
                >
                  <option
                    v-for="source in monitorSourcesFor(lane.id)"
                    :key="source.key"
                    :value="source.key"
                  >
                    {{ source.label }}
                  </option>
                </select>
              </label>
              <div class="rec-media">
                <LaneLivePlayer :active="monitorMode" :url="monitorUrlFor(lane.id)" />
              </div>
            </template>
            <div v-else class="rec-media">
              <img
                v-if="laneRecognitions[lane.id]?.eventImage"
                :src="laneRecognitions[lane.id]!.eventImage ?? undefined"
                class="rec-img"
                alt="recognition"
              />
              <div v-else class="rec-placeholder">
                {{
                  laneRecognitions[lane.id] ? t('booths.noImage') : t('booths.noRecognition')
                }}
              </div>
            </div>
          </div>

          <div class="lane-actions">
            <button type="button" class="primary gate-open" @click="onGateCommand(lane, 'open')">
              {{ t('booths.gateOpen') }}
            </button>
            <button type="button" class="primary gate-hold" @click="onGateCommand(lane, 'holdOpen')">
              {{ t('booths.gateHoldOpen') }}
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
  --lane-media-height: 240px;
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

.head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-left: auto;
}

.ghost {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.45rem 0.75rem;
  font-weight: 600;
  background: #fff;
  color: var(--text);
  cursor: pointer;
}

.ghost:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ghost.active {
  border-color: var(--accent);
  color: var(--accent);
  background: #eef6f3;
}

.monitor-source {
  display: grid;
  gap: 0.3rem;
  margin-bottom: 0.5rem;
  font-size: 0.82rem;
  color: var(--muted);
}

.monitor-source select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.35rem 0.5rem;
  background: #fff;
  color: var(--text);
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
  align-items: stretch;
}

.lane-card {
  display: grid;
  grid-template-rows: auto 1fr auto;
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
  height: var(--lane-media-height);
}

.rec-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #111;
  border-radius: 6px;
}

.rec-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  border-radius: 6px;
  background: #f2f4f3;
  color: var(--muted);
  font-size: 0.85rem;
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

.gate-hold {
  background: var(--accent);
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
