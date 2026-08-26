import type { PlateColor } from '@/api/client'

const STORAGE_KEY = 'freepark.planning.laneSimEvents'
const MAX_EVENTS = 200

export type LaneSimDirection = 'ENTRANCE' | 'EXIT'

export type LaneSimResult = 'ALLOWED' | 'INTERCEPTED'

export interface LaneSimEvent {
  id: string
  laneId: string
  lotId: string
  plateNumber: string
  plateColor: PlateColor
  direction: LaneSimDirection
  result: LaneSimResult
  /** Optional note keys or free text, e.g. blacklisted_vehicle, whitelist_match, no_open_session */
  remark: string
  createdAt: string
}

export interface SimulateLaneEventInput {
  laneId: string
  lotId: string
  plateNumber: string
  plateColor: PlateColor
  direction: LaneSimDirection
  /** Decided result from the backend access decision API. */
  result: LaneSimResult
  /** Optional note key from the backend decision, e.g. blacklisted_vehicle. */
  remark?: string
}

function nowIso(): string {
  return new Date().toISOString()
}

function newId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `sim-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

function loadEvents(): LaneSimEvent[] {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return []
  }
  try {
    const parsed = JSON.parse(raw) as LaneSimEvent[]
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed
  } catch {
    return []
  }
}

function persist(events: LaneSimEvent[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(events.slice(0, MAX_EVENTS)))
}

export function listLaneSimEvents(laneId?: string): LaneSimEvent[] {
  const all = loadEvents()
  if (!laneId) {
    return all
  }
  return all.filter((item) => item.laneId === laneId)
}

/** Plates currently considered in-lot for a lot (last ENTRANCE ALLOWED without later EXIT ALLOWED). */
export function listOpenSimSessions(lotId: string): string[] {
  const events = loadEvents()
    .filter((item) => item.lotId === lotId && item.result === 'ALLOWED')
    .slice()
    .reverse()
  const open = new Set<string>()
  const closed = new Set<string>()
  for (const event of events) {
    const plate = event.plateNumber.toUpperCase()
    if (closed.has(plate) || open.has(plate)) {
      continue
    }
    if (event.direction === 'EXIT') {
      closed.add(plate)
    } else {
      open.add(plate)
    }
  }
  return [...open]
}

export function hasOpenSimSession(lotId: string, plateNumber: string): boolean {
  const plate = plateNumber.trim().toUpperCase()
  return listOpenSimSessions(lotId).includes(plate)
}

export function simulateLaneEvent(input: SimulateLaneEventInput): LaneSimEvent {
  const plateNumber = input.plateNumber.trim().toUpperCase()
  const remark = input.remark?.trim() ?? ''

  const event: LaneSimEvent = {
    id: newId(),
    laneId: input.laneId,
    lotId: input.lotId,
    plateNumber,
    plateColor: input.plateColor,
    direction: input.direction,
    result: input.result,
    remark,
    createdAt: nowIso(),
  }

  const next = [event, ...loadEvents()].slice(0, MAX_EVENTS)
  persist(next)
  return event
}

export function clearLaneSimEvents(laneId?: string): void {
  if (!laneId) {
    persist([])
    return
  }
  persist(loadEvents().filter((item) => item.laneId !== laneId))
}
