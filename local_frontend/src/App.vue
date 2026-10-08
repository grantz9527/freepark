<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { RouterView } from 'vue-router'
import { useI18n } from 'vue-i18n'

import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import type { Language } from 'element-plus/es/locale'

import SessionExpiredDialog from '@/components/SessionExpiredDialog.vue'
import { isRtl } from '@/i18n/locales'

const { t, locale } = useI18n()

const EL_LOCALES: Record<string, Language> = {
  'zh-CN': zhCn,
  en,
}
const elLocale = computed(() => EL_LOCALES[locale.value] ?? en)

watchEffect(() => {
  document.documentElement.lang = locale.value
  document.documentElement.dir = isRtl(locale.value) ? 'rtl' : 'ltr'
  document.title = t('app.name')
})
</script>

<template>
  <ElConfigProvider :locale="elLocale">
    <RouterView />
    <SessionExpiredDialog />
  </ElConfigProvider>
</template>
