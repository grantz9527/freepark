import type { PlateColor } from '@/api/client'
import type { RecognitionRecord } from '@/hardware/recognitionRecords'

const STORAGE_KEY = 'freepark.planning.parkingSessions'
const MAX_SESSIONS = 500

export type ParkingSessionStatus = 'OPEN' | 'CLOSED' | 'VOIDED'

export interface ParkingSession {
  id: string
  lotId: string
  lotName: string
  plateNumber: string
  plateColor: PlateColor
  status: ParkingSessionStatus
  entryTime: string
  entryLaneId: string | null
  entryLaneName: string | null
  entryRecognitionId: string | null
  entryImage: string | null
  exitTime: string | null
  exitLaneId: string | null
  exitLaneName: string | null
  exitRecognitionId: string | null
  exitImage: string | null
  createdAt: string
  updatedAt: string
}

function nowIso(): string {
  return new Date().toISOString()
}

function newId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `ps-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

function loadSessions(): ParkingSession[] {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return []
  }
  try {
    const parsed = JSON.parse(raw) as ParkingSession[]
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed
  } catch {
    return []
  }
}

function persist(sessions: ParkingSession[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions.slice(0, MAX_SESSIONS)))
}

export function listParkingSessions(filters?: {
  lotId?: string
  keyword?: string
  status?: ParkingSessionStatus
}): ParkingSession[] {
  let items = loadSessions()
  if (filters?.lotId) {
    items = items.filter((item) => item.lotId === filters.lotId)
  }
  if (filters?.status) {
    items = items.filter((item) => item.status === filters.status)
  }
  const keyword = filters?.keyword?.trim().toLowerCase()
  if (keyword) {
    items = items.filter(
      (item) =>
        item.plateNumber.toLowerCase().includes(keyword) ||
        (item.entryLaneName ?? '').toLowerCase().includes(keyword) ||
        (item.exitLaneName ?? '').toLowerCase().includes(keyword) ||
        (item.lotName ?? '').toLowerCase().includes(keyword),
    )
  }
  return items
}

export function hasOpenParkingSession(lotId: string, plateNumber: string): boolean {
  const plate = plateNumber.trim().toUpperCase()
  return loadSessions().some(
    (item) => item.lotId === lotId && item.plateNumber === plate && item.status === 'OPEN',
  )
}

/**
 * Find the open entry session for this plate/lot whose entryTime is before exitTime
 * and closest to exitTime (nearest prior unmatched entry).
 */
export function findBestOpenSessionForExit(
  lotId: string,
  plateNumber: string,
  exitTime: string,
): ParkingSession | null {
  const plate = plateNumber.trim().toUpperCase()
  const exitMs = Date.parse(exitTime)
  if (Number.isNaN(exitMs)) {
    return null
  }
  const candidates = loadSessions().filter((item) => {
    if (item.lotId !== lotId || item.plateNumber !== plate || item.status !== 'OPEN') {
      return false
    }
    const entryMs = Date.parse(item.entryTime)
    if (Number.isNaN(entryMs)) {
      return false
    }
    return entryMs < exitMs
  })
  if (candidates.length === 0) {
    return null
  }
  candidates.sort((a, b) => Date.parse(b.entryTime) - Date.parse(a.entryTime))
  return candidates[0] ?? null
}

export function openParkingSessionFromEntry(record: RecognitionRecord): ParkingSession {
  const stamp = nowIso()
  const session: ParkingSession = {
    id: newId(),
    lotId: record.lotId,
    lotName: record.lotName,
    plateNumber: record.plateNumber.trim().toUpperCase(),
    plateColor: record.plateColor,
    status: 'OPEN',
    entryTime: record.eventTime,
    entryLaneId: record.laneId,
    entryLaneName: record.laneName,
    entryRecognitionId: record.id,
    entryImage: record.eventImage,
    exitTime: null,
    exitLaneId: null,
    exitLaneName: null,
    exitRecognitionId: null,
    exitImage: null,
    createdAt: stamp,
    updatedAt: stamp,
  }
  persist([session, ...loadSessions()].slice(0, MAX_SESSIONS))
  return session
}

export function closeParkingSessionWithExit(
  sessionId: string,
  record: RecognitionRecord,
): ParkingSession | null {
  const stamp = nowIso()
  let updated: ParkingSession | null = null
  const next = loadSessions().map((item) => {
    if (item.id !== sessionId || item.status !== 'OPEN') {
      return item
    }
    updated = {
      ...item,
      status: 'CLOSED',
      exitTime: record.eventTime,
      exitLaneId: record.laneId,
      exitLaneName: record.laneName,
      exitRecognitionId: record.id,
      exitImage: record.eventImage,
      updatedAt: stamp,
    }
    return updated
  })
  if (updated) {
    persist(next)
  }
  return updated
}

export type ParkingFlowResult =
  | { kind: 'entry'; session: ParkingSession }
  | { kind: 'exit_matched'; session: ParkingSession }
  | { kind: 'exit_unmatched' }
  | { kind: 'skipped' }

/**
 * Void a parking session (OPEN or CLOSED). The linked recognition records
 * should be voided separately via markRecognitionVoided.
 */
export function voidParkingSession(sessionId: string): ParkingSession | null {
  const stamp = nowIso()
  let updated: ParkingSession | null = null
  const next = loadSessions().map((item) => {
    if (item.id !== sessionId || item.status === 'VOIDED') {
      return item
    }
    updated = {
      ...item,
      status: 'VOIDED',
      updatedAt: stamp,
    }
    return updated
  })
  if (updated) {
    persist(next)
  }
  return updated
}

/**
 * Apply recognition to parking sessions:
 * - ENTRANCE → create open session
 * - EXIT → match nearest prior unmatched entry; if none, exit_unmatched
 */
export function applyRecognitionToParkingSession(record: RecognitionRecord): ParkingFlowResult {
  if (record.direction === 'ENTRANCE') {
    return { kind: 'entry', session: openParkingSessionFromEntry(record) }
  }
  if (record.direction === 'EXIT') {
    const match = findBestOpenSessionForExit(record.lotId, record.plateNumber, record.eventTime)
    if (!match) {
      return { kind: 'exit_unmatched' }
    }
    const closed = closeParkingSessionWithExit(match.id, record)
    if (!closed) {
      return { kind: 'exit_unmatched' }
    }
    return { kind: 'exit_matched', session: closed }
  }
  return { kind: 'skipped' }
}

export function clearParkingSessions(lotId?: string): void {
  if (!lotId) {
    persist([])
    return
  }
  persist(loadSessions().filter((item) => item.lotId !== lotId))
}
