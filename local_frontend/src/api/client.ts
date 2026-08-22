const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
}

export interface I18nView {
  locale: string
  welcome: string
  supportedLocales: string[]
}

export interface HealthView {
  status: string
}

async function getJson<T>(path: string, locale?: string): Promise<T> {
  const headers: HeadersInit = { Accept: 'application/json' }
  if (locale) {
    headers['Accept-Language'] = locale
  }

  const response = await fetch(`${API_BASE}${path}`, { headers })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return response.json() as Promise<T>
}

export function getI18n(locale: string): Promise<ApiResponse<I18nView>> {
  const query = new URLSearchParams({ lang: locale })
  return getJson(`/api/v1/i18n?${query.toString()}`, locale)
}

export function getHealth(): Promise<HealthView> {
  return getJson('/actuator/health')
}
