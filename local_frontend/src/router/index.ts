import { createRouter, createWebHistory } from 'vue-router'

import { isAuthenticated, getUser } from '@/auth/session'
import { detectLocale } from '@/i18n/locales'
import { clearSiteSettingsCache, ensureSiteSettings } from '@/site/settings'
import AdminLayout from '@/layouts/AdminLayout.vue'
import ComingSoonView from '@/views/ComingSoonView.vue'
import HomeView from '@/views/HomeView.vue'
import InternalVehiclesView from '@/views/InternalVehiclesView.vue'
import LotsView from '@/views/LotsView.vue'
import LotInterceptConfigView from '@/views/LotInterceptConfigView.vue'
import LoginView from '@/views/LoginView.vue'
import OperatorsView from '@/views/OperatorsView.vue'
import SettingsView from '@/views/SettingsView.vue'
import SystemSettingsView from '@/views/SystemSettingsView.vue'
import SpacesView from '@/views/SpacesView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true },
    },
    {
      path: '/',
      component: AdminLayout,
      children: [
        { path: '', name: 'home', component: HomeView, meta: { titleKey: 'nav.overview' } },
        { path: 'lots', name: 'lots', component: LotsView, meta: { titleKey: 'nav.lots' } },
        {
          path: 'lots/:lotId/intercept',
          name: 'lotIntercept',
          component: LotInterceptConfigView,
          meta: {
            titleKey: 'lots.interceptConfig',
            requiresAdmin: true,
            breadcrumbKeys: ['nav.sectionOps', 'nav.lots', 'lots.interceptConfig'],
            parentNav: { groupId: 'ops', childName: 'lots' },
          },
        },
        { path: 'spaces', name: 'spaces', component: SpacesView, meta: { titleKey: 'nav.spaces' } },
        {
          path: 'internal-vehicles',
          name: 'internalVehicles',
          component: InternalVehiclesView,
          meta: { titleKey: 'nav.internalVehicles' },
        },
        {
          path: 'whitelist',
          name: 'whitelist',
          component: ComingSoonView,
          meta: { titleKey: 'nav.whitelist' },
        },
        {
          path: 'blacklist',
          name: 'blacklist',
          component: ComingSoonView,
          meta: { titleKey: 'nav.blacklist' },
        },
        {
          path: 'operators',
          name: 'operators',
          component: OperatorsView,
          meta: { titleKey: 'nav.operators', requiresAdmin: true },
        },
        {
          path: 'system-settings',
          name: 'systemSettings',
          component: SystemSettingsView,
          meta: { titleKey: 'nav.systemSettings', requiresAdmin: true },
        },
        {
          path: 'settings',
          name: 'settings',
          component: SettingsView,
          meta: { titleKey: 'nav.settings' },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.public) {
    if (isAuthenticated() && to.name === 'login') {
      return { name: 'home' }
    }
    return true
  }
  if (!isAuthenticated()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && getUser()?.role !== 'ADMIN') {
    return { name: 'home' }
  }
  await ensureSiteSettings(detectLocale())
  return true
})

export default router
