export const SUPPORTED_LOCALES = [
  'en',
  'zh-CN',
  'zh-TW',
  'ja',
  'ko',
  'es',
  'fr',
  'de',
  'pt',
  'ar',
] as const

export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]

export const DEFAULT_LOCALE: SupportedLocale = 'en'

export const LOCALE_LABELS: Record<SupportedLocale, string> = {
  en: 'English',
  'zh-CN': '简体中文',
  'zh-TW': '繁體中文',
  ja: '日本語',
  ko: '한국어',
  es: 'Español',
  fr: 'Français',
  de: 'Deutsch',
  pt: 'Português',
  ar: 'العربية',
}

const STORAGE_KEY = 'freepark.locale'

export function isRtl(locale: string): boolean {
  return locale === 'ar'
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
    const region = normalized.toLowerCase()
    if (
      region.includes('tw') ||
      region.includes('hk') ||
      region.includes('mo') ||
      region.includes('hant')
    ) {
      return 'zh-TW'
    }
    return 'zh-CN'
  }

  if (language && isSupportedLocale(language)) {
    return language
  }

  return DEFAULT_LOCALE
}

export function detectLocale(): SupportedLocale {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved && isSupportedLocale(saved)) {
    return saved
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
