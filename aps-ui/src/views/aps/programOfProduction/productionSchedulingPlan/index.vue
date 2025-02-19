<template>
  <div class="basic-layout-box aps-programOfProduction-productionSchedulingPlan">
    <div class="top-box" :style="!(processInfo && processInfo.status != 'NONE') ? 'cursor: pointer;' : ''" @click="generateOpen">
      <div class="left">
        <img src="/jvs-ui-public/img/processBack.png" alt="">
      </div>
      <div class="right">
        <div v-if="processInfo && processInfo.status != 'NONE'" class="progress-info">
          <div class="title">
            <span>{{processStatusLabel(processInfo.status)}}</span>
            <span class="detail" @click="processOpen">{{t(`table.info`)}}></span>
          </div>
          <div class="bar">
            <el-progress :text-inside="true" :stroke-width="16" color="#1E6FFF" :striped="processInfo.ratio < 100" striped-flow :duration="10" :percentage="processInfo.ratio" />
          </div>
        </div>
        <div v-else class="title-text">
          <span>{{t(`aps.programOfProduction.productionSchedulingPlan.generate`)}}</span>
        </div>
      </div>
    </div>
    <div class="content-box">
      <el-tabs v-model="activeName" class="demo-tabs" @tab-change="handleClick">
        <el-tab-pane v-for="tab in tabList" :key="tab.name" :label="tab.label" :name="tab.name">
          <component v-if="activeName == tab.name" :is="componentList[tab.name]" :freshRandom="freshRandom"></component>
        </el-tab-pane>
      </el-tabs>
    </div>
    <!-- 排产进度 -->
    <el-dialog
      class="drawer-popup-dialog"
      v-model="processVisible"
      :title="t(`aps.programOfProduction.productionSchedulingPlan.genTitle`)"
      width="57%"
      append-to-body
      :close-on-click-modal="false"
      :before-close="processClose"
    >
      <div v-if="processVisible" class="process-detail-box">
        <div class="prcess-ratio">
          <div class="label">排产进度：</div>
          <div class="bar">
            <el-progress :show-text="false" :stroke-width="16" color="#1E6FFF" :striped="processInfo.ratio < 100" striped-flow :duration="10" :percentage="processInfo.ratio" />
          </div>
          <div class="label">{{processInfo.ratio}}%</div>
        </div>
        <el-scrollbar class="body-box">
          <div class="process-text">
            <div v-for="(it, ix) in processInfo.contents" :key="'process-content-item'+ix" class="process-text-item">
              <div v-html="it"></div>
            </div>
          </div>
        </el-scrollbar>
        <div class="footer-box">
          <el-button v-if="processInfo && ['SUCCESS', 'NO_SCHEDULED'].indexOf(processInfo.status) > -1" type="primary" :loading="confirmLoading" @click="confirmHandle">{{t(`form.submit`)}}</el-button>
          <el-button :loading="abandonLoading" @click="abandonHandle">{{t(`aps.programOfProduction.productionSchedulingPlan.abandon`)}}</el-button>
          <el-button v-if="processInfo && ['SUCCESS'].indexOf(processInfo.status) > -1" plain @click="openPreview">预览</el-button>
        </div>
      </div>
    </el-dialog>
    <!-- 智能排产 -->
    <el-dialog
      v-model="generateVisible"
      :title="t(`aps.programOfProduction.productionSchedulingPlan.genTitle`)"
      width="52%"
      append-to-body
      :close-on-click-modal="false"
      :before-close="generateClose"
    >
      <div v-if="generateVisible" class="dialog-page-box">
        <productionSchedulingStrategy openOprate="dialog" @row-click="generateClick"></productionSchedulingStrategy>
      </div>
    </el-dialog>
    <!-- 二次确认 -->
    <el-dialog
      width="520"
      v-model="confirmVisible"
      :title="t(`common.tip`)"
      class="center-dialog"
      :close-on-click-modal="false"
      :before-close="confirmClose"
    >
      <div v-if="confirmVisible" style="padding: 20px;">
        <div>若有调整，锁定的任务调整将生效，未锁定的任务将被清空。是否继续？</div>
      </div>
      <template #footer>
        <div class="dialog-footer" style="padding: 0 10px 10px 0;">
          <el-button @click="confirmClose">{{t(`common.cancel`)}}</el-button>
          <el-button type="primary" @click="confirmSubmit">{{t(`common.confirm`)}}</el-button>
        </div>
      </template>
    </el-dialog>
    <!-- 预览 -->
    <el-dialog
      class="drawer-popup-dialog"
      v-model="previewVisible"
      :title="t(`table.preview`)"
      width="100%"
      append-to-body
      :close-on-click-modal="false"
      :before-close="previewClose"
    >
      <div v-if="previewVisible" style="width: 100%;height: 100%;">
        <previewInfo></previewInfo>
      </div>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup name="productionSchedulingPlan">
import {
  ref,
  reactive,
  watch ,
  onMounted,
  computed,
  getCurrentInstance,
  nextTick,
  shallowRef,
} from 'vue'
import { useI18n } from 'vue-i18n'
import { ElNotification } from 'element-plus'

import tabsCom from './component'
const componentList = shallowRef({
  ...tabsCom
})

import productionSchedulingStrategy from '../productionSchedulingStrategy/index.vue'
import previewInfo from './preview.vue'

import { getProgress, generate, cancel, confirm } from './api'

const { proxy } = getCurrentInstance()
const emit = defineEmits([])
const { t } = useI18n()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

const activeName = ref('orders')
const tabList = ref([
  {label: '待排订单', name: 'orders'},
  {label: '资源甘特图', name: 'resourceGantt'},
  {label: '订单甘特图', name: 'orderGantt'},
  {label: '物料需求甘特图', name: 'materialGantt'},
  {label: '计划任务', name: 'task'},
])
const timer = ref(null)
const processInfo = ref(null)
const processVisible = ref(false)
const confirmLoading = ref(false)
const abandonLoading = ref(false)
const generateVisible = ref(false)
const confirmVisible = ref(false)
const chooseRow = ref(null)
const freshRandom = ref(-1)
const previewVisible = ref(false)

getgetProgressInfo()

function getgetProgressInfo () {
  if(timer.value) {
    clearInterval(timer.value)
    timer.value = null
  }
  timer.value = setInterval(() => {
    if(processInfo.value && processInfo.value['status'] != 'SCHEDULING') {
      clearInterval(timer.value)
      timer.value = null
    }else{
      getProgressHandle()
    }
  }, 2000)
}

function getProgressHandle () {
  getProgress().then(res => {
    if(res.data && res.data.code == 0) {
      processInfo.value = res.data.data
    }else{
      if(timer.value) {
        clearInterval(timer.value)
        timer.value = null
      } 
    }
  }).catch(e => {
    if(timer.value) {
      clearInterval(timer.value)
      timer.value = null
    }
  })
}

function processStatusLabel (status) {
  let str = ''
  if(status) {
    switch(status) {
      case 'FAILURE': str = '排程失败';break;
      case 'NONE': str = '暂无排程进度';break;
      case 'NO_SCHEDULED': str = '无需排程';break;
      case 'SCHEDULING': str = '排程中...';break;
      case 'SUCCESS': str = '排程成功';break;
      default: ;break;
    }
  }
  return str
}

function handleClick (tab) {
  activeName.value = tab
}

function processOpen () {
  processVisible.value = true
}

function abandonHandle () {
  abandonLoading.value = true
  cancel().then(res => {
    if(res.data && res.data.code == 0) {
      ElNotification.closeAll()
      ElNotification({
        title: t(`common.tip`),
        message: t(`aps.programOfProduction.productionSchedulingPlan.abandon`) + t(`aps.programOfProduction.productionSchedulingPlan.success`),
        position: 'bottom-right',
        type: 'success',
      })
      processClose()
      getProgressHandle()
    }
    abandonLoading.value = false
  }).catch(e => {
    abandonLoading.value = false
  })
}

function confirmHandle () {
  confirmLoading.value = true
  confirm().then(res => {
    if(res.data && res.data.code == 0) {
      ElNotification.closeAll()
      ElNotification({
        title: t(`common.tip`),
        message: t(`form.submit`) + t(`aps.programOfProduction.productionSchedulingPlan.success`),
        position: 'bottom-right',
        type: 'success',
      })
      processClose()
      getProgressHandle()
      freshRandom.value = Math.random()
    }
    confirmLoading.value = false
  }).catch(e => {
    confirmLoading.value = false
  })
}

function processClose () {
  processVisible.value = false
}

function generateOpen () {
  if(processInfo && processInfo.value.status != 'NONE') {
    return
  }
  generateVisible.value = true
}

function generateClick (data) {
  let { row } = data
  chooseRow.value = row
  confirmVisible.value = true
}

function generateClose () {
  generateVisible.value = false
}

function confirmSubmit () {
  if(chooseRow.value && chooseRow.value.id) {
    generate({planningStrategyId: chooseRow.value.id}).then(res => {
      if(res.data && res.data.code == 0) {
        if(processInfo.value && processInfo.value['status'] == 'NONE') {
          processInfo.value = null
        }
        getProgressHandle()
        getgetProgressInfo()
        ElNotification.closeAll()
        ElNotification({
          title: t(`common.tip`),
          message: t(`aps.programOfProduction.productionSchedulingPlan.generate`) + t(`aps.programOfProduction.productionSchedulingPlan.success`),
          position: 'bottom-right',
          type: 'success',
        })
        generateClose()
      }
      confirmClose()
    }).catch(e => {
      confirmClose()
    })
  }
}

function confirmClose () {
  confirmVisible.value = false
}

function openPreview () {
  previewVisible.value = true
}

function previewClose () {
  previewVisible.value = false
}
</script>
<style lang="scss" scoped>
.aps-programOfProduction-productionSchedulingPlan{
  padding: 16px 0!important;
  .top-box{
    margin: 0 24px;
    width: 240px;
    height: 72px;
    background: linear-gradient( 135deg, #F1F6FF 0%, #DCE9FF 100%);
    border-radius: 4px;
    display: flex;
    .left{
      display: flex;
      align-items: center;
      justify-content: center;
      img{
        display: block;
        width: 60px;
        height: 60px;
      }
    }
    .right{
      flex: 1;
      margin-left: 5px;
      margin-right: 20px;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      overflow: hidden;
      .title-text{
        width: 100%;
        font-family: Microsoft YaHei-Regular, Microsoft YaHei;
        font-weight: 400;
        font-size: 16px;
        color: #363B4C;
      }
      .progress-info{
        width: 100%;
        height: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        .title{
          width: 100%;
          display: flex;
          align-items: center;
          justify-content: space-between;
          span{
            font-size: 14px;
            color: #363B4C;
          }
          .detail{
            font-size: 12px;
            color: #1E6FFF;
            cursor: pointer;
          }
        }
        .bar{
          width: 100%;
          margin-top: 16px;
          ::v-deep(.el-progress){
            .el-progress-bar{
              .el-progress-bar__outer{
                background-color: #fff;
              }
            }
          }
        }
      }
    }
  }
  .content-box{
    height: calc(100% - 72px);
    ::v-deep(.el-tabs){
      --el-tabs-header-height: 50px;
      height: 100%;
      .el-tabs__header{
        margin-bottom: 16px;
        .el-tabs__nav-wrap{
          padding: 0 8px;
          .el-tabs__nav{
            .el-tabs__active-bar{
              background: #1E6FFF;
              border-radius: 2px 0px 2px 0px;
            }
            .el-tabs__item{
              padding: 0 16px;
              @include SourceHanSansCN-Regular;
              font-size: 14px;
              color: #6F7588;
              &.is-active{
                color: #1E6FFF;
                @include SourceHanSansCN-Bold;
              }
            }
          }
          &::after{
            height: 1px;
            background: #EEEFF0;
          }
        }
      }
      .el-tabs__content{
        height: calc(100% - 65px);
        .el-tab-pane{
          height: 100%;
        }
      }
    }
  }
}
.dialog-page-box{
  height: calc(70vh - 48px);
  padding: 20px;
  box-sizing: border-box;
  overflow: hidden;
}
.process-detail-box{
  height: 100%;
  overflow: hidden;
  .prcess-ratio{
    display: flex;
    align-items: center;
    padding: 16px 24px;
    .label{
      font-family: Microsoft YaHei-Bold, Microsoft YaHei;
      font-weight: 700;
      font-size: 16px;
      color: #363B4C;
    }
    .bar{
      width: 58%;
      margin: 0 16px;
      .el-progress{
        ::v-deep(.el-progress-bar){
          .el-progress-bar__outer{
            background-color: #F5F6F7;
          }
        }
      }
    }
  }
  .body-box{
    height: calc(100% - 126px);
    .process-text{
      margin: 0 24px;
      background: #F5F6F7;
      border-radius: 4px;
      padding: 16px;
      min-height: 229px;
      box-sizing: border-box;
      .process-text-item{
        font-family: Microsoft YaHei-Regular, Microsoft YaHei;
        font-weight: 400;
        font-size: 14px;
        color: #363B4C;
        line-height: 18px;
        &+.process-text-item{
          margin-top: 8px;
        }
      }
    }
  }
  .footer-box{
    height: 72px;
    box-sizing: border-box;
    border-top: 1px solid #EEEFF0;
    display: flex;
    align-items: center;
    padding-left: 24px;
    .el-button{
      width: 88px;
      height: 32px;
      background: #F5F6F7;
      font-family: Microsoft YaHei-Regular, Microsoft YaHei;
      font-weight: 400;
      font-size: 14px;
      color: #363B4C;
      &.el-button--primary{
        background: #1E6FFF;
        color: #fff;
      }
      &.is-plain{
        background: #E4EDFF;
        color: #1E6FFF;
        border: 1px solid #1E6FFF;
      }
    }
  }
}
::v-deep(.el-overlay){
  .el-overlay-dialog{
    .center-dialog{
      margin-top: calc(50vh - 82px);
    }
  }
}
</style>