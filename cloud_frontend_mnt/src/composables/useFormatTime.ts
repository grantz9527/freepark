export function useFormatTime() {
  function formatTime(value: string): string {
    if (!value) {
      return ''
    }
    try {
      return new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
      }).format(new Date(value))
    } catch {
      return value
    }
  }

  return { formatTime }
}
