import request from '@/api/request'
export function getSolveAudit(id) {
  return request({url: `/mgr/jvs-aps/smart-scheduling/audit/${encodeURIComponent(id)}`, method: 'get'})
}

// 获取排产进度
export function getProgress () {
  return request({
    url: `/mgr/jvs-aps/smart-scheduling/plan/progress`,
    method: 'get',
    timeoutError: true
  })
}

// 生成排产计划
export function generate (data) {
  return request({
    url: `/mgr/jvs-aps/smart-scheduling/generate`,
    method: 'post',
    data: data
  })
}

// 放弃排产计划
export function cancel () {
  return request({
    url: `/mgr/jvs-aps/smart-scheduling/plan/pending/cancel`,
    method: 'post',
  })
}

// 确认排产计划
export function confirm () {
  return request({
    url: `/mgr/jvs-aps/smart-scheduling/plan/pending/confirm`,
    method: 'post'
  })
}

// 获取列表
export function getList (query) {
  return request({
    url: ``,
    method: 'get',
    params: query
  })
}

// 新增
export function add (data) {
  return request({
    url: ``,
    method: 'post',
    data: data
  })
}

// 修改
export function edit (data) {
  return request({
    url: ``,
    method: 'put',
    data: data
  })
}

// 删除
export function del (id) {
  return request({
    url: ``,
    method: 'delete'
  })
}

// 缺料汇总（齐套线）：进度完成后给计划员一个“本轮缺了多少”的入口。
// 403/无权限时由调用方忽略，不打断排产动线。
export function getShortageSummary () {
  return request({
    url: `/mgr/jvs-aps/shortage/items?dimension=ORDER&limit=1`,
    method: 'get',
    skipServerErrorRedirect: true
  })
}
