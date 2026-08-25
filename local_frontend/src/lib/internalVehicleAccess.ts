import { listInternalVehicles } from '@/api/client'

/** Whether plate is registered as an enabled internal vehicle on this lot. */
export async function isRegisteredInternalVehicle(
  lotId: string,
  plateNumber: string,
  locale: string,
): Promise<boolean> {
  const plate = plateNumber.trim().toUpperCase()
  if (!plate) {
    return false
  }
  const result = await listInternalVehicles(lotId, locale, { plate, page: 0, size: 20 })
  return result.data.items.some(
    (item) => item.enabled && item.plateNumber.trim().toUpperCase() === plate,
  )
}
