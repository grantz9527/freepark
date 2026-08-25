export type BarrierLinkStatus = 'DISCONNECTED' | 'CONNECTED' | 'FAILED'

export type BarrierCameraType = 'ZHENSHI' | 'HUAXIA' | 'QIANYI' | 'GENERIC'

export type BarrierBoardId = 'ZS_IO' | 'ZS_RS485' | 'HX_RELAY' | 'QY_IO' | 'GENERIC_RELAY'

export interface BarrierCommand {
  id: string
  labelKey: string
  payload: string
}

export interface BarrierBoardProfile {
  id: BarrierBoardId
  cameraType: BarrierCameraType
  hintKey: string
  commands: BarrierCommand[]
}

export type BarrierBindDirection = 'ENTRANCE' | 'EXIT'

export interface BarrierDevice {
  id: string
  name: string
  code: string
  cameraType: BarrierCameraType
  boardId: BarrierBoardId
  host: string
  port: number
  enabled: boolean
  linkStatus: BarrierLinkStatus
  lastTestAt: string | null
  laneId: string | null
  bindDirection: BarrierBindDirection | null
  createdAt: string
  updatedAt: string
}

export const BARRIER_CAMERA_TYPES: BarrierCameraType[] = ['ZHENSHI', 'HUAXIA', 'QIANYI', 'GENERIC']

export const BARRIER_BOARDS: BarrierBoardProfile[] = [
  {
    id: 'ZS_IO',
    cameraType: 'ZHENSHI',
    hintKey: 'barriers.boardHints.ZS_IO',
    commands: [
      { id: 'open', labelKey: 'barriers.cmdOpen', payload: 'IO1 pulse 500ms HIGH' },
      { id: 'close', labelKey: 'barriers.cmdClose', payload: 'IO2 pulse 500ms HIGH' },
      { id: 'stayOpen', labelKey: 'barriers.cmdStayOpen', payload: 'IO1 hold HIGH' },
      { id: 'lock', labelKey: 'barriers.cmdLock', payload: 'IO3 hold HIGH' },
    ],
  },
  {
    id: 'ZS_RS485',
    cameraType: 'ZHENSHI',
    hintKey: 'barriers.boardHints.ZS_RS485',
    commands: [
      { id: 'open', labelKey: 'barriers.cmdOpen', payload: 'RS485 01 06 00 01 00 01' },
      { id: 'close', labelKey: 'barriers.cmdClose', payload: 'RS485 01 06 00 01 00 00' },
      { id: 'query', labelKey: 'barriers.cmdQuery', payload: 'RS485 01 03 00 00 00 01' },
    ],
  },
  {
    id: 'HX_RELAY',
    cameraType: 'HUAXIA',
    hintKey: 'barriers.boardHints.HX_RELAY',
    commands: [
      { id: 'open', labelKey: 'barriers.cmdOpen', payload: 'RELAY1 ON 300ms' },
      { id: 'close', labelKey: 'barriers.cmdClose', payload: 'RELAY2 ON 300ms' },
    ],
  },
  {
    id: 'QY_IO',
    cameraType: 'QIANYI',
    hintKey: 'barriers.boardHints.QY_IO',
    commands: [
      { id: 'open', labelKey: 'barriers.cmdOpen', payload: 'GPIO3 HIGH 200ms' },
      { id: 'close', labelKey: 'barriers.cmdClose', payload: 'GPIO4 HIGH 200ms' },
      { id: 'query', labelKey: 'barriers.cmdQuery', payload: 'STATUS GET' },
    ],
  },
  {
    id: 'GENERIC_RELAY',
    cameraType: 'GENERIC',
    hintKey: 'barriers.boardHints.GENERIC_RELAY',
    commands: [
      { id: 'open', labelKey: 'barriers.cmdOpen', payload: 'RELAY open' },
      { id: 'close', labelKey: 'barriers.cmdClose', payload: 'RELAY close' },
    ],
  },
]

export function boardsForCamera(cameraType: BarrierCameraType): BarrierBoardProfile[] {
  return BARRIER_BOARDS.filter((board) => board.cameraType === cameraType)
}

export function defaultBoardId(cameraType: BarrierCameraType): BarrierBoardId {
  return boardsForCamera(cameraType)[0]?.id ?? 'GENERIC_RELAY'
}

export function boardProfile(boardId: BarrierBoardId): BarrierBoardProfile | undefined {
  return BARRIER_BOARDS.find((board) => board.id === boardId)
}

export function commandsForBoard(boardId: BarrierBoardId): BarrierCommand[] {
  return boardProfile(boardId)?.commands ?? []
}

const STORAGE_KEY = 'freepark.planning.barrierDevices'

function nowIso(): string {
  return new Date().toISOString()
}

function isBindDirection(value: unknown): value is BarrierBindDirection {
  return value === 'ENTRANCE' || value === 'EXIT'
}

function normalizeDevice(item: BarrierDevice): BarrierDevice {
  const cameraType = item.cameraType ?? 'ZHENSHI'
  const allowed = boardsForCamera(cameraType).map((board) => board.id)
  const boardId = allowed.includes(item.boardId) ? item.boardId : defaultBoardId(cameraType)
  const bindDirection =
    item.laneId && isBindDirection(item.bindDirection) ? item.bindDirection : null
  return { ...item, cameraType, boardId, bindDirection }
}

function loadDevices(): BarrierDevice[] {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return []
  }
  try {
    const parsed = JSON.parse(raw) as BarrierDevice[]
    return parsed.map(normalizeDevice)
  } catch {
    return []
  }
}

function persist(devices: BarrierDevice[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(devices))
}

export function listBarrierDevices(): BarrierDevice[] {
  return loadDevices()
}

export function saveBarrierDevice(
  input: {
    id?: string
    name: string
    code: string
    cameraType: BarrierCameraType
    boardId: BarrierBoardId
    host: string
    port: number
    enabled: boolean
  },
  existing: BarrierDevice[],
): BarrierDevice[] {
  const stamp = nowIso()
  const boardId = boardsForCamera(input.cameraType).some((board) => board.id === input.boardId)
    ? input.boardId
    : defaultBoardId(input.cameraType)
  if (input.id) {
    const next = existing.map((item) => {
      if (item.id !== input.id) {
        return item
      }
      const protocolChanged =
        item.host !== input.host ||
        item.port !== input.port ||
        item.cameraType !== input.cameraType ||
        item.boardId !== boardId
      return {
        ...item,
        name: input.name,
        cameraType: input.cameraType,
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
  const created: BarrierDevice = {
    id: crypto.randomUUID(),
    name: input.name,
    code: input.code,
    cameraType: input.cameraType,
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

export function setBarrierLinkStatus(
  deviceId: string,
  linkStatus: BarrierLinkStatus,
  existing: BarrierDevice[],
): BarrierDevice[] {
  const stamp = nowIso()
  const next = existing.map((item) =>
    item.id === deviceId
      ? { ...item, linkStatus, lastTestAt: stamp, updatedAt: stamp }
      : item,
  )
  persist(next)
  return next
}

export function bindBarrierToLane(
  deviceId: string,
  laneId: string,
  bindDirection: BarrierBindDirection,
  existing: BarrierDevice[],
): BarrierDevice[] {
  const next = existing.map((item) =>
    item.id === deviceId
      ? { ...item, laneId, bindDirection, updatedAt: nowIso() }
      : item,
  )
  persist(next)
  return next
}

export function unbindBarrier(deviceId: string, existing: BarrierDevice[]): BarrierDevice[] {
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
