import { createI18n } from 'vue-i18n'
import lang_zh_CN from './locales/zh_CN'
import lang_en from './locales/en'

const language = navigator.language || navigator.userLanguage

let localLang = (/zh/i.test(language) || /zh-CN/i.test(language) || /zh-TW/i.test(language) || /zh-HK/i.test(language)) ? 'zh-CN' : 'en'

const i18n = createI18n({
  locale: localLang,
  globalInjection: true,
  allowComposition: true,
  legacy: false,
  messages: {
		'zh-CN': lang_zh_CN,
		'en': lang_en,
	}
})

export default i18n