import request from '@/api/request'

// 获取列表
export function getList (query) {
  return request({
    url: `/mgr/jvs-aps/process/page`,
    method: 'get',
    params: query
  })
}

// 删除
export function del (id) {
  return request({
    url: `/mgr/jvs-aps/process/${id}`,
    method: 'delete'
  })
}