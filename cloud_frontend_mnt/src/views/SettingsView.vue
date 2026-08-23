<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { ApiError, changePassword } from '@/api/client'
import { getUser } from '@/auth/session'

const { t, locale } = useI18n()

const user = computed(() => getUser())
const roleLabel = computed(() => {
  const value = user.value?.role
  if (!value) {
    return '—'
  }
  const key = `roles.${value}`
  const label = t(key)
  return label === key ? value : label
})
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

async function onSubmit(): Promise<void> {
  errorMessage.value = ''
  successMessage.value = ''

  if (!currentPassword.value || !newPassword.value || !confirmPassword.value) {
    errorMessage.value = t('settings.passwordRequired')
    return
  }
  if (newPassword.value.length < 6) {
    errorMessage.value = t('settings.passwordTooShort')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = t('settings.passwordMismatch')
    return
  }

  submitting.value = true
  try {
    await changePassword(currentPassword.value, newPassword.value, locale.value)
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    successMessage.value = t('settings.passwordChanged')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('settings.passwordFailed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="page">
    <article class="card">
      <h3>{{ t('settings.account') }}</h3>
      <dl class="profile">
        <div>
          <dt>{{ t('admins.displayName') }}</dt>
          <dd>{{ user?.displayName ?? '—' }}</dd>
        </div>
        <div>
          <dt>{{ t('login.username') }}</dt>
          <dd>{{ user?.username ?? '—' }}</dd>
        </div>
        <div>
          <dt>{{ t('dashboard.role') }}</dt>
          <dd>{{ roleLabel }}</dd>
        </div>
      </dl>
    </article>

    <article class="card">
      <h3>{{ t('settings.changePassword') }}</h3>
      <p class="hint">{{ t('settings.changePasswordHint') }}</p>
      <form class="form" @submit.prevent="onSubmit">
        <label>
          <span>{{ t('settings.currentPassword') }}</span>
          <input v-model="currentPassword" type="password" autocomplete="current-password" />
        </label>
        <label>
          <span>{{ t('settings.newPassword') }}</span>
          <input v-model="newPassword" type="password" autocomplete="new-password" />
        </label>
        <label>
          <span>{{ t('settings.confirmPassword') }}</span>
          <input v-model="confirmPassword" type="password" autocomplete="new-password" />
        </label>
        <p v-if="errorMessage" class="message error">{{ errorMessage }}</p>
        <p v-if="successMessage" class="message ok">{{ successMessage }}</p>
        <button type="submit" :disabled="submitting">
          {{ submitting ? t('settings.saving') : t('settings.savePassword') }}
        </button>
      </form>
    </article>
  </section>
</template>

<style scoped>
.page {
  display: grid;
  gap: 0.9rem;
  max-width: 560px;
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

.profile {
  display: grid;
  gap: 0.75rem;
  margin: 0;
}

dt {
  color: var(--muted);
  font-size: 0.8rem;
}

dd {
  margin: 0.15rem 0 0;
}

.form {
  display: grid;
  gap: 0.75rem;
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
  padding: 0.65rem 0.9rem;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
}

button:disabled {
  opacity: 0.7;
}
</style>
