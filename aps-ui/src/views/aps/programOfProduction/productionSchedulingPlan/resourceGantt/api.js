import request from '@/api/request'

// 获取列表
export function getList (data, isPreview) {
  return request({
    url: isPreview ? `/mgr/jvs-aps/smart-scheduling/preview/plan/resource/gantt` : `/mgr/jvs-aps/schedule-result/report/plan/resource/gantt`,
    method: 'post',
    data: data
  })
}

// 获取资源组
export function getGroupList () {
  return request({
    url: `/mgr/jvs-aps/production-resource/group/list`,
    method: 'get',
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

// 甘特图任务详情
export function gettaskDetail(code) {
  return request({
    url: `/mgr/jvs-aps/schedule-result/report/plan/task/detail/${code}`,
    method: 'post',
  })
}

// 修改指定任务报工进度
export function updateProgress(data) {
  return request({
    url: `/mgr/jvs-aps/task-report/update/progress`,
    method: 'post',
    data: data
  })
}

// 批量修改指定任务报工进度
export function updateBatchProgress(data) {
  return request({
    url: `/mgr/jvs-aps/task-report/update/batch/progress`,
    method: 'post',
    data: data
  })
}

// 检查任务是否已调整
export function checkAdjusted() {
  return request({
    url: `/mgr/jvs-aps/task-adjust/adjusted`,
    method: 'post',
  })
}

// 撤销任务调整
export function cancelAdjusted() {
  return request({
    url: `/mgr/jvs-aps/task-adjust/cancel`,
    method: 'post',
  })
}

// 任务拆分——按拆出数量拆分
export function splitQuantity(data) {
  return request({
    url: `/mgr/jvs-aps/task-adjust/split/quantity`,
    method: 'post',
    data: data
  })
}

// 任务拆分——按任务数量平均拆分
export function splitTaskNumber(data) {
  return request({
    url: `/mgr/jvs-aps/task-adjust/split/evenly-task-number`,
    method: 'post',
    data: data
  })
}

// 任务拆分——按完成数量拆分
export function splitCompletion(data) {
  return request({
    url: `/mgr/jvs-aps/task-adjust/split/completion`,
    method: 'post',
    data: data
  })
}

// 任务合并——选中的任务
export function mergeTasks(data) {
  return request({
    url: `/mgr/jvs-aps/task-adjust/merge/selected-tasks`,
    method: 'post',
    data: data
  })
}

// 任务移动——移动指定任务
export function movePosition(data) {
  return request({
    url: `/mgr/jvs-aps/task-adjust/move/position`,
    method: 'post',
    data: data
  })
}

// 任务移动——移动部分完成的任务进度到指定时间
export function moveCompleted(data) {
  return request({
    url: `/mgr/jvs-aps/task-adjust/move/partially-completed-task-time`,
    method: 'post',
    data: data
  })
}

// 锁定-解锁——选中的任务
export function freezeTasks(data) {
  return request({
    url: `/mgr/jvs-aps/task-adjust/freeze/selected-tasks`,
    method: 'post',
    data: data
  })
}

// 锁定-解锁——所有已开工的任务
export function freezeStarted(data) {
  return request({
    url: `/mgr/jvs-aps/task-adjust/freeze/started-tasks`,
    method: 'post',
    data: data
  })
}

// 导出
export const exportTask = (data) => {
  return request({
    url: `/mgr/jvs-aps/task-assignment/export`,
    method: 'post',
    data: data,
    responseType: 'arraybuffer',
  })
}