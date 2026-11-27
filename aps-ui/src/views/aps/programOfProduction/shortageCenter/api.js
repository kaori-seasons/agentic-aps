import request from '@/api/request'

// 统一走 skipServerErrorRedirect：页面对 code!=0 自行提示，不被全局跳转打断。
function call(path, method = 'get', data) {
  return request({ url: '/mgr/jvs-aps/shortage' + path, method, data, skipServerErrorRedirect: true })
}

function query(params) {
  const search = new URLSearchParams()
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.append(key, value)
  })
  const text = search.toString()
  return text ? '?' + text : ''
}

// 欠料清单：summary 与筛选条件无关，始终返回两条线的全量分线计数。
export function listItems(params) {
  return call('/items' + query(params))
}

// 单条明细，含在途行明细。shortageKey 含冒号等保留字符，一律走查询参数，不进路径。
export function itemDetail(shortageKey) {
  return call('/items/detail' + query({ shortageKey }))
}

// 条目级回执：ack 确认 / resolve 解决 / mute 静默（P1 后端会拒绝，前端先拦一层）。
export function handleItem(shortageKey, action, note) {
  return call('/items/handle', 'post', { shortageKey, action, note })
}

export function listAlerts(params) {
  return call('/alerts' + query(params))
}

export function alertDetail(alertId) {
  return call('/alerts/' + encodeURIComponent(alertId))
}

export function listNotifies(alertId) {
  return call('/notifies' + query({ alertId }))
}

// 手工触发复评并立刻投递一轮，返回各环节计数。
export function scan() {
  return call('/scan', 'post', {})
}
