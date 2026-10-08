<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  getDatabaseSettings,
  restoreDatabaseSettings,
  testDatabaseSettings,
  updateDatabaseSettings,
  type DatabaseSettingsSource,
} from '@/api/client'
import { formatSiteTime } from '@/composables/useSiteTime'

const { t, locale } = useI18n()

const loading = ref(true)
const testing = ref(false)
const submitting = ref(false)
const restoring = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const host = ref('localhost')
const port = ref(8306)
const database = ref('freepark_local')
const username = ref('freepark_local')
const password = ref('')
const passwordSet = ref(false)
const source = ref<DatabaseSettingsSource>('DEFAULT')
const filePresent = ref(false)
const connected = ref(false)
const updatedAt = ref<string | null>(null)
const testedKey = ref<string | null>(null)

const busy = computed(() => testing.value || submitting.value || restoring.value)

const formKey = computed(() =>
  JSON.stringify({
    host: host.value.trim(),
    port: Number(port.value),
    database: database.value.trim(),
    username: username.value.trim(),
    password: password.value,
  }),
)

const canSave = computed(() => testedKey.value === formKey.value)

const passwordPlaceholder = computed(() =>
  passwordSet.value
    ? t('databaseConfig.passwordKeepPlaceholder')
    : t('databaseConfig.passwordEmptyPlaceholder'),
)

const sourceLabel = computed(() => {
  if (source.value === 'FILE') {
    return t('databaseConfig.sourceFile')
  }
  if (source.value === 'ENVIRONMENT') {
    return t('databaseConfig.sourceEnvironment')
  }
  return t('databaseConfig.sourceDefault')
})

function applySettings(data: {
  host: string
  port: number
  database: string
  username: string
  passwordSet: boolean
  source: DatabaseSettingsSource
  filePresent: boolean
  connected: boolean
  updatedAt: string | null
}): void {
  host.value = data.host
  port.value = data.port
  database.value = data.database
  username.value = data.username
  password.value = ''
  passwordSet.value = data.passwordSet
  source.value = data.source
  filePresent.value = data.filePresent
  connected.value = data.connected
  updatedAt.value = data.updatedAt
  testedKey.value = null
}

function payload() {
  return {
    host: host.value.trim(),
    port: Number(port.value),
    database: database.value.trim(),
    username: username.value.trim(),
    password: password.value,
  }
}

function validate(): string {
  if (!host.value.trim()) {
    return t('databaseConfig.hostRequired')
  }
  const nextPort = Number(port.value)
  if (!Number.isInteger(nextPort) || nextPort < 1 || nextPort > 65535) {
    return t('databaseConfig.portInvalid')
  }
  if (!/^[A-Za-z0-9_]{1,64}$/.test(database.value.trim())) {
    return t('databaseConfig.databaseInvalid')
  }
  if (!username.value.trim()) {
    return t('databaseConfig.usernameRequired')
  }
  return ''
}

async function loadSettings(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await getDatabaseSettings(locale.value)
    applySettings(result.data)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('databaseConfig.loadFailed')
  } finally {
    loading.value = false
  }
}

async function onTest(): Promise<void> {
  const invalid = validate()
  if (invalid) {
    errorMessage.value = invalid
    successMessage.value = ''
    return
  }
  testing.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await testDatabaseSettings(payload(), locale.value)
    testedKey.value = formKey.value
    successMessage.value = t('databaseConfig.testOk')
  } catch (error) {
    testedKey.value = null
    errorMessage.value = error instanceof ApiError ? error.message : t('databaseConfig.testFailed')
  } finally {
    testing.value = false
  }
}

async function onSave(): Promise<void> {
  const invalid = validate()
  if (invalid) {
    errorMessage.value = invalid
    successMessage.value = ''
    return
  }
  if (!canSave.value) {
    errorMessage.value = t('databaseConfig.saveNeedsTest')
    successMessage.value = ''
    return
  }
  submitting.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await updateDatabaseSettings(payload(), locale.value)
    applySettings(result.data)
    successMessage.value = t('databaseConfig.saved')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('databaseConfig.saveFailed')
  } finally {
    submitting.value = false
  }
}

async function onRestore(): Promise<void> {
  if (!window.confirm(t('databaseConfig.restoreConfirm'))) {
    return
  }
  restoring.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await restoreDatabaseSettings(locale.value)
    applySettings(result.data)
    successMessage.value = t('databaseConfig.restored')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('databaseConfig.restoreFailed')
  } finally {
    restoring.value = false
  }
}

onMounted(() => {
  void loadSettings()
})
</script>

<template>
  <section class="page">
    <p v-if="loading" class="hint">{{ t('databaseConfig.loading') }}</p>
    <form v-else class="page-form" @submit.prevent="onSave">
      <article class="card">
        <h3>{{ t('databaseConfig.title') }}</h3>
        <p class="hint">{{ t('databaseConfig.hint') }}</p>
        <div class="status-row">
          <span class="pill" :class="connected ? 'ok' : 'bad'">
            {{ connected ? t('databaseConfig.connected') : t('databaseConfig.disconnected') }}
          </span>
          <span class="meta">{{ t('databaseConfig.source') }}：{{ sourceLabel }}</span>
          <span v-if="updatedAt" class="meta">
            {{ t('databaseConfig.lastUpdated') }}：{{ formatSiteTime(updatedAt) }}
          </span>
        </div>
        <div class="form form-row">
          <label>
            <span>{{ t('databaseConfig.host') }}</span>
            <input
              v-model="host"
              type="text"
              autocomplete="off"
              :placeholder="t('databaseConfig.hostPlaceholder')"
            />
          </label>
          <label>
            <span>{{ t('databaseConfig.port') }}</span>
            <input v-model.number="port" type="number" min="1" max="65535" />
          </label>
        </div>
        <div class="form form-row">
          <label>
            <span>{{ t('databaseConfig.database') }}</span>
            <input
              v-model="database"
              type="text"
              autocomplete="off"
              :placeholder="t('databaseConfig.databasePlaceholder')"
            />
          </label>
          <label>
            <span>{{ t('databaseConfig.username') }}</span>
            <input v-model="username" type="text" autocomplete="off" />
          </label>
        </div>
        <label>
          <span>{{ t('databaseConfig.password') }}</span>
          <input
            v-model="password"
            type="password"
            autocomplete="new-password"
            :placeholder="passwordPlaceholder"
          />
        </label>
        <p class="field-hint">{{ t('databaseConfig.passwordHint') }}</p>
        <p v-if="!canSave && !errorMessage && !successMessage" class="field-hint">
          {{ t('databaseConfig.saveNeedsTest') }}
        </p>
        <p v-if="errorMessage" class="message error">{{ errorMessage }}</p>
        <p v-if="successMessage" class="message ok">{{ successMessage }}</p>
        <div class="actions">
          <button
            v-if="filePresent"
            type="button"
            class="ghost danger"
            :disabled="busy"
            @click="onRestore"
          >
            {{ restoring ? t('databaseConfig.restoring') : t('databaseConfig.restore') }}
          </button>
          <button type="button" class="ghost" :disabled="busy" @click="onTest">
            {{ testing ? t('databaseConfig.testing') : t('databaseConfig.test') }}
          </button>
          <button type="submit" :disabled="busy || !canSave">
            {{ submitting ? t('databaseConfig.saving') : t('databaseConfig.save') }}
          </button>
        </div>
      </article>
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
  max-width: 44rem;
}

.card {
  display: grid;
  gap: 0.75rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.2rem 1.25rem;
  box-shadow: var(--shadow);
}

.card h3 {
  margin: 0;
}

.hint {
  margin: 0;
  color: var(--muted);
  font-size: 0.9rem;
  line-height: 1.5;
}

.status-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.55rem 0.9rem;
}

.pill {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 0.15rem 0.65rem;
  font-size: 0.8rem;
  font-weight: 600;
}

.pill.ok {
  color: #0f5132;
  background: #d1e7dd;
}

.pill.bad {
  color: #842029;
  background: #f8d7da;
}

.meta {
  color: var(--muted);
  font-size: 0.85rem;
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

label span {
  font-size: 0.9rem;
}

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

.field-hint {
  margin: 0;
  color: var(--muted);
  font-size: 0.82rem;
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

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.5rem;
}

button {
  border: 0;
  border-radius: 8px;
  padding: 0.55rem 0.95rem;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
  cursor: pointer;
}

.ghost {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
}

.ghost.danger {
  border-color: #f1c0c4;
  color: var(--danger);
}

button:disabled,
.ghost:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

@media (max-width: 720px) {
  .form-row {
    grid-template-columns: 1fr;
  }

  .actions {
    flex-direction: column-reverse;
  }

  .actions button {
    width: 100%;
  }
}
</style>
