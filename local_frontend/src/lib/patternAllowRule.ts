export type PatternMatchMode = 'startsWith' | 'endsWith' | 'contains'

/** Escape a literal so it is safe inside a regex. */
export function escapeRegexLiteral(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * Build a full-string regex for plate allow rules.
 * Uses .* anchors so Java Pattern.matches / full-match semantics work.
 */
export function buildAllowPattern(mode: PatternMatchMode, keyword: string): string {
  const escaped = escapeRegexLiteral(keyword.trim())
  if (!escaped) {
    return ''
  }
  switch (mode) {
    case 'startsWith':
      return `^${escaped}.*`
    case 'endsWith':
      return `.*${escaped}$`
    case 'contains':
      return `.*${escaped}.*`
  }
}

export function testAllowPattern(pattern: string, plate: string): boolean {
  if (!pattern.trim() || !plate.trim()) {
    return false
  }
  try {
    return new RegExp(pattern).test(plate.trim())
  } catch {
    return false
  }
}
