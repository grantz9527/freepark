export type IotLinkStatus = 'DISCONNECTED' | 'CONNECTED' | 'FAILED'

export type IotDeviceType = 'USR' | 'FOURFAITH' | 'HONGDIAN' | 'GENERIC'

export type IotBoardId = 'USR_RS485' | 'USR_MODBUS' | 'FF_RS485' | 'HD_RS485' | 'GENERIC_RS485'

export interface IotCommand {
  id: string
  labelKey: string
  payload: string
}

export interface IotBoardProfile {
  id: IotBoardId
  deviceType: IotDeviceType
  hintKey: string
  commands: IotCommand[]
}

export type IotBindDirection = 'ENTRANCE' | 'EXIT'

export interface IotDevice {
  id: string
  name: string
  code: string
  deviceType: IotDeviceType
  boardId: IotBoardId
  host: string
  port: number
  enabled: boolean
  linkStatus: IotLinkStatus
  lastTestAt: string | null
  laneId: string | null
  bindDirection: IotBindDirection | null
  createdAt: string
  updatedAt: string
}

export const IOT_DEVICE_TYPES: IotDeviceType[] = ['USR', 'FOURFAITH', 'HONGDIAN', 'GENERIC']

export const IOT_BOARDS: IotBoardProfile[] = [
  {
    id: 'USR_RS485',
    deviceType: 'USR',
    hintKey: 'iot.boardHints.USR_RS485',
    commands: [
      { id: 'open', labelKey: 'iot.cmdOpen', payload: 'RS485 HEX 01 05 00 00 FF 00 8C 3A' },
      { id: 'close', labelKey: 'iot.cmdClose', payload: 'RS485 HEX 01 05 00 00 00 00 CD CA' },
      { id: 'query', labelKey: 'iot.cmdQuery', payload: 'RS485 HEX 01 01 00 00 00 01 FD CA' },
    ],
  },
  {
    id: 'USR_MODBUS',
    deviceType: 'USR',
    hintKey: 'iot.boardHints.USR_MODBUS',
    commands: [
      { id: 'open', labelKey: 'iot.cmdOpen', payload: 'Modbus 01 06 00 01 00 01 19 CA' },
      { id: 'close', labelKey: 'iot.cmdClose', payload: 'Modbus 01 06 00 01 00 00 D8 0A' },
      { id: 'query', labelKey: 'iot.cmdQuery', payload: 'Modbus 01 03 00 00 00 01 84 0A' },
    ],
  },
  {
    id: 'FF_RS485',
    deviceType: 'FOURFAITH',
    hintKey: 'iot.boardHints.FF_RS485',
    commands: [
      { id: 'open', labelKey: 'iot.cmdOpen', payload: 'RS485 HEX 01 05 00 00 FF 00 8C 3A' },
      { id: 'close', labelKey: 'iot.cmdClose', payload: 'RS485 HEX 01 05 00 00 00 00 CD CA' },
      { id: 'stayOpen', labelKey: 'iot.cmdStayOpen', payload: 'RS485 HEX 01 05 00 01 FF 00 DD FA' },
    ],
  },
  {
    id: 'HD_RS485',
    deviceType: 'HONGDIAN',
    hintKey: 'iot.boardHints.HD_RS485',
    commands: [
      { id: 'open', labelKey: 'iot.cmdOpen', payload: 'RS485 HEX 01 06 00 00 00 01 48 0A' },
      { id: 'close', labelKey: 'iot.cmdClose', payload: 'RS485 HEX 01 06 00 00 00 00 89 CA' },
    ],
  },
  {
    id: 'GENERIC_RS485',
    deviceType: 'GENERIC',
    hintKey: 'iot.boardHints.GENERIC_RS485',
    commands: [
      { id: 'open', labelKey: 'iot.cmdOpen', payload: 'RS485 open' },
      { id: 'close', labelKey: 'iot.cmdClose', payload: 'RS485 close' },
    ],
  },
]

export function boardsForDevice(deviceType: IotDeviceType): IotBoardProfile[] {
  return IOT_BOARDS.filter((board) => board.deviceType === deviceType)
}

export function defaultBoardId(deviceType: IotDeviceType): IotBoardId {
  return boardsForDevice(deviceType)[0]?.id ?? 'GENERIC_RS485'
}

export function boardProfile(boardId: IotBoardId): IotBoardProfile | undefined {
  return IOT_BOARDS.find((board) => board.id === boardId)
}

export function commandsForBoard(boardId: IotBoardId): IotCommand[] {
  return boardProfile(boardId)?.commands ?? []
}

const STORAGE_KEY = 'freepark.planning.iotDevices'

function nowIso(): string {
  return new Date().toISOString()
}

function isBindDirection(value: unknown): value is IotBindDirection {
  return value === 'ENTRANCE' || value === 'EXIT'
}

function isDeviceType(value: unknown): value is IotDeviceType {
  return IOT_DEVICE_TYPES.includes(value as IotDeviceType)
}

function migrateDeviceType(item: Partial<IotDevice> & { modelId?: string }): IotDeviceType {
  if (isDeviceType(item.deviceType)) {
    return item.deviceType
  }
  const modelId = item.modelId ?? ''
  if (modelId.startsWith('USR_')) {
    return 'USR'
  }
  if (modelId === 'GENERIC_DTU') {
    return 'GENERIC'
  }
  return 'USR'
}

function migrateBoardId(
  item: Partial<IotDevice> & { protocol?: string },
  deviceType: IotDeviceType,
): IotBoardId {
  const allowed = boardsForDevice(deviceType).map((board) => board.id)
  if (item.boardId && allowed.includes(item.boardId)) {
    return item.boardId
  }
  if (deviceType === 'USR' && item.protocol === 'MODBUS_RTU') {
    return 'USR_MODBUS'
  }
  return defaultBoardId(deviceType)
}

function normalizeDevice(item: IotDevice): IotDevice {
  const deviceType = migrateDeviceType(item)
  const boardId = migrateBoardId(item, deviceType)
  const bindDirection =
    item.laneId && isBindDirection(item.bindDirection) ? item.bindDirection : null
  return { ...item, deviceType, boardId, bindDirection }
}

function loadDevices(): IotDevice[] {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return []
  }
  try {
    const parsed = JSON.parse(raw) as IotDevice[]
    return parsed.map(normalizeDevice)
  } catch {
    return []
  }
}

function persist(devices: IotDevice[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(devices))
}

export function listIotDevices(): IotDevice[] {
  return loadDevices()
}

export function saveIotDevice(
  input: {
    id?: string
    name: string
    code: string
    deviceType: IotDeviceType
    boardId: IotBoardId
    host: string
    port: number
    enabled: boolean
  },
  existing: IotDevice[],
): IotDevice[] {
  const stamp = nowIso()
  const boardId = boardsForDevice(input.deviceType).some((board) => board.id === input.boardId)
    ? input.boardId
    : defaultBoardId(input.deviceType)
  if (input.id) {
    const next = existing.map((item) => {
      if (item.id !== input.id) {
        return item
      }
      const protocolChanged =
        item.host !== input.host ||
        item.port !== input.port ||
        item.deviceType !== input.deviceType ||
        item.boardId !== boardId
      return {
        ...item,
        name: input.name,
        deviceType: input.deviceType,
        boardId,
        host: input.host,
        port: input.port,
        enabled: input.enabled,
        linkStatus: protocolChanged ? 'DISCONNECTED' : item.linkStatus,
        lastTestAt: protocolChanged ? null : item.lastTestAt,
        updatedAt: stamp,
      }
    })
    persist(next)
    return next
  }
  const created: IotDevice = {
    id: crypto.randomUUID(),
    name: input.name,
    code: input.code,
    deviceType: input.deviceType,
    boardId,
    host: input.host,
    port: input.port,
    enabled: input.enabled,
    linkStatus: 'DISCONNECTED',
    lastTestAt: null,
    laneId: null,
    bindDirection: null,
    createdAt: stamp,
    updatedAt: stamp,
  }
  const next = [created, ...existing]
  persist(next)
  return next
}

export function setIotLinkStatus(
  deviceId: string,
  linkStatus: IotLinkStatus,
  existing: IotDevice[],
): IotDevice[] {
  const stamp = nowIso()
  const next = existing.map((item) =>
    item.id === deviceId
      ? { ...item, linkStatus, lastTestAt: stamp, updatedAt: stamp }
      : item,
  )
  persist(next)
  return next
}

export function bindIotDeviceToLane(
  deviceId: string,
  laneId: string,
  bindDirection: IotBindDirection,
  existing: IotDevice[],
): IotDevice[] {
  const next = existing.map((item) =>
    item.id === deviceId
      ? { ...item, laneId, bindDirection, updatedAt: nowIso() }
      : item,
  )
  persist(next)
  return next
}

export function unbindIotDevice(deviceId: string, existing: IotDevice[]): IotDevice[] {
  const next = existing.map((item) =>
    item.id === deviceId
      ? { ...item, laneId: null, bindDirection: null, updatedAt: nowIso() }
      : item,
  )
  persist(next)
  return next
}

export function wait(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}
