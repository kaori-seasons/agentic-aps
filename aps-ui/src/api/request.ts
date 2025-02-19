import axios from 'axios'
import { serialize, noEmptyOfObject } from '@/util/util'
import Cancel from './cancelToken.js'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { errorCode, authCode, authErrorCode } from '@/const/errorCode.js'
import router from '@/router'
import { ElNotification } from 'element-plus'

const instance = axios.create({
	baseURL: import.meta.env.VITE_BASE_URL,
	timeout: 30000,
	withCredentials: true,
	validateStatus: (status:number) => {
		return status >= 200 && status <= 500
	}
})

NProgress.configure({
	showSpinner: false
})

let currentRoutePath = '' // 当前路由
let lastUrl = ''
// 返回的状态码
const statusCodeEnum = {
  Success: 0, // 成功
  ErrorTip: 1, // 提示错误
  ErrorTip_1: -1, // 提示错误
  Fresh: -3, // 需要刷新页面
}

// 请求拦截器
instance.interceptors.request.use( (config:any) => {
	// 添加请求头 固定参数
	config.headers['jvs-rule-ua'] = encodeURI('2.2')
	// headers中配置serialize为true开启序列化 文件
	if(config.methods === 'post' && config.headers.serialize) {
		config.data = serialize(config.data)
		delete config.data.serialize
	}
	// 去除空值参数
	if(config.params) {
		config.params = noEmptyOfObject(config.params)
	}
	if(config.data && config.headers['type'] != 'FormData') {
		config.data = noEmptyOfObject(config.data)
	}
	let config1 = config
	// 是否需要防重复提交中断请求
	if (!config.isNotCancel && router.currentRoute.value.path != '/designoffice') {
		Cancel.removePending(config)
		config1 = Cancel.addPending(config)
	}
	return config1
}, (err:any) => {
		return Promise.reject(err);
	}
)

// 响应拦截器
instance.interceptors.response.use( (res:any) => {
	// 这里根据业务需求对响应进行处理，可以根据响应状态码进行不同的处理
	NProgress.done()
	const status = Number(res.status) || 200
	const message = res.data.msg || errorCode[res.status] || errorCode['default']
	if (router.currentRoute.value.path !== currentRoutePath) {
		currentRoutePath = router.currentRoute.value.path
		lastUrl = location.href
		window.parent.postMessage({ command: 'isIndex' }, '*')
	}
	// 401为没有权限的情况
	if(status === 401) {
		sessionStorage.setItem('lastUrl', lastUrl)
		if (router.currentRoute.value && router.currentRoute.value.path != '/') {
			router.push({ path: '/' })
		}
		return
	}

	if(status === 404) {
		return Promise.reject(new Error(message))
	}
	if(status !== 200 || res.data.code === statusCodeEnum.ErrorTip) {
		if(!res.config.isReturn) {
			ElNotification.closeAll()
			ElNotification.error({
				title: '提示',
				message: message,
				position: 'bottom-right',
			})
		}
		if(!res.config.isReturn) {
			return Promise.reject(new Error(message))
		}else {
			return res
		}
	}

	if(res.data && res.data.code == statusCodeEnum.ErrorTip_1) {
		ElNotification.closeAll()
		ElNotification.error({
			title: '提示',
			message: message,
			position: 'bottom-right',
		})
		return Promise.reject(new Error(message))
		// 如果授权码需要返回请在请求体上加上isReturn:true
	}else if (authErrorCode.indexOf(res.data.code + '') != statusCodeEnum.ErrorTip_1 && !res.config.isReturn) {
		const authMessage = res.data.msg || authCode[res.data.code]
		ElNotification.closeAll()
		ElNotification.error({
			title: '提示',
			message: authMessage,
			position: 'bottom-right',
		})
		return Promise.reject(new Error(authMessage))
	}else {
		return res
	}
}, (err:any) => {
	NProgress.done()
	if(err.code == 'ERR_CANCELED' || axios.isCancel(err) || new Error(err) == new Error('Error: Cancel')) {
		return new Promise( () => {})
	}else {
		return Promise.reject(new Error(err))
	}
})

export default instance
