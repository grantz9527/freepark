<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  createLane,
  listLanes,
  listLots,
  postAccessDecision,
  updateLane,
  type LaneType,
  type LaneView,
  type LotView,
  type PlateColor,
} from '@/api/client'
import { getUser } from '@/auth/session'
import { usePlateColorLabel } from '@/composables/usePlateColorLabel'
import { useSiteTime } from '@/composables/useSiteTime'
import {
  bindBarrierToLane,
  listBarrierDevices,
  unbindBarrier,
  type BarrierBindDirection,
  type BarrierDevice,
} from '@/hardware/barrierDevices'
import {
  bindFrigateCameraToLane,
  listFrigateCameras,
  unbindFrigateCamera,
  type FrigateBindDirection,
  type FrigateCamera,
} from '@/hardware/frigateCameras'
import {
  bindIotDeviceToLane,
  listIotDevices,
  unbindIotDevice,
  type IotBindDirection,
  type IotDevice,
} from '@/hardware/iotDevices'
import {
  getLanePlateIntercept,
  listLanePlateIntercept,
  saveLanePlateIntercept,
} from '@/hardware/lanePlateIntercept'
import {
  clearLaneSimEvents,
  listLaneSimEvents,
  simulateLaneEvent,
  type LaneSimDirection,
  type LaneSimEvent,
} from '@/hardware/laneSimEvents'
import {
  buildSimEventImage,
  createRecognitionRecord,
  markRecognitionAbnormal,
} from '@/hardware/recognitionRecords'
import { applyRecognitionToParkingSession, hasOpenParkingSession } from '@/hardware/parkingSessions'
import { siteAllowedPlateColors, siteDefaultPlateColor } from '@/site/settings'

const LOT_STORAGE_KEY = 'freepark.lanes.lotId'

const { t, locale } = useI18n()
const route = useRoute()
const { formatTime } = useSiteTime()
const { plateColorLabel } = usePlateColorLabel()

const loading = ref(false)
const submitting = ref(false)
const lots = ref<LotView[]>([])
const selectedLotId = ref('')
const lanes = ref<LaneView[]>([])
const errorMessage = ref('')
const showForm = ref(false)
const editingLaneId = ref<string | null>(null)
const formName = ref('')
const formCode = ref('')
const formLaneType = ref<LaneType>('ENTRANCE')
const formLotId = ref('')
const formLinkedLotId = ref('')
const formEnabled = ref(true)
const formInterceptColors = ref<PlateColor[]>([])
const formError = ref('')
const searchQuery = ref('')
const interceptByLaneId = ref<Record<string, PlateColor[]>>({})

const barrierLane = ref<LaneView | null>(null)
const barrierDevices = ref<BarrierDevice[]>([])
const bindDeviceId = ref('')
const bindDirection = ref<BarrierBindDirection | ''>('')
const bindError = ref('')

const frigateLane = ref<LaneView | null>(null)
const frigateCameras = ref<FrigateCamera[]>([])
const bindCameraId = ref('')
const bindFrigateDirection = ref<FrigateBindDirection | ''>('')
const bindLinkageEnabled = ref(true)
const bindFrigateError = ref('')

const iotLane = ref<LaneView | null>(null)
const iotDevices = ref<IotDevice[]>([])
const bindIotDeviceId = ref('')
const bindIotDirection = ref<IotBindDirection | ''>('')
const bindIotError = ref('')

const simLane = ref<LaneView | null>(null)
const simPlate = ref('')
const simPlateColor = ref<PlateColor>(siteDefaultPlateColor.value)
const simDirection = ref<LaneSimDirection>('ENTRANCE')
const simError = ref('')
const simBusy = ref(false)
const simEvents = ref<LaneSimEvent[]>([])
const simLogs = ref<string[]>([])

const laneTypeOptions: LaneType[] = ['ENTRANCE', 'EXIT']
const plateColorOptions = computed(() => siteAllowedPlateColors.value)

const isAdmin = computed(() => getUser()?.role === 'ADMIN')
const isEditing = computed(() => editingLaneId.value !== null)
const filteredLanes = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) {
    return lanes.value
  }
  return lanes.value.filter((lane) => {
    const lotsText = connectedLotsLabel(lane).toLowerCase()
    const colorsText = interceptColorsLabel(lane.id).toLowerCase()
    const barrierText = boundDeviceNames(lane.id).toLowerCase()
    const iotText = boundIotNames(lane.id).toLowerCase()
    const frigateText = boundFrigateNames(lane.id).toLowerCase()
    return (
      lane.name.toLowerCase().includes(query) ||
      lane.code.toLowerCase().includes(query) ||
      lotsText.includes(query) ||
      colorsText.includes(query) ||
      barrierText.includes(query) ||
      iotText.includes(query) ||
      frigateText.includes(query)
    )
  })
})
const isSearching = computed(() => searchQuery.value.trim().length > 0)
const linkedLotOptions = computed(() => lots.value.filter((lot) => lot.id !== formLotId.value))
const isBidirectionalLane = computed(() => barrierLane.value?.laneType === 'BIDIRECTIONAL')
const isBidirectionalFrigateLane = computed(() => frigateLane.value?.laneType === 'BIDIRECTIONAL')
const isBidirectionalIotLane = computed(() => iotLane.value?.laneType === 'BIDIRECTIONAL')

async function loadLots(): Promise<void> {
  const result = await listLots(locale.value)
  lots.value = result.data
  if (lots.value.length === 0) {
    selectedLotId.value = ''
    return
  }
  const queryLotId = typeof route.query.lotId === 'string' ? route.query.lotId : ''
  if (queryLotId) {
    const queryMatch = lots.value.find((lot) => lot.id === queryLotId)
    selectedLotId.value = queryMatch?.id ?? ''
    if (selectedLotId.value) {
      sessionStorage.setItem(LOT_STORAGE_KEY, selectedLotId.value)
    }
    return
  }
  const stored = sessionStorage.getItem(LOT_STORAGE_KEY)
  if (stored === '') {
    selectedLotId.value = ''
    return
  }
  const match = lots.value.find((lot) => lot.id === stored)
  selectedLotId.value = match?.id ?? ''
}

async function loadLanes(): Promise<void> {
  const result = await listLanes(locale.value, selectedLotId.value || undefined)
  lanes.value = result.data
}

async function reload(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadLots()
    await loadLanes()
    refreshBarrierDevices()
    refreshIotDevices()
    refreshFrigateCameras()
    refreshPlateIntercept()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('lanes.loadFailed')
  } finally {
    loading.value = false
  }
}

async function onLotChange(): Promise<void> {
  sessionStorage.setItem(LOT_STORAGE_KEY, selectedLotId.value)
  searchQuery.value = ''
  loading.value = true
  errorMessage.value = ''
  try {
    await loadLanes()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('lanes.loadFailed')
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  editingLaneId.value = null
  formName.value = ''
  formCode.value = ''
  formLaneType.value = 'ENTRANCE'
  formLotId.value = selectedLotId.value || lots.value[0]?.id || ''
  formLinkedLotId.value = ''
  formEnabled.value = true
  formInterceptColors.value = []
  formError.value = ''
}

function openCreateForm(): void {
  resetForm()
  showForm.value = true
}

function openEditForm(lane: LaneView): void {
  editingLaneId.value = lane.id
  formName.value = lane.name
  formCode.value = lane.code
  formLaneType.value = lane.laneType
  formLotId.value = lane.lotId
  formLinkedLotId.value = lane.linkedLotId ?? ''
  formEnabled.value = lane.enabled
  formInterceptColors.value = getLanePlateIntercept(lane.id)
  formError.value = ''
  showForm.value = true
}

function closeForm(): void {
  showForm.value = false
  resetForm()
}

function onFormLotChange(): void {
  if (formLinkedLotId.value === formLotId.value) {
    formLinkedLotId.value = ''
  }
}

async function onSubmit(): Promise<void> {
  formError.value = ''
  if (!formName.value.trim()) {
    formError.value = t('lanes.formRequired')
    return
  }
  if (!isEditing.value) {
    if (!formCode.value.trim()) {
      formError.value = t('lanes.formRequired')
      return
    }
    if (formCode.value.trim().length < 2) {
      formError.value = t('lanes.codeTooShort')
      return
    }
  }
  if (!formLotId.value) {
    formError.value = t('lanes.formRequired')
    return
  }

  submitting.value = true
  try {
    const linkedLotId = formLinkedLotId.value || null
    if (isEditing.value && editingLaneId.value) {
      const result = await updateLane(
        editingLaneId.value,
        {
          name: formName.value.trim(),
          laneType: formLaneType.value,
          lotId: formLotId.value,
          linkedLotId,
          enabled: formEnabled.value,
        },
        locale.value,
      )
      lanes.value = lanes.value.map((item) => (item.id === result.data.id ? result.data : item))
      persistIntercept(result.data.id)
    } else {
      const result = await createLane(
        {
          name: formName.value.trim(),
          code: formCode.value.trim(),
          laneType: formLaneType.value,
          lotId: formLotId.value,
          linkedLotId,
          enabled: formEnabled.value,
        },
        locale.value,
      )
      lanes.value = [result.data, ...lanes.value]
      persistIntercept(result.data.id)
    }
    closeForm()
  } catch (error) {
    formError.value =
      error instanceof ApiError
        ? error.message
        : isEditing.value
          ? t('lanes.updateFailed')
          : t('lanes.createFailed')
  } finally {
    submitting.value = false
  }
}

function connectedLotsLabel(lane: LaneView): string {
  if (lane.linkedLotName) {
    return `${lane.lotName} · ${lane.linkedLotName}`
  }
  return lane.lotName
}

function laneTypeLabel(type: LaneType): string {
  const key = `laneTypes.${type}`
  const label = t(key)
  return label === key ? type : label
}

function refreshPlateIntercept(): void {
  interceptByLaneId.value = listLanePlateIntercept()
}

function persistIntercept(laneId: string): void {
  saveLanePlateIntercept(laneId, formInterceptColors.value)
  refreshPlateIntercept()
}

function interceptColorsOf(laneId: string): PlateColor[] {
  return interceptByLaneId.value[laneId] ?? []
}

function interceptColorsLabel(laneId: string): string {
  const colors = interceptColorsOf(laneId)
  if (colors.length === 0) {
    return t('lanes.interceptPlateColorsNone')
  }
  return colors.map((color) => plateColorLabel(color)).join(' · ')
}

function isInterceptColorChecked(color: PlateColor): boolean {
  return formInterceptColors.value.includes(color)
}

function toggleInterceptColor(color: PlateColor, checked: boolean): void {
  if (checked) {
    if (!formInterceptColors.value.includes(color)) {
      formInterceptColors.value = [...formInterceptColors.value, color]
    }
    return
  }
  formInterceptColors.value = formInterceptColors.value.filter((item) => item !== color)
}

function refreshBarrierDevices(): void {
  barrierDevices.value = listBarrierDevices()
}

function boundDevices(laneId: string): BarrierDevice[] {
  return barrierDevices.value.filter((item) => item.laneId === laneId)
}

function boundDeviceNames(laneId: string): string {
  const lane = lanes.value.find((item) => item.id === laneId)
  const names = boundDevices(laneId).map((item) => {
    const direction = bindDirectionOf(item, lane)
    if (!direction) {
      return item.name
    }
    return `${item.name}（${bindDirectionLabel(direction)}）`
  })
  return names.length > 0 ? names.join(' · ') : t('lanes.noBarrier')
}

function availableDevices(): BarrierDevice[] {
  return barrierDevices.value.filter(
    (item) => item.enabled && item.linkStatus === 'CONNECTED' && !item.laneId,
  )
}

function bindDirectionLabel(direction: BarrierBindDirection): string {
  return direction === 'EXIT' ? t('barriers.bindDirectionExit') : t('barriers.bindDirectionEntrance')
}

function bindDirectionOf(
  device: BarrierDevice,
  lane: LaneView | undefined,
): BarrierBindDirection | null {
  if (lane && lane.laneType !== 'BIDIRECTIONAL') {
    return lane.laneType === 'EXIT' ? 'EXIT' : 'ENTRANCE'
  }
  return device.bindDirection
}

function bindDirectionText(
  device: BarrierDevice,
  lane: LaneView | undefined,
): string {
  const direction = bindDirectionOf(device, lane)
  return direction ? bindDirectionLabel(direction) : t('barriers.bindDirectionUnknown')
}

function openBarrierPanel(lane: LaneView): void {
  barrierLane.value = lane
  bindDeviceId.value = ''
  bindDirection.value = ''
  bindError.value = ''
  refreshBarrierDevices()
}

function closeBarrierPanel(): void {
  barrierLane.value = null
  bindDeviceId.value = ''
  bindDirection.value = ''
  bindError.value = ''
}

function onBindBarrier(): void {
  bindError.value = ''
  if (!barrierLane.value || !bindDeviceId.value) {
    bindError.value = t('barriers.bindRequired')
    return
  }
  const device = barrierDevices.value.find((item) => item.id === bindDeviceId.value)
  if (!device || device.linkStatus !== 'CONNECTED') {
    bindError.value = t('barriers.needConnected')
    return
  }
  let direction: BarrierBindDirection
  if (barrierLane.value.laneType === 'BIDIRECTIONAL') {
    if (bindDirection.value !== 'ENTRANCE' && bindDirection.value !== 'EXIT') {
      bindError.value = t('barriers.bindDirectionRequired')
      return
    }
    direction = bindDirection.value
  } else {
    direction = barrierLane.value.laneType === 'EXIT' ? 'EXIT' : 'ENTRANCE'
  }
  barrierDevices.value = bindBarrierToLane(
    device.id,
    barrierLane.value.id,
    direction,
    barrierDevices.value,
  )
  bindDeviceId.value = ''
  bindDirection.value = ''
}

function onUnbindBarrier(deviceId: string): void {
  barrierDevices.value = unbindBarrier(deviceId, barrierDevices.value)
}

function refreshIotDevices(): void {
  iotDevices.value = listIotDevices()
}

function boundIotDevices(laneId: string): IotDevice[] {
  return iotDevices.value.filter((item) => item.laneId === laneId)
}

function boundIotNames(laneId: string): string {
  const lane = lanes.value.find((item) => item.id === laneId)
  const names = boundIotDevices(laneId).map((item) => {
    const direction = iotDirectionOf(item, lane)
    if (!direction) {
      return item.name
    }
    return `${item.name}（${bindDirectionLabel(direction)}）`
  })
  return names.length > 0 ? names.join(' · ') : t('lanes.noIot')
}

function availableIotDevices(): IotDevice[] {
  return iotDevices.value.filter(
    (item) => item.enabled && item.linkStatus === 'CONNECTED' && !item.laneId,
  )
}

function iotDirectionOf(device: IotDevice, lane: LaneView | undefined): IotBindDirection | null {
  if (lane && lane.laneType !== 'BIDIRECTIONAL') {
    return lane.laneType === 'EXIT' ? 'EXIT' : 'ENTRANCE'
  }
  return device.bindDirection
}

function iotDirectionText(device: IotDevice, lane: LaneView | undefined): string {
  const direction = iotDirectionOf(device, lane)
  return direction ? bindDirectionLabel(direction) : t('iot.bindDirectionUnknown')
}

function openIotPanel(lane: LaneView): void {
  iotLane.value = lane
  bindIotDeviceId.value = ''
  bindIotDirection.value = ''
  bindIotError.value = ''
  refreshIotDevices()
}

function closeIotPanel(): void {
  iotLane.value = null
  bindIotDeviceId.value = ''
  bindIotDirection.value = ''
  bindIotError.value = ''
}

function onBindIot(): void {
  bindIotError.value = ''
  if (!iotLane.value || !bindIotDeviceId.value) {
    bindIotError.value = t('iot.bindRequired')
    return
  }
  const device = iotDevices.value.find((item) => item.id === bindIotDeviceId.value)
  if (!device || device.linkStatus !== 'CONNECTED') {
    bindIotError.value = t('iot.needConnected')
    return
  }
  let direction: IotBindDirection
  if (iotLane.value.laneType === 'BIDIRECTIONAL') {
    if (bindIotDirection.value !== 'ENTRANCE' && bindIotDirection.value !== 'EXIT') {
      bindIotError.value = t('iot.bindDirectionRequired')
      return
    }
    direction = bindIotDirection.value
  } else {
    direction = iotLane.value.laneType === 'EXIT' ? 'EXIT' : 'ENTRANCE'
  }
  iotDevices.value = bindIotDeviceToLane(
    device.id,
    iotLane.value.id,
    direction,
    iotDevices.value,
  )
  bindIotDeviceId.value = ''
  bindIotDirection.value = ''
}

function onUnbindIot(deviceId: string): void {
  iotDevices.value = unbindIotDevice(deviceId, iotDevices.value)
}

function refreshFrigateCameras(): void {
  frigateCameras.value = listFrigateCameras()
}

function boundFrigateCameras(laneId: string): FrigateCamera[] {
  return frigateCameras.value.filter((item) => item.laneId === laneId)
}

function boundFrigateNames(laneId: string): string {
  const lane = lanes.value.find((item) => item.id === laneId)
  const names = boundFrigateCameras(laneId).map((item) => {
    const direction = frigateDirectionOf(item, lane)
    if (!direction) {
      return item.name
    }
    return `${item.name}（${bindDirectionLabel(direction)}）`
  })
  return names.length > 0 ? names.join(' · ') : t('lanes.noFrigate')
}

function availableFrigateCameras(): FrigateCamera[] {
  return frigateCameras.value.filter(
    (item) => item.enabled && item.linkStatus === 'CONNECTED' && !item.laneId,
  )
}

function frigateDirectionOf(
  camera: FrigateCamera,
  lane: LaneView | undefined,
): FrigateBindDirection | null {
  if (lane && lane.laneType !== 'BIDIRECTIONAL') {
    return lane.laneType === 'EXIT' ? 'EXIT' : 'ENTRANCE'
  }
  return camera.bindDirection
}

function frigateDirectionText(camera: FrigateCamera, lane: LaneView | undefined): string {
  const direction = frigateDirectionOf(camera, lane)
  return direction ? bindDirectionLabel(direction) : t('frigate.bindDirectionUnknown')
}

function lastFrigateEventText(camera: FrigateCamera): string {
  if (!camera.lastPlate || !camera.lastEventAt) {
    return t('frigate.noEvent')
  }
  return `${camera.lastPlate} · ${formatTime(camera.lastEventAt)}`
}

function openFrigatePanel(lane: LaneView): void {
  frigateLane.value = lane
  bindCameraId.value = ''
  bindFrigateDirection.value = ''
  bindLinkageEnabled.value = true
  bindFrigateError.value = ''
  refreshFrigateCameras()
}

function closeFrigatePanel(): void {
  frigateLane.value = null
  bindCameraId.value = ''
  bindFrigateDirection.value = ''
  bindFrigateError.value = ''
}

function onBindFrigate(): void {
  bindFrigateError.value = ''
  if (!frigateLane.value || !bindCameraId.value) {
    bindFrigateError.value = t('frigate.bindRequired')
    return
  }
  const camera = frigateCameras.value.find((item) => item.id === bindCameraId.value)
  if (!camera || camera.linkStatus !== 'CONNECTED') {
    bindFrigateError.value = t('frigate.needConnected')
    return
  }
  let direction: FrigateBindDirection
  if (frigateLane.value.laneType === 'BIDIRECTIONAL') {
    if (bindFrigateDirection.value !== 'ENTRANCE' && bindFrigateDirection.value !== 'EXIT') {
      bindFrigateError.value = t('frigate.bindDirectionRequired')
      return
    }
    direction = bindFrigateDirection.value
  } else {
    direction = frigateLane.value.laneType === 'EXIT' ? 'EXIT' : 'ENTRANCE'
  }
  frigateCameras.value = bindFrigateCameraToLane(
    camera.id,
    frigateLane.value.id,
    direction,
    bindLinkageEnabled.value,
    frigateCameras.value,
  )
  bindCameraId.value = ''
  bindFrigateDirection.value = ''
  bindLinkageEnabled.value = true
}

function onUnbindFrigate(cameraId: string): void {
  frigateCameras.value = unbindFrigateCamera(cameraId, frigateCameras.value)
}

function refreshSimEvents(): void {
  if (!simLane.value) {
    simEvents.value = []
    return
  }
  simEvents.value = listLaneSimEvents(simLane.value.id)
}

function pushSimLog(message: string): void {
  const stamp = new Date().toLocaleTimeString()
  simLogs.value = [`[${stamp}] ${message}`, ...simLogs.value].slice(0, 40)
}

function defaultSimDirection(lane: LaneView): LaneSimDirection {
  if (lane.laneType === 'EXIT') {
    return 'EXIT'
  }
  return 'ENTRANCE'
}

function openSimPanel(lane: LaneView): void {
  simLane.value = lane
  simPlate.value = ''
  simPlateColor.value = siteDefaultPlateColor.value
  simDirection.value = defaultSimDirection(lane)
  simError.value = ''
  simBusy.value = false
  simLogs.value = []
  refreshSimEvents()
}

function closeSimPanel(): void {
  simLane.value = null
  simPlate.value = ''
  simPlateColor.value = siteDefaultPlateColor.value
  simDirection.value = 'ENTRANCE'
  simError.value = ''
  simBusy.value = false
  simEvents.value = []
  simLogs.value = []
}

function simDirectionLabel(direction: LaneSimDirection): string {
  return direction === 'EXIT' ? t('lanes.simExit') : t('lanes.simEntrance')
}

function simResultLabel(result: LaneSimEvent['result']): string {
  return result === 'INTERCEPTED' ? t('lanes.simResultIntercepted') : t('lanes.simResultAllowed')
}

function linkedSimBarriers(lane: LaneView, direction: LaneSimDirection): BarrierDevice[] {
  return listBarrierDevices().filter((item) => {
    if (item.laneId !== lane.id || item.linkStatus !== 'CONNECTED') {
      return false
    }
    if (!item.bindDirection) {
      return true
    }
    return item.bindDirection === direction
  })
}

async function onSimulate(): Promise<void> {
  if (!simLane.value) {
    return
  }
  const plate = simPlate.value.trim()
  if (!plate) {
    simError.value = t('lanes.simPlateRequired')
    return
  }
  const direction = simDirection.value
  simError.value = ''
  simBusy.value = true
  try {
    const interceptColors = getLanePlateIntercept(simLane.value.id)
    const openSession = hasOpenParkingSession(simLane.value.lotId, plate)
    const decision = await postAccessDecision(
      simLane.value.lotId,
      {
        laneId: simLane.value.id,
        plateNumber: plate,
        plateColor: simPlateColor.value,
        direction,
        interceptColors,
        hasOpenSession: direction === 'EXIT' ? openSession : undefined,
      },
      locale.value,
    )
    const event = simulateLaneEvent({
      laneId: simLane.value.id,
      lotId: simLane.value.lotId,
      plateNumber: plate,
      plateColor: simPlateColor.value,
      direction,
      result: decision.data.result,
      remark: decision.data.remark,
    })
    refreshSimEvents()
    pushSimLog(
      t('lanes.simLogEvent', {
        direction: simDirectionLabel(event.direction),
        plate: event.plateNumber,
        color: plateColorLabel(event.plateColor),
      }),
    )
    if (event.result === 'INTERCEPTED') {
      if (event.remark === 'blacklisted_vehicle') {
        simError.value = t('lanes.simBlacklisted')
        pushSimLog(t('lanes.simLogBlacklisted', { plate: event.plateNumber }))
      } else if (event.remark === 'not_internal_vehicle') {
        simError.value = t('lanes.simInternalRejected')
        pushSimLog(t('lanes.simLogInternalRejected', { plate: event.plateNumber }))
        createRecognitionRecord({
          lotId: simLane.value.lotId,
          lotName: simLane.value.lotName,
          laneId: simLane.value.id,
          laneName: simLane.value.name,
          plateNumber: event.plateNumber,
          plateColor: event.plateColor,
          eventTime: event.createdAt,
          eventImage: buildSimEventImage(event.plateNumber, event.plateColor),
          eventType: 'DEVICE',
          direction: event.direction,
          abnormal: true,
          abnormalReason: 'not_internal_vehicle',
          sourceSimEventId: event.id,
        })
        pushSimLog(t('lanes.simLogRecognitionNotInternal', { plate: event.plateNumber }))
      } else {
        pushSimLog(t('lanes.simLogIntercepted', { plate: event.plateNumber }))
      }
      return
    }
    if (event.direction === 'ENTRANCE') {
      pushSimLog(t('lanes.simLogEntry', { plate: event.plateNumber }))
    } else {
      pushSimLog(t('lanes.simLogExit', { plate: event.plateNumber }))
    }
    const recognition = createRecognitionRecord({
      lotId: simLane.value.lotId,
      lotName: simLane.value.lotName,
      laneId: simLane.value.id,
      laneName: simLane.value.name,
      plateNumber: event.plateNumber,
      plateColor: event.plateColor,
      eventTime: event.createdAt,
      eventImage: buildSimEventImage(event.plateNumber, event.plateColor),
      eventType: 'DEVICE',
      direction: event.direction,
      sourceSimEventId: event.id,
    })
    pushSimLog(t('lanes.simLogRecognition', { plate: event.plateNumber }))
    const flow = applyRecognitionToParkingSession(recognition)
    if (flow.kind === 'entry') {
      pushSimLog(t('lanes.simLogSessionOpened', { plate: event.plateNumber }))
    } else if (flow.kind === 'exit_matched') {
      pushSimLog(t('lanes.simLogSessionClosed', { plate: event.plateNumber }))
    } else if (flow.kind === 'exit_unmatched') {
      markRecognitionAbnormal(recognition.id, 'exit_unmatched')
      pushSimLog(t('lanes.simLogSessionUnmatched', { plate: event.plateNumber }))
    }
    const barriers = linkedSimBarriers(simLane.value, event.direction)
    if (barriers.length > 0) {
      pushSimLog(
        t('lanes.simLogBarrier', {
          plate: event.plateNumber,
          devices: barriers.map((item) => item.name).join(' · '),
        }),
      )
    } else {
      pushSimLog(t('lanes.simLogNoBarrier'))
    }
  } finally {
    simBusy.value = false
  }
}

function onClearSimEvents(): void {
  if (!simLane.value) {
    return
  }
  clearLaneSimEvents(simLane.value.id)
  refreshSimEvents()
  pushSimLog(t('lanes.simLogCleared'))
}

onMounted(reload)
</script>

<template>
  <section class="page">
    <p class="banner planning">{{ t('lanes.barrierRuleHint') }}</p>
    <p class="banner planning">{{ t('lanes.iotRuleHint') }}</p>
    <p class="banner planning">{{ t('lanes.frigateRuleHint') }}</p>

    <div class="lot-bar">
      <label class="lot-select">
        <span>{{ t('spaces.lotLabel') }}</span>
        <select v-model="selectedLotId" @change="onLotChange">
          <option value="">{{ t('lanes.allLots') }}</option>
          <option v-for="lot in lots" :key="lot.id" :value="lot.id">{{ lot.name }}</option>
        </select>
      </label>
    </div>

    <p v-if="errorMessage" class="banner error">{{ errorMessage }}</p>

    <div v-if="lots.length === 0" class="table-card">
      <div class="empty">
        <strong>{{ t('spaces.noLot') }}</strong>
        <p>{{ t('spaces.noLotHint') }}</p>
      </div>
    </div>

    <template v-else>
      <div class="toolbar">
        <label class="search">
          <span class="sr-only">{{ t('page.search') }}</span>
          <input v-model="searchQuery" type="search" :placeholder="t('lanes.searchPlaceholder')" />
        </label>
        <button v-if="isAdmin" type="button" @click="openCreateForm">{{ t('lanes.create') }}</button>
      </div>

      <div class="table-card">
        <table v-if="filteredLanes.length > 0">
          <thead>
            <tr>
              <th>{{ t('lanes.colName') }}</th>
              <th>{{ t('lanes.colCode') }}</th>
              <th>{{ t('lanes.colLaneType') }}</th>
              <th>{{ t('lanes.colLots') }}</th>
              <th>{{ t('lanes.colInterceptColors') }}</th>
              <th>{{ t('lanes.colBarriers') }}</th>
              <th>{{ t('lanes.colIot') }}</th>
              <th>{{ t('lanes.colFrigate') }}</th>
              <th>{{ t('page.colStatus') }}</th>
              <th>{{ t('page.colUpdated') }}</th>
              <th class="col-actions">{{ t('lanes.colActions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredLanes" :key="item.id">
              <td>{{ item.name }}</td>
              <td>{{ item.code }}</td>
              <td>{{ laneTypeLabel(item.laneType) }}</td>
              <td>{{ connectedLotsLabel(item) }}</td>
              <td>{{ interceptColorsLabel(item.id) }}</td>
              <td>{{ boundDeviceNames(item.id) }}</td>
              <td>{{ boundIotNames(item.id) }}</td>
              <td>{{ boundFrigateNames(item.id) }}</td>
              <td>
                <span class="pill" :class="item.enabled ? 'ok' : 'fail'">
                  {{ item.enabled ? t('lanes.statusActive') : t('lanes.statusDisabled') }}
                </span>
              </td>
              <td>{{ formatTime(item.updatedAt) }}</td>
              <td class="col-actions">
                <div class="action-group">
                  <button type="button" class="link-btn" @click="openSimPanel(item)">
                    {{ t('lanes.simulate') }}
                  </button>
                  <button type="button" class="link-btn" @click="openBarrierPanel(item)">
                    {{ t('lanes.manageBarriers') }}
                  </button>
                  <button type="button" class="link-btn" @click="openIotPanel(item)">
                    {{ t('lanes.manageIot') }}
                  </button>
                  <button type="button" class="link-btn" @click="openFrigatePanel(item)">
                    {{ t('lanes.manageFrigate') }}
                  </button>
                  <button v-if="isAdmin" type="button" class="link-btn" @click="openEditForm(item)">
                    {{ t('lanes.edit') }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else-if="loading" class="empty">
          <p>{{ t('lanes.loading') }}</p>
        </div>
        <div v-else-if="isSearching && lanes.length > 0" class="empty">
          <strong>{{ t('lanes.searchNoResults') }}</strong>
        </div>
        <div v-else class="empty">
          <strong>{{ t('lanes.empty') }}</strong>
          <p>{{ isAdmin ? t('lanes.emptyHintAdmin') : t('lanes.emptyHint') }}</p>
        </div>
      </div>
    </template>

    <div v-if="showForm" class="modal-backdrop">
      <form class="modal" @submit.prevent="onSubmit">
        <h3>{{ isEditing ? t('lanes.editTitle') : t('lanes.createTitle') }}</h3>
        <p class="hint">{{ isEditing ? t('lanes.editHint') : t('lanes.createHint') }}</p>
        <label>
          <span>{{ t('lanes.name') }}</span>
          <input v-model="formName" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('lanes.code') }}</span>
          <input
            v-model="formCode"
            type="text"
            autocomplete="off"
            :readonly="isEditing"
            :class="{ locked: isEditing }"
          />
          <span v-if="isEditing" class="field-hint">{{ t('lanes.codeLocked') }}</span>
        </label>
        <label>
          <span>{{ t('lanes.laneType') }}</span>
          <select v-model="formLaneType">
            <option v-for="option in laneTypeOptions" :key="option" :value="option">
              {{ laneTypeLabel(option) }}
            </option>
          </select>
        </label>
        <label>
          <span>{{ t('lanes.lot') }}</span>
          <select v-model="formLotId" @change="onFormLotChange">
            <option v-for="lot in lots" :key="lot.id" :value="lot.id">{{ lot.name }}</option>
          </select>
        </label>
        <label>
          <span>{{ t('lanes.linkedLot') }}</span>
          <select v-model="formLinkedLotId">
            <option value="">{{ t('lanes.linkedLotNone') }}</option>
            <option v-for="lot in linkedLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</option>
          </select>
        </label>
        <div class="field-block">
          <span>{{ t('lanes.interceptPlateColors') }}</span>
          <p class="field-hint">{{ t('lanes.interceptPlateColorsHint') }}</p>
          <p class="field-hint">{{ t('lanes.interceptPlateColorsPlanning') }}</p>
          <div class="color-grid">
            <label v-for="color in siteAllowedPlateColors" :key="color" class="color-option">
              <input
                type="checkbox"
                :checked="isInterceptColorChecked(color)"
                @change="toggleInterceptColor(color, ($event.target as HTMLInputElement).checked)"
              />
              <span>{{ plateColorLabel(color) }}</span>
            </label>
          </div>
        </div>
        <label class="checkbox">
          <input v-model="formEnabled" type="checkbox" />
          <span>{{ t('lanes.enabled') }}</span>
        </label>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" @click="closeForm">{{ t('lanes.cancel') }}</button>
          <button type="submit" :disabled="submitting">
            {{
              submitting
                ? isEditing
                  ? t('lanes.saving')
                  : t('lanes.creating')
                : isEditing
                  ? t('lanes.save')
                  : t('lanes.create')
            }}
          </button>
        </div>
      </form>
    </div>

    <div v-if="barrierLane" class="modal-backdrop">
      <div class="modal wide">
        <h3>{{ t('barriers.panelTitle') }} · {{ barrierLane.name }}</h3>
        <p class="hint">{{ t('barriers.panelHint') }}</p>
        <table v-if="boundDevices(barrierLane.id).length > 0">
          <thead>
            <tr>
              <th>{{ t('barriers.colName') }}</th>
              <th>{{ t('barriers.colCode') }}</th>
              <th>{{ t('barriers.colBindDirection') }}</th>
              <th>{{ t('barriers.colCamera') }}</th>
              <th>{{ t('barriers.colBoard') }}</th>
              <th>{{ t('barriers.colLink') }}</th>
              <th v-if="isAdmin" class="col-actions">{{ t('barriers.colActions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in boundDevices(barrierLane.id)" :key="item.id">
              <td>{{ item.name }}</td>
              <td>{{ item.code }}</td>
              <td>{{ bindDirectionText(item, barrierLane) }}</td>
              <td>{{ t(`barriers.cameras.${item.cameraType}`) }}</td>
              <td>{{ t(`barriers.boards.${item.boardId}`) }}</td>
              <td>
                <span class="pill" :class="item.linkStatus === 'CONNECTED' ? 'ok' : 'fail'">
                  {{
                    item.linkStatus === 'CONNECTED'
                      ? t('barriers.linkConnected')
                      : t('barriers.linkDisconnected')
                  }}
                </span>
              </td>
              <td v-if="isAdmin" class="col-actions">
                <button type="button" class="link-btn" @click="onUnbindBarrier(item.id)">
                  {{ t('barriers.unbind') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty">
          <strong>{{ t('barriers.laneEmpty') }}</strong>
          <p>{{ t('barriers.panelHint') }}</p>
        </div>
        <template v-if="isAdmin">
          <label v-if="availableDevices().length > 0">
            <span>{{ t('barriers.bindLabel') }}</span>
            <select v-model="bindDeviceId">
              <option value="">{{ t('barriers.bindPlaceholder') }}</option>
              <option v-for="item in availableDevices()" :key="item.id" :value="item.id">
                {{ item.name }} ({{ item.code }}) ·
                {{ t(`barriers.cameras.${item.cameraType}`) }} /
                {{ t(`barriers.boards.${item.boardId}`) }}
              </option>
            </select>
          </label>
          <label v-if="availableDevices().length > 0 && isBidirectionalLane">
            <span>{{ t('barriers.bindDirection') }}</span>
            <select v-model="bindDirection">
              <option value="">{{ t('barriers.bindDirectionPlaceholder') }}</option>
              <option value="ENTRANCE">{{ t('barriers.bindDirectionEntrance') }}</option>
              <option value="EXIT">{{ t('barriers.bindDirectionExit') }}</option>
            </select>
            <span class="field-hint">{{ t('barriers.bindDirectionHint') }}</span>
          </label>
          <p
            v-else-if="availableDevices().length > 0"
            class="field-hint"
          >
            {{ t('barriers.bindDirectionAuto', { type: laneTypeLabel(barrierLane.laneType) }) }}
          </p>
          <p v-if="availableDevices().length === 0" class="field-hint">
            {{ t('barriers.noConnectedDevice') }}
            <RouterLink to="/hardware/barriers">{{ t('barriers.goDocking') }}</RouterLink>
          </p>
          <p v-if="bindError" class="form-error">{{ bindError }}</p>
        </template>
        <div class="actions">
          <button type="button" class="ghost" @click="closeBarrierPanel">{{ t('lanes.cancel') }}</button>
          <button
            v-if="isAdmin && availableDevices().length > 0"
            type="button"
            @click="onBindBarrier"
          >
            {{ t('barriers.bind') }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="iotLane" class="modal-backdrop">
      <div class="modal wide">
        <h3>{{ t('iot.panelTitle') }} · {{ iotLane.name }}</h3>
        <p class="hint">{{ t('iot.panelHint') }}</p>
        <table v-if="boundIotDevices(iotLane.id).length > 0">
          <thead>
            <tr>
              <th>{{ t('iot.colName') }}</th>
              <th>{{ t('iot.colCode') }}</th>
              <th>{{ t('iot.colBindDirection') }}</th>
              <th>{{ t('iot.colDevice') }}</th>
              <th>{{ t('iot.colBoard') }}</th>
              <th>{{ t('iot.colLink') }}</th>
              <th v-if="isAdmin" class="col-actions">{{ t('iot.colActions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in boundIotDevices(iotLane.id)" :key="item.id">
              <td>{{ item.name }}</td>
              <td>{{ item.code }}</td>
              <td>{{ iotDirectionText(item, iotLane) }}</td>
              <td>{{ t(`iot.devices.${item.deviceType}`) }}</td>
              <td>{{ t(`iot.boards.${item.boardId}`) }}</td>
              <td>
                <span class="pill" :class="item.linkStatus === 'CONNECTED' ? 'ok' : 'fail'">
                  {{
                    item.linkStatus === 'CONNECTED'
                      ? t('iot.linkConnected')
                      : t('iot.linkDisconnected')
                  }}
                </span>
              </td>
              <td v-if="isAdmin" class="col-actions">
                <button type="button" class="link-btn" @click="onUnbindIot(item.id)">
                  {{ t('iot.unbind') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty">
          <strong>{{ t('iot.laneEmpty') }}</strong>
          <p>{{ t('iot.panelHint') }}</p>
        </div>
        <template v-if="isAdmin">
          <label v-if="availableIotDevices().length > 0">
            <span>{{ t('iot.bindLabel') }}</span>
            <select v-model="bindIotDeviceId">
              <option value="">{{ t('iot.bindPlaceholder') }}</option>
              <option v-for="item in availableIotDevices()" :key="item.id" :value="item.id">
                {{ item.name }} ({{ item.code }}) ·
                {{ t(`iot.devices.${item.deviceType}`) }} /
                {{ t(`iot.boards.${item.boardId}`) }}
              </option>
            </select>
          </label>
          <label v-if="availableIotDevices().length > 0 && isBidirectionalIotLane">
            <span>{{ t('iot.bindDirection') }}</span>
            <select v-model="bindIotDirection">
              <option value="">{{ t('iot.bindDirectionPlaceholder') }}</option>
              <option value="ENTRANCE">{{ t('iot.bindDirectionEntrance') }}</option>
              <option value="EXIT">{{ t('iot.bindDirectionExit') }}</option>
            </select>
            <span class="field-hint">{{ t('iot.bindDirectionHint') }}</span>
          </label>
          <p
            v-else-if="availableIotDevices().length > 0"
            class="field-hint"
          >
            {{ t('iot.bindDirectionAuto', { type: laneTypeLabel(iotLane.laneType) }) }}
          </p>
          <p v-if="availableIotDevices().length === 0" class="field-hint">
            {{ t('iot.noConnectedDevice') }}
            <RouterLink to="/hardware/iot">{{ t('iot.goDocking') }}</RouterLink>
          </p>
          <p v-if="bindIotError" class="form-error">{{ bindIotError }}</p>
        </template>
        <div class="actions">
          <button type="button" class="ghost" @click="closeIotPanel">{{ t('lanes.cancel') }}</button>
          <button
            v-if="isAdmin && availableIotDevices().length > 0"
            type="button"
            @click="onBindIot"
          >
            {{ t('iot.bind') }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="frigateLane" class="modal-backdrop">
      <div class="modal wide">
        <h3>{{ t('frigate.panelTitle') }} · {{ frigateLane.name }}</h3>
        <p class="hint">{{ t('frigate.panelHint') }}</p>
        <table v-if="boundFrigateCameras(frigateLane.id).length > 0">
          <thead>
            <tr>
              <th>{{ t('frigate.colName') }}</th>
              <th>{{ t('frigate.colCameraName') }}</th>
              <th>{{ t('frigate.colBindDirection') }}</th>
              <th>{{ t('frigate.colLinkage') }}</th>
              <th>{{ t('frigate.colLastEvent') }}</th>
              <th>{{ t('frigate.colLink') }}</th>
              <th v-if="isAdmin" class="col-actions">{{ t('frigate.colActions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in boundFrigateCameras(frigateLane.id)" :key="item.id">
              <td>{{ item.name }}</td>
              <td>{{ item.cameraName }}</td>
              <td>{{ frigateDirectionText(item, frigateLane) }}</td>
              <td>{{ item.linkageEnabled ? t('frigate.linkageOn') : t('frigate.linkageOff') }}</td>
              <td>{{ lastFrigateEventText(item) }}</td>
              <td>
                <span class="pill" :class="item.linkStatus === 'CONNECTED' ? 'ok' : 'fail'">
                  {{
                    item.linkStatus === 'CONNECTED'
                      ? t('frigate.linkConnected')
                      : t('frigate.linkDisconnected')
                  }}
                </span>
              </td>
              <td v-if="isAdmin" class="col-actions">
                <button type="button" class="link-btn" @click="onUnbindFrigate(item.id)">
                  {{ t('frigate.unbind') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty">
          <strong>{{ t('frigate.laneEmpty') }}</strong>
          <p>{{ t('frigate.panelHint') }}</p>
        </div>
        <template v-if="isAdmin">
          <label v-if="availableFrigateCameras().length > 0">
            <span>{{ t('frigate.bindLabel') }}</span>
            <select v-model="bindCameraId">
              <option value="">{{ t('frigate.bindPlaceholder') }}</option>
              <option v-for="item in availableFrigateCameras()" :key="item.id" :value="item.id">
                {{ item.name }} ({{ item.cameraName }})
              </option>
            </select>
          </label>
          <label v-if="availableFrigateCameras().length > 0 && isBidirectionalFrigateLane">
            <span>{{ t('frigate.bindDirection') }}</span>
            <select v-model="bindFrigateDirection">
              <option value="">{{ t('frigate.bindDirectionPlaceholder') }}</option>
              <option value="ENTRANCE">{{ t('frigate.bindDirectionEntrance') }}</option>
              <option value="EXIT">{{ t('frigate.bindDirectionExit') }}</option>
            </select>
            <span class="field-hint">{{ t('frigate.bindDirectionHint') }}</span>
          </label>
          <p v-else-if="availableFrigateCameras().length > 0" class="field-hint">
            {{ t('frigate.bindDirectionAuto', { type: laneTypeLabel(frigateLane.laneType) }) }}
          </p>
          <label v-if="availableFrigateCameras().length > 0" class="checkbox">
            <input v-model="bindLinkageEnabled" type="checkbox" />
            <span>{{ t('frigate.linkageEnabled') }}</span>
          </label>
          <p v-if="availableFrigateCameras().length > 0" class="field-hint">
            {{ t('frigate.linkageHint') }}
          </p>
          <p v-if="availableFrigateCameras().length === 0" class="field-hint">
            {{ t('frigate.noConnectedCamera') }}
            <RouterLink to="/hardware/frigate">{{ t('frigate.goDocking') }}</RouterLink>
          </p>
          <p v-if="bindFrigateError" class="form-error">{{ bindFrigateError }}</p>
        </template>
        <div class="actions">
          <button type="button" class="ghost" @click="closeFrigatePanel">{{ t('lanes.cancel') }}</button>
          <button
            v-if="isAdmin && availableFrigateCameras().length > 0"
            type="button"
            @click="onBindFrigate"
          >
            {{ t('frigate.bind') }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="simLane" class="modal-backdrop" @click.self="closeSimPanel">
      <div class="modal wide">
        <div class="modal-head">
          <h3>{{ t('lanes.simTitle') }} · {{ simLane.name }}</h3>
          <button
            type="button"
            class="modal-close"
            :aria-label="t('lanes.cancel')"
            @click="closeSimPanel"
          >
            ×
          </button>
        </div>
        <p class="hint">{{ t('lanes.simHint') }}</p>
        <p class="banner planning">{{ t('lanes.simPlanning') }}</p>
        <div class="sim-form">
          <label>
            <span>{{ t('lanes.simPlate') }}</span>
            <input
              v-model="simPlate"
              type="text"
              autocomplete="off"
              :placeholder="t('lanes.simPlatePlaceholder')"
              @keyup.enter="onSimulate"
            />
          </label>
          <label>
            <span>{{ t('lanes.simPlateColor') }}</span>
            <select v-model="simPlateColor">
              <option v-for="color in plateColorOptions" :key="color" :value="color">
                {{ plateColorLabel(color) }}
              </option>
            </select>
          </label>
          <template v-if="simLane.laneType === 'BIDIRECTIONAL'">
            <label>
              <span>{{ t('lanes.simDirection') }}</span>
              <select v-model="simDirection">
                <option value="ENTRANCE">{{ t('lanes.simEntrance') }}</option>
                <option value="EXIT">{{ t('lanes.simExit') }}</option>
              </select>
              <span class="field-hint">
                {{
                  t('lanes.simDirectionAuto', {
                    type: laneTypeLabel(simLane.laneType),
                    action: simDirectionLabel(simDirection),
                  })
                }}
              </span>
            </label>
          </template>
          <label v-else>
            <span>{{ t('lanes.simDirection') }}</span>
            <span class="pill ok">{{ simDirectionLabel(defaultSimDirection(simLane)) }}</span>
            <span class="field-hint">
              {{
                t('lanes.simDirectionAuto', {
                  type: laneTypeLabel(simLane.laneType),
                  action: simDirectionLabel(defaultSimDirection(simLane)),
                })
              }}
            </span>
          </label>
        </div>
        <p v-if="simError" class="form-error">{{ simError }}</p>
        <div class="actions start">
          <button type="button" :disabled="simBusy" @click="onSimulate">
            {{ simBusy ? t('lanes.simSubmitting') : t('lanes.simSubmit') }}
          </button>
          <button
            v-if="simEvents.length > 0"
            type="button"
            class="ghost"
            :disabled="simBusy"
            @click="onClearSimEvents"
          >
            {{ t('lanes.simClear') }}
          </button>
        </div>

        <div v-if="simLogs.length > 0" class="sim-log">
          <strong>{{ t('lanes.simLogTitle') }}</strong>
          <ul>
            <li v-for="(line, index) in simLogs" :key="`${index}-${line}`">{{ line }}</li>
          </ul>
        </div>

        <div class="sim-history">
          <strong>{{ t('lanes.simHistoryTitle') }}</strong>
          <table v-if="simEvents.length > 0">
            <thead>
              <tr>
                <th>{{ t('lanes.simColTime') }}</th>
                <th>{{ t('lanes.simColDirection') }}</th>
                <th>{{ t('lanes.simColPlate') }}</th>
                <th>{{ t('lanes.simColColor') }}</th>
                <th>{{ t('lanes.simColResult') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in simEvents" :key="item.id">
                <td>{{ formatTime(item.createdAt) }}</td>
                <td>{{ simDirectionLabel(item.direction) }}</td>
                <td>{{ item.plateNumber }}</td>
                <td>{{ plateColorLabel(item.plateColor) }}</td>
                <td>
                  <span class="pill" :class="item.result === 'ALLOWED' ? 'ok' : 'fail'">
                    {{ simResultLabel(item.result) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else class="field-hint">{{ t('lanes.simHistoryEmpty') }}</p>
        </div>

        <div class="actions">
          <button type="button" class="ghost" @click="closeSimPanel">{{ t('lanes.cancel') }}</button>
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

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
}

.search {
  flex: 1;
  max-width: 18rem;
}

.search input,
.toolbar button {
  border: 1px solid var(--border);
  border-radius: 8px;
  min-height: 2.25rem;
  padding: 0 0.8rem;
}

.search input {
  width: 100%;
  background: var(--surface);
  color: var(--text);
}

.toolbar button {
  background: var(--accent);
  color: #fff;
  font-weight: 600;
  border-color: transparent;
}

.table-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow);
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
  width: 12rem;
  text-align: end;
}

.action-group {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.75rem;
}

tbody tr:last-child td {
  border-bottom: 0;
}

.link-btn {
  border: 0;
  background: none;
  color: var(--accent);
  font-weight: 600;
  padding: 0;
  cursor: pointer;
}

.link-btn:hover {
  color: var(--accent-dark);
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
  padding: 3rem 1.5rem;
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

.banner.error {
  margin: 0;
  padding: 0.65rem 0.9rem;
  border-radius: 8px;
  color: var(--danger);
  background: #fdecec;
}

.banner.planning {
  margin: 0;
  padding: 0.65rem 0.9rem;
  border-radius: 8px;
  color: #6b5a12;
  background: #fff6d8;
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

.modal-backdrop.stacked {
  z-index: 30;
}

.modal {
  width: min(520px, 100%);
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

.modal.wide {
  width: min(720px, 100%);
}

.modal h3 {
  margin: 0;
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.modal-close {
  flex: 0 0 auto;
  width: 2rem;
  height: 2rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  color: var(--muted);
  font-size: 1.25rem;
  line-height: 1;
  cursor: pointer;
}

.modal-close:hover {
  color: var(--text);
  background: #f2f4f3;
}

.hint {
  margin: -0.25rem 0 0;
  color: var(--muted);
  font-size: 0.9rem;
}

.field-hint {
  color: var(--muted);
  font-size: 0.82rem;
}

input.locked {
  background: #f4f6f5;
  color: var(--muted);
  cursor: not-allowed;
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

.field-block {
  display: grid;
  gap: 0.4rem;
}

.color-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(7.5rem, 1fr));
  gap: 0.4rem 0.75rem;
}

.color-option {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.9rem;
}

.color-option input {
  width: auto;
}

input {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
}

select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
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

.actions.start {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.actions button {
  border: 0;
  border-radius: 8px;
  padding: 0.55rem 0.85rem;
  font-weight: 600;
}

.actions button:not(.ghost) {
  color: #fff;
  background: var(--accent);
}

.ghost {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
}

.sim-form {
  display: grid;
  gap: 0.75rem;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  align-items: end;
}

.sim-log,
.sim-history {
  display: grid;
  gap: 0.5rem;
  margin-top: 0.35rem;
}

.sim-log ul {
  margin: 0;
  padding: 0.65rem 0.85rem;
  list-style: none;
  max-height: 9rem;
  overflow: auto;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #f7faf8;
  font-size: 0.85rem;
  color: var(--muted);
}

.sim-log li + li {
  margin-top: 0.35rem;
}

.sim-history table {
  width: 100%;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
