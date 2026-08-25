import { createRouter, createWebHistory } from 'vue-router'

import { isAuthenticated, getUser } from '@/auth/session'
import { detectLocale } from '@/i18n/locales'
import { clearSiteSettingsCache, ensureSiteSettings } from '@/site/settings'
import AdminLayout from '@/layouts/AdminLayout.vue'
import HomeView from '@/views/HomeView.vue'
import InternalVehiclesView from '@/views/InternalVehiclesView.vue'
import WhitelistView from '@/views/WhitelistView.vue'
import BlacklistView from '@/views/BlacklistView.vue'
import PatternAllowlistView from '@/views/PatternAllowlistView.vue'
import AccessJudgmentConfigView from '@/views/AccessJudgmentConfigView.vue'
import LotsView from '@/views/LotsView.vue'
import LotInterceptConfigView from '@/views/LotInterceptConfigView.vue'
import LoginView from '@/views/LoginView.vue'
import OperatorsView from '@/views/OperatorsView.vue'
import RecognitionRecordsView from '@/views/RecognitionRecordsView.vue'
import ParkingSessionsView from '@/views/ParkingSessionsView.vue'
import SettingsView from '@/views/SettingsView.vue'
import SystemSettingsView from '@/views/SystemSettingsView.vue'
import SpacesView from '@/views/SpacesView.vue'
import LanesView from '@/views/LanesView.vue'
import BarrierDockingView from '@/views/BarrierDockingView.vue'
import FrigateDockingView from '@/views/FrigateDockingView.vue'
import IotDockingView from '@/views/IotDockingView.vue'

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
          component: WhitelistView,
          meta: { titleKey: 'nav.whitelist' },
        },
        {
          path: 'blacklist',
          name: 'blacklist',
          component: BlacklistView,
          meta: { titleKey: 'nav.blacklist' },
        },
        {
          path: 'pattern-allowlist',
          name: 'patternAllowlist',
          component: PatternAllowlistView,
          meta: { titleKey: 'nav.patternAllowlist' },
        },
        {
          path: 'access-judgment',
          name: 'accessJudgment',
          component: AccessJudgmentConfigView,
          meta: {
            titleKey: 'nav.accessJudgment',
            breadcrumbKeys: ['nav.sectionAccess', 'nav.accessJudgment'],
            parentNav: { groupId: 'access', childName: 'accessJudgment' },
          },
        },
        {
          path: 'parking',
          redirect: { name: 'recognitionRecords' },
        },
        {
          path: 'parking/recognition-records',
          name: 'recognitionRecords',
          component: RecognitionRecordsView,
          meta: { titleKey: 'nav.recognitionRecords' },
        },
        {
          path: 'parking/sessions',
          name: 'parkingSessions',
          component: ParkingSessionsView,
          meta: { titleKey: 'nav.parkingSessions' },
        },
        {
          path: 'hardware',
          redirect: { name: 'lanes' },
        },
        {
          path: 'hardware/lanes',
          name: 'lanes',
          component: LanesView,
          meta: { titleKey: 'nav.lanes' },
        },
        {
          path: 'hardware/barriers',
          name: 'barriers',
          component: BarrierDockingView,
          meta: { titleKey: 'nav.barriers' },
        },
        {
          path: 'hardware/iot',
          name: 'iot',
          component: IotDockingView,
          meta: { titleKey: 'nav.iot' },
        },
        {
          path: 'hardware/frigate',
          name: 'frigate',
          component: FrigateDockingView,
          meta: { titleKey: 'nav.frigate' },
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
