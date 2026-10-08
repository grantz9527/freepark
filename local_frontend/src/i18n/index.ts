import { createI18n } from 'vue-i18n'

import { DEFAULT_LOCALE, detectLocale, type SupportedLocale } from '@/i18n/locales'
import en from '@/i18n/messages/en.json'
import zhCN from '@/i18n/messages/zh-CN.json'

const messages = {
  en,
  'zh-CN': zhCN,
}

export type MessageSchema = typeof en

export const i18n = createI18n<[MessageSchema], SupportedLocale>({
  legacy: false,
  locale: detectLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  missingWarn: false,
  fallbackWarn: false,
  messages: messages as unknown as Record<SupportedLocale, MessageSchema>,
})
