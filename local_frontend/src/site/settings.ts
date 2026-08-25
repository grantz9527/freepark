import { ref } from 'vue'

import { getSystemSettings, type PlateColor, type SystemSettingsView } from '@/api/client'
import { DEFAULT_LOCALE, isSupportedLocale, type SupportedLocale } from '@/i18n/locales'

export const siteTimezone = ref('Asia/Shanghai')
export const siteDefaultLocale = ref<SupportedLocale>(DEFAULT_LOCALE)
export const siteDefaultPlateColor = ref<PlateColor>('BLUE')
export const siteAllowedPlateColors = ref<PlateColor[]>([
  'BLUE',
  'YELLOW',
  'GREEN',
  'YELLOW_GREEN',
  'BLACK',
  'WHITE',
])
export const siteImageStoragePath = ref('./data/images')

let loadedForSession = false
let loadingPromise: Promise<void> | null = null

export function applySiteSettings(data: SystemSettingsView): void {
  siteTimezone.value = data.timezone
  if (isSupportedLocale(data.defaultLocale)) {
    siteDefaultLocale.value = data.defaultLocale
  }
  siteDefaultPlateColor.value = data.defaultPlateColor
  siteAllowedPlateColors.value = [...data.allowedPlateColors]
  siteImageStoragePath.value = data.imageStoragePath || './data/images'
  loadedForSession = true
}

export function clearSiteSettingsCache(): void {
  loadedForSession = false
  loadingPromise = null
  siteTimezone.value = 'Asia/Shanghai'
  siteDefaultLocale.value = DEFAULT_LOCALE
  siteDefaultPlateColor.value = 'BLUE'
  siteAllowedPlateColors.value = ['BLUE', 'YELLOW', 'GREEN', 'YELLOW_GREEN', 'BLACK', 'WHITE']
  siteImageStoragePath.value = './data/images'
}

export async function ensureSiteSettings(locale: string, force = false): Promise<void> {
  if (loadedForSession && !force) {
    return
  }
  if (loadingPromise && !force) {
    return loadingPromise
  }

  loadingPromise = (async () => {
    try {
      const result = await getSystemSettings(locale)
      applySiteSettings(result.data)
    } catch {
      loadedForSession = false
    } finally {
      loadingPromise = null
    }
  })()

  return loadingPromise
}
