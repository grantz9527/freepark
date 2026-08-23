<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { getHealth, getI18n, type I18nView } from '@/api/client'
import { getUser } from '@/auth/session'

const { t, locale } = useI18n()
const user = computed(() => getUser())
const displayName = computed(() => user.value?.displayName ?? user.value?.username ?? '')
const username = computed(() => user.value?.username ?? '')
const role = computed(() => {
  const value = user.value?.role
  if (!value) {
    return ''
  }
  const key = `roles.${value}`
  const label = t(key)
  return label === key ? value : label
})

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
  <div class="dashboard">
    <section class="intro">
      <div>
        <h2>{{ t('dashboard.welcome', { name: displayName }) }}</h2>
        <p>{{ t('dashboard.subtitle') }}</p>
      </div>
      <button type="button" class="ghost" @click="loadBackend">{{ t('home.retry') }}</button>
    </section>

    <section class="kpis">
      <article class="kpi">
        <p>{{ t('dashboard.system') }}</p>
        <strong :class="backendOk ? 'ok' : 'fail'">
          {{ loading ? '…' : backendOk ? t('dashboard.online') : t('dashboard.offline') }}
        </strong>
        <span>{{ health || '—' }}</span>
      </article>
      <article class="kpi">
        <p>{{ t('dashboard.user') }}</p>
        <strong>{{ displayName }}</strong>
        <span>{{ username }}</span>
      </article>
      <article class="kpi">
        <p>{{ t('dashboard.role') }}</p>
        <strong>{{ role }}</strong>
        <span>{{ t('app.console') }}</span>
      </article>
      <article class="kpi">
        <p>{{ t('dashboard.locale') }}</p>
        <strong>{{ locale }}</strong>
        <span v-if="backend">{{ backend.locale }}</span>
      </article>
    </section>

    <section class="grid">
      <article class="panel">
        <div class="panel-head">
          <h3>{{ t('home.backend') }}</h3>
          <span class="pill" :class="backendOk ? 'ok' : 'fail'">
            {{ backendOk ? t('home.backendOk') : t('home.backendFail') }}
          </span>
        </div>
        <dl>
          <div>
            <dt>{{ t('home.backendLocale') }}</dt>
            <dd>{{ backend?.locale ?? '—' }}</dd>
          </div>
          <div>
            <dt>{{ t('home.backendWelcome') }}</dt>
            <dd>{{ backend?.welcome ?? '—' }}</dd>
          </div>
        </dl>
      </article>
    </section>
  </div>
</template>

<style scoped>
.dashboard {
  display: grid;
  gap: 1rem;
}

.intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.intro h2 {
  margin: 0;
  font-size: 1.35rem;
}

.intro p {
  margin: 0.3rem 0 0;
  color: var(--muted);
}

.ghost {
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text);
  border-radius: 8px;
  padding: 0.45rem 0.8rem;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.9rem;
}

.kpi,
.panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow);
}

.kpi {
  padding: 1rem 1.1rem;
}

.kpi p,
.kpi span {
  margin: 0;
  color: var(--muted);
  font-size: 0.85rem;
}

.kpi strong {
  display: block;
  margin: 0.35rem 0 0.2rem;
  font-size: 1.25rem;
}

.grid {
  display: grid;
  gap: 0.9rem;
}

.panel {
  padding: 1.1rem 1.2rem;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.panel-head h3 {
  margin: 0;
  font-size: 1rem;
}

.pill {
  border-radius: 999px;
  padding: 0.15rem 0.6rem;
  font-size: 0.78rem;
  background: #f2f4f3;
}

dl {
  display: grid;
  gap: 0.85rem;
  margin: 0;
}

dt {
  color: var(--muted);
  font-size: 0.8rem;
}

dd {
  margin: 0.2rem 0 0;
}

.ok {
  color: var(--ok);
}

.fail {
  color: var(--danger);
}

@media (max-width: 960px) {
  .kpis {
    grid-template-columns: 1fr;
  }
}
</style>
