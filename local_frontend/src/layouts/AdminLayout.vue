<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import LocaleSwitcher from '@/components/LocaleSwitcher.vue'
import { clearSession, getUser } from '@/auth/session'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const user = computed(() => getUser())
const displayName = computed(() => user.value?.displayName ?? user.value?.username ?? '')
const role = computed(() => user.value?.role ?? '')
const initials = computed(() => {
  const source = displayName.value.trim()
  return source ? source.slice(0, 1).toUpperCase() : 'A'
})
const pageTitle = computed(() => t(String(route.meta.titleKey ?? 'nav.overview')))

const navItems = computed(() => [
  { to: '/', name: 'home', label: t('nav.overview'), group: 'ops', icon: 'overview' },
  { to: '/lots', name: 'lots', label: t('nav.lots'), group: 'ops', icon: 'lots' },
  { to: '/spaces', name: 'spaces', label: t('nav.spaces'), group: 'ops', icon: 'spaces' },
  { to: '/operators', name: 'operators', label: t('nav.operators'), group: 'system', icon: 'operators' },
  { to: '/settings', name: 'settings', label: t('nav.settings'), group: 'system', icon: 'settings' },
])

function isActive(name: string): boolean {
  return route.name === name
}

function logout(): void {
  clearSession()
  void router.replace({ name: 'login' })
}
</script>

<template>
  <div class="admin">
    <aside class="sidebar">
      <div class="brand">
        <span class="mark" aria-hidden="true">P</span>
        <div>
          <strong>{{ t('app.name') }}</strong>
          <p>{{ t('app.console') }}</p>
        </div>
      </div>

      <nav class="nav">
        <p class="group">{{ t('nav.sectionOps') }}</p>
        <RouterLink
          v-for="item in navItems.filter((entry) => entry.group === 'ops')"
          :key="item.name"
          :to="item.to"
          class="nav-link"
          :class="{ active: isActive(item.name) }"
        >
          <span class="icon" aria-hidden="true">
            <svg v-if="item.icon === 'overview'" viewBox="0 0 24 24" fill="none">
              <rect x="3" y="3" width="8" height="8" rx="1.5" stroke="currentColor" stroke-width="1.8" />
              <rect x="13" y="3" width="8" height="5" rx="1.5" stroke="currentColor" stroke-width="1.8" />
              <rect x="13" y="10" width="8" height="11" rx="1.5" stroke="currentColor" stroke-width="1.8" />
              <rect x="3" y="13" width="8" height="8" rx="1.5" stroke="currentColor" stroke-width="1.8" />
            </svg>
            <svg v-else-if="item.icon === 'lots'" viewBox="0 0 24 24" fill="none">
              <path d="M4 20V8.5L12 4l8 4.5V20" stroke="currentColor" stroke-width="1.8" />
              <path d="M9 20v-7h6v7" stroke="currentColor" stroke-width="1.8" />
            </svg>
            <svg v-else-if="item.icon === 'spaces'" viewBox="0 0 24 24" fill="none">
              <rect x="3" y="6" width="18" height="12" rx="2" stroke="currentColor" stroke-width="1.8" />
              <path d="M8 10h3.2a2.4 2.4 0 0 1 0 4.8H8V10Z" stroke="currentColor" stroke-width="1.8" />
            </svg>
          </span>
          {{ item.label }}
        </RouterLink>

        <p class="group">{{ t('nav.sectionSystem') }}</p>
        <RouterLink
          v-for="item in navItems.filter((entry) => entry.group === 'system')"
          :key="item.name"
          :to="item.to"
          class="nav-link"
          :class="{ active: isActive(item.name) }"
        >
          <span class="icon" aria-hidden="true">
            <svg v-if="item.icon === 'operators'" viewBox="0 0 24 24" fill="none">
              <circle cx="9" cy="8" r="3" stroke="currentColor" stroke-width="1.8" />
              <path d="M4 19a5 5 0 0 1 10 0" stroke="currentColor" stroke-width="1.8" />
              <circle cx="17" cy="9" r="2.2" stroke="currentColor" stroke-width="1.8" />
              <path d="M16 19a4 4 0 0 1 4-4" stroke="currentColor" stroke-width="1.8" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.8" />
              <path
                d="M12 4.5v1.8M12 17.7v1.8M19.5 12h-1.8M6.3 12H4.5M16.9 7.1l-1.3 1.3M8.4 15.6l-1.3 1.3M16.9 16.9l-1.3-1.3M8.4 8.4 7.1 7.1"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
              />
            </svg>
          </span>
          {{ item.label }}
        </RouterLink>
      </nav>
    </aside>

    <div class="workspace">
      <header class="topbar">
        <div>
          <p class="crumb">{{ t('app.name') }} / {{ pageTitle }}</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="top-actions">
          <LocaleSwitcher compact />
          <div class="account">
            <span class="avatar">{{ initials }}</span>
            <div>
              <strong>{{ displayName }}</strong>
              <p>{{ role }}</p>
            </div>
            <button type="button" class="logout" @click="logout">{{ t('nav.logout') }}</button>
          </div>
        </div>
      </header>
      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin {
  display: flex;
  min-height: 100%;
  background: var(--bg);
}

.sidebar {
  width: 248px;
  flex-shrink: 0;
  background: var(--sidebar);
  color: #fff;
  padding: 1.25rem 0.9rem;
  display: flex;
  flex-direction: column;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.35rem 0.5rem 1.25rem;
}

.brand p {
  margin: 0.15rem 0 0;
  color: var(--sidebar-text);
  font-size: 0.8rem;
}

.mark {
  width: 2.25rem;
  height: 2.25rem;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: var(--accent);
  font-weight: 800;
}

.nav {
  display: grid;
  gap: 0.25rem;
}

.group {
  margin: 1rem 0.6rem 0.4rem;
  color: #7f938c;
  font-size: 0.72rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.55rem 0.75rem;
  border-radius: 8px;
  color: var(--sidebar-text);
}

.icon {
  width: 1.05rem;
  height: 1.05rem;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.icon svg {
  width: 100%;
  height: 100%;
}

.nav-link:hover,
.nav-link.active {
  background: var(--sidebar-active);
  color: #fff;
}

.workspace {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.5rem;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

.crumb {
  margin: 0;
  color: var(--muted);
  font-size: 0.8rem;
}

.topbar h1 {
  margin: 0.1rem 0 0;
  font-size: 1.15rem;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.account {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding-inline-start: 0.85rem;
  border-inline-start: 1px solid var(--border);
}

.account p {
  margin: 0;
  color: var(--muted);
  font-size: 0.75rem;
}

.avatar {
  width: 2rem;
  height: 2rem;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: #d9efe9;
  color: var(--accent-dark);
  font-weight: 700;
}

.logout {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
  border-radius: 8px;
  padding: 0.35rem 0.7rem;
}

.content {
  padding: 1.25rem 1.5rem 2rem;
}

@media (max-width: 860px) {
  .admin {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
  }

  .nav {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .topbar,
  .account {
    flex-wrap: wrap;
  }
}
</style>
