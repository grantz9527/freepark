import { siteDefaultLocale, siteTimezone } from '@/site/settings'

export function formatSiteTime(value: string): string {
  if (!value) {
    return ''
  }
  try {
    return new Intl.DateTimeFormat(siteDefaultLocale.value, {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: siteTimezone.value,
    }).format(new Date(value))
  } catch {
    return value
  }
}

export function useSiteTime() {
  return { formatTime: formatSiteTime }
}
