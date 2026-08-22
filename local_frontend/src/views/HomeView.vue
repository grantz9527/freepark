<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { getHealth, getI18n, type I18nView } from '@/api/client'

const { t, locale } = useI18n()

const loading = ref(false)
const backendOk = ref(false)
const backend = ref<I18nView | null>(null)
const health = ref('')

async function loadBackend(): Promise<void> {
  loading.value = true
  try {
    const [i18nResult, healthResult] = await Promise.all([getI18n(locale.value), getHealth()])
    backend.value = i18nResult.data
    health.value = healthResult.status
    backendOk.value = i18nResult.success && healthResult.status === 'UP'
  } catch {
    backend.value = null
    health.value = ''
    backendOk.value = false
  } finally {
    loading.value = false
  }
}

onMounted(loadBackend)
watch(locale, loadBackend)
</script>

<template>
  <main class="home">
    <section class="hero">
      <p class="eyebrow">{{ t('app.tagline') }}</p>
      <h1>{{ t('home.title') }}</h1>
      <p class="lead">{{ t('home.description') }}</p>
    </section>

    <section class="cards">
      <article class="card">
        <h2>{{ t('home.frontendLocale') }}</h2>
        <p class="value">{{ locale }}</p>
      </article>

      <article class="card">
        <h2>{{ t('home.backend') }}</h2>
        <p class="value" :class="backendOk ? 'ok' : 'fail'">
          {{ loading ? '…' : backendOk ? t('home.backendOk') : t('home.backendFail') }}
        </p>
        <p v-if="backendOk && backend" class="meta">
          {{ t('home.backendLocale') }}: {{ backend.locale }}
        </p>
        <p v-if="backendOk && backend" class="meta">
          {{ t('home.backendWelcome') }}: {{ backend.welcome }}
        </p>
        <p v-if="backendOk && health" class="meta">health: {{ health }}</p>
        <button type="button" class="retry" @click="loadBackend">{{ t('home.retry') }}</button>
      </article>
    </section>
  </main>
</template>

<style scoped>
.home {
  display: grid;
  gap: 2rem;
}

.eyebrow {
  margin: 0 0 0.5rem;
  color: var(--accent);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  font-size: 0.8rem;
}

h1 {
  margin: 0 0 0.75rem;
  font-size: clamp(1.8rem, 3vw, 2.6rem);
  line-height: 1.2;
}

.lead {
  margin: 0;
  max-width: 42rem;
  color: var(--muted);
  font-size: 1.05rem;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1rem;
}

.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 1.25rem;
}

h2 {
  margin: 0 0 0.5rem;
  font-size: 0.95rem;
  color: var(--muted);
  font-weight: 600;
}

.value {
  margin: 0;
  font-size: 1.4rem;
  font-weight: 700;
}

.ok {
  color: #15803d;
}

.fail {
  color: #b91c1c;
}

.meta {
  margin: 0.5rem 0 0;
  color: var(--muted);
  font-size: 0.95rem;
}

.retry {
  margin-top: 1rem;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text);
  border-radius: 8px;
  padding: 0.4rem 0.8rem;
  font: inherit;
  cursor: pointer;
}
</style>
