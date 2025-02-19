import { createApp } from 'vue'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import i18n from '@/lang'
import '@/permission.js'
import '@/style.css'
import App from './App.vue'
import clickOutside from 'click-outside-vue3'

import router from '@/router/index'
import pinia from '@/store'

import onePrompt from '@/util/onePrompt'
import { openUrl } from '@/util/common'

import {registerDirectives,directives} from '@/directives'

/**
 * 全局注册容器、组件
 * 不可删除，添加全局组件引用请修改index.js
 */
import installComponent from '@/components/index'
import '@/styles/resetAll.scss'

const app = createApp(App)
installComponent(app)

registerDirectives(app)
Object.keys(directives).forEach((key) => {
    app.directive(key, directives[key])
})
app.config.globalProperties.$onePrompt= onePrompt
app.config.globalProperties.$openUrl = openUrl
// 国际化
app.use(i18n)
app.use(clickOutside)

app.use(pinia)
app.use(router)
app.use(ElementPlus, {
    locale: zhCn,
})
app.mount('#app')