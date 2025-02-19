import request from '@/api/request'

// 获取列表
export function getList (query) {
  return request({
    url: `/mgr/jvs-aps/production-resource/page`,
    method: 'get',
    params: query
  })
}

// 新增
export function add (data) {
  return request({
    url: `/mgr/jvs-aps/production-resource`,
    method: 'post',
    data: data
  })
}

// 修改
export function edit (data) {
  return request({
    url: `/mgr/jvs-aps/production-resource`,
    method: 'put',
    data: data
  })
}

// 删除
export function del (id) {
  return request({
    url: `/mgr/jvs-aps/production-resource/${id}`,
    method: 'delete'
  })
}

// 获取资源组
export function getGroupList () {
  return request({
    url: `/mgr/jvs-aps/production-resource/group/list`,
    method: 'get',
  })
}