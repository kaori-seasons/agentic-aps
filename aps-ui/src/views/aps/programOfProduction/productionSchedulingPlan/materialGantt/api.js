import request from '@/api/request'

// 获取列表
export function getList (data, isPreview) {
  return request({
    url: isPreview ? `/mgr/jvs-aps/smart-scheduling/preview/plan/material/gantt` : `/mgr/jvs-aps/schedule-result/report/plan/material/gantt`,
    method: 'post',
    data: data
  })
}
// 齐套线欠料（默认只看未处理）：甘特左侧清单行标缺料角标用。
export function listShortageItems (params) {
  return request({
    url: `/mgr/jvs-aps/shortage/items`,
    method: 'get',
    params: params,
    skipServerErrorRedirect: true
  })
}
