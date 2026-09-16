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
    <section class="card">
      <header class="head">
        <div>
          <h2>{{ t('dashboard.welcome', { name: displayName }) }}</h2>
          <p class="lede">{{ t('dashboard.subtitle') }}</p>
        </div>
        <button type="button" class="ghost" @click="loadBackend">{{ t('home.retry') }}</button>
      </header>

      <div class="about">
        <h3>{{ t('dashboard.aboutTitle') }}</h3>
        <p class="body">{{ t('dashboard.aboutBody') }}</p>
        <div class="meta">
          <span class="badge">{{ t('dashboard.aboutOfflineBadge') }}</span>
          <p>{{ t('dashboard.aboutCloud') }}</p>
        </div>
      </div>

      <dl class="stats">
        <div>
          <dt>{{ t('dashboard.system') }}</dt>
          <dd :class="backendOk ? 'ok' : 'fail'">
            {{ loading ? '…' : backendOk ? t('dashboard.online') : t('dashboard.offline') }}
          </dd>
          <small>{{ health || '—' }}</small>
        </div>
        <div>
          <dt>{{ t('home.backend') }}</dt>
          <dd :class="backendOk ? 'ok' : 'fail'">
            {{ backendOk ? t('home.backendOk') : t('home.backendFail') }}
          </dd>
          <small>{{ backend?.locale ?? '—' }}</small>
        </div>
        <div>
          <dt>{{ t('dashboard.user') }}</dt>
          <dd>{{ displayName }}</dd>
          <small>{{ username }}</small>
        </div>
        <div>
          <dt>{{ t('dashboard.role') }}</dt>
          <dd>{{ role }}</dd>
          <small>{{ t('app.console') }}</small>
        </div>
      </dl>
    </section>
  </div>
</template>

<style scoped>
.dashboard {
  display: grid;
}

.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow);
  overflow: hidden;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.35rem 1.5rem 0;
}

.head h2 {
  margin: 0;
  font-size: 1.28rem;
  letter-spacing: -0.02em;
  line-height: 1.3;
}

.lede {
  margin: 0.28rem 0 0;
  color: var(--muted);
  font-size: 0.9rem;
}

.ghost {
  flex-shrink: 0;
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
  border-radius: 8px;
  padding: 0.45rem 0.8rem;
  cursor: pointer;
}

.ghost:hover {
  background: #f4f8f6;
}

.about {
  padding: 1.15rem 1.5rem 1.35rem;
}

.about h3 {
  margin: 0 0 0.45rem;
  font-size: 0.72rem;
  font-weight: 650;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--accent);
}

.body {
  margin: 0;
  color: var(--text);
  font-size: 0.98rem;
  line-height: 1.75;
  overflow-wrap: break-word;
}

.meta {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  margin-top: 0.95rem;
}

.badge {
  flex-shrink: 0;
  margin-top: 0.1rem;
  border-radius: 999px;
  padding: 0.18rem 0.65rem;
  background: #ecf8f0;
  color: var(--ok);
  font-size: 0.75rem;
  font-weight: 650;
  line-height: 1.4;
}

.meta p {
  margin: 0;
  color: var(--muted);
  font-size: 0.88rem;
  line-height: 1.55;
}

.stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin: 0;
  border-top: 1px solid var(--border);
  background: #f7faf8;
}

.stats > div {
  padding: 0.95rem 1.25rem 1.05rem;
}

.stats > div:nth-child(odd) {
  border-right: 1px solid #e4ece8;
}

.stats > div:nth-child(-n + 2) {
  border-bottom: 1px solid #e4ece8;
}

dt {
  color: var(--muted);
  font-size: 0.75rem;
}

dd {
  margin: 0.28rem 0 0;
  font-size: 1.02rem;
  font-weight: 650;
  line-height: 1.3;
}

small {
  display: block;
  margin-top: 0.15rem;
  color: var(--muted);
  font-size: 0.75rem;
}

.ok {
  color: var(--ok);
}

.fail {
  color: var(--danger);
}

@media (min-width: 1280px) {
  .stats {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .stats > div {
    border-right: 1px solid #e4ece8;
    border-bottom: 0;
  }

  .stats > div:nth-child(odd) {
    border-right: 1px solid #e4ece8;
  }

  .stats > div:last-child {
    border-right: 0;
  }
}

@media (max-width: 860px) {
  .meta {
    flex-direction: column;
  }
}
</style>
