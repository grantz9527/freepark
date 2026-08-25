import type { AccessJudgmentRuleType } from '@/api/client'

/** Default order when a lot has no saved access judgment configuration. */
export const DEFAULT_ACCESS_JUDGMENT_ORDER: AccessJudgmentRuleType[] = [
  'BLACKLIST',
  'WHITELIST',
  'PATTERN_ALLOWLIST',
]

const ALL_RULE_TYPES = new Set(DEFAULT_ACCESS_JUDGMENT_ORDER)

export function normalizeAccessJudgmentOrder(
  order: AccessJudgmentRuleType[] | null | undefined,
): AccessJudgmentRuleType[] {
  if (!order || order.length !== DEFAULT_ACCESS_JUDGMENT_ORDER.length) {
    return [...DEFAULT_ACCESS_JUDGMENT_ORDER]
  }
  if (new Set(order).size !== order.length) {
    return [...DEFAULT_ACCESS_JUDGMENT_ORDER]
  }
  for (const rule of order) {
    if (!ALL_RULE_TYPES.has(rule)) {
      return [...DEFAULT_ACCESS_JUDGMENT_ORDER]
    }
  }
  return [...order]
}
