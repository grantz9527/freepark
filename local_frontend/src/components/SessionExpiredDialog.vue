<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { ApiError, login } from '@/api/client'
import { getLastUsername, isSessionExpired, setSession } from '@/auth/session'

const { t, locale } = useI18n()
const route = useRoute()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')

const open = computed(() => isSessionExpired() && route.name !== 'login')

watch(
  open,
  (visible) => {
    if (!visible) {
      return
    }
    username.value = getLastUsername()
    password.value = ''
    errorMessage.value = ''
  },
  { immediate: true },
)

async function onSubmit(): Promise<void> {
  errorMessage.value = ''
  if (!username.value.trim() || !password.value) {
    errorMessage.value = t('login.required')
    return
  }

  submitting.value = true
  try {
    const result = await login(username.value.trim(), password.value, locale.value)
    setSession(result.data.token, result.data.user)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('login.failed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div v-if="open" class="expired-backdrop" role="dialog" aria-modal="true" aria-labelledby="expired-title">
    <form class="expired-card" @submit.prevent="onSubmit">
      <h2 id="expired-title">{{ t('login.expiredTitle') }}</h2>
      <p class="expired-note">{{ t('login.expired') }}</p>
      <label>
        <span>{{ t('login.username') }}</span>
        <input v-model="username" type="text" autocomplete="username" name="username" />
      </label>
      <label>
        <span>{{ t('login.password') }}</span>
        <input
          v-model="password"
          type="password"
          autocomplete="current-password"
          name="password"
          autofocus
        />
      </label>
      <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
      <button type="submit" :disabled="submitting">
        {{ submitting ? t('login.submitting') : t('login.submit') }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.expired-backdrop {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 1rem;
  background: rgb(16 33 29 / 62%);
  backdrop-filter: blur(4px);
}

.expired-card {
  width: min(400px, 100%);
  display: grid;
  gap: 0.85rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.5rem 1.45rem 1.35rem;
  box-shadow: 0 18px 48px rgb(16 33 29 / 28%);
}

h2 {
  margin: 0;
  font-size: 1.2rem;
}

.expired-note {
  margin: -0.2rem 0 0.15rem;
  color: #9a6700;
  background: #fffaeb;
  border: 1px solid #fedf89;
  border-radius: 8px;
  padding: 0.55rem 0.7rem;
  font-size: 0.9rem;
  line-height: 1.5;
}

label {
  display: grid;
  gap: 0.35rem;
  font-size: 0.95rem;
}

input {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
}

.error {
  margin: 0;
  color: var(--danger);
}

button {
  border: 0;
  border-radius: 8px;
  padding: 0.7rem 0.9rem;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
}

button:disabled {
  opacity: 0.7;
  cursor: wait;
}
</style>
