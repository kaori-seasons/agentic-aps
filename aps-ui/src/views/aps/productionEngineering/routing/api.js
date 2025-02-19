import request from '@/api/request'

// 分页查询'制造'物料
export function getProducedList (query) {
  return request({
    url: `/mgr/jvs-aps/material/produced/page`,
    method: 'get',
    params: query
  })
}

// 获取列表
export function getList (materialId) {
  return request({
    url: `/mgr/jvs-aps/process-route/material/${materialId}`,
    method: 'get',
  })
}

// 新增
export function add (data) {
  return request({
    url: `/mgr/jvs-aps/process-route`,
    method: 'post',
    data: data
  })
}

// 修改
export function edit (data) {
  return request({
    url: `/mgr/jvs-aps/process-route`,
    method: 'put',
    data: data
  })
}

// 启用 工艺路线
export function enable (id) {
  return request({
    url: `/mgr/jvs-aps/process-route/${id}/enable`,
    method: 'put'
  })
}

// 禁用 工艺路线
export function disable (id) {
  return request({
    url: `/mgr/jvs-aps/process-route/${id}/disable`,
    method: 'put'
  })
}

// 删除
export function del (id) {
  return request({
    url: `/mgr/jvs-aps/process-route/${id}`,
    method: 'delete'
  })
}