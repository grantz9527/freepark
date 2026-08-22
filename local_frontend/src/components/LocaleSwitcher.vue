<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import {
  persistLocale,
  LOCALE_LABELS,
  SUPPORTED_LOCALES,
  type SupportedLocale,
} from '@/i18n/locales'

defineProps<{ compact?: boolean }>()

const { t, locale } = useI18n()

function onChange(event: Event): void {
  const value = (event.target as HTMLSelectElement).value as SupportedLocale
  locale.value = value
  persistLocale(value)
}
</script>

<template>
  <label class="locale-switcher" :class="{ compact }">
    <span v-if="!compact" class="locale-switcher__label">{{ t('locale.label') }}</span>
    <select
      class="locale-switcher__select"
      :value="locale"
      :aria-label="t('locale.label')"
      @change="onChange"
    >
      <option v-for="code in SUPPORTED_LOCALES" :key="code" :value="code">
        {{ LOCALE_LABELS[code] }}
      </option>
    </select>
  </label>
</template>

<style scoped>
.locale-switcher {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
}

.locale-switcher__label {
  color: var(--muted);
}

.locale-switcher.compact .locale-switcher__select {
  min-height: 2rem;
  padding: 0.28rem 0.5rem;
  font-size: 0.85rem;
}

.locale-switcher__select {
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text);
  border-radius: 8px;
  padding: 0.4rem 0.6rem;
  font: inherit;
}
</style>
