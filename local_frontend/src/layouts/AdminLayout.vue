<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import LocaleSwitcher from '@/components/LocaleSwitcher.vue'
import { clearSession, getUser } from '@/auth/session'

interface NavChild {
  to: string
  name: string
  label: string
  icon: string
}

interface NavGroup {
  id: string
  label: string
  children: NavChild[]
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const user = computed(() => getUser())
const displayName = computed(() => user.value?.displayName ?? user.value?.username ?? '')
const role = computed(() => {
  const value = user.value?.role
  if (!value) {
    return ''
  }
  const key = `roles.${value}`
  const label = t(key)
  return label === key ? value : label
})
const initials = computed(() => {
  const source = displayName.value.trim()
  return source ? source.slice(0, 1).toUpperCase() : 'A'
})

const navGroups = computed((): NavGroup[] => {
  const systemChildren: NavChild[] = []
  if (user.value?.role === 'ADMIN') {
    systemChildren.push({
      to: '/operators',
      name: 'operators',
      label: t('nav.operators'),
      icon: 'operators',
    })
  }
  systemChildren.push({
    to: '/settings',
    name: 'settings',
    label: t('nav.settings'),
    icon: 'settings',
  })

  return [
    {
      id: 'ops',
      label: t('nav.sectionOps'),
      children: [
        { to: '/lots', name: 'lots', label: t('nav.lots'), icon: 'lots' },
        { to: '/spaces', name: 'spaces', label: t('nav.spaces'), icon: 'spaces' },
        {
          to: '/internal-vehicles',
          name: 'internalVehicles',
          label: t('nav.internalVehicles'),
          icon: 'internalVehicles',
        },
      ],
    },
    {
      id: 'access',
      label: t('nav.sectionAccess'),
      children: [
        {
          to: '/whitelist',
          name: 'whitelist',
          label: t('nav.whitelist'),
          icon: 'whitelist',
        },
        {
          to: '/blacklist',
          name: 'blacklist',
          label: t('nav.blacklist'),
          icon: 'blacklist',
        },
      ],
    },
    {
      id: 'system',
      label: t('nav.sectionSystem'),
      children: systemChildren,
    },
  ]
})

const expandedGroups = ref<string[]>(['ops', 'access', 'system'])

const pageTitle = computed(() => {
  for (const group of navGroups.value) {
    const child = group.children.find((item) => item.name === route.name)
    if (child) {
      return child.label
    }
  }
  const parentNav = route.meta.parentNav as { childName?: string } | undefined
  if (parentNav?.childName) {
    for (const group of navGroups.value) {
      const child = group.children.find((item) => item.name === parentNav.childName)
      if (child) {
        return t(String(route.meta.titleKey ?? child.label))
      }
    }
  }
  return t(String(route.meta.titleKey ?? 'nav.overview'))
})

const pageCrumb = computed(() => {
  const keys = route.meta.breadcrumbKeys as string[] | undefined
  if (keys?.length) {
    return keys.map((key) => t(key)).join(' / ')
  }
  for (const group of navGroups.value) {
    const child = group.children.find((item) => item.name === route.name)
    if (child) {
      return `${group.label} / ${child.label}`
    }
  }
  return t('nav.overview')
})

function syncExpandedGroups(): void {
  const parentNav = route.meta.parentNav as { groupId?: string; childName?: string } | undefined
  const activeGroup = navGroups.value.find((group) => {
    if (parentNav?.groupId && group.id === parentNav.groupId) {
      return true
    }
    return group.children.some((child) => child.name === route.name)
  })
  if (activeGroup && !expandedGroups.value.includes(activeGroup.id)) {
    expandedGroups.value = [...expandedGroups.value, activeGroup.id]
  }
}

watch(() => route.name, syncExpandedGroups, { immediate: true })

function isExpanded(groupId: string): boolean {
  return expandedGroups.value.includes(groupId)
}

function toggleGroup(groupId: string): void {
  if (isExpanded(groupId)) {
    expandedGroups.value = expandedGroups.value.filter((id) => id !== groupId)
  } else {
    expandedGroups.value = [...expandedGroups.value, groupId]
  }
}

function isActive(name: string): boolean {
  const parentNav = route.meta.parentNav as { childName?: string } | undefined
  if (parentNav?.childName === name) {
    return true
  }
  return route.name === name
}

function isGroupActive(group: NavGroup): boolean {
  const parentNav = route.meta.parentNav as { groupId?: string; childName?: string } | undefined
  if (parentNav?.groupId === group.id) {
    return true
  }
  return group.children.some((child) => child.name === route.name)
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
        <RouterLink to="/" class="nav-link level-1" :class="{ active: isActive('home') }">
          <span class="icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <rect x="3" y="3" width="8" height="8" rx="1.5" stroke="currentColor" stroke-width="1.8" />
              <rect x="13" y="3" width="8" height="5" rx="1.5" stroke="currentColor" stroke-width="1.8" />
              <rect x="13" y="10" width="8" height="11" rx="1.5" stroke="currentColor" stroke-width="1.8" />
              <rect x="3" y="13" width="8" height="8" rx="1.5" stroke="currentColor" stroke-width="1.8" />
            </svg>
          </span>
          {{ t('nav.overview') }}
        </RouterLink>

        <div v-for="group in navGroups" :key="group.id" class="nav-group">
          <button
            type="button"
            class="nav-group-btn"
            :class="{ active: isGroupActive(group) }"
            :aria-expanded="isExpanded(group.id)"
            @click="toggleGroup(group.id)"
          >
            <span class="group-label">{{ group.label }}</span>
            <span class="chevron" :class="{ open: isExpanded(group.id) }" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path d="M8 10l4 4 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              </svg>
            </span>
          </button>

          <div v-show="isExpanded(group.id)" class="nav-children">
            <RouterLink
              v-for="item in group.children"
              :key="item.name"
              :to="item.to"
              class="nav-link level-2"
              :class="{ active: isActive(item.name) }"
            >
              <span class="icon" aria-hidden="true">
                <svg v-if="item.icon === 'lots'" viewBox="0 0 24 24" fill="none">
                  <path d="M4 20V8.5L12 4l8 4.5V20" stroke="currentColor" stroke-width="1.8" />
                  <path d="M9 20v-7h6v7" stroke="currentColor" stroke-width="1.8" />
                </svg>
                <svg v-else-if="item.icon === 'spaces'" viewBox="0 0 24 24" fill="none">
                  <rect x="3" y="6" width="18" height="12" rx="2" stroke="currentColor" stroke-width="1.8" />
                  <path d="M8 10h3.2a2.4 2.4 0 0 1 0 4.8H8V10Z" stroke="currentColor" stroke-width="1.8" />
                </svg>
                <svg v-else-if="item.icon === 'internalVehicles'" viewBox="0 0 24 24" fill="none">
                  <path
                    d="M5 17h14M6 17l1.2-4.5a2 2 0 0 1 1.9-1.5h7.8a2 2 0 0 1 1.9 1.5L19 17"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                  />
                  <path d="M7.5 17a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3ZM16.5 17a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z" />
                  <path d="M6 11h12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                </svg>
                <svg v-else-if="item.icon === 'whitelist'" viewBox="0 0 24 24" fill="none">
                  <path
                    d="M9 12l2 2 4-4M7 4h10a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                <svg v-else-if="item.icon === 'blacklist'" viewBox="0 0 24 24" fill="none">
                  <path
                    d="M9 9l6 6M15 9l-6 6M7 4h10a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                <svg v-else-if="item.icon === 'operators'" viewBox="0 0 24 24" fill="none">
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
          </div>
        </div>
      </nav>
    </aside>

    <div class="workspace">
      <header class="topbar">
        <div>
          <p class="crumb">{{ t('app.name') }} / {{ pageCrumb }}</p>
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
  gap: 0.35rem;
}

.nav-group {
  display: grid;
  gap: 0.15rem;
}

.nav-group-btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  width: 100%;
  padding: 0.5rem 0.75rem;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #c5d4ce;
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
}

.nav-group-btn:hover,
.nav-group-btn.active {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.group-label {
  flex: 1;
  text-align: start;
}

.chevron {
  width: 1rem;
  height: 1rem;
  display: grid;
  place-items: center;
  opacity: 0.8;
  transition: transform 0.15s ease;
}

.chevron.open {
  transform: rotate(180deg);
}

.chevron svg {
  width: 100%;
  height: 100%;
}

.nav-children {
  display: grid;
  gap: 0.1rem;
  padding-left: 0.35rem;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.55rem 0.75rem;
  border-radius: 8px;
  color: var(--sidebar-text);
}

.nav-link.level-2 {
  padding: 0.48rem 0.75rem;
  font-size: 0.92rem;
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

  .topbar,
  .account {
    flex-wrap: wrap;
  }
}
</style>
