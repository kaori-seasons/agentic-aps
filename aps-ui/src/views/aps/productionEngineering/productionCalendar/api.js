import request from '@/api/request'

// 工作模式分页
export function getModeList (query) {
  return request({
    url: `/mgr/jvs-aps/work-mode/page`,
    method: 'get',
    params: query
  })
}

// 新增工作模式
export function addMode (data) {
  return request({
    url: `/mgr/jvs-aps/work-mode`,
    method: 'post',
    data: data
  })
}

// 修改工作模式
export function editMode (data) {
  return request({
    url: `/mgr/jvs-aps/work-mode`,
    method: 'put',
    data: data
  })
}

// 删除工作模式
export function delMode (id) {
  return request({
    url: `/mgr/jvs-aps/work-mode/${id}`,
    method: 'delete'
  })
}

// 获取列表
export function getList (query) {
  return request({
    url: `/mgr/jvs-aps/work-calendar/page`,
    method: 'get',
    params: query
  })
}

// 获取资源列表分页
export function getResourceList (query) {
  return request({
    url: `/mgr/jvs-aps/production-resource/page`,
    method: 'get',
    params: query
  })
}

// 新增
export function add (data) {
  return request({
    url: `/mgr/jvs-aps/work-calendar`,
    method: 'post',
    data: data
  })
}

// 获取使用指定日历的所有资源
export function getInfoById (id) {
  return request({
    url: `/mgr/jvs-aps/resource-calendar/${id}/resources`,
    method: 'get'
  })
}

// 修改
export function edit (data) {
  return request({
    url: `/mgr/jvs-aps/work-calendar`,
    method: 'put',
    data: data
  })
}

// 删除
export function del (id) {
  return request({
    url: `/mgr/jvs-aps/work-calendar/${id}`,
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