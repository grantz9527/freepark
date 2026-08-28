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
export type SoftwarePlateProvider = 'YOLO26_PLATE' | 'HYPER_LPR3'
export const softwarePlateProvider = ref<SoftwarePlateProvider>('YOLO26_PLATE')
export interface Yolo26PlateSettings {
  enabled: boolean
  baseUrl: string
  minConfidence: number
  connectTimeoutMs: number
  readTimeoutMs: number
}
export const yolo26PlateSettings = ref<Yolo26PlateSettings>({
  enabled: false,
  baseUrl: 'http://127.0.0.1:8780',
  minConfidence: 0.25,
  connectTimeoutMs: 5000,
  readTimeoutMs: 60000,
})
export interface HyperLpr3Settings {
  enabled: boolean
  baseUrl: string
  minConfidence: number
  connectTimeoutMs: number
  readTimeoutMs: number
}
export const hyperLpr3Settings = ref<HyperLpr3Settings>({
  enabled: false,
  baseUrl: 'http://127.0.0.1:8715',
  minConfidence: 0.6,
  connectTimeoutMs: 5000,
  readTimeoutMs: 60000,
})

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
  softwarePlateProvider.value =
    data.softwarePlateProvider === 'HYPER_LPR3' ? 'HYPER_LPR3' : 'YOLO26_PLATE'
  yolo26PlateSettings.value = {
    enabled: !!data.yolo26Plate?.enabled,
    baseUrl: data.yolo26Plate?.baseUrl || 'http://127.0.0.1:8780',
    minConfidence: clamp01(data.yolo26Plate?.minConfidence ?? 0.25),
    connectTimeoutMs: clampInt(data.yolo26Plate?.connectTimeoutMs ?? 5000, 1000, 600000),
    readTimeoutMs: clampInt(data.yolo26Plate?.readTimeoutMs ?? 60000, 1000, 600000),
  }
  hyperLpr3Settings.value = {
    enabled: !!data.hyperLpr3?.enabled,
    baseUrl: data.hyperLpr3?.baseUrl || 'http://127.0.0.1:8715',
    minConfidence: clamp01(data.hyperLpr3?.minConfidence ?? 0.6),
    connectTimeoutMs: clampInt(data.hyperLpr3?.connectTimeoutMs ?? 5000, 1000, 600000),
    readTimeoutMs: clampInt(data.hyperLpr3?.readTimeoutMs ?? 60000, 1000, 600000),
  }
  loadedForSession = true
}

function clamp01(v: number): number {
  if (Number.isNaN(v)) return 0.25
  return Math.max(0, Math.min(1, v))
}
function clampInt(v: number, lo: number, hi: number): number {
  if (!Number.isFinite(v)) return (lo + hi) / 2
  return Math.max(lo, Math.min(hi, v))
}

export function clearSiteSettingsCache(): void {
  loadedForSession = false
  loadingPromise = null
  siteTimezone.value = 'Asia/Shanghai'
  siteDefaultLocale.value = DEFAULT_LOCALE
  siteDefaultPlateColor.value = 'BLUE'
  siteAllowedPlateColors.value = ['BLUE', 'YELLOW', 'GREEN', 'YELLOW_GREEN', 'BLACK', 'WHITE']
  siteImageStoragePath.value = './data/images'
  softwarePlateProvider.value = 'YOLO26_PLATE'
  yolo26PlateSettings.value = {
    enabled: false,
    baseUrl: 'http://127.0.0.1:8780',
    minConfidence: 0.25,
    connectTimeoutMs: 5000,
    readTimeoutMs: 60000,
  }
  hyperLpr3Settings.value = {
    enabled: false,
    baseUrl: 'http://127.0.0.1:8715',
    minConfidence: 0.6,
    connectTimeoutMs: 5000,
    readTimeoutMs: 60000,
  }
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
