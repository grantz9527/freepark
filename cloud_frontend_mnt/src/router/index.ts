import { createRouter, createWebHistory } from 'vue-router'

import { isAuthenticated, getUser } from '@/auth/session'
import AdminLayout from '@/layouts/AdminLayout.vue'
import BillingPlansView from '@/views/BillingPlansView.vue'
import ComingSoonView from '@/views/ComingSoonView.vue'
import HomeView from '@/views/HomeView.vue'
import LotBillingConfigView from '@/views/LotBillingConfigView.vue'
import LotInterceptConfigView from '@/views/LotInterceptConfigView.vue'
import LotsView from '@/views/LotsView.vue'
import LoginView from '@/views/LoginView.vue'
import SettingsView from '@/views/SettingsView.vue'

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
        {
          path: 'lots',
          name: 'lots',
          component: LotsView,
          meta: { titleKey: 'nav.lots' },
        },
        {
          path: 'lots/:lotId/intercept',
          name: 'lotIntercept',
          component: LotInterceptConfigView,
          meta: {
            titleKey: 'lots.interceptConfig',
            requiresAdmin: true,
          },
        },
        {
          path: 'lots/:lotId/billing',
          name: 'lotBilling',
          component: LotBillingConfigView,
          meta: {
            titleKey: 'lots.billingConfig',
            requiresAdmin: true,
          },
        },
        {
          path: 'spaces',
          name: 'spaces',
          component: ComingSoonView,
          meta: { titleKey: 'nav.spaces' },
        },
        {
          path: 'internal-vehicles',
          name: 'internalVehicles',
          component: ComingSoonView,
          meta: { titleKey: 'nav.internalVehicles' },
        },
        {
          path: 'parking-orders',
          name: 'parkingOrders',
          component: ComingSoonView,
          meta: { titleKey: 'nav.parkingOrders' },
        },
        {
          path: 'parking-records',
          name: 'parkingRecords',
          component: ComingSoonView,
          meta: { titleKey: 'nav.parkingRecords' },
        },
        {
          path: 'on-site-vehicles',
          name: 'onSiteVehicles',
          component: ComingSoonView,
          meta: { titleKey: 'nav.onSiteVehicles' },
        },
        {
          path: 'entry-exit-records',
          name: 'entryExitRecords',
          component: ComingSoonView,
          meta: { titleKey: 'nav.entryExitRecords' },
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
          path: 'billing-plans',
          name: 'billingPlans',
          component: BillingPlansView,
          meta: { titleKey: 'nav.billingPlans' },
        },
        {
          path: 'rate-rules',
          name: 'rateRules',
          component: ComingSoonView,
          meta: { titleKey: 'nav.rateRules' },
        },
        {
          path: 'monthly-plans',
          name: 'monthlyPlans',
          component: ComingSoonView,
          meta: { titleKey: 'nav.monthlyPlans' },
        },
        {
          path: 'users',
          name: 'users',
          component: ComingSoonView,
          meta: { titleKey: 'nav.users' },
        },
        {
          path: 'phones',
          name: 'phones',
          component: ComingSoonView,
          meta: { titleKey: 'nav.phones' },
        },
        {
          path: 'payment-orders',
          name: 'paymentOrders',
          component: ComingSoonView,
          meta: { titleKey: 'nav.paymentOrders' },
        },
        {
          path: 'bills',
          name: 'bills',
          component: ComingSoonView,
          meta: { titleKey: 'nav.bills' },
        },
        {
          path: 'reconciliation',
          name: 'reconciliation',
          component: ComingSoonView,
          meta: { titleKey: 'nav.reconciliation' },
        },
        {
          path: 'admins',
          name: 'admins',
          component: ComingSoonView,
          meta: { titleKey: 'nav.admins', requiresAdmin: true },
        },
        {
          path: 'system-settings',
          name: 'systemSettings',
          component: ComingSoonView,
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

router.beforeEach((to) => {
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
  return true
})

export default router
