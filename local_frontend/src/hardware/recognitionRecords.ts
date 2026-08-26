import type { PlateColor } from '@/api/client'

const STORAGE_KEY = 'freepark.planning.recognitionRecords'
const MAX_RECORDS = 500

export type RecognitionEventType = 'MANUAL' | 'DEVICE'
export type RecognitionDirection = 'ENTRANCE' | 'EXIT'

export interface RecognitionRecord {
  id: string
  lotId: string
  lotName: string
  laneId: string | null
  laneName: string | null
  plateNumber: string
  plateColor: PlateColor
  /** ISO timestamp */
  eventTime: string
  /** Image URL or data URL; null when unavailable */
  eventImage: string | null
  eventType: RecognitionEventType
  direction: RecognitionDirection | null
  /** True when exit could not match an open entry session, etc. */
  abnormal: boolean
  abnormalReason: string | null
  /** True when the linked parking session was voided. */
  voided: boolean
  sourceSimEventId?: string
  createdAt: string
}

export interface CreateRecognitionRecordInput {
  lotId: string
  lotName: string
  laneId?: string | null
  laneName?: string | null
  plateNumber: string
  plateColor: PlateColor
  eventTime?: string
  eventImage?: string | null
  eventType: RecognitionEventType
  direction?: RecognitionDirection | null
  abnormal?: boolean
  abnormalReason?: string | null
  sourceSimEventId?: string
}

function nowIso(): string {
  return new Date().toISOString()
}

function newId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `rec-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

function loadRecords(): RecognitionRecord[] {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return []
  }
  try {
    const parsed = JSON.parse(raw) as RecognitionRecord[]
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed.map(normalizeRecord)
  } catch {
    return []
  }
}

function normalizeRecord(item: RecognitionRecord): RecognitionRecord {
  return {
    ...item,
    abnormal: Boolean(item.abnormal),
    abnormalReason: item.abnormalReason ?? null,
    voided: Boolean(item.voided),
  }
}

function persist(records: RecognitionRecord[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(records.slice(0, MAX_RECORDS)))
}

/** Placeholder capture image for simulated device events. */
export function buildSimEventImage(plateNumber: string, plateColor: PlateColor): string {
  const plate = plateNumber.trim().toUpperCase() || '----'
  const label = plate.length > 10 ? `${plate.slice(0, 10)}…` : plate
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="320" height="180" viewBox="0 0 320 180">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#1f2933"/>
      <stop offset="100%" stop-color="#3d4f5f"/>
    </linearGradient>
  </defs>
  <rect width="320" height="180" fill="url(#g)"/>
  <rect x="24" y="48" width="272" height="72" rx="8" fill="#f5f7f6" stroke="#111827" stroke-width="3"/>
  <text x="160" y="94" text-anchor="middle" font-family="ui-monospace,Consolas,monospace" font-size="28" font-weight="700" fill="#111827">${escapeXml(label)}</text>
  <text x="160" y="148" text-anchor="middle" font-family="sans-serif" font-size="12" fill="#cbd5e1">${escapeXml(plateColor)}</text>
</svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

export function listRecognitionRecords(filters?: {
  lotId?: string
  laneId?: string
  keyword?: string
  eventType?: RecognitionEventType
  abnormalOnly?: boolean
}): RecognitionRecord[] {
  let items = loadRecords()
  if (filters?.lotId) {
    items = items.filter((item) => item.lotId === filters.lotId)
  }
  if (filters?.laneId) {
    items = items.filter((item) => item.laneId === filters.laneId)
  }
  if (filters?.eventType) {
    items = items.filter((item) => item.eventType === filters.eventType)
  }
  if (filters?.abnormalOnly) {
    items = items.filter((item) => item.abnormal)
  }
  const keyword = filters?.keyword?.trim().toLowerCase()
  if (keyword) {
    items = items.filter(
      (item) =>
        item.plateNumber.toLowerCase().includes(keyword) ||
        (item.laneName ?? '').toLowerCase().includes(keyword) ||
        (item.lotName ?? '').toLowerCase().includes(keyword),
    )
  }
  return items
}

export function createRecognitionRecord(input: CreateRecognitionRecordInput): RecognitionRecord {
  const stamp = input.eventTime?.trim() || nowIso()
  const record: RecognitionRecord = {
    id: newId(),
    lotId: input.lotId,
    lotName: input.lotName.trim(),
    laneId: input.laneId ?? null,
    laneName: input.laneName?.trim() || null,
    plateNumber: input.plateNumber.trim().toUpperCase(),
    plateColor: input.plateColor,
    eventTime: stamp,
    eventImage: input.eventImage ?? null,
    eventType: input.eventType,
    direction: input.direction ?? null,
    abnormal: Boolean(input.abnormal),
    abnormalReason: input.abnormalReason ?? null,
    voided: false,
    sourceSimEventId: input.sourceSimEventId,
    createdAt: nowIso(),
  }
  persist([record, ...loadRecords()].slice(0, MAX_RECORDS))
  return record
}

export function markRecognitionAbnormal(
  recordId: string,
  reason: string,
): RecognitionRecord | null {
  let updated: RecognitionRecord | null = null
  const next = loadRecords().map((item) => {
    if (item.id !== recordId) {
      return item
    }
    updated = {
      ...item,
      abnormal: true,
      abnormalReason: reason,
    }
    return updated
  })
  if (updated) {
    persist(next)
  }
  return updated
}

/** Mark a recognition record as voided (e.g. its linked session was voided). */
export function markRecognitionVoided(recordId: string): RecognitionRecord | null {
  let updated: RecognitionRecord | null = null
  const next = loadRecords().map((item) => {
    if (item.id !== recordId) {
      return item
    }
    updated = {
      ...item,
      voided: true,
    }
    return updated
  })
  if (updated) {
    persist(next)
  }
  return updated
}

export function clearRecognitionRecords(lotId?: string): void {
  if (!lotId) {
    persist([])
    return
  }
  persist(loadRecords().filter((item) => item.lotId !== lotId))
}
