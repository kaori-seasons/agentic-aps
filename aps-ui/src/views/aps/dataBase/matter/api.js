import request from '@/api/request'

// 获取列表
export function getList (query) {
  return request({
    url: `/mgr/jvs-aps/material/page`,
    method: 'get',
    params: query
  })
}

// 新增
export function add (data) {
  return request({
    url: `/mgr/jvs-aps/material`,
    method: 'post',
    data: data
  })
}

// 修改
export function edit (data) {
  return request({
    url: `/mgr/jvs-aps/material`,
    method: 'put',
    data: data
  })
}

// 删除
export function del (id) {
  return request({
    url: `/mgr/jvs-aps/material/${id}`,
    method: 'delete'
  })
}