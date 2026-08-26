<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { ApiError, getNodeSettings, updateNodeSettings, type NodeMode } from '@/api/client'
import { formatSiteTime } from '@/composables/useSiteTime'

const { t, locale } = useI18n()

const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const mode = ref<NodeMode>('OFFLINE')
const mqttHost = ref('')
const mqttPort = ref(1883)
const mqttClientId = ref('')
const mqttUsername = ref('')
const mqttPassword = ref('')
const mqttPasswordSet = ref(false)
const mqttTopicPrefix = ref('')
const updatedAt = ref('')

const isEdge = computed(() => mode.value === 'EDGE')
const passwordPlaceholder = computed(() =>
  mqttPasswordSet.value
    ? t('nodeConfig.mqttPasswordKeepPlaceholder')
    : t('nodeConfig.mqttPasswordEmptyPlaceholder'),
)

function formatUpdatedAt(iso: string): string {
  return formatSiteTime(iso)
}

async function loadSettings(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getNodeSettings(locale.value)
    applySettings(response.data)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('nodeConfig.loadFailed')
  } finally {
    loading.value = false
  }
}

function applySettings(data: {
  mode: NodeMode
  mqttHost: string
  mqttPort: number
  mqttClientId: string
  mqttUsername: string
  mqttPasswordSet: boolean
  mqttTopicPrefix: string
  updatedAt: string
}): void {
  mode.value = data.mode
  mqttHost.value = data.mqttHost || ''
  mqttPort.value = data.mqttPort || 1883
  mqttClientId.value = data.mqttClientId || ''
  mqttUsername.value = data.mqttUsername || ''
  mqttPasswordSet.value = data.mqttPasswordSet
  mqttPassword.value = ''
  mqttTopicPrefix.value = data.mqttTopicPrefix || ''
  updatedAt.value = data.updatedAt
}

async function onSubmit(): Promise<void> {
  errorMessage.value = ''
  successMessage.value = ''
  if (mode.value === 'EDGE' && !mqttHost.value.trim()) {
    errorMessage.value = t('nodeConfig.hostRequired')
    return
  }
  const port = Number(mqttPort.value)
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    errorMessage.value = t('nodeConfig.portInvalid')
    return
  }
  submitting.value = true
  try {
    const response = await updateNodeSettings(
      {
        mode: mode.value,
        mqttHost: mqttHost.value.trim(),
        mqttPort: port,
        mqttClientId: mqttClientId.value.trim(),
        mqttUsername: mqttUsername.value.trim(),
        mqttPassword: mqttPassword.value,
        mqttTopicPrefix: mqttTopicPrefix.value.trim(),
      },
      locale.value,
    )
    applySettings(response.data)
    successMessage.value = t('nodeConfig.saved')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('nodeConfig.saveFailed')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadSettings()
})
</script>

<template>
  <section class="page">
    <p v-if="loading" class="hint">{{ t('nodeConfig.loading') }}</p>
    <form v-else class="page-form" @submit.prevent="onSubmit">
      <article class="card card-mode">
        <h3>{{ t('nodeConfig.mode') }}</h3>
        <p class="hint">{{ t('nodeConfig.modeHint') }}</p>
        <div class="mode-grid">
          <label class="mode-option" :class="{ active: mode === 'OFFLINE' }">
            <input v-model="mode" type="radio" value="OFFLINE" />
            <strong>{{ t('nodeConfig.modeOffline') }}</strong>
            <span>{{ t('nodeConfig.modeOfflineHint') }}</span>
          </label>
          <label class="mode-option" :class="{ active: mode === 'EDGE' }">
            <input v-model="mode" type="radio" value="EDGE" />
            <strong>{{ t('nodeConfig.modeEdge') }}</strong>
            <span>{{ t('nodeConfig.modeEdgeHint') }}</span>
          </label>
        </div>
      </article>

      <article v-if="isEdge" class="card card-mqtt">
        <h3>{{ t('nodeConfig.mqtt') }}</h3>
        <p class="hint">{{ t('nodeConfig.mqttHint') }}</p>
        <div class="form form-row">
          <label>
            <span>{{ t('nodeConfig.mqttHost') }}</span>
            <input v-model="mqttHost" type="text" :placeholder="t('nodeConfig.mqttHostPlaceholder')" />
          </label>
          <label>
            <span>{{ t('nodeConfig.mqttPort') }}</span>
            <input v-model.number="mqttPort" type="number" min="1" max="65535" />
          </label>
        </div>
        <div class="form form-row">
          <label>
            <span>{{ t('nodeConfig.mqttClientId') }}</span>
            <input
              v-model="mqttClientId"
              type="text"
              :placeholder="t('nodeConfig.mqttClientIdPlaceholder')"
            />
          </label>
          <label>
            <span>{{ t('nodeConfig.mqttUsername') }}</span>
            <input v-model="mqttUsername" type="text" autocomplete="off" />
          </label>
        </div>
        <div class="form">
          <label>
            <span>{{ t('nodeConfig.mqttPassword') }}</span>
            <input
              v-model="mqttPassword"
              type="password"
              autocomplete="new-password"
              :placeholder="passwordPlaceholder"
            />
          </label>
          <label>
            <span>{{ t('nodeConfig.mqttTopicPrefix') }}</span>
            <input
              v-model="mqttTopicPrefix"
              type="text"
              :placeholder="t('nodeConfig.mqttTopicPrefixPlaceholder')"
            />
          </label>
        </div>
      </article>

      <div class="form-footer">
        <div class="footer-meta">
          <p v-if="updatedAt" class="meta">
            {{ t('nodeConfig.lastUpdated') }}: {{ formatUpdatedAt(updatedAt) }}
          </p>
          <p v-if="errorMessage" class="message error">{{ errorMessage }}</p>
          <p v-if="successMessage" class="message ok">{{ successMessage }}</p>
        </div>
        <button type="submit" :disabled="submitting">
          {{ submitting ? t('nodeConfig.saving') : t('nodeConfig.save') }}
        </button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.page {
  display: grid;
  gap: 0.9rem;
  width: 100%;
}

.page-form {
  display: grid;
  gap: 0.9rem;
  max-width: 52rem;
}

.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.2rem 1.25rem;
  box-shadow: var(--shadow);
}

.card h3 {
  margin: 0 0 0.75rem;
}

.hint {
  margin: -0.35rem 0 0.75rem;
  color: var(--muted);
  font-size: 0.9rem;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.9rem;
}

.mode-option {
  display: grid;
  gap: 0.35rem;
  padding: 0.9rem 1rem;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.mode-option input {
  width: auto;
  justify-self: start;
}

.mode-option strong {
  font-size: 0.98rem;
}

.mode-option span {
  color: var(--muted);
  font-size: 0.88rem;
  line-height: 1.5;
}

.mode-option.active {
  border-color: var(--accent);
  background: #f2faf6;
}

.form {
  display: grid;
  gap: 0.75rem;
}

.form-row {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

label {
  display: grid;
  gap: 0.35rem;
}

select,
input[type='text'],
input[type='password'],
input[type='number'] {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
  font: inherit;
  box-sizing: border-box;
}

.form-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem 1rem;
  padding: 0.25rem 0.1rem 0;
}

.footer-meta {
  display: grid;
  gap: 0.55rem;
  min-width: min(100%, 20rem);
  flex: 1;
}

.meta {
  margin: 0;
  color: var(--muted);
  font-size: 0.85rem;
}

.message {
  margin: 0;
  padding: 0.55rem 0.75rem;
  border-radius: 8px;
  font-size: 0.9rem;
}

.message.error {
  color: var(--danger);
  background: #fdecec;
}

.message.ok {
  color: var(--ok);
  background: #e8f5ef;
}

button {
  border: 0;
  border-radius: 8px;
  padding: 0.65rem 1.2rem;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
  justify-self: end;
}

button:disabled {
  opacity: 0.7;
}

@media (max-width: 720px) {
  .mode-grid,
  .form-row {
    grid-template-columns: 1fr;
  }

  .form-footer {
    flex-direction: column;
    align-items: stretch;
  }

  button {
    width: 100%;
  }
}
</style>
