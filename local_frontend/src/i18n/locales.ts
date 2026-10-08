export const SUPPORTED_LOCALES = ['zh-CN', 'en'] as const

export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]

export const DEFAULT_LOCALE: SupportedLocale = 'en'

export const LOCALE_LABELS: Record<SupportedLocale, string> = {
  'zh-CN': '简体中文',
  en: 'English',
}

const STORAGE_KEY = 'freepark.locale'

export function isRtl(_locale: string): boolean {
  return false
}

export function isSupportedLocale(value: string): value is SupportedLocale {
  return SUPPORTED_LOCALES.includes(value as SupportedLocale)
}

export function matchLocale(tag: string): SupportedLocale {
  const normalized = tag.trim().replace('_', '-')
  if (isSupportedLocale(normalized)) {
    return normalized
  }

  const language = normalized.split('-')[0]
  if (language === 'zh') {
    return 'zh-CN'
  }

  if (language && isSupportedLocale(language)) {
    return language
  }

  return DEFAULT_LOCALE
}

export function detectLocale(): SupportedLocale {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved) {
    return matchLocale(saved)
  }

  const candidates = navigator.languages?.length ? navigator.languages : [navigator.language]
  for (const candidate of candidates) {
    if (candidate) {
      return matchLocale(candidate)
    }
  }

  return DEFAULT_LOCALE
}

export function persistLocale(locale: SupportedLocale): void {
  localStorage.setItem(STORAGE_KEY, locale)
}
