import { createI18n } from 'vue-i18n'

import { DEFAULT_LOCALE, detectLocale, type SupportedLocale } from '@/i18n/locales'
import ar from '@/i18n/messages/ar.json'
import de from '@/i18n/messages/de.json'
import en from '@/i18n/messages/en.json'
import es from '@/i18n/messages/es.json'
import fr from '@/i18n/messages/fr.json'
import ja from '@/i18n/messages/ja.json'
import ko from '@/i18n/messages/ko.json'
import pt from '@/i18n/messages/pt.json'
import zhCN from '@/i18n/messages/zh-CN.json'
import zhTW from '@/i18n/messages/zh-TW.json'

const messages = {
  en,
  'zh-CN': zhCN,
  'zh-TW': zhTW,
  ja,
  ko,
  es,
  fr,
  de,
  pt,
  ar,
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
