<script setup lang="ts">
import { watchEffect } from 'vue'
import { RouterView } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { isRtl } from '@/i18n/locales'

const { t, locale } = useI18n()

watchEffect(() => {
  document.documentElement.lang = locale.value
  document.documentElement.dir = isRtl(locale.value) ? 'rtl' : 'ltr'
  document.title = t('app.name')
})
</script>

<template>
  <RouterView />
</template>
