<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { ApiError, login } from '@/api/client'
import { setSession } from '@/auth/session'
import LocaleSwitcher from '@/components/LocaleSwitcher.vue'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')

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
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('login.failed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="brand-pane">
      <div>
        <span class="mark">M</span>
        <p class="eyebrow">{{ t('app.console') }}</p>
        <h1>{{ t('app.name') }}</h1>
        <p class="lead">{{ t('login.hero') }}</p>
      </div>
    </section>
    <section class="form-pane">
      <div class="toolbar">
        <LocaleSwitcher />
      </div>
      <form class="card" @submit.prevent="onSubmit">
        <h2>{{ t('login.title') }}</h2>
        <p class="subtitle">{{ t('login.subtitle') }}</p>
        <label>
          <span>{{ t('login.username') }}</span>
          <input v-model="username" type="text" autocomplete="username" name="username" />
        </label>
        <label>
          <span>{{ t('login.password') }}</span>
          <input v-model="password" type="password" autocomplete="current-password" name="password" />
        </label>
        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
        <button type="submit" :disabled="submitting">
          {{ submitting ? t('login.submitting') : t('login.submit') }}
        </button>
      </form>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  min-height: 100%;
}

.brand-pane {
  display: grid;
  align-items: center;
  padding: 3rem;
  background:
    radial-gradient(circle at top left, #1c8f84 0%, transparent 28%),
    var(--sidebar);
  color: #fff;
}

.mark {
  width: 3rem;
  height: 3rem;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: var(--accent);
  font-weight: 800;
  margin-bottom: 1.5rem;
}

.eyebrow {
  margin: 0 0 0.5rem;
  color: #9ab5ae;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-size: 0.78rem;
}

.brand-pane h1 {
  margin: 0 0 0.75rem;
  font-size: clamp(2rem, 4vw, 3rem);
}

.lead {
  margin: 0;
  max-width: 24rem;
  color: #d5e4df;
}

.form-pane {
  display: grid;
  align-content: center;
  padding: 2rem;
  background: var(--bg);
  position: relative;
}

.toolbar {
  position: absolute;
  inset-block-start: 1.25rem;
  inset-inline-end: 1.25rem;
}

.card {
  width: min(420px, 100%);
  margin: 0 auto;
  display: grid;
  gap: 0.9rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.6rem;
  box-shadow: var(--shadow);
}

h2 {
  margin: 0;
}

.subtitle {
  margin: -0.4rem 0 0.4rem;
  color: var(--muted);
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

@media (max-width: 860px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-pane {
    padding: 2rem 1.5rem;
  }
}
</style>
