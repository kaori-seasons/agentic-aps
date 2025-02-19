<template>
  <div :class="{'aps-programOfProduction-productionSchedulingPlan-resource-gantt': true, 'disable-handle': props.disableHandle}">
    <div v-if="!props.disableHandle" class="jvs-table">
      <jvs-form :option="searchOption" :formData="queryParams" :isSearch="true" class="search-form" @submit="searchChange"></jvs-form>
    </div>
    <div :id="ganttId" class="materialGantt">
    </div>
    <div v-if="tableLoading" class="loading"></div>
  </div>
</template>
<script lang="ts" setup name="materialGantt">
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

import { getList } from './api'

const props = defineProps({
  freshRandom: {
    type: Number
  },
  disableHandle: {
    type: Boolean
  }
})

const { proxy } = getCurrentInstance()
const emit = defineEmits([])
const { t } = useI18n()
const uploadBtn = ref<UploadInstance>()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

const ganttId = ref('materialGantt')

const queryParams = ref({})
const searchOption = ref({
  labelWidth: 'auto',
  cancelBtn: false,
  isSearch: true,
  fromAlign: 'right',
  inline: true,
  column: [
    {
      label: '物料',
      prop: 'id',
      type: 'select',
      dicData: [],
      clearable: true,
      searchSpan: 4,
      props: {
        label: 'name',
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
const currentScale = ref('hour')

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
    fill: false,
  })
  // 进度
  let bar = new VTableGantt.VRender.Group({
    width,
    dx: 0,
    height,
    cornerRadius: [0, 4, 4, 0],
    fill: false,
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    alignItems: 'flex-start',
  })
  let texts = [taskRecord.code]
  let name = new VTableGantt.VRender.Text({
    text: texts.join('/'),
    fill: '#363B4C',
    fontSize: 12,
    lineClamp: 1,
    whiteSpace: 'no-wrap',
    wordBreak: 'break-all',
    suffixPosition: 'end',
  })
  bar.add(name)
  container.add(bar)
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
    title: '物料编码',
    width: '130px',
    sort: false,
    editor: 'input',
    tree: true,
  },
  {
    field: 'name',
    title: '物料名称',
    width: '150px',
    sort: false,
    editor: 'input',
    tree: true,
  },
  {
    field: 'totalQuantity',
    title: '合计',
    width: '100px',
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
    tableWidth: 380,
    minTableWidth: 180,
    maxTableWidth: 380,
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
      {
        unit: 'month',
        step: 1,
        format(date) {
          return `${dateFormat(date.startDate, 'yyyy-MM')}`
        },
        style: {
          fontSize: 14,
          fontWeight: 'bold',
          color: '#363B4C',
          textAlign: 'left',
          textBaseline: 'middle',
          backgroundColor: '#F5F6F7',
          border: '1px solid #EEEFF0'
        }
      },
      {
        unit: 'day',
        step: 1,
        format(date) {
          return date.dateIndex.toString()
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

if(props.disableHandle) {
  ganttId.value = 'materialGanttPreview'
}

onMounted(() => {
  getListHandle()
})

function init () {
  if(ganttInstance.value) {
    ganttInstance.value.setRecords(tableData.value)
    ganttInstance.value.updateDateRange(minDateTime.value, maxDateTime.value)
  }else{
    ganttInstance.value = new VTableGantt.Gantt(document.getElementById(ganttId.value), option.value)
  }
}

function getListHandle () {
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
      let materials = []
      if(res.data.data.materials && res.data.data.materials.length > 0) {
        materials = res.data.data.materials
        searchOption.value.column.filter(col => {
          if(col.prop == 'id') {
            col.dicData = JSON.parse(JSON.stringify(res.data.data.materials))
          }
        })
      }
      if(res.data.data.requirements && res.data.data.requirements.length > 0) {
        let tasks = res.data.data.requirements
        for(let i in materials) {
          for(let j in tasks) {
            if(tasks[j].id == materials[i].id) {
              materials[i].totalQuantity = tasks[j].totalQuantity
              if(!materials[i].children) {
                materials[i].children = []
              }
              if(tasks[j].dailyDemand) {
                for(let t in tasks[j].dailyDemand) {
                  materials[i].children.push({
                    id: t,
                    startTime: t,
                    endTime: t,
                    code: tasks[j].dailyDemand[t]
                  })
                  if(!materials[i].startTime) {
                    materials[i].startTime = t
                  }else{
                    if(new Date(materials[i].startTime) > new Date(t)) {
                      materials[i].startTime = t
                    }
                  }
                  if(!materials[i].endTime) {
                    materials[i].endTime = t
                  }else{
                    if(new Date(materials[i].endTime) < new Date(t)) {
                      materials[i].endTime = t
                    }
                  }
                }
              }
            }
          }
        }
      }
      tableData.value = materials
      option.value.records = tableData.value
      // --- end 数据
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
  }
  .materialGantt{
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
    .materialGantt{
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