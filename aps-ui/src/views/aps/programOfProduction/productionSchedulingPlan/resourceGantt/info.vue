<template>
  <el-dialog
    class="drawer-popup-dialog task-info-dialog"
    v-model="dialogVisible"
    :title="dialogTitle"
    width="472px"
    append-to-body
    :modal="false"
    :before-close="handleClose"
  >
    <template #header>
      <div class="my-header">
        <div :class="{'title': true, 'active': activeTitle == 'task'}" @click="tabChange('task')">任务信息</div>
        <div v-if="rowData.mergeTask" :class="{'title': true, 'active': activeTitle == 'child'}" @click="tabChange('child')">子任务</div>
      </div>
    </template>
    <div v-if="dialogVisible" class="dialog-box">
      <el-scrollbar v-if="activeTitle == 'task'" :class="{'task-box': true, 'has-footer': rowData.mergeTask ? (activeTitle == 'child') : true}">
        <div v-for="(info, index) in infoOptions" :key="'info-card-'+index" class="task-item">
          <div class="title-box">
            <svg>
              <use xlink:href="#icon-jvs-rongqi"></use>
            </svg>
            <span>{{info.title}}</span>
          </div>
          <div v-if="info.type == 'card'" class="card">
            <div v-for="(card, cix) in info.column" :key="'info-card-'+index + '-task-item-info-card-'+cix" v-show="card.display !== false" :class="{'card-item': true, 'required': (!rowData.mergeTask && !rowData.adjusted && card.type), 'error': (rowData[card.prop] === null || rowData[card.prop] === undefined)}">
              <div class="label">{{card.label}}</div>
              <div class="content">
                <div v-if="!rowData.mergeTask && !rowData.adjusted && card.type" class="jvs-form-item">
                  <el-input-number v-if="card.type == 'inputNumber'" v-model="rowData[card.prop]" :min="0" :precision="0" :controls="false"></el-input-number>
                  <el-date-picker
                    v-if="card.type == 'datePicker'"
                    v-model="rowData[card.prop]"
                    type="datetime"
                    :placeholder="card.label"
                    format="YYYY-MM-DD HH:mm:ss"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    date-format="YYYY-MM-DD"
                    time-format="HH:mm:ss"
                  >
                  </el-date-picker>
                </div>
                <span v-else>{{rowData[card.prop]}}</span>
              </div>
            </div>
          </div>
          <div v-if="info.type == 'table'" class="table">
            <div class="header">
              <div v-for="(head, hix) in info.column" :key="'info-card-'+index + '-task-item-info-table-header-'+hix" class="header-item">
                <span>{{head.label}}</span>
              </div>
            </div>
            <div v-if="info.prop && rowData[info.prop] && rowData[info.prop].length > 0" class="body">
              <div v-for="(row, rix) in rowData[info.prop]" :key="'info-table-row-'+index+'-'+rix" class="body-item">
                <div v-for="(head, hix) in info.column" :key="'info-card-'+index + '-task-item-info-table-body-'+rix+'-'+hix" class="body-item-column">
                  <span>{{row[head.prop]}}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-scrollbar>
      <el-scrollbar v-if="activeTitle == 'child'" class="child-box">
        <div style="margin-top: 20px;">
          <div v-if="childOption.prop && rowData[childOption.prop] && rowData[childOption.prop].length > 0">
            <div v-if="!rowData.adjusted" class="card">
              <div class="card-item">
                <div class="label">实际完工时间</div>
                <div class="content">
                  <div class="jvs-form-item">
                    <el-date-picker
                      v-model="childTime"
                      type="datetime"
                      placeholder="实际完工时间"
                      format="YYYY-MM-DD hh:mm:ss"
                      value-format="YYYY-MM-DD hh:mm:ss"
                      date-format="YYYY-MM-DD"
                      time-format="hh:mm:ss"
                      @change="timeChange"
                    ></el-date-picker>
                  </div>
                </div>
              </div>
            </div>
            <div v-for="(row, rix) in rowData[childOption.prop]" :key="'info-table-row-'+rix+'-'+rix" class="card">
              <div v-for="(head, hix) in childOption.column" :key="'info-card-'+rix + '-task-item-info-table-body-'+rix+'-'+hix" class="card-item">
                <div class="label">{{head.label}}</div>
                <div class="content">
                  <div v-if="!rowData.adjusted && head.type" class="jvs-form-item">
                    <el-input-number v-if="head.type == 'inputNumber'" v-model="row[head.prop]" :min="0" :precision="0" :controls="false"></el-input-number>
                    <el-date-picker
                      v-if="head.type == 'datePicker'"
                      v-model="row[head.prop]"
                      type="datetime"
                      :placeholder="head.label"
                      format="YYYY-MM-DD hh:mm:ss"
                      value-format="YYYY-MM-DD hh:mm:ss"
                      date-format="YYYY-MM-DD"
                      time-format="hh:mm:ss"
                    ></el-date-picker>
                  </div>
                  <span v-else>{{row[head.prop]}}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-scrollbar>
      <div v-if="!rowData.adjusted && (rowData.mergeTask ? (activeTitle == 'child') : true)" class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button :loading="submitLoading" type="primary" @click="submitHandle">确定</el-button>
      </div>
    </div>
  </el-dialog>
</template>
<script lang="ts" setup name="taskInfo">
import {
  ref,
  reactive,
  watch ,
  onMounted,
  computed,
  getCurrentInstance,
  nextTick
} from 'vue'
import { useI18n } from 'vue-i18n'
import { ElNotification } from 'element-plus'
import { Close } from '@element-plus/icons-vue'

import { updateProgress, updateBatchProgress } from './api'

const emit = defineEmits(['fresh', 'submit', 'close'])
const { t } = useI18n()

const props = defineProps({
  visible: {
    type: Boolean
  },
  dialogTitle: {
    type: String
  },
  row: {
    type: Object
  },
})

const dialogVisible = ref(false)
const activeTitle = ref('task')
const rowData = ref({})
const submitLoading = ref(false)
const infoOptions = ref([
  {
    title: '基本信息',
    type: 'card',
    column: [
      {
        label: '任务编码',
        prop: 'code'
      },
      {
        label: '生成订单',
        prop: 'orderCode',
        display: true
      },
      {
        label: '产品编码',
        prop: 'materialCode',
        display: true
      },
      {
        label: '产品名称',
        prop: 'materialName',
        display: true
      },
      {
        label: '需求数量',
        prop: 'orderMaterialQuantity',
        display: true
      },
      {
        label: '任务数量',
        prop: 'scheduledQuantity'
      },
      {
        label: '工序名',
        prop: 'processName'
      },
      {
        label: '工序编码',
        prop: 'processCode'
      },
    ]
  },
  {
    title: '计划信息',
    type: 'card',
    column: [
      {
        label: '计划主资源',
        prop: 'mainResourceName'
      },
      {
        label: '计划开工时间',
        prop: 'startTime'
      },
      {
        label: '计划完工时间',
        prop: 'endTime'
      },
      {
        label: '计划总工时',
        prop: 'totalTimeString'
      },
      {
        label: '实际完工时间',
        prop: 'completionTime',
        type: 'datePicker'
      },
      {
        label: '实际完成数量',
        prop: 'quantityCompleted',
        type: 'inputNumber'
      },
    ]
  },
  {
    title: '所需物料',
    type: 'table',
    prop: 'inputMaterials',
    column: [
      {
        label: '编码',
        prop: 'code'
      },
      {
        label: '名称',
        prop: 'name'
      },
      {
        label: '总数',
        prop: 'quantity'
      },
    ]
  }
])
const childOption = ref({
  prop: 'childTasks',
  column: [
    {
        label: '任务编码',
        prop: 'code'
      },
      {
        label: '产品编号',
        prop: 'materialCode'
      },
      {
        label: '任务数量',
        prop: 'scheduledQuantity'
      },
      {
        label: '实际完工时间',
        prop: 'completionTime',
      },
      {
        label: '实际完成数量',
        prop: 'quantityCompleted',
        type: 'inputNumber'
      },
  ]
})
const childTime = ref('')

function initHandle () {
  if(props.row) {
    let row = props.row
    rowData.value = JSON.parse(JSON.stringify(row))
    infoOptions.value.filter(info => {
      if(info.column && info.column.length > 0) {
        info.column.filter(col => {
          col['display'] = true
          if(['orderCode', 'materialCode', 'materialName', 'orderMaterialQuantity'].indexOf(col.prop) > -1) {
            col['display'] = !row.mergeTask
          }
        })
      }
    })
    if(row.completionTime) {
      childTime.value = row.completionTime
    }
    dialogVisible.value = true
  }
}

function handleClose () {
  dialogVisible.value = false
  rowData.value = {}
  emit('close')
}

function tabChange (name) {
  activeTitle.value = name
}

function submitHandle () {
  let func;
  let temp = null
  let validate = true
  if(activeTitle.value == 'task') {
    func = updateProgress
    let obj = {taskCode: rowData.value['code']}
    if(rowData.value['completionTime']) {
      obj['endTime'] = rowData.value['completionTime']
    }else{
      validate = false
    }
    if(rowData.value['quantityCompleted'] || rowData.value['quantityCompleted'] === 0) {
      obj['quantityCompleted'] = rowData.value['quantityCompleted']
    }else{
      validate = false
    }
    temp = obj
  }
  if(activeTitle.value == 'child') {
    func = updateBatchProgress
    temp = []
    if(rowData.value['childTasks'] && rowData.value['childTasks'].length > 0) {
      rowData.value['childTasks'].filter(cid => {
        if(cid['completionTime'] && (cid['quantityCompleted'] || cid['quantityCompleted'] === 0)) {
          temp.push({
            taskCode: cid['code'],
            endTime: cid['completionTime'],
            quantityCompleted: cid['quantityCompleted']
          })
        }
      })
    }
  }
  if(validate && func) {
    submitLoading.value = true
    func(temp).then(res => {
      if(res.data && res.data.code == 0) {
        ElNotification.closeAll()
        ElNotification({
          title: t(`common.tip`),
          message: t(`form.submit`) + t(`aps.productionEngineering.technology.success`),
          position: 'bottom-right',
          type: 'success',
        })
        handleClose()
        emit('fresh')
      }
      submitLoading.value = false
    }).catch(e => {
      submitLoading.value = false
    })
  }
}

function timeChange () {
  if(rowData.value['childTasks'] && rowData.value['childTasks'].length > 0) {
    rowData.value['childTasks'].filter(cid => {
      cid.completionTime = childTime.value
    })
  }
}

watch(() => props.visible, (newVal, oldVal) => {
  if(newVal) {
    initHandle()
  }else{
    dialogVisible.value = false
  }
})
</script>
<style lang="scss" scoped>
.my-header{
  display: flex;
  align-items: center;
  .title{
    margin-right: 40px;
    font-family: Source Han Sans-Regular, Source Han Sans;
    font-weight: 400;
    font-size: 20px;
    color: #6F7588;
    cursor: pointer;
    &.active{
      font-family: Source Han Sans-Bold, Source Han Sans;
      font-weight: 700;
      color: #363B4C;
    }
  }
}
.dialog-box{
  height: 100%;
  overflow: hidden;
  .task-box, .child-box{
    height: calc(100% - 60px);
    padding: 0 24px;
    &.task-box{
      height: 100%;
    }
    &.has-footer{
      height: calc(100% - 60px);
    }
    .task-item{
      margin-top: 14px;
      &:nth-last-of-type(1){
        margin-bottom: 14px;
      }
    }
    .title-box{
      display: flex;
      align-items: center;
      margin-bottom: 16px;
      svg{
        width: 16px;
        height: 16px;
      }
      span{
        line-height: 21px;
        margin-left: 8px;
        font-size: 16px;
        color: #363B4C;
        @include SourceHanSansCN-Regular;
      }
    }
    .card{
      border-radius: 4px;
      border: 1px solid #EEEFF0;
      &+.card{
        margin-top: 20px;
      }
      .card-item{
        display: flex;
        align-items: center;
        height: 42px;
        box-sizing: border-box;
        font-family: Microsoft YaHei-Regular, Microsoft YaHei;
        font-weight: 400;
        font-size: 14px;
        color: #363B4C;
        &+.card-item{
          border-top: 1px solid #EEEFF0;
        }
        &.required{
          .label::before{
            content: "*";
            color: var(--el-color-danger);
            margin-right: 4px;
          }
          &.error{
            .content{
              ::v-deep(.el-input__wrapper){
                box-shadow: 0 0 0 1px var(--el-color-danger);
              }
            }
          }
        }
        .label{
          background: #F5F6F7;
          width: 128px;
          height: 100%;
          padding-left: 16px;
          line-height: 46px;
          box-sizing: border-box;
          border-right: 1px solid #EEEFF0;
        }
        .content{
          width: calc(100% - 128px);
          padding: 0 16px;
          box-sizing: border-box;
          .jvs-form-item{
            width: 100%;
            display: flex;
            align-items: center;
            .el-input-number{
              width: 100%;
            }
          }
        }
      }
    }
    .table{
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
          font-family: Microsoft YaHei-Bold, Microsoft YaHei;
          font-weight: 700;
          font-size: 14px;
          color: #363B4C;
        }
      }
      .body{
        .body-item{
          display: flex;
          display: flex;
          align-items: center;
          height: 43px;
          line-height: 43px;
          box-sizing: border-box;
          border-bottom: 1px solid #EEEFF0;
          .body-item-column{
            flex: 1;
            overflow: hidden;
            padding-left: 24px;
            text-overflow: ellipsis;
            white-space: pre;
          }
        }
      }
    }
  }
  .footer{
    width: 100%;
    height: 60px;
    padding: 0 16px;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    border-top: 1px solid #EEEFF0;
    box-sizing: border-box;
  }
}
</style>
<style lang="scss">
.el-dialog.task-info-dialog{
  .el-dialog__header{
    padding: 0 24px;
    background: none;
    height: 70px;
    line-height: 70px;
    border-bottom: 1px solid #EEEFF0;
    box-sizing: border-box;
    position: relative;
    .el-dialog__headerbtn{
      height: 100%;
    }
  }
  .el-dialog__body{
    height: calc(100% - 70px);
  }
}
</style>