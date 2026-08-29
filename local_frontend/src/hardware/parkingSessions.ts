import {
  hasOpenParkingSessionApi,
  listParkingSessionsApi,
  voidParkingSessionApi,
  type ParkingSession,
  type ParkingSessionStatus,
} from '@/api/client'

export type { ParkingSession, ParkingSessionStatus } from '@/api/client'

export async function listParkingSessions(
  locale: string,
  filters?: {
    lotId?: string
    keyword?: string
    status?: ParkingSessionStatus
  },
): Promise<ParkingSession[]> {
  const result = await listParkingSessionsApi(locale, filters ?? {})
  return result.data
}

export async function hasOpenParkingSession(
  lotId: string,
  plateNumber: string,
  locale: string,
): Promise<boolean> {
  const result = await hasOpenParkingSessionApi(lotId, plateNumber, locale)
  return result.data
}

/** 作废停车流水，后端会联动将关联识别记录标记为作废。 */
export async function voidParkingSession(
  sessionId: string,
  locale: string,
): Promise<ParkingSession> {
  const result = await voidParkingSessionApi(sessionId, locale)
  return result.data
}
