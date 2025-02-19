import request from '@/api/request'

// 获取资源列表分页
export function getResourceList (query) {
  return request({
    url: `/mgr/jvs-aps/production-resource/page`,
    method: 'get',
    params: query
  })
}