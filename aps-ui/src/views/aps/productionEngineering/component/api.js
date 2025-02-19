import request from '@/api/request'

// 获取资源列表分页
export function getResourceList (query) {
  return request({
    url: `/mgr/jvs-aps/production-resource/page`,
    method: 'get',
    params: query
  })
}

// 获取物料列表分页
export function getMatterList (query) {
  return request({
    url: `/mgr/jvs-aps/material/page`,
    method: 'get',
    params: query
  })
}

// 新增
export function add (data) {
  return request({
    url: `/mgr/jvs-aps/process`,
    method: 'post',
    data: data
  })
}

//工艺详情
export function getProcessInfo (id) {
  return request({
    url: `/mgr/jvs-aps/process/${id}`,
    method: 'get'
  })
}

// 修改
export function edit (data) {
  return request({
    url: `/mgr/jvs-aps/process`,
    method: 'put',
    data: data
  })
}

// 获取工序模板列表
export function getGXModelList (query) {
  return request({
    url: `/mgr/jvs-aps/process/page`,
    method: 'get',
    params: query
  })
}


// 获取资源组
export function getGroupList () {
  return request({
    url: `/mgr/jvs-aps/production-resource/group/list`,
    method: 'get',
  })
}