import request from '@/api/request'

// 获取列表
export function getList (data, isPreview) {
  return request({
    url: isPreview ? `/mgr/jvs-aps/smart-scheduling/preview/plan/material/gantt` : `/mgr/jvs-aps/schedule-result/report/plan/material/gantt`,
    method: 'post',
    data: data
  })
}