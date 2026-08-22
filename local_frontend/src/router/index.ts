import { createRouter, createWebHistory } from 'vue-router'

import { isAuthenticated, getUser } from '@/auth/session'
import AdminLayout from '@/layouts/AdminLayout.vue'
import ComingSoonView from '@/views/ComingSoonView.vue'
import HomeView from '@/views/HomeView.vue'
import LotsView from '@/views/LotsView.vue'
import LoginView from '@/views/LoginView.vue'
import OperatorsView from '@/views/OperatorsView.vue'
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
        { path: 'lots', name: 'lots', component: LotsView, meta: { titleKey: 'nav.lots' } },
        { path: 'spaces', name: 'spaces', component: ComingSoonView, meta: { titleKey: 'nav.spaces' } },
        {
          path: 'operators',
          name: 'operators',
          component: OperatorsView,
          meta: { titleKey: 'nav.operators', requiresAdmin: true },
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
