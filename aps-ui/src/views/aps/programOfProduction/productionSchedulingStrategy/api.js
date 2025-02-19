import request from '@/api/request'

// 获取列表
export function getList (query) {
  return request({
    url: `/mgr/jvs-aps/planning-strategy/page`,
    method: 'get',
    params: query
  })
}

// 获取订单规则可选项
export function getOptions () {
  return request({
    url: `/mgr/jvs-aps/planning-strategy/scheduling-rule/options`,
    method: 'get'
  })
}

// 新增
export function add (data) {
  return request({
    url: `/mgr/jvs-aps/planning-strategy`,
    method: 'post',
    data: data
  })
}

// 修改
export function edit (data) {
  return request({
    url: `/mgr/jvs-aps/planning-strategy`,
    method: 'put',
    data: data
  })
}

// 修改策略有效状态
export function editActive (id, data) {
  return request({
    url: `/mgr/jvs-aps/planning-strategy/change/${id}/active`,
    method: 'put',
    params: data
  })
}

// 删除
export function del (id) {
  return request({
    url: `/mgr/jvs-aps/planning-strategy/${id}`,
    method: 'delete'
  })
}