<template>
  <div :class="{'aps-programOfProduction-productionSchedulingPlan-resource-gantt': true, 'disable-handle': props.disableHandle}">
    <div v-if="!props.disableHandle" class="jvs-table">
      <jvs-form :option="searchOption" :formData="queryParams" :isSearch="true" class="search-form" @submit="searchChange"></jvs-form>
      <div class="right">
        <div class="tools">
          <div class="tools-list">
            <el-popconfirm width="360px" title="撤销操作将撤回您对任务的移动、拆分和合并更改，但锁定和解锁状态将不受影响。确认继续吗？" @confirm="cancelAdjustHandle(adjusted)">
              <template #reference>
                <el-button :loading="adjustLoading" :class="{'tool-item': true, 'enable': adjusted}" :disabled="!(adjusted && !adjustLoading)">
                  <svg>
                    <use xlink:href="#jvs-aps-icon-chexiao"></use>
                  </svg>
                  <span>撤销</span>
                </el-button>
              </template>
            </el-popconfirm>
          </div>
          <div class="tools-list">
            <el-popover
              v-model:visible="splitPopover"
              popper-class="no-padding-border-popover"
              placement="bottom"
              :width="168"
              :disabled="!(activeRecord.length == 1)"
              trigger="click"
            >
              <template #reference>
                <div :class="{'tool-item': true, 'enable': activeRecord.length == 1}" @click.stop="openSplitPopover">
                  <svg>
                    <use xlink:href="#jvs-aps-icon-chaifen"></use>
                  </svg>
                  <span>拆分</span>
                  <el-icon><ArrowDownBold /></el-icon>
                </div>
              </template>
              <div class="popover-tool-list">
                <div :class="{'item': true, 'enable': true}" @click="openSplit('custom')">按拆出数量拆分</div>
                <div :class="{'item': true, 'enable': true}" @click="openSplit('average')">按任务数量平均拆分</div>
                <div :class="{'item': true, 'enable': clickTargetData.taskProgressTime}" @click="splitCompletionHandle(clickTargetData.taskProgressTime)">按任务完成数量拆分</div>
              </div>
            </el-popover>
            <el-popconfirm title="确认合并?" @confirm="mergeHandle(activeRecord.length > 1)">
              <template #reference>
                <el-button :loading="mergeLoading" :class="{'tool-item': true, 'enable': activeRecord.length > 1}" :disabled="activeRecord.length < 1">
                <svg>
                  <use xlink:href="#jvs-aps-icon-icon-hebing"></use>
                </svg>
                <span>合并</span>
              </el-button>
              </template>
            </el-popconfirm>
            
            <el-popover
              popper-class="no-padding-border-popover"
              placement="bottom"
              :width="168"
              trigger="click"
            >
              <template #reference>
                <div :class="{'tool-item': true, 'enable': true}">
                <svg>
                  <use xlink:href="#jvs-aps-icon-yidong"></use>
                </svg>
                <span>移动</span>
                <el-icon><ArrowDownBold /></el-icon>
              </div>
              </template>
              <div class="popover-tool-list">
                <div :class="{'item': true, 'enable': activeRecord.length == 1}" @click="openMove(activeRecord.length == 1, 'move')">移动指定任务</div>
                <div :class="{'item': true, 'enable': true}" @click="openMove(true, 'complete')">完成任务到指定时间</div>
              </div>
            </el-popover>
            <el-popover
              v-model:visible="lockPopover"
              popper-class="no-padding-border-popover"
              placement="bottom"
              :width="168"
              :disabled="false"
              trigger="click"
            >
              <template #reference>
                <div :class="{'tool-item': true, 'enable': true}">
                  <svg>
                    <use xlink:href="#jvs-aps-icon-suoding"></use>
                  </svg>
                  <span>锁定</span>
                  <el-icon><ArrowDownBold /></el-icon>
                </div>
              </template>
              <div class="popover-tool-list">
                <div :class="{'item': true, 'enable': activeRecord.length > 0}" @click="lockTask(activeRecord.length > 0, 'select', true)">锁定指定任务</div>
                <div :class="{'item': true, 'enable': true}" @click="lockTask(true, 'start', true)">锁定已开工任务</div>
              </div>
            </el-popover>
            <el-popover
              v-model:visible="unlockPopover"
              popper-class="no-padding-border-popover"
              placement="bottom"
              :width="168"
              :disabled="false"
              trigger="click"
            >
              <template #reference>
                <div :class="{'tool-item': true, 'enable': true}">
                  <svg>
                    <use xlink:href="#jvs-aps-icon-jiesuo1"></use>
                  </svg>
                  <span>解锁</span>
                  <el-icon><ArrowDownBold /></el-icon>
                </div>
              </template>
              <div class="popover-tool-list">
                <div :class="{'item': true, 'enable': activeRecord.length > 0}" @click="lockTask(activeRecord.length > 0, 'select', false)">解锁指定任务</div>
                <div :class="{'item': true, 'enable': true}" @click="lockTask(true, 'start', false)">解锁已开工任务</div>
              </div>
            </el-popover>
          </div>
          <div class="tools-list">
            <div :class="{'tool-item': true, 'enable': true}" @click="openImport">
              <svg>
                <use xlink:href="#jvs-aps-icon-piliangbaogong"></use>
              </svg>
              <span>报工</span>
            </div>
            <div :class="{'tool-item': true, 'enable': true}" @click="openExport">
              <svg>
                <use xlink:href="#jvs-aps-icon-daochu"></use>
              </svg>
              <span>派工</span>
            </div>
          </div>
        </div>
        <el-button type="primary" @click="openParam">参数设置</el-button>
      </div>
    </div>
    <div :id="ganttId" class="resourceGantt" v-click-outside="hideTooltip">
      <!-- <div class="rightTool">
        <div class="today">今天</div>
        <div class="timeScales">
          <div :class="{'timeScales-item': true, 'actice': currentScale == 'hour'}">时</div>
          <div :class="{'timeScales-item': true, 'actice': currentScale == 'day'}">天</div>
          <div :class="{'timeScales-item': true, 'actice': currentScale == 'month'}">月</div>
        </div>
      </div> -->
    </div>
    <div v-if="tableLoading" class="loading"></div>

    <!-- 任务信息 -->
    <taskInfo :visible="dialogVisible" :row="rowData" @fresh="getListHandle" @close="closeHandle"></taskInfo>
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
        <jvs-form v-if="dialogType == 'split'" :option="splitOption" :formData="splitForm" @submit="splitHandle" @cancelClick="paramClose"></jvs-form>
        <jvs-form v-if="dialogType == 'move'" :option="mergeOption" :formData="splitForm" @submit="moveSubmit" @cancelClick="paramClose">
          <template #resourceIdForm>
            <div class="jvs-form-item">
              <el-input v-model="splitForm.mainResourceName" disabled>
                <template #suffix>
                  <el-icon style="cursor: pointer;" @click.stop="openResource"><Search /></el-icon>
                </template>
              </el-input>
            </div>
          </template>
        </jvs-form>
        <jvs-form v-if="dialogType == 'export'" :option="exportOption" :formData="splitForm" @submit="exportSubmit" @cancelClick="paramClose"></jvs-form>
      </div>
    </el-dialog>
    <!-- 选择资源 -->
    <resourceDialog dialogTitle="选择资源" :visible="resourceVisible" @row-click="resourceSubmit" @close="resourceClose"></resourceDialog>
    <!-- 导入数据 -->
    <el-dialog
      title="报工"
      v-model="tempFileDialogVisible"
      width="720px"
      append-to-body
      :close-on-click-modal="false"
      :before-close="tempFileHandleClose">
      <div class="import-data-box">
        <div class="uploading-box" v-show="uploading">
          <div>
            <i class="el-icon-loading"></i>
          </div>
          <div>正在上传...</div>
        </div>
        <el-upload
          v-show="!uploading"
          class="import-data-upload"
          ref="uploadBtn"
          accept=".xlsx"
          :action="`/mgr/jvs-aps/task-report/import`"
          :file-list="tempfileList"
          :show-file-list="false"
          :before-upload="beforeTempUpload"
          :on-change="onTempChange"
          :on-success="importSuccess"
          :on-error="errTempHandle"
          drag
          multiple>
          <div class="el-upload__text">
            <svg aria-hidden="true" style="width: 24px; height: 24px;margin-bottom: 16px">
              <use xlink:href="#icon-upload"></use>
            </svg>
            <div>点击或者拖动文件到虚线框内上传</div>
            <div style="color: #a2a3a5;font-size: 12px;margin-top: 8px;">支持.xlsx类型的文件</div>
          </div>
        </el-upload>
        <div class="upload-explain">
          <span style="color: #a2a3a5;">上传的文件符合以下规范：</span>
          <ul>
            <li style="list-style: disc">仅支持<span>（*.xlsx）</span>文件</li>
          </ul>
        </div>
      </div>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup name="resourceGantt">
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
import { ArrowDownBold, Loading, Search } from '@element-plus/icons-vue'
import  * as VTableGantt  from '@visactor/vtable-gantt'

import taskInfo  from './info.vue'
import resourceDialog from '../../component/resourceDialog.vue'

import { getList, getGroupList, getFieldOption, getFieldSetting, saveFieldSetting, getPlanSetting, gettaskDetail,
  checkAdjusted, cancelAdjusted, splitQuantity, splitTaskNumber, splitCompletion, mergeTasks, movePosition, moveCompleted,
  freezeTasks, freezeStarted, exportTask,
} from './api'

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

const ganttId = ref('resourceGantt')

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
const adjusted = ref(false)
const adjustLoading = ref(false)
const splitType = ref('')
const splitPopover = ref(false)
const splitForm = ref(null)
const splitOption = ref({
  submitLoading: false,
  emptyBtn: false,
  formAlign: 'top',
  column: [
    {
      label: '拆出数量',
      prop: 'splitQuantity',
      type: 'inputNumber',
      min: 1,
      precision: 0,
      rules: [ { required: true, message: '请填写拆出数量', trigger: 'blur' } ]
    },
    {
      label: '平均拆分为几个任务',
      prop: 'splitTaskNumber',
      type: 'inputNumber',
      min: 1,
      precision: 0,
      rules: [ { required: true, message: '请填写平均拆分任务数', trigger: 'blur' } ]
    }
  ]
})
const mergeLoading = ref(false)
const mergeOption = ref({
  submitLoading: false,
  emptyBtn: false,
  formAlign: 'top',
  column: [
    {
      label: '新的计划开始时间',
      prop: 'newStartTime',
      type: 'datePicker',
      datetype: 'datetime',
      rules: [ { required: true, message: '请选择新的计划开始时间', trigger: 'change' } ]
    },
    {
      label: '目标资源',
      prop: 'resourceId',
      formSlot: true,
      rules: [ { required: true, message: '请选择目标资源', trigger: 'change' } ]
    },
    {
      label: '时间',
      prop: 'time',
      type: 'datePicker',
      datetype: 'datetime',
      rules: [ { required: true, message: '请选择时间', trigger: 'change' } ]
    }
  ]
})
const resourceVisible = ref(false)
const lockPopover = ref(false)
const unlockPopover = ref(false)
const exportOption = ref({
  submitLoading: false,
  emptyBtn: false,
  formAlign: 'top',
  column: [
    {
      label: '起始时间',
      prop: 'beginTime',
      type: 'datePicker',
      datetype: 'datetime',
      rules: [ { required: true, message: '请选择起始时间', trigger: 'change' } ]
    },
    {
      label: '结束时间',
      prop: 'endTime',
      type: 'datePicker',
      datetype: 'datetime',
      rules: [ { required: true, message: '请选择结束时间', trigger: 'change' } ]
    },
  ]
})
const tempFileDialogVisible = ref(false)
const limitShow = ref(false)
const tempfileList = ref([])
const uploading = ref(false)
const dialogVisible = ref(false)
const rowData = ref(null)
const queryParams = ref({})
const searchOption = ref({
  labelWidth: 'auto',
  cancelBtn: false,
  isSearch: true,
  fromAlign: 'right',
  inline: true,
  column: [
    {
      label: '资源组',
      prop: 'resourceGroup',
      type: 'select',
      dicData: [],
      clearable: true,
      searchSpan: 3,
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
const lineData = ref([])
const infoTip = ref(null)
const removeInPopup = ref(false)
const currentScale = ref('hour')
const columns = ref([
  {
    field: 'name',
    title: '资源名称',
    width: '160px',
    sort: false,
    editor: 'input',
    tree: true,
  },
  {
    field: 'code',
    title: '编码',
    width: '130px',
    sort: false,
    editor: 'input'
  },
])
const markLine = ref([])

// 鼠标移入提示信息框
const popup = document.createElement('div')
Object.assign(popup.style, {
  position: 'fixed',
  width: '320px',
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
  if((x + 320) > document.body.clientWidth) {
    left = document.body.clientWidth - 320
  }
  if((y + 268) > document.body.clientHeight) {
    top = y - 268 - 40
  }
  popup.innerHTML = ''
  popup.id = props.disableHandle ? 'previewPopup' : 'popup'
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
      {fieldKey: 'code', fieldName: '资源编码'},
      {fieldKey: 'processName', fieldName: '工序名称'},
      {fieldKey: 'startTime', fieldName: '计划开始'},
      {fieldKey: 'endTime', fieldName: '计划结束'},
    ]
  }
  let temp = infoList
  if(infoList.extras) {
    for(let k in infoList.extras) {
      temp[k] = infoList.extras[k]
    }
  }
  let info1 = document.createElement('div')
  info1.style.height = props.disableHandle ? '256px' : '208px'
  info1.style.overflow = 'hidden'
  let html = `<div class="scroll-list" style="height: 100%;padding: 0 24px;">`
  tooltipFields.filter(t => {
    if(temp[t.fieldKey] !== null && temp[t.fieldKey] !== undefined) {
      html += `
        <div style="margin-top: 16px;">
          <span style="display: inline-block;width: 100px;">${t.fieldName}：</span>
          <span style="margin-left: 8px;color: #363B4C;">${getDicLabel(temp[t.fieldKey], t.fieldKey)}</span>
        </div>
      `
    }
  })
  html += `</div>`
  info1.innerHTML = html
  popup.appendChild(info1)
  if(!props.disableHandle) {
    let button = document.createElement('div')
    button.className = 'viewInfo'
    button.style.margin = '16px 24px'
    button.style.width = '88px'
    button.style.height = '32px'
    button.style.cursor = 'pointer'
    button.style.background = '#1E6FFF'
    button.style.textAlign = 'center'
    button.style.color = '#fff'
    button.style.fontSize = '14px'
    button.style.borderRadius = '4px'
    button.style.lineHeight = '32px'
    button.innerText = `查看详情`
    button.dataInfo = infoList
    popup.appendChild(button)
  }
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
const activeRecord = ref([])
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
    height: height - 3,
    cornerRadius: [0, 0, 0, 0],
    fill: taskRecord.overdueTimeString ? false : (taskRecord?.color ? taskRecord.color : '#C2C5CF'),
    fillOpacity: 0.2,
    background: taskRecord.overdueTimeString ? '/jvs-ui-public/img/line.png' : '',
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
    width: width * process - ((activeRecord.value && activeRecord.value.length > 0 && (activeRecord.value.indexOf(taskRecord.id) > -1)) ? 6 : 0),
    dx: (activeRecord.value && activeRecord.value.length > 0 && (activeRecord.value.indexOf(taskRecord.id) > -1)) ? 6 : 0,
    height: (taskRecord.overdueTimeString ? (height - 3 - 2) : ((activeRecord.value && activeRecord.value.length > 0 && (activeRecord.value.indexOf(taskRecord.id) > -1)) ? (height -3 - 2) : (height - 3))),
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
    texts = [taskRecord.code]
  }
  if(taskRecord.compliant !== true || taskRecord.pinned || taskRecord.mergeTask) {
    let iconBox = new VTableGantt.VRender.Group({
      width: width,
      height: 14,
    })
    if(taskRecord.compliant !== true) {
      let warning = new VTableGantt.VRender.Image({
        width: 10,
        height: 10,
        image: '/jvs-ui-public/img/warning.png'
      })
      iconBox.add(warning)
    }
    if(taskRecord.pinned) {
      let lock = new VTableGantt.VRender.Image({
        width: 12,
        height: 12,
        image: '/jvs-ui-public/img/lock.png',
        dx: (taskRecord.compliant !== true) ? 16 : 0,
      })
      iconBox.add(lock)
    }
    if(taskRecord.mergeTask) {
      let merge = new VTableGantt.VRender.Image({
        width: 12,
        height: 12,
        image: '/jvs-ui-public/img/merge.png',
        dx: (taskRecord.compliant !== true || taskRecord.pinned) ? ((taskRecord.compliant !== true && taskRecord.pinned) ? 32 : 16) : 0,
      })
      iconBox.add(merge)
    }
    bar.add(iconBox)
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
  if(taskRecord.overdueTimeString){
    let bottomLine = new VTableGantt.VRender.Rect({
      x: 0,
      y: height - 5,
      width: width,
      height: 2,
      cornerRadius: 0,
      fill: '#C2C5CF'
    })
    container.add(bottomLine)
  }
  // 边框
  let addLine = true
  if(addLine) {
    let topLine = new VTableGantt.VRender.Rect({
      width: width,
      height: (activeRecord.value && activeRecord.value.length > 0 && (activeRecord.value.indexOf(taskRecord.id) > -1)) ? 1 : 0,
      fill: taskRecord?.color ? taskRecord.color : '#C2C5CF'
    })
    container.add(topLine)
    let bottomLine = new VTableGantt.VRender.Rect({
      width: width,
      height: (activeRecord.value && activeRecord.value.length > 0 && (activeRecord.value.indexOf(taskRecord.id) > -1)) ? 1 : 0,
      fill: taskRecord?.color ? taskRecord.color : '#C2C5CF',
      dy: height - 4
    })
    container.add(bottomLine)
    let leftLine = new VTableGantt.VRender.Group({
      width: (activeRecord.value && activeRecord.value.length > 0 && (activeRecord.value.indexOf(taskRecord.id) > -1)) ? 6 : 0,
      height: height - 3,
      fill: taskRecord?.color ? taskRecord.color : '#C2C5CF',
      // cursor: (activeRecord.value && activeRecord.value.length > 0 && (activeRecord.value.indexOf(taskRecord.id) > -1)) ? 'move' : 'default',
    })
    let leftInLine = new VTableGantt.VRender.Rect({
      width: (activeRecord.value && activeRecord.value.length > 0 && (activeRecord.value.indexOf(taskRecord.id) > -1)) ? 2 : 0,
      height: 10,
      fill: '#fff',
      dx: 2,
      dy: (height - 3) / 2 - 5,
      // cursor: (activeRecord.value && activeRecord.value.length > 0 && (activeRecord.value.indexOf(taskRecord.id) > -1)) ? 'move' : 'default',
    })
    leftLine.add(leftInLine)
    container.add(leftLine)
    let rightLine = new VTableGantt.VRender.Group({
      width: (activeRecord.value && activeRecord.value.length > 0 && (activeRecord.value.indexOf(taskRecord.id) > -1)) ? 6 : 0,
      height: height - 3,
      fill: taskRecord?.color ? taskRecord.color : '#C2C5CF',
      dx: width - 6,
      // cursor: (activeRecord.value && activeRecord.value.length > 0 && (activeRecord.value.indexOf(taskRecord.id) > -1)) ? 'move' : 'default',
    })
    let rightInLine = new VTableGantt.VRender.Rect({
      width: (activeRecord.value && activeRecord.value.length > 0 && (activeRecord.value.indexOf(taskRecord.id) > -1)) ? 2 : 0,
      height: 10,
      fill: '#fff',
      dx: 2,
      dy: (height - 3) / 2 - 5,
      // cursor: (activeRecord.value && activeRecord.value.length > 0 && (activeRecord.value.indexOf(taskRecord.id) > -1)) ? 'move' : 'default',
    })
    rightLine.add(rightInLine)
    container.add(rightLine)
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
  // 改动
  if(taskRecord.adjusted){
    let changeLine = new VTableGantt.VRender.Line({
      x: 0,
      y: height,
      curveType: 'linear',
      stroke: '#363B4C',
      lineWidth: 2,
      lineDash: [3, 3],
      points: [[0, 0], [width, 0]].map(item => ({ x: item[0], y: item[1] }))
    })
    rootContainer.add(changeLine)
  }
  return {
    rootContainer: rootContainer
  }
})

const minDateTime = ref('')
const maxDateTime = ref('')
const option = ref({
  overscrollBehavior: 'none',
  records: tableData.value,
  groupBy: true,
  tasksShowMode: VTableGantt.TYPES.TasksShowMode.Sub_Tasks_Compact,
  minDate: minDateTime.value,
  maxDate: maxDateTime.value,
  taskListTable: {
    columns: columns.value,
    tableWidth: 290,
    minTableWidth: 160,
    maxTableWidth: 290,
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
  dependency: {
    links: [],
    linkLineStyle: {
      lineColor: 'red',
      lineWidth: 1
    },
    linkDeletable: true,
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
  markLine: markLine.value,
  scrollStyle: {
    scrollRailColor: '#EEEFF0',
    visible: 'scrolling',
    width: 6,
    scrollSliderCornerRadius: 2,
    scrollSliderColor: 'RGBA(0,0,0,0.1)'
  }
})
const ganttInstance = ref(null)
const clickTarget = ref('')
const clickTargetData = ref({})
const taskDataArray = ref([])

createHandle()

onMounted(() => {
  getListHandle()
})

onUnmounted(() => {
  dialogVisible.value = false
  hideTooltip()
})

function createHandle () {
  getPlanSettingHandle()
  if(props.disableHandle) {
    ganttId.value = 'resourceGanttPreview'
  }else{
    getGroupList().then(res => {
      if(res.data && res.data.code == 0) {
        let list = []
        for(let i in res.data.data) {
          list.push({
            label: res.data.data[i],
            value: res.data.data[i]
          })
        }
        searchOption.value.column.filter(col => {
          if(col.prop == 'resourceGroup') {
            col.dicData = list
          }
        })
      }
    })
    getFieldOption('PLAN_RESOURCE_TASK_GANTT').then(res => {
      if(res.data && res.data.code == 0) {
        fieldList.value = res.data.data
        paramOption.value.column.filter(col => {
          if(col.prop == 'fieldKey') {
            col.dicData = res.data.data
          }
        })
      }
    })
    checkAdjustedHandle()
  }
}

function getPlanSettingHandle () {
  getPlanSetting('PLAN_RESOURCE_TASK_GANTT').then(res => {
    if(res.data && res.data.code == 0) {
      infoTip.value = res.data.data
    }
  })
}

function checkAdjustedHandle () {
  checkAdjusted().then(res => {
    if(res.data && res.data.code == 0) {
      adjusted.value = res.data.data
    }
  })
}

function init () {
  if(ganttInstance.value) {
    ganttInstance.value.setRecords(tableData.value)
    ganttInstance.value.updateMarkLine(markLine.value)
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
    ganttInstance.value.on('click_task_bar', data => {
      let { record, index, sub_task_index } = data
      clickTarget.value = record.id
      clickTargetData.value = record
      let activeIndex = activeRecord.value.indexOf(record.id)
      if(activeIndex == -1) {
        activeRecord.value.push(record.id)
        ganttInstance.value.updateTaskRecord(record, index, sub_task_index)
        if(record.nextTaskCodes && record.nextTaskCodes.length > 0) {
          if(lineData.value && lineData.value.length > 0) {
            for(let i in lineData.value) {
              ganttInstance.value.deleteLink(lineData.value[i])
            }
            lineData.value = []
          }
          linkPath(record.code, record.nextTaskCodes, 'add')
        }
      }else{
        activeRecord.value.splice(activeIndex, 1)
        ganttInstance.value.updateTaskRecord(record, index, sub_task_index)
        if(lineData.value && lineData.value.length > 0) {
          for(let i in lineData.value) {
            ganttInstance.value.deleteLink(lineData.value[i])
          }
          lineData.value = []
        }
      }
    })
    ganttInstance.value.canvas.onclick = function (e) {
      if(!clickTarget.value) {
        hideTooltip()
        // 清除选中效果及连线
        if(lineData.value && lineData.value.length > 0) {
          for(let i in lineData.value) {
            ganttInstance.value.deleteLink(lineData.value[i])
          }
          lineData.value = []
        }
        activeRecord.value = []
        ganttInstance.value.setRecords(tableData.value)
      }
      clickTarget.value = ''
    }
  }
}

function linkPath (from, list, oprate) {
  for(let i in list) {
    let line = {
      type: VTableGantt.TYPES.DependencyType.FinishToStart,
      linkedFromTaskKey: from,
      linkedToTaskKey: list[i]
    }
    if(oprate == 'add') {
      lineData.value.push(line)
      ganttInstance.value.addLink(line)
    }
    if(oprate == 'delete') {
      let delIndex = -1
      lineData.value.filter((lit, lix) => {
        if(lit.linkedFromTaskKey == line.linkedFromTaskKey && lit.linkedToTaskKey == lit.linkedToTaskKey) {
          delIndex = lix
        }
      })
      if(delIndex > -1) {
        lineData.value.splice(delIndex, 1)
      }
      ganttInstance.value.addLink(line)
    }
    taskDataArray.value.filter(task => {
      if(task.code == list[i] && task.nextTaskCodes && task.nextTaskCodes.length > 0) {
        linkPath(list[i], task.nextTaskCodes, oprate)
      }
    })
  }
}

function getListHandle () {
  // let temp = {
  //   beginDate: dateFormat(new Date((new Date().getTime()) - (10 * 24 * 60 * 60 * 1000)), 'yyyy-MM-dd')
  // }
  let temp = {}
  if(!props.disableHandle) {
    hideTooltip()
    // 清除选中效果及连线
    if(lineData.value && lineData.value.length > 0) {
      for(let i in lineData.value) {
        ganttInstance.value.deleteLink(lineData.value[i])
      }
      lineData.value = []
    }
    activeRecord.value = []
    
    if(queryParams.value.resourceGroup) {
      temp.resourceGroup = queryParams.value.resourceGroup
    }
    if(queryParams.value.dateRange && queryParams.value.dateRange.length > 0) {
      temp.dateRange = [(queryParams.value.dateRange[0] + ' 00:00:00'), (queryParams.value.dateRange[1] + ' 23:59:59')]
    }
  }
  tableData.value = []
  tableLoading.value = true
  taskDataArray.value = []
  // Object.assign(queryParams.value, temp)
  getList(temp, props.disableHandle).then(res => {
    if(res.data && res.data.code == 0) {
      // 数据
      let resources = []
      if(res.data.data.resources && res.data.data.resources.length > 0) {
        resources = res.data.data.resources
      }
      if(res.data.data.tasks && res.data.data.tasks.length > 0) {
        taskDataArray.value = res.data.data.tasks
        let tasks = res.data.data.tasks
        for(let i in resources) {
          for(let j in tasks) {
            if(tasks[j].mainResourceId == resources[i].id) {
              if(!resources[i].children) {
                resources[i].children = []
              }
              resources[i].children.push({...tasks[j], id: tasks[j].code, mainResourceName: resources[i].name})
              if(!resources[i].startTime) {
                resources[i].startTime = tasks[j].startTime
              }else{
                if(new Date(resources[i].startTime) > new Date(tasks[j].startTime)) {
                  resources[i].startTime = tasks[j].startTime
                }
              }
              if(!resources[i].endTime) {
                resources[i].endTime = tasks[j].endTime
              }else{
                if(new Date(resources[i].endTime) < new Date(tasks[j].endTime)) {
                  resources[i].endTime = tasks[j].endTime
                }
              }
            }
          }
        }
      }
      tableData.value = resources
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
      if(res.data.data.earliestTaskStartTime) {
        markLs.push({
          date: new Date(res.data.data.earliestTaskStartTime),
          scrollToMarkLine: res.data.data.lastTaskAssignmentTime ? false : true,
          position: 'middle',
          style: {
            lineColor: 'red',
            lineWidth: 1,
            lineDash: [5, 5]
          }
        })
      }
      markLine.value = markLs
      option.value.markLine = markLine.value
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
  checkAdjustedHandle()
}

function viewMoreInfo (info) {
  gettaskDetail(info.code).then(res => {
    if(res.data && res.data.code == 0) {
      rowData.value = res.data.data
      dialogVisible.value = true
      hideTooltip()
    }
  })
}

function closeHandle () {
  dialogVisible.value = false
}

function openParam () {
  getFieldSetting('PLAN_RESOURCE_TASK_GANTT').then(res => {
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
    reportType: 'PLAN_RESOURCE_TASK_GANTT',
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
  splitForm.value = null
  dialogType.value = ''
  splitType.value = ''
  paramVisible.value = false
  splitPopover.value = false
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

function cancelAdjustHandle (bool) {
  if(bool) {
    adjustLoading.value = false
    cancelAdjusted().then(res => {
      if(res.data && res.data.code == 0) {
        ElNotification.closeAll()
        ElNotification({
          title: t(`common.tip`),
          message: '撤销' + t(`common.success`),
          position: 'bottom-right',
          type: 'success',
        })
        checkAdjustedHandle()
        getListHandle()
      }
      adjustLoading.value = false
    }).catch(e => {
      adjustLoading.value = false
    })
  }
}

function openSplitPopover () {
  // splitPopover.value = true
}

function openSplit (type) {
  splitType.value = type
  dialogType.value = 'split'
  splitOption.value.column.filter(col => {
    col.display = false
    if(col.prop == 'splitQuantity') {
      col.display = (type == 'custom')
    }
    if(col.prop == 'splitTaskNumber') {
      col.display = (type == 'average')
    }
  })
  splitForm.value = {}
  paramVisible.value = true
}

function splitHandle () {
  let temp = {
    taskCode: activeRecord.value[0],
  }
  splitOption.value.submitLoading = true
  let func = new Function()
  if(splitType.value == 'custom') {
    func = splitQuantity
    temp.splitQuantity = splitForm.value.splitQuantity
  }
  if(splitType.value == 'average') {
    func = splitTaskNumber
    temp.splitTaskNumber = splitForm.value.splitTaskNumber
  }
  func(temp).then(res => {
    if(res.data && res.data.code == 0) {
      ElNotification.closeAll()
      ElNotification({
        title: t(`common.tip`),
        message: (splitType.value == 'average' ? '按任务数量平均拆分' : '按拆出数量拆分') + t(`common.success`),
        position: 'bottom-right',
        type: 'success',
      })
      checkAdjustedHandle()
      getListHandle()
      paramClose()
    }
    splitOption.value.submitLoading = false
  }).catch(e => {
    splitOption.value.submitLoading = false
  })
}

function splitCompletionHandle (bool) {
  if(bool) {
    splitPopover.value = false
    splitCompletion({taskCode: activeRecord.value[0]}).then(res => {
      if(res.data && res.data.code == 0) {
        ElNotification.closeAll()
        ElNotification({
          title: t(`common.tip`),
          message: '按完成数量拆分' + t(`common.success`),
          position: 'bottom-right',
          type: 'success',
        })
        checkAdjustedHandle()
        getListHandle()
      }
    })
  }
}

function mergeHandle (bool) {
  if(bool) {
    mergeLoading.value = true
    mergeTasks({taskCodes: activeRecord.value}).then(res => {
      if(res.data && res.data.code == 0) {
        ElNotification.closeAll()
        ElNotification({
          title: t(`common.tip`),
          message: '合并' + t(`common.success`),
          position: 'bottom-right',
          type: 'success',
        })
        checkAdjustedHandle()
        getListHandle()
      }
      mergeLoading.value = false
    }).catch(e => {
      mergeLoading.value = false
    })
  }
}

function openMove (bool, type) {
  if(bool) {
    splitType.value = type
    dialogType.value = 'move'
    mergeOption.value.column.filter(col => {
      col.display = false
      if(['newStartTime', 'resourceId'].indexOf(col.prop) > -1) {
        col.display = (type == 'move')
      }
      if(col.prop == 'time') {
        col.display = (type == 'complete')
      }
    })
    if(type == 'move') {
      splitForm.value = {
        resourceId: clickTargetData.value.mainResourceId,
        mainResourceName: clickTargetData.value.mainResourceName,
        taskCode: activeRecord.value[0]
      }
    }else{
      splitForm.value = {}
    }
    paramVisible.value = true
  }
}

function moveSubmit () {
  let temp = JSON.parse(JSON.stringify(splitForm.value))
  delete temp.mainResourceName
  mergeOption.value.submitLoading = true
  let func = new Function()
  if(splitType.value == 'move') {
    func = movePosition
  }
  if(splitType.value == 'complete') {
    func = moveCompleted
  }
  func(temp).then(res => {
    if(res.data && res.data.code == 0) {
      ElNotification.closeAll()
      ElNotification({
        title: t(`common.tip`),
        message: (splitType.value == 'complete' ? '移动部分完成的任务进度到指定时间' : '移动指定任务') + t(`common.success`),
        position: 'bottom-right',
        type: 'success',
      })
      checkAdjustedHandle()
      getListHandle()
      paramClose()
    }
    mergeOption.value.submitLoading = false
  }).catch(e => {
    mergeOption.value.submitLoading = false
  })
}

function openResource () {
  resourceVisible.value = true
}

function resourceSubmit (row) {
  splitForm.value.resourceId = row.id
  splitForm.value.mainResourceName = row.name
}

function resourceClose () {
  resourceVisible.value = false
}

function lockTask (bool, type, locked) {
  if(bool) {
    let temp = {
      pinned: locked
    }
    let msg = ''
    let func = new Function()
    if(type == 'select') {
      func = freezeTasks
      temp['taskCodes'] = activeRecord.value
      msg = (locked ? '锁定' : '解锁') + '选中的任务'
    }
    if(type == 'start') {
      func = freezeStarted
      msg = (locked ? '锁定' : '解锁') + '所有已开工的任务'
    }
    func(temp).then(res => {
      if(res.data && res.data.code == 0) {
        ElNotification.closeAll()
        ElNotification({
          title: t(`common.tip`),
          message: msg + t(`common.success`),
          position: 'bottom-right',
          type: 'success',
        })
        checkAdjustedHandle()
        getListHandle()
        paramClose()
      }
    })
  }
}

// 导出
function openExport () {
  splitForm.value = {}
  dialogType.value = 'export'
  paramVisible.value = true
}

function exportSubmit () {
  exportOption.value.submitLoading = true
  exportTask(splitForm.value).then(res => {
    exportOption.value.submitLoading = false
    if(res.data) {
      let name = res.headers["content-disposition"].split(";")[1]
      name = name.split("=")[1]
      downloadFile(decodeURIComponent(name), res.data)
      paramClose()
    }
  }).catch(e => {
    exportOption.value.submitLoading = false
  })
}
// 下载文件
function downloadFile(filename, content) {
  var elink = document.createElement('a')
  if(filename) {
    elink.download = filename
  }
  elink.style.display = 'none'

  var blob = new Blob([content],{}) //,{type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8'})
  elink.href = URL.createObjectURL(blob)
  document.body.appendChild(elink)
  elink.click()
  document.body.removeChild(elink)
}

function openImport () {
  tempFileDialogVisible.value = true
}

// 上传前的处理
function beforeTempUpload(file) {
  uploading.value = true
}

function onTempChange (file, fileList) {
  uploading.value = false
  tempfileList.value = fileList
}

function errTempHandle (err, file, fileList) {
  uploading.value = false
  uploadBtn.value.clearFiles()
  tempfileList.value = []
  ElNotification.closeAll()
  ElNotification({
    title: t(`common.tip`),
    message: err,
    position: 'bottom-right',
    type: 'error',
  })
}

function importSuccess (response, fileList) {
  if(response && response.code === 0) {
    uploading.value = false
    tempFileHandleClose()
    ElNotification({
      title: t(`common.tip`),
      message: '派工' + t(`common.success`),
      position: 'bottom-right',
      type: 'success',
    })
    checkAdjustedHandle()
    getListHandle()
  } else {
    uploading.value = false
    ElNotification.closeAll()
    ElNotification({
      title: t(`common.tip`),
      message: response.msg,
      position: 'bottom-right',
      type: 'error',
    })
  }
}

function tempFileHandleClose () {
  tempfileList.value = []
  limitShow.value = false
  uploadBtn.value.clearFiles()
  tempFileDialogVisible.value = false
  uploading.value = false
}

watch(() => props.freshRandom, (newVal, oldVal) => {
  if(newVal > -1) {
    checkAdjustedHandle()
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
      .tools{
        display: flex;
        align-items: center;
        margin-right: 7px;
        .tools-list{
          display: flex;
          align-items: center;
          margin-left: 7px;
          .tool-item{
            padding: 0 5px;
            margin-left: 7px;
            display: flex;
            align-items: center;
            height: 32px;
            font-family: Source Han Sans-Regular, Source Han Sans;
            font-weight: 400;
            font-size: 14px;
            color: #D7D8DB;
            line-height: 20px;
            background: none;
            cursor: no-drop;
            svg{
              width: 16px;
              height: 16px;
              margin-right: 4px;
              fill: #D7D8DB;
            }
            span{
              margin-right: 4px;
            }
            &.enable{
              cursor: pointer;
              color: #363B4C;
              svg{
                fill: #363B4C;
              }
            }
            &.is-loading{
              color: #D7D8DB;
              cursor: no-drop;
              svg{
                display: none;
              }
            }
            &.enable:hover{
              background: #F5F6F7;
              border-radius: 4px;
            }
          }
        }
        .tools-list+.tools-list{
          &::before{
            content: '';
            width: 1px;
            height: 16px;
            background: #EEEFF0;
          }
        }
      }
    }
  }
  .resourceGantt{
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
    .resourceGantt{
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
    &.enable{
      color: #363B4C;
      cursor: pointer;
      &:hover{
        background: #F5F6F7;
      }
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