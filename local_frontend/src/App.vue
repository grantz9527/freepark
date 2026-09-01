<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { RouterView } from 'vue-router'
import { useI18n } from 'vue-i18n'

import zhCn from 'element-plus/es/locale/lang/zh-cn'
import zhTw from 'element-plus/es/locale/lang/zh-tw'
import en from 'element-plus/es/locale/lang/en'
import ja from 'element-plus/es/locale/lang/ja'
import ko from 'element-plus/es/locale/lang/ko'
import es from 'element-plus/es/locale/lang/es'
import fr from 'element-plus/es/locale/lang/fr'
import de from 'element-plus/es/locale/lang/de'
import pt from 'element-plus/es/locale/lang/pt'
import ar from 'element-plus/es/locale/lang/ar'
import type { Language } from 'element-plus/es/locale'

import { isRtl } from '@/i18n/locales'

const { t, locale } = useI18n()

// Element Plus 组件内置文案跟随站点语言
const EL_LOCALES: Record<string, Language> = {
  'zh-CN': zhCn,
  'zh-TW': zhTw,
  en,
  ja,
  ko,
  es,
  fr,
  de,
  pt,
  ar,
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
  </ElConfigProvider>
</template>
