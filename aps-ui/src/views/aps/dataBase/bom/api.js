import request from '@/api/request'

// 获取列表
export function getList (query) {
  return request({
    url: `/mgr/jvs-aps/manufacture-bom/page`,
    method: 'get',
    params: query
  })
}

// BOM树
export function getBomTree (id) {
  return request({
    url: `/mgr/jvs-aps/manufacture-bom/${id}/tree`,
    method: 'get'
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

// BOM详情
export function getInfo (id) {
  return request({
    url: `/mgr/jvs-aps/manufacture-bom/${id}`,
    method: 'get'
  })
}

// 新增
export function add (data) {
  return request({
    url: `/mgr/jvs-aps/manufacture-bom`,
    method: 'post',
    data: data
  })
}

// 修改
export function edit (data) {
  return request({
    url: `/mgr/jvs-aps/manufacture-bom`,
    method: 'put',
    data: data
  })
}

// 删除
export function del (id) {
  return request({
    url: `/mgr/jvs-aps/manufacture-bom/${id}`,
    method: 'delete'
  })
}