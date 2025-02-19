import request from '@/api/request'

// 获取列表
export function getList (query) {
  return request({
    url: `/mgr/jvs-aps/planning-production-order/page/pending/orders`,
    method: 'get',
    params: query
  })
}

// 修改订单排序
export function sortOrder (data) {
  return request({
    url: `/mgr/jvs-aps/production-order/sort`,
    method: 'put',
    data: data
  })
}

// 查询补充订单
export function getSupplement (id) {
  return request({
    url: `/mgr/jvs-aps/production-order/supplement/list/${id}`,
    method: 'get'
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