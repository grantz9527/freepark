<script setup lang="ts">
import { watchEffect } from 'vue'
import { RouterView } from 'vue-router'
import { useI18n } from 'vue-i18n'

import LocaleSwitcher from '@/components/LocaleSwitcher.vue'
import { isRtl } from '@/i18n/locales'

const { t, locale } = useI18n()

watchEffect(() => {
  document.documentElement.lang = locale.value
  document.documentElement.dir = isRtl(locale.value) ? 'rtl' : 'ltr'
  document.title = t('app.name')
})
</script>

<template>
  <div class="shell">
    <header class="header">
      <div class="brand">
        <span class="mark" aria-hidden="true">P</span>
        <div>
          <strong>{{ t('app.name') }}</strong>
          <p>{{ t('app.tagline') }}</p>
        </div>
      </div>
      <LocaleSwitcher />
    </header>
    <RouterView />
  </div>
</template>

<style scoped>
.shell {
  width: min(1080px, calc(100% - 2rem));
  margin: 0 auto;
  padding: 1.5rem 0 3rem;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 2rem;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.brand p {
  margin: 0.15rem 0 0;
  color: var(--muted);
  font-size: 0.9rem;
}

.mark {
  width: 2.5rem;
  height: 2.5rem;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: var(--accent);
  color: #fff;
  font-weight: 800;
}
</style>
