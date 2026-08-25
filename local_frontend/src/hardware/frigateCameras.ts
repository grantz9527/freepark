export type FrigateLinkStatus = 'DISCONNECTED' | 'CONNECTED' | 'FAILED'

export type FrigateBindDirection = 'ENTRANCE' | 'EXIT'

export interface FrigateServer {
  apiHost: string
  apiPort: number
  mqttHost: string
  mqttPort: number
  topicPrefix: string
  mqttUsername: string
  enabled: boolean
  linkStatus: FrigateLinkStatus
  lastTestAt: string | null
  updatedAt: string
}

export interface FrigateCamera {
  id: string
  name: string
  cameraName: string
  enabled: boolean
  linkStatus: FrigateLinkStatus
  lastTestAt: string | null
  laneId: string | null
  bindDirection: FrigateBindDirection | null
  linkageEnabled: boolean
  lastPlate: string | null
  lastEventAt: string | null
  createdAt: string
  updatedAt: string
}

const SERVER_KEY = 'freepark.planning.frigateServer'
const CAMERA_KEY = 'freepark.planning.frigateCameras'

function nowIso(): string {
  return new Date().toISOString()
}

function isBindDirection(value: unknown): value is FrigateBindDirection {
  return value === 'ENTRANCE' || value === 'EXIT'
}

function isLinkStatus(value: unknown): value is FrigateLinkStatus {
  return value === 'DISCONNECTED' || value === 'CONNECTED' || value === 'FAILED'
}

function defaultServer(): FrigateServer {
  return {
    apiHost: '127.0.0.1',
    apiPort: 5000,
    mqttHost: '127.0.0.1',
    mqttPort: 1883,
    topicPrefix: 'frigate',
    mqttUsername: '',
    enabled: true,
    linkStatus: 'DISCONNECTED',
    lastTestAt: null,
    updatedAt: nowIso(),
  }
}

function normalizeServer(item: Partial<FrigateServer>): FrigateServer {
  const fallback = defaultServer()
  return {
    apiHost: item.apiHost?.trim() || fallback.apiHost,
    apiPort: Number(item.apiPort) > 0 ? Number(item.apiPort) : fallback.apiPort,
    mqttHost: item.mqttHost?.trim() || fallback.mqttHost,
    mqttPort: Number(item.mqttPort) > 0 ? Number(item.mqttPort) : fallback.mqttPort,
    topicPrefix: item.topicPrefix?.trim() || fallback.topicPrefix,
    mqttUsername: item.mqttUsername?.trim() ?? '',
    enabled: item.enabled !== false,
    linkStatus: isLinkStatus(item.linkStatus) ? item.linkStatus : 'DISCONNECTED',
    lastTestAt: item.lastTestAt ?? null,
    updatedAt: item.updatedAt ?? nowIso(),
  }
}

function normalizeCamera(item: FrigateCamera): FrigateCamera {
  const bindDirection =
    item.laneId && isBindDirection(item.bindDirection) ? item.bindDirection : null
  return {
    ...item,
    cameraName: item.cameraName?.trim() || item.name,
    enabled: item.enabled !== false,
    linkStatus: isLinkStatus(item.linkStatus) ? item.linkStatus : 'DISCONNECTED',
    bindDirection,
    linkageEnabled: item.linkageEnabled !== false,
    lastPlate: item.lastPlate ?? null,
    lastEventAt: item.lastEventAt ?? null,
  }
}

export function loadFrigateServer(): FrigateServer {
  const raw = localStorage.getItem(SERVER_KEY)
  if (!raw) {
    return defaultServer()
  }
  try {
    return normalizeServer(JSON.parse(raw) as Partial<FrigateServer>)
  } catch {
    return defaultServer()
  }
}

export function saveFrigateServer(input: Omit<FrigateServer, 'updatedAt'>): FrigateServer {
  const current = loadFrigateServer()
  const next = normalizeServer({
    ...input,
    linkStatus:
      current.apiHost !== input.apiHost ||
      current.apiPort !== input.apiPort ||
      current.mqttHost !== input.mqttHost ||
      current.mqttPort !== input.mqttPort ||
      current.topicPrefix !== input.topicPrefix
        ? 'DISCONNECTED'
        : input.linkStatus,
    lastTestAt:
      current.apiHost !== input.apiHost ||
      current.apiPort !== input.apiPort ||
      current.mqttHost !== input.mqttHost ||
      current.mqttPort !== input.mqttPort ||
      current.topicPrefix !== input.topicPrefix
        ? null
        : input.lastTestAt,
    updatedAt: nowIso(),
  })
  localStorage.setItem(SERVER_KEY, JSON.stringify(next))
  return next
}

export function setFrigateServerLinkStatus(
  linkStatus: FrigateLinkStatus,
  current: FrigateServer,
): FrigateServer {
  const next = {
    ...current,
    linkStatus,
    lastTestAt: nowIso(),
    updatedAt: nowIso(),
  }
  localStorage.setItem(SERVER_KEY, JSON.stringify(next))
  return next
}

function loadCameras(): FrigateCamera[] {
  const raw = localStorage.getItem(CAMERA_KEY)
  if (!raw) {
    return []
  }
  try {
    const parsed = JSON.parse(raw) as FrigateCamera[]
    return parsed.map(normalizeCamera)
  } catch {
    return []
  }
}

function persistCameras(cameras: FrigateCamera[]): void {
  localStorage.setItem(CAMERA_KEY, JSON.stringify(cameras))
}

export function listFrigateCameras(): FrigateCamera[] {
  return loadCameras()
}

export function eventTopicOf(cameraName: string, server = loadFrigateServer()): string {
  const prefix = server.topicPrefix.replace(/\/+$/, '')
  return `${prefix}/${cameraName}`
}

export function saveFrigateCamera(
  input: {
    id?: string
    name: string
    cameraName: string
    enabled: boolean
  },
  existing: FrigateCamera[],
): FrigateCamera[] {
  const stamp = nowIso()
  if (input.id) {
    const next = existing.map((item) => {
      if (item.id !== input.id) {
        return item
      }
      const nameChanged = item.cameraName !== input.cameraName
      return {
        ...item,
        name: input.name,
        cameraName: input.cameraName,
        enabled: input.enabled,
        linkStatus: nameChanged ? 'DISCONNECTED' : item.linkStatus,
        lastTestAt: nameChanged ? null : item.lastTestAt,
        updatedAt: stamp,
      }
    })
    persistCameras(next)
    return next
  }
  const created: FrigateCamera = {
    id: crypto.randomUUID(),
    name: input.name,
    cameraName: input.cameraName,
    enabled: input.enabled,
    linkStatus: 'DISCONNECTED',
    lastTestAt: null,
    laneId: null,
    bindDirection: null,
    linkageEnabled: true,
    lastPlate: null,
    lastEventAt: null,
    createdAt: stamp,
    updatedAt: stamp,
  }
  const next = [created, ...existing]
  persistCameras(next)
  return next
}

export function setFrigateCameraLinkStatus(
  cameraId: string,
  linkStatus: FrigateLinkStatus,
  existing: FrigateCamera[],
): FrigateCamera[] {
  const stamp = nowIso()
  const next = existing.map((item) =>
    item.id === cameraId
      ? { ...item, linkStatus, lastTestAt: stamp, updatedAt: stamp }
      : item,
  )
  persistCameras(next)
  return next
}

export function bindFrigateCameraToLane(
  cameraId: string,
  laneId: string,
  bindDirection: FrigateBindDirection,
  linkageEnabled: boolean,
  existing: FrigateCamera[],
): FrigateCamera[] {
  const next = existing.map((item) =>
    item.id === cameraId
      ? {
          ...item,
          laneId,
          bindDirection,
          linkageEnabled,
          updatedAt: nowIso(),
        }
      : item,
  )
  persistCameras(next)
  return next
}

export function unbindFrigateCamera(cameraId: string, existing: FrigateCamera[]): FrigateCamera[] {
  const next = existing.map((item) =>
    item.id === cameraId
      ? {
          ...item,
          laneId: null,
          bindDirection: null,
          updatedAt: nowIso(),
        }
      : item,
  )
  persistCameras(next)
  return next
}

export function recordFrigateRecognition(
  cameraId: string,
  plate: string,
  existing: FrigateCamera[],
): FrigateCamera[] {
  const stamp = nowIso()
  const next = existing.map((item) =>
    item.id === cameraId
      ? { ...item, lastPlate: plate, lastEventAt: stamp, updatedAt: stamp }
      : item,
  )
  persistCameras(next)
  return next
}

export function wait(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}
