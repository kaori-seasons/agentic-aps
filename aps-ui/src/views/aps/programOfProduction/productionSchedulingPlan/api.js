import request from '@/api/request'

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