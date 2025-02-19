import request from '@/api/request'

// 获取列表
export function getList (query) {
  return request({
    url: `/mgr/jvs-aps/incoming-material-order/page`,
    method: 'get',
    params: query
  })
}

// 新增
export function add (data) {
  return request({
    url: `/mgr/jvs-aps/incoming-material-order`,
    method: 'post',
    data: data
  })
}

// 修改
export function edit (data) {
  return request({
    url: `/mgr/jvs-aps/incoming-material-order`,
    method: 'put',
    data: data
  })
}

// 删除
export function del (id) {
  return request({
    url: `/mgr/jvs-aps/incoming-material-order/${id}`,
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