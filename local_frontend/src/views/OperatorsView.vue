<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { ApiError, createOperator, listOperators, type OperatorView } from '@/api/client'
import { getUser } from '@/auth/session'
import { useSiteTime } from '@/composables/useSiteTime'

const { t, locale } = useI18n()
const { formatTime } = useSiteTime()

const loading = ref(false)
const submitting = ref(false)
const operators = ref<OperatorView[]>([])
const errorMessage = ref('')
const showForm = ref(false)
const formUsername = ref('')
const formPassword = ref('')
const formDisplayName = ref('')
const formError = ref('')

const isAdmin = computed(() => getUser()?.role === 'ADMIN')

async function loadOperators(): Promise<void> {
  if (!isAdmin.value) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await listOperators(locale.value)
    operators.value = result.data
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('operators.loadFailed')
  } finally {
    loading.value = false
  }
}

function openForm(): void {
  formUsername.value = ''
  formPassword.value = ''
  formDisplayName.value = ''
  formError.value = ''
  showForm.value = true
}

function closeForm(): void {
  showForm.value = false
}

async function onSubmit(): Promise<void> {
  formError.value = ''
  if (!formUsername.value.trim() || !formPassword.value || !formDisplayName.value.trim()) {
    formError.value = t('operators.formRequired')
    return
  }
  if (formPassword.value.length < 6) {
    formError.value = t('operators.passwordTooShort')
    return
  }

  submitting.value = true
  try {
    const result = await createOperator(
      {
        username: formUsername.value.trim(),
        password: formPassword.value,
        displayName: formDisplayName.value.trim(),
      },
      locale.value,
    )
    operators.value = [result.data, ...operators.value]
    closeForm()
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : t('operators.createFailed')
  } finally {
    submitting.value = false
  }
}

onMounted(loadOperators)
</script>

<template>
  <section class="page">
    <div v-if="!isAdmin" class="notice">
      <strong>{{ t('operators.forbidden') }}</strong>
      <p>{{ t('operators.forbiddenHint') }}</p>
    </div>

    <template v-else>
      <div class="toolbar">
        <label class="search">
          <span class="sr-only">{{ t('page.search') }}</span>
          <input type="search" disabled :placeholder="t('page.search')" />
        </label>
        <button type="button" @click="openForm">{{ t('operators.create') }}</button>
      </div>

      <p v-if="errorMessage" class="banner error">{{ errorMessage }}</p>

      <div class="table-card">
        <table v-if="operators.length > 0">
          <thead>
            <tr>
              <th>{{ t('operators.colDisplayName') }}</th>
              <th>{{ t('operators.colUsername') }}</th>
              <th>{{ t('page.colStatus') }}</th>
              <th>{{ t('page.colUpdated') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in operators" :key="item.id">
              <td>{{ item.displayName }}</td>
              <td>{{ item.username }}</td>
              <td>
                <span class="pill" :class="item.enabled ? 'ok' : 'fail'">
                  {{ item.enabled ? t('operators.statusActive') : t('operators.statusDisabled') }}
                </span>
              </td>
              <td>{{ formatTime(item.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else-if="loading" class="empty">
          <p>{{ t('operators.loading') }}</p>
        </div>
        <div v-else class="empty">
          <strong>{{ t('operators.empty') }}</strong>
          <p>{{ t('operators.emptyHint') }}</p>
        </div>
      </div>
    </template>

    <div v-if="showForm" class="modal-backdrop">
      <form class="modal" @submit.prevent="onSubmit">
        <h3>{{ t('operators.createTitle') }}</h3>
        <p class="hint">{{ t('operators.createHint') }}</p>
        <label>
          <span>{{ t('operators.displayName') }}</span>
          <input v-model="formDisplayName" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('login.username') }}</span>
          <input v-model="formUsername" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('login.password') }}</span>
          <input v-model="formPassword" type="password" autocomplete="new-password" />
        </label>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" @click="closeForm">{{ t('operators.cancel') }}</button>
          <button type="submit" :disabled="submitting">
            {{ submitting ? t('operators.creating') : t('operators.create') }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: grid;
  gap: 0.9rem;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
}

.search {
  flex: 1;
  max-width: 18rem;
}

.search input,
.toolbar button {
  border: 1px solid var(--border);
  border-radius: 8px;
  min-height: 2.25rem;
  padding: 0 0.8rem;
}

.search input {
  width: 100%;
  background: var(--surface);
  color: var(--text);
}

.toolbar button {
  background: var(--accent);
  color: #fff;
  font-weight: 600;
  border-color: transparent;
}

.search input:disabled {
  opacity: 0.55;
}

.table-card,
.notice {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow);
}

.notice {
  padding: 2rem 1.5rem;
  text-align: center;
}

.notice p {
  margin: 0.35rem auto 0;
  max-width: 28rem;
  color: var(--muted);
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: start;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--border);
}

th {
  color: var(--muted);
  font-size: 0.8rem;
  font-weight: 600;
  background: #f7faf8;
}

tbody tr:last-child td {
  border-bottom: 0;
}

.pill {
  border-radius: 999px;
  padding: 0.15rem 0.6rem;
  font-size: 0.78rem;
  background: #f2f4f3;
}

.pill.ok {
  color: var(--ok);
  background: #e8f5ef;
}

.pill.fail {
  color: var(--danger);
  background: #fdecec;
}

.empty {
  padding: 3rem 1.5rem;
  text-align: center;
}

.empty strong {
  display: block;
  margin-bottom: 0.35rem;
}

.empty p {
  margin: 0 auto;
  max-width: 28rem;
  color: var(--muted);
}

.banner.error {
  margin: 0;
  padding: 0.65rem 0.9rem;
  border-radius: 8px;
  color: var(--danger);
  background: #fdecec;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 20, 0.45);
  display: grid;
  place-items: center;
  padding: 1rem;
  z-index: 20;
}

.modal {
  width: min(420px, 100%);
  display: grid;
  gap: 0.75rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: var(--shadow);
}

.modal h3 {
  margin: 0;
}

.hint {
  margin: -0.25rem 0 0;
  color: var(--muted);
  font-size: 0.9rem;
}

label {
  display: grid;
  gap: 0.35rem;
}

input {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
}

.form-error {
  margin: 0;
  color: var(--danger);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.actions button {
  border: 0;
  border-radius: 8px;
  padding: 0.55rem 0.85rem;
  font-weight: 600;
}

.actions button:not(.ghost) {
  color: #fff;
  background: var(--accent);
}

.ghost {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
