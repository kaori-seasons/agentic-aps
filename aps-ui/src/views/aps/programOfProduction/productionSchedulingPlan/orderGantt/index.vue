<template>
  <div :class="{'aps-programOfProduction-productionSchedulingPlan-resource-gantt': true, 'disable-handle': props.disableHandle}">
    <div v-if="!props.disableHandle" class="jvs-table">
      <jvs-form :option="searchOption" :formData="queryParams" :isSearch="true" class="search-form" @submit="searchChange"></jvs-form>
      <div class="right">
        <el-button type="primary" @click="openParam">参数设置</el-button>
      </div>
    </div>
    <div :id="ganttId" class="orderGantt" v-click-outside="hideTooltip">
    </div>
    <div v-if="tableLoading" class="loading"></div>

    <!-- 参数设置 -->
    <el-dialog
      v-model="paramVisible"
      title="参数设置"
      width="520"
      append-to-body
      :close-on-click-modal="false"
      :before-close="paramClose"
    >
      <div v-if="paramVisible" class="dialog-form-box">
        <jvs-form v-if="dialogType == 'param'" :option="paramOption" :formData="paramData" @submit="submitHandle" @cancelClick="paramClose">
          <template #tooltipFieldsForm>
            <div class="tooltipFields-table">
              <div class="header">
                <div class="header-item">字段名</div>
                <div class="header-item">自定义显示名</div>
                <span class="delete-icon-button" style="background: none;"></span>
              </div>
              <el-scrollbar class="body">
                <div v-for="(field, fix) in paramData.tooltipFields" :key="'tooltip-fields-'+fix" class="body-item">
                  <div :class="{'body-item-column': true, 'error': !field.fieldKey}">
                    <el-select v-model="field.fieldKey" placeholder="字段名" @change="fieldChange(field.fieldKey, fix, paramData.tooltipFields)">
                      <el-option v-for="fit in fieldList" :key="'select-it-key-'+fix+'-'+fit.fieldKey" :label="fit.fieldName" :value="fit.fieldKey" :disabled="(field.fieldKey == fit.fieldKey) ? false : (selectedFieldKey.indexOf(fit.fieldKey) > -1)"></el-option>
                    </el-select>
                  </div>
                  <!-- , 'error': !field.fieldName -->
                  <div :class="{'body-item-column': true}">
                    <el-input v-model="field.fieldName" placeholder="自定义显示名"></el-input>
                  </div>
                  <span class="delete-icon-button" @click="deleteParam(fix, paramData.tooltipFields)">
                    <span class="border-line"></span>
                  </span>
                </div>
              </el-scrollbar>
              <div class="bottom-add-button">
                <div class="button" @click="addParam">
                  <div class="icon">
                    <svg aria-hidden="true">
                      <use xlink:href="#jvs-ui-icon-xinjian"></use>
                    </svg>
                  </div>
                  <span>{{t(`table.add`)}}</span>
                </div>
              </div>
            </div>
          </template>
        </jvs-form>
      </div>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup name="orderGantt">
import {
  ref,
  reactive,
  watch ,
  onMounted,
  onUnmounted,
  computed,
  getCurrentInstance,
  nextTick,
  shallowRef,
} from 'vue'
import { useI18n } from 'vue-i18n'
import { ElNotification } from 'element-plus'
import type { UploadInstance } from 'element-plus'
import  * as VTableGantt  from '@visactor/vtable-gantt'

import { getList, getFieldOption, getFieldSetting, saveFieldSetting, getPlanSetting } from './api'

const { proxy } = getCurrentInstance()
const emit = defineEmits([])
const { t } = useI18n()
const uploadBtn = ref<UploadInstance>()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

const props = defineProps({
  freshRandom: {
    type: Number
  },
  disableHandle: {
    type: Boolean
  }
})

const ganttId = ref('orderGantt')

const selectedFieldKey = computed(() => {
  let temp = []
  if(paramData.value && paramData.value.tooltipFields && paramData.value.tooltipFields.length > 0) {
    paramData.value.tooltipFields.filter(it => {
      if(it.fieldKey) {
        if(temp.indexOf(it.fieldKey) == -1) {
          temp.push(it.fieldKey)
        }
      }
    })
  }
  return temp
})

// 十六进制到 RGB
function hexToRgba (hex, a) {
  // 去除可能存在的井号
  hex = hex.replace(/^#/, '');
  // 转换为RGB数组
  const r = parseInt(hex.slice(0, 2), 16);
  const g = parseInt(hex.slice(2, 4), 16);
  const b = parseInt(hex.slice(4, 6), 16);
  // 返回RGB字符串
  return rgbaTo16color(`rgb(${r}, ${g}, ${b}, ${a})`);
}

function rgbaTo16color(color) {
  let val = color.replace(/rgba?\(/, '').replace(/\)/, '').replace(/[\s+]/g, '').split(',')
  let a = parseFloat(val[3] || 1), r = Math.floor(a * parseInt(val[0]) + (1 - a) * 255),
    g = Math.floor(a * parseInt(val[1]) + (1 - a) * 255), b = Math.floor(a * parseInt(val[2]) + (1 - a) * 255);
  return "#" + ("0" + r.toString(16)).slice(-2) + ("0" + g.toString(16)).slice(-2) + ("0" + b.toString(16)).slice(-2)
}

function getDicLabel (val, key) {
  let str = val
  if(key == 'taskStatus') {
    switch(val) {
      case 'COMPLETED': str = '已完成';break;
      case 'PARTIALLY_COMPLETED': str = '部分完成';break;
      case 'PENDING': str = '待完成';break;
      default: str = val;break;
    }
  }
  return str
}

const paramVisible = ref(false)
const dialogType = ref('')
const paramData = ref({})
const fieldList = ref([])
const paramOption = ref({
  submitLoading: false,
  emptyBtn: false,
  formAlign: 'top',
  column: [
    {
      label: '任务条字段',
      prop: 'fieldKey',
      type: 'select',
      dicData: [],
      props: {
        label: 'fieldName',
        value: 'fieldKey'
      },
      rules: [ { required: true, message: '请选择任务条字段', trigger: 'change' } ]
    },
    {
      label: '提示字段',
      prop: 'tooltipFields',
      formSlot: true,
      tableColumn: [
        {
          label: '字段',
          prop: 'fieldKey',
        },
        {
          label: '自定义显示名',
          prop: 'fieldName'
        }
      ]
    }
  ]
})

const queryParams = ref({})
const searchOption = ref({
  labelWidth: 'auto',
  cancelBtn: false,
  isSearch: true,
  fromAlign: 'right',
  inline: true,
  column: [
    {
      label: '订单',
      prop: 'orderId',
      type: 'select',
      dicData: [],
      clearable: true,
      searchSpan: 4,
      props: {
        label: 'code',
        value: 'id'
      }
    },
    {
      label: '日期范围',
      prop: 'dateRange',
      type: 'datePicker',
      datetype: 'daterange',
      searchSpan: 5,
    }
  ]
})
const tableLoading = ref(false)
const tableData = ref([])
const infoTip = ref(null)
const removeInPopup = ref(false)
const currentScale = ref('hour')

// 鼠标移入提示信息框
const popup = document.createElement('div')
Object.assign(popup.style, {
  position: 'fixed',
  width: '340px',
  backgroundColor: '#fff',
  border: '0',
  padding: '0',
  textAlign: 'left',
  boxShadow: '0px 4px 10px 0px rgba(54,59,76,0.15)',
  fontFamily: 'Microsoft YaHei-Regular, Microsoft YaHei',
  fontWeight: 400,
  fontSize: '14px',
  color: '#6F7588',
  lineHeight: '18px',
  boxSizing: 'border-box',
})
popup.addEventListener('mouseenter', e => {
  removeInPopup.value = true
})
popup.addEventListener('mouseleave', () => {
  if(removeInPopup.value) {
    removeInPopup.value = false
    hideTooltip()
  }
})
popup.addEventListener('click', e => {
  e.stopPropagation()
  if(e?.target?.className == 'viewInfo') {
    if(e.target.dataInfo) {
      viewMoreInfo(e.target.dataInfo)
    }
    hideTooltip()
  }
})
function showTooltip (infoList, x, y) {
  let left = x
  let top = y
  if((x + 340) > document.body.clientWidth) {
    left = document.body.clientWidth - 340
  }
  if((y + 268) > document.body.clientHeight) {
    top = y - 268 - 42
  }
  popup.innerHTML = ''
  popup.id = props.disableHandle ? 'popupPreview' : 'popup'
  popup.style.left = left + 'px'
  popup.style.top = top + 'px'
  if(props.disableHandle) {
    popup.style.zIndex = '9999'
  }
  let tooltipFields = []
  if(infoTip.value && infoTip.value.tooltipFields && infoTip.value.tooltipFields.length > 0) {
    tooltipFields = infoTip.value.tooltipFields
  }else{
    tooltipFields = [
      {fieldKey: 'code', fieldName: '订单编号'},
      {fieldKey: 'processCode', fieldName: '工序编码'},
      {fieldKey: 'startTime', fieldName: '计划开始时间'},
      {fieldKey: 'endTime', fieldName: '计划完成时间'},
    ]
  }
  let temp = infoList
  if(infoList.extras) {
    for(let k in infoList.extras) {
      temp[k] = infoList.extras[k]
    }
  }
  let info1 = document.createElement('div')
  info1.style.height = '256px'
  info1.style.paddingBottom = '16px'
  info1.style.overflow = 'hidden'
  let html = `<div class="scroll-list" style="height: 100%;padding: 0 24px;">`
  tooltipFields.filter(t => {
    if(temp[t.fieldKey] !== null && temp[t.fieldKey] !== undefined) {
      html += `
        <div style="margin-top: 16px;">
          <span style="display: inline-block;width: 120px;">${t.fieldName}：</span>
          <span style="margin-left: 8px;color: #363B4C;">${getDicLabel(temp[t.fieldKey], t.fieldKey)}</span>
        </div>
      `
    }
  })
  html += `</div>`
  info1.innerHTML = html
  popup.appendChild(info1)
  // 将弹出框添加到文档主体中
  document.body.appendChild(popup)
  removeInPopup.value = true
}
function hideTooltip () {
  if(document.body.contains(popup)) {
    document.body.removeChild(popup)
  }
}
// 自定义任务条
const customLayout = ref(args => {
  const { width, height, taskRecord } = args;
  if(taskRecord.extras) {
    for(let k in taskRecord.extras) {
      taskRecord[k] = taskRecord.extras[k]
    }
  }
  const rootContainer = new VTableGantt.VRender.Group({
    width,
    height: height,
    cornerRadius: [0, 0, 0, 0],
  })
  const container = new VTableGantt.VRender.Group({
    width,
    height: height,
    cornerRadius: [0, 0, 0, 0],
    fill: taskRecord[props.disableHandle ? 'delayTimeString' : 'overdueTimeString'] ? false : (taskRecord?.color ? taskRecord.color : '#C2C5CF'),
    fillOpacity: 0.2,
    background: taskRecord[props.disableHandle ? 'delayTimeString' : 'overdueTimeString'] ? '/jvs-ui-public/img/line.png' : '',
    backgroundMode: 'repeat-x',
  })
  // 进度
  let process = 1
  let total = (new Date(taskRecord.endTime).getTime() - new Date(taskRecord.startTime).getTime())
  if(taskRecord?.taskProgressTime) {
    let pt = (new Date(taskRecord.taskProgressTime).getTime() - new Date(taskRecord.startTime).getTime())
    process = Number.parseFloat(Number(pt / total).toFixed(2))
  }
  let bar = new VTableGantt.VRender.Group({
    width: width * process,
    dx: 0,
    height: taskRecord[props.disableHandle ? 'delayTimeString' : 'overdueTimeString'] ? (height - 2) : height,
    cornerRadius: [0, 4, 4, 0],
    fill: (taskRecord?.taskProgressTime) ? hexToRgba((taskRecord?.color ? taskRecord.color : '#C2C5CF'), 0.4) : false,
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    alignItems: 'flex-start',
  })
  let texts = []
  if(infoTip.value && infoTip.value.taskBarFields && infoTip.value.taskBarFields.length > 0) {
    infoTip.value.taskBarFields.filter(tit => {
      texts.push(taskRecord[tit.fieldKey])
    })
  }else{
    texts = [taskRecord.processCode]
  }
  let name = new VTableGantt.VRender.Text({
    text: texts.join('/'),
    fill: '#363B4C',
    fontSize: 12,
    lineClamp: 1,
    whiteSpace: (total > (3 * 60 * 60 * 1000) || texts.join('/').length < 7) ? 'no-wrap' : 'normal',
    wordBreak: 'break-all',
    suffixPosition: 'end',
  })
  bar.add(name)
  container.add(bar)
  // 逾期
  if(taskRecord[props.disableHandle ? 'delayTimeString' : 'overdueTimeString']){
    let bottomLine = new VTableGantt.VRender.Rect({
      x: 0,
      y: height - 2,
      width: width,
      height: 2,
      cornerRadius: 0,
      fill: '#C2C5CF'
    })
    container.add(bottomLine)
  }
  container.addEventListener('mouseenter', event => {
    removeInPopup.value = false
    const contbox = document.getElementById(ganttId.value)
    const containerRect = contbox.getBoundingClientRect()
    let target = event?.target
    for(let i = 0; i < 4; i++) {
      if(target?.type != 'group') {
        target = target.parent
      }
      if(target?._lastChild?.type != 'group') {
        target = target.parent
      }
    }
    const targetX = target.globalAABBBounds.x1
    const targetY = target.globalAABBBounds.y2
    let x = event.client.x // targetX + containerRect.left
    let y = targetY + containerRect.top
    showTooltip(taskRecord, x, y)
  })
  rootContainer.add(container)
  return {
    rootContainer: rootContainer
  }
})

const minDateTime = ref('')
const maxDateTime = ref('')
const columns = ref([
  {
    field: 'code',
    title: '订单号',
    width: '150px',
    sort: false,
    editor: 'input',
    tree: true,
  },
])
const option = ref({
  overscrollBehavior: 'none',
  records: tableData.value,
  groupBy: true,
  tasksShowMode: VTableGantt.TYPES.TasksShowMode.Sub_Tasks_Compact,
  minDate: minDateTime.value,
  maxDate: maxDateTime.value,
  taskListTable: {
    columns: columns.value,
    tableWidth: 150,
    minTableWidth: 100,
    maxTableWidth: 200,
    theme: {
      headerStyle: {
        borderColor: '#EEEFF0',
        borderLineWidth: 1,
        fontSize: 14,
        fontWeight: 'normal',
        color: '#363B4C',
        bgColor: '#F5F6F7'
      },
      bodyStyle: {
        borderColor: '#EEEFF0',
        borderLineWidth: [1, 1, 1, 1],
        fontSize: 16,
        color: '#4D4D4D',
        bgColor: '#FFF'
      },
    },
  },
  frame: {
    outerFrameStyle: {
      borderLineWidth: 1,
      borderColor: '#EEEFF0',
      cornerRadius: 0
    },
    verticalSplitLine: {
      lineColor: '#EEEFF0',
      lineWidth: 4
    },
    horizontalSplitLine: {
      lineColor: '#EEEFF0',
      lineWidth: 1
    },
    verticalSplitLineMoveable: true,
    verticalSplitLineHighlight: {
      lineColor: '#EEEFF0',
      lineWidth: 1
    }
  },
  grid: {
    verticalLine: {
      lineWidth: 1,
      lineColor: '#EEEFF0'
    },
    horizontalLine: {
      lineWidth: 1,
      lineColor: '#EEEFF0'
    }
  },
  headerRowHeight: 32,
  rowHeight: 50,
  taskBar: {
    startDateField: 'startTime',
    endDateField: 'endTime',
    progressField: 'progress',
    resizable: false,
    moveable: false,
    barStyle: {
      width: 40,
      cornerRadius: [0, 4, 4, 0],
    },
    hoverBarStyle: {
      barOverlayColor: ''
    },
    selectedBarStyle: {
      borderLineWidth: 0,
    },
    customLayout: customLayout.value
  },
  timelineHeader: {
    colWidth: 20,
    backgroundColor: '#F5F6F7',
    horizontalLine: {
      lineWidth: 1,
      lineColor: '#EEEFF0'
    },
    verticalLine: {
      lineWidth: 1,
      lineColor: '#EEEFF0'
    },
    scales: [
      // {
      //   unit: 'month',
      //   step: 1,
      //   format(date) {
      //     return `${dateFormat(date.startDate, 'yyyy-MM')}`
      //   },
      //   style: {
      //     fontSize: 14,
      //     fontWeight: 'bold',
      //     color: '#363B4C',
      //     textAlign: 'left',
      //     textBaseline: 'middle',
      //     backgroundColor: '#F5F6F7',
      //     border: '1px solid #EEEFF0'
      //   }
      // },
      {
        unit: 'day',
        step: 1,
        format(date) {
          return `${dateFormat(date.startDate, 'yyyy-MM-dd')}` // date.dateIndex.toString()
        },
        style: {
          fontSize: 14,
          fontWeight: 'normal',
          color: '#363B4C',
          textAlign: 'center',
          textBaseline: 'middle',
          backgroundColor: '#F5F6F7'
        }
      },
      {
        unit: 'hour',
        step: 1,
        style: {
          fontSize: 14,
          fontWeight: 'normal',
          color: '#363B4C',
          textAlign: 'center',
          textBaseline: 'middle',
          backgroundColor: '#F5F6F7'
        }
      }
    ]
  },
  markLine: [],
  scrollStyle: {
    scrollRailColor: '#EEEFF0',
    visible: 'scrolling',
    width: 6,
    scrollSliderCornerRadius: 2,
    scrollSliderColor: 'RGBA(0,0,0,0.1)'
  }
})
const ganttInstance = ref(null)

createHandle()

onMounted(() => {
  getListHandle()
})

onUnmounted(() => {
  hideTooltip()
})

function createHandle () {
  getPlanSettingHandle()
  if(props.disableHandle) {
    ganttId.value = 'orderGanttPreview'
  }else{
    getFieldOption('PLAN_ORDER_TASK_GANTT').then(res => {
      if(res.data && res.data.code == 0) {
        fieldList.value = res.data.data
        paramOption.value.column.filter(col => {
          if(col.prop == 'fieldKey') {
            col.dicData = res.data.data
          }
        })
      }
    })
  }
}

function getPlanSettingHandle () {
  getPlanSetting('PLAN_ORDER_TASK_GANTT').then(res => {
    if(res.data && res.data.code == 0) {
      infoTip.value = res.data.data
    }
  })
}

function init () {
  if(ganttInstance.value) {
    ganttInstance.value.setRecords(tableData.value)
    ganttInstance.value.updateDateRange(minDateTime.value, maxDateTime.value)
  }else{
    ganttInstance.value = new VTableGantt.Gantt(document.getElementById(ganttId.value), option.value)
    ganttInstance.value.on('scroll', (data) => {
      hideTooltip()
    })
    // mouseenter_task_bar 会影响任务条拉缩
    ganttInstance.value.on('mouseleave_task_bar', data => {
      if(removeInPopup.value === true) {
        hideTooltip()
      }
    })
    ganttInstance.value.taskListTableInstance.on('click_cell', args => {
      hideTooltip()
    })
    ganttInstance.value.canvas.onclick = function (e) {
      hideTooltip()
    }
  }
}

function getListHandle () {
  hideTooltip()
  // let temp = {
  //   beginDate: dateFormat(new Date((new Date().getTime()) - (10 * 24 * 60 * 60 * 1000)), 'yyyy-MM-dd')
  // }
  let temp = {}
  if(!props.disableHandle) {
    if(queryParams.value.orderId) {
      temp.orderId = queryParams.value.orderId
    }
    if(queryParams.value.dateRange && queryParams.value.dateRange.length > 0) {
      temp.dateRange = [(queryParams.value.dateRange[0] + ' 00:00:00'), (queryParams.value.dateRange[1] + ' 23:59:59')]
    }
  }
  tableData.value = []
  tableLoading.value = true
  // Object.assign(queryParams.value, temp)
  getList(temp, props.disableHandle).then(res => {
    if(res.data && res.data.code == 0) {
      // 数据
      let orders = []
      if(res.data.data.orders && res.data.data.orders.length > 0) {
        orders = res.data.data.orders
        searchOption.value.column.filter(col => {
          if(col.prop == 'orderId') {
            col.dicData = JSON.parse(JSON.stringify(res.data.data.orders))
          }
        })
      }
      if(res.data.data.tasks && res.data.data.tasks.length > 0) {
        let tasks = res.data.data.tasks
        for(let i in orders) {
          for(let j in tasks) {
            if(tasks[j].productionOrderId == orders[i].id) {
              if(!orders[i].children) {
                orders[i].children = []
              }
              orders[i].children.push({...tasks[j], id: tasks[j].processCode, code: orders[i].code})
              if(!orders[i].startTime) {
                orders[i].startTime = tasks[j].startTime
              }else{
                if(new Date(orders[i].startTime) > new Date(tasks[j].startTime)) {
                  orders[i].startTime = tasks[j].startTime
                }
              }
              if(!orders[i].endTime) {
                orders[i].endTime = tasks[j].endTime
              }else{
                if(new Date(orders[i].endTime) < new Date(tasks[j].endTime)) {
                  orders[i].endTime = tasks[j].endTime
                }
              }
            }
          }
        }
      }
      tableData.value = orders
      option.value.records = tableData.value
      // --- end 数据
      // 标记线
      let markLs = []
      if(res.data.data.lastTaskAssignmentTime) {
        markLs.push({
          date: new Date(res.data.data.lastTaskAssignmentTime),
          scrollToMarkLine: true,
          position: 'middle',
          style: {
            lineColor: 'red',
            lineWidth: 1,
            lineDash: [5, 5]
          }
        })
      }
      // --- end 标记线
      // 日期范围
      if(res.data.data.dateRange && res.data.data.dateRange.length > 0) {
        minDateTime.value = res.data.data.dateRange[0]
        maxDateTime.value = res.data.data.dateRange[1]
        option.value.minDate = minDateTime.value
        option.value.maxDate = maxDateTime.value
      }else{
        if(temp.dateRange && temp.dateRange.length > 0) {
          minDateTime.value = temp.dateRange[0]
          maxDateTime.value = temp.dateRange[1]
          option.value.minDate = minDateTime.value
          option.value.maxDate = maxDateTime.value
        }else{
          minDateTime.value = ''
          maxDateTime.value = ''
        }
      }
      // --- end 日期范围
      init()
    }
    tableLoading.value = false
  }).catch(e => {
    tableLoading.value = false
  })
}

function searchChange (form) {
  queryParams.value = form
  getListHandle()
}

function openParam () {
  getFieldSetting('PLAN_ORDER_TASK_GANTT').then(res => {
    if(res.data && res.data.code == 0) {
      if(res.data.data) {
        if(res.data.data.taskBarFields && res.data.data.taskBarFields.length > 0) {
          paramData.value['fieldKey'] = res.data.data.taskBarFields[0].fieldKey
          paramData.value['fieldName'] = res.data.data.taskBarFields[0].fieldName
        }
        if(res.data.data.tooltipFields && res.data.data.tooltipFields.length > 0) {
          paramData.value['tooltipFields'] = res.data.data.tooltipFields
        }
      }
      if(!paramData.value['tooltipFields']) {
        paramData.value['tooltipFields'] = []
      }
      dialogType.value = 'param'
      paramVisible.value = true
    }
  })
}

function addParam () {
  paramData.value.tooltipFields.push({})
}

function fieldChange (val, index, list) {
  fieldList.value.filter(fit => {
    if(fit.fieldKey == val) {
      if(!list[index].fieldName) {
        list[index].fieldName = fit.fieldName
      }
    }
  })
}

function deleteParam (index, list) {
  list.splice(index, 1)
}

function submitHandle () {
  let fieldName = ''
  if(paramData.value.fieldKey) {
    fieldList.value.filter(fit => {
      if(fit.fieldKey == paramData.value.fieldKey) {
        fieldName = fit.fieldName
      }
    })
  }
  let temp = {
    reportType: 'PLAN_ORDER_TASK_GANTT',
    taskBarFields: [{
      fieldKey: paramData.value.fieldKey,
      fieldName: fieldName
    }],
    tooltipFields: paramData.value.tooltipFields
  }
  let validate = true
  if(paramData.value.tooltipFields && paramData.value.tooltipFields.length > 0) {
    paramData.value.tooltipFields.filter(fit => {
      if(!fit.fieldKey) {
        validate = false
      }
    })
  }
  if(validate) {
    paramOption.value.submitLoading = true
    saveFieldSetting(temp).then(res => {
      if(res.data && res.data.code == 0) {
        ElNotification.closeAll()
        ElNotification({
          title: t(`common.tip`),
          message: t(`form.save`) + t(`common.success`),
          position: 'bottom-right',
          type: 'success',
        })
        getPlanSettingHandle()
        getListHandle()
        paramClose()
      }
      paramOption.value.submitLoading = false
    }).catch(e => {
      paramOption.value.submitLoading = false
    })
  }
}

function paramClose () {
  paramData.value = {}
  dialogType.value = ''
  paramVisible.value = false
}

function dateFormat (date, type) {
  let format = 'yyyy-MM-dd hh:mm:ss'
  if(type) {
    format = type
  }
  if(typeof date == 'string') {
    return date
  }
  if (date != 'Invalid Date') {
    var o = {
      'M+': date.getMonth() + 1, // month
      'd+': date.getDate(), // day
      'h+': date.getHours(), // hour
      'm+': date.getMinutes(), // minute
      's+': date.getSeconds(), // second
      'q+': Math.floor((date.getMonth() + 3) / 3), // quarter
      'S': date.getMilliseconds() // millisecond
    }
    if (/(y+)/.test(format)) {
      format = format.replace(RegExp.$1, (date.getFullYear() + '').substr(4 - RegExp.$1.length))
    }
    for (var k in o) {
      if (new RegExp('(' + k + ')').test(format)) {
        format = format.replace(RegExp.$1, RegExp.$1.length == 1 ? o[k] : ('00' + o[k]).substr(('' + o[k]).length))
      }
    }
    return format
  }
  return ''
}

watch(() => props.freshRandom, (newVal, oldVal) => {
  if(newVal > -1) {
    getListHandle()
  }
})
</script>
<style lang="scss" scoped>
.aps-programOfProduction-productionSchedulingPlan-resource-gantt{
  height: 100%;
  position: relative;
  .jvs-table{
    height: unset;
    padding: 0 24px;
    position: relative;
    .search-form{
      ::v-deep(.el-form-item){
        margin-bottom: 0;
        .el-form-item__label, .el-form-item__content{
          line-height: 32px;
          height: 32px;
        }
        .jvs-form-item{
          min-height: 32px;
          .el-select, .el-date-editor{
            height: 32px;
            .el-select__wrapper{
              min-height: 32px;
            }
          }
        }
        &.form-btn-bar{
          .el-button{
            height: 32px;
          }
        }
      }
    }
    .right{
      position: absolute;
      right: 24px;
      display: flex;
      align-items: center;
    }
  }
  .orderGantt{
    position: relative;
    margin-top: 18px;
    height: calc(100% - 52px);
    .rightTool{
      position: absolute;
      top: 5px;
      right: 24px;
      height: 24px;
      display: flex;
      align-items: center;
      text-align: center;
      z-index: 1;
      .today{
        width: 40px;
        height: 24px;
        line-height: 24px;
        background: #FFFFFF;
        border-radius: 4px;
        cursor: pointer;
      }
      .timeScales{
        margin-left: 8px;
        width: 138px;
        height: 24px;
        display: flex;
        justify-content: space-between;
        align-items: center;
        background: #fff;
        border-radius: 4px;
        .timeScales-item{
          width: 32px;
          height: 22px;
          line-height: 22px;
          border-radius: 4px;
          font-family: Microsoft YaHei-Regular, Microsoft YaHei;
          font-weight: 400;
          font-size: 12px;
          color: #363B4C;
          &.active{
            background: #E4EDFF;
            color: #1E6FFF;
          }
        }
      }
    }
  }
  .loading{
    background: #fff;
    position: absolute;
    width: 100%;
    height: calc(100% - 52px);
    top: 52px;
    left: 0;
    z-index: 100;
    background-image: url('/jvs-ui-public/img/loading.gif');
    background-repeat: no-repeat;
    background-position: center;
  }
  &.disable-handle{
    .orderGantt{
      margin-top: 0;
      height: 100%;
    }
  }
}
.tooltipFields-table{
  .header{
    display: flex;
    align-items: center;
    height: 40px;
    line-height: 40px;
    background: #F5F6F7;
    border-radius: 4px;
    .header-item{
      flex: 1;
      overflow: hidden;
      padding: 0 24px;
      margin-right: 12px;
      font-family: Microsoft YaHei-Bold, Microsoft YaHei;
      font-weight: 700;
      font-size: 14px;
      color: #363B4C;
      margin-right: 12px;
    }
  }
  .body{
    height: calc(70vh - 295px);
    .body-item{
      display: flex;
      align-items: center;
      height: 56px;
      box-sizing: border-box;
      border-bottom: 1px solid #EEEFF0;
      padding-right: 12px;
      .body-item-column{
        display: flex;
        align-items: center;
        flex: 1;
        overflow: hidden;
        padding-left: 24px;
        margin-right: 12px;
        &.error{
          ::v-deep(.el-input__wrapper), ::v-deep(.el-select__wrapper){
            border: 1px solid var(--el-color-danger);
            box-shadow: none;
          }
        }
      }
    }
  }
}
.bottom-add-button{
  margin-top: 8px;
  .button{
    width: 80px;
    display: flex;
    align-items: center;
    cursor: pointer;
    .icon{
      width: 16px;
      height: 16px;
      background: #1E6FFF;
      border-radius: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 4px;
      svg{
        width: 12px;
        height: 12px;
        fill: #fff;
      }
    }
    span{
      font-family: Microsoft YaHei-Regular, Microsoft YaHei;
      font-weight: 400;
      font-size: 14px;
      color: #1E6FFF;
      line-height: 18px;
    }
  } 
}
.delete-icon-button{
  width: 16px;
  height: 16px!important;
  background: #36B452;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  cursor: pointer;
  .border-line{
    width: 10px;
    height: 2px;
    background: #fff;
    border-radius: 2px;
  }
}
::v-deep(.el-popover){
  padding: 0;
}
.popover-tool-list{
  width: 168px;
  padding: 10px 8px;
  box-sizing: border-box;
  .item{
    width: 152px;
    height: 32px;
    line-height: 32px;
    border-radius: 4px;
    padding: 0 8px;
    box-sizing: border-box;
    color: #D7D8DB;
    cursor: no-drop;
    &:hover{
      background: #F5F6F7;
    }
    &.enable{
      color: #363B4C;
      cursor: pointer;
    }
  }
}
.import-data-box{
  padding: 20px;
  .import-data-upload{
    text-align: center;
    .el-upload-dragger{
      display: flex;
      align-items: center;
      justify-content: center;
      background-color: #f7f7f7;
      width: 600px;
    }
  }
  .upload-explain{
    margin-top: 16px;
    font-size: 12px;
    ul{
      padding: 0 16px;
      margin: 8px 0;
      li{
        line-height: 20px;
        span{
          font-weight: bold;
        }
      }
    }
  }
  .uploading-box{
    display: flex;
    background-color: rgb(247, 247, 247);
    width: 600px;
    border: 1px dashed rgb(217, 217, 217);
    border-radius: 6px;
    height: 180px;
    box-sizing: border-box;
    text-align: center;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    margin-left: calc(50% - 300px);
  }
}
</style>