import request from '@/api/request'

// 获取列表
export function getList (query) {
  return request({
    url: `/mgr/jvs-aps/production-order/page`,
    method: 'get',
    params: query
  })
}

// 新增
export function add (data) {
  return request({
    url: `/mgr/jvs-aps/production-order`,
    method: 'post',
    data: data
  })
}

// 修改
export function edit (data) {
  return request({
    url: `/mgr/jvs-aps/production-order`,
    method: 'put',
    data: data
  })
}

// 删除
export function del (id) {
  return request({
    url: `/mgr/jvs-aps/production-order/${id}`,
    method: 'delete'
  })
}

// 获取物料列表
export function getMaterialList (query) {
  return request({
    url: `/mgr/jvs-aps/material/page`,
    method: 'get',
    params: query
  })
}

// 修改生产订单排产状态
export function editSchedule (id, data) {
  return request({
    url: `/mgr/jvs-aps/production-order/change/${id}/can/schedule`,
    method: 'put',
    params: data
  })
}

// 查询补充订单
export function getSupplement (id) {
  return request({
    url: `/mgr/jvs-aps/production-order/supplement/list/${id}`,
    method: 'get'
  })
}