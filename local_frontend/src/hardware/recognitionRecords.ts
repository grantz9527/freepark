import {
  createRecognitionRecordApi,
  listRecognitionRecordsApi,
  markRecognitionAbnormalApi,
  markRecognitionVoidedApi,
  type CreateRecognitionRecordInput,
  type ParkingFlowResult,
  type PlateColor,
  type RecognitionDirection,
  type RecognitionEventType,
  type RecognitionRecord,
} from '@/api/client'

export type {
  CreateRecognitionRecordInput,
  RecognitionDirection,
  RecognitionEventType,
  RecognitionRecord,
} from '@/api/client'

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

export async function listRecognitionRecords(
  locale: string,
  filters?: {
    lotId?: string
    laneId?: string
    keyword?: string
    eventType?: RecognitionEventType
    abnormalOnly?: boolean
  },
): Promise<RecognitionRecord[]> {
  const result = await listRecognitionRecordsApi(locale, filters ?? {})
  return result.data
}

/** 创建识别记录，后端会联动停车流水并自动标记异常（exit_unmatched）。 */
export function createRecognitionRecord(
  input: CreateRecognitionRecordInput,
  locale: string,
): Promise<ParkingFlowResult> {
  return createRecognitionRecordApi(input, locale).then((result) => result.data)
}

export async function markRecognitionAbnormal(
  recordId: string,
  reason: string,
  locale: string,
): Promise<RecognitionRecord> {
  const result = await markRecognitionAbnormalApi(recordId, reason, locale)
  return result.data
}

/** 标记识别记录为作废（例如其关联停车流水被作废）。 */
export async function markRecognitionVoided(recordId: string, locale: string): Promise<RecognitionRecord> {
  const result = await markRecognitionVoidedApi(recordId, locale)
  return result.data
}
