import request from '@/api/request'

// 获取列表
export function getList (data, isPreview) {
  return request({
    url: isPreview ? `/mgr/jvs-aps/smart-scheduling/preview/plan/order/gantt` : `/mgr/jvs-aps/schedule-result/report/plan/order/gantt`,
    method: 'post',
    data: data
  })
}

// 获取配置
export function getFieldSetting (reportType) {
  return request({
    url: `/mgr/jvs-aps/plan-report-field-setting/${reportType}`,
    method: 'get',
  })
}

// 获取可配置字段
export function getFieldOption (reportType) {
  return request({
    url: `/mgr/jvs-aps/plan-report-field-setting/field/options/${reportType}`,
    method: 'get',
  })
}

// 保存配置
export function saveFieldSetting(data) {
  return request({
    url: `/mgr/jvs-aps/plan-report-field-setting`,
    method: 'post',
    data: data
  })
}


// 获取显示配置
export function getPlanSetting (reportType) {
  return request({
    url: `/mgr/jvs-aps/plan-report-field-setting/${reportType}`,
    method: 'get',
  })
}