import { siteDefaultLocale, siteTimezone } from '@/site/settings'

function timezoneOffsetMs(utcMs: number, timeZone: string): number {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-GB', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(new Date(utcMs))
      .map((part) => [part.type, part.value]),
  )
  const asUtc = Date.UTC(
    Number(parts.year),
    Number(parts.month) - 1,
    Number(parts.day),
    Number(parts.hour),
    Number(parts.minute),
    Number(parts.second),
  )
  return asUtc - utcMs
}

export function formatSiteTime(value: string | null | undefined): string {
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

/** 带秒的时间格式（用于识别记录等需要精确到秒的页面）。 */
export function formatSiteTimeWithSeconds(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  try {
    return new Intl.DateTimeFormat(siteDefaultLocale.value, {
      dateStyle: 'medium',
      timeStyle: 'medium',
      timeZone: siteTimezone.value,
    }).format(new Date(value))
  } catch {
    return value
  }
}

export function toDateTimeLocal(value: string | null | undefined, timeZone = siteTimezone.value): string {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-GB', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(date)
      .map((part) => [part.type, part.value]),
  )
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`
}

export function fromDateTimeLocal(value: string, timeZone = siteTimezone.value): string {
  if (!value) {
    return ''
  }
  const normalized = value.length === 16 ? `${value}:00Z` : `${value}Z`
  const naiveUtc = Date.parse(normalized)
  if (Number.isNaN(naiveUtc)) {
    return ''
  }
  return new Date(naiveUtc - timezoneOffsetMs(naiveUtc, timeZone)).toISOString()
}

export function defaultDateTimeLocal(offsetMs = 0): string {
  return toDateTimeLocal(new Date(Date.now() + offsetMs).toISOString())
}

export function useSiteTime() {
  return {
    formatTime: formatSiteTime,
    formatTimeWithSeconds: formatSiteTimeWithSeconds,
    toDateTimeLocal,
    fromDateTimeLocal,
    defaultDateTimeLocal,
  }
}
