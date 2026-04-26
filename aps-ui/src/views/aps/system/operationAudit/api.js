import request from '@/api/request'

export const getList = (params) => {
  return request({
    url: `/mgr/jvs-aps/operation-audit/page`,
    method: 'get',
    params
  })
}
