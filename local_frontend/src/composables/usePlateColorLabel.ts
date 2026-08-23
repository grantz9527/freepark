import { useI18n } from 'vue-i18n'

import type { PlateColor } from '@/api/client'

export function usePlateColorLabel() {
  const { t } = useI18n()

  function plateColorLabel(color: PlateColor): string {
    const key = `plateColors.${color}`
    const label = t(key)
    return label === key ? color : label
  }

  return { plateColorLabel }
}
