<template>
  <div class="basic-layout-box aps-programOfProduction-productionSchedulingPlan">
    <div class="top-box" :style="canScheduleRun && !(processInfo && processInfo.status != 'NONE') ? 'cursor: pointer;' : ''" @click="generateOpen">
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
      title="排程进度与验收"
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
              <div>{{it}}</div>
            </div>
          </div>
        </el-scrollbar>
        <el-card v-if="cpSatAudit" shadow="never" style="margin:12px 0" aria-label="CP-SAT求解验收">
          <template #header><strong>OR-Tools CP-SAT · {{cpSatAudit.executionMode === 'SHADOW' ? '影子验算' : cpSatAudit.adoption?.accepted === false ? '保留原计划' : '候选计划'}}</strong></template>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="求解状态">{{cpSatAudit.worker?.status || cpSatAudit.status}}</el-descriptions-item>
            <el-descriptions-item label="独立约束复核">{{cpSatAudit.verified ? '通过' : '未通过，不可发布'}}</el-descriptions-item>
            <el-descriptions-item label="统一业务成本">{{cpSatAudit.exactCost ?? '—'}}</el-descriptions-item>
            <el-descriptions-item label="含调整成本的目标下界">{{cpSatAudit.worker?.objectiveBound ?? '—'}}</el-descriptions-item>
            <el-descriptions-item label="可行基线保护">{{cpSatAudit.baselineAccepted ? '已验证并约束不劣化' : '未取得完整可行基线'}}</el-descriptions-item>
            <el-descriptions-item label="总耗时">{{cpSatAudit.totalMillis}} ms</el-descriptions-item>
            <el-descriptions-item label="逾期末工序">{{cpSatAudit.businessMetrics?.lateTerminalTasks ?? '—'}}</el-descriptions-item>
            <el-descriptions-item label="总逾期分钟">{{cpSatAudit.businessMetrics?.tardinessMinutes ?? '—'}}</el-descriptions-item>
            <el-descriptions-item label="进程复用">{{cpSatAudit.workerReused ? '已复用' : '新进程'}}</el-descriptions-item>
            <el-descriptions-item label="模型缓存">{{cpSatAudit.worker?.performance?.modelCacheHit ? '命中' : '重新建模'}}</el-descriptions-item>
            <el-descriptions-item label="原生加载 / 建模">{{cpSatAudit.worker?.performance?.nativeLoadMillis ?? '—'}} / {{cpSatAudit.worker?.performance?.modelBuildMillis ?? '—'}} ms</el-descriptions-item>
            <el-descriptions-item label="模型变量 / 约束">{{cpSatAudit.worker?.performance?.modelStats?.variables ?? '—'}} / {{cpSatAudit.worker?.performance?.modelStats?.constraints ?? '—'}}</el-descriptions-item>
            <el-descriptions-item label="邻接模型 / 日历分段">{{cpSatAudit.worker?.performance?.modelStats?.circuits ?? '—'}} / {{cpSatAudit.worker?.performance?.modelStats?.retainedSegments ?? '—'}}</el-descriptions-item>
            <el-descriptions-item v-if="cpSatAudit.refinementRounds" label="局部精修轮次">{{cpSatAudit.refinementRounds.length}}</el-descriptions-item>
            <el-descriptions-item label="Worker CPU">{{cpSatAudit.workerCpuMillis ?? '—'}} ms{{cpSatAudit.workerCpuComplete === false ? '（部分记录）' : ''}}</el-descriptions-item>
            <el-descriptions-item v-if="cpSatAudit.hybrid" label="热启动 / 共享预算">{{cpSatAudit.hybrid.timefoldMillis ?? 0}} ms / {{cpSatAudit.hybrid.sharedBudgetSeconds}} s</el-descriptions-item>
            <el-descriptions-item label="优化范围">{{cpSatAudit.optimizationScope === 'LOCAL' ? '局部' : '全量'}} · {{cpSatAudit.movableTasks}} 项可调</el-descriptions-item>
            <el-descriptions-item label="候选调整任务">{{cpSatAudit.adoption?.changedTasks ?? '—'}}</el-descriptions-item>
            <el-descriptions-item label="候选调整成本">{{cpSatAudit.adoptionExact?.adjustmentCost ?? cpSatAudit.adoption?.adjustmentCost ?? '—'}}</el-descriptions-item>
            <el-descriptions-item label="候选净收益">{{cpSatAudit.adoptionExact?.netGain ?? cpSatAudit.adoption?.netGain ?? '—'}}</el-descriptions-item>
            <el-descriptions-item label="方案决策">{{cpSatAudit.adoption?.accepted ? '采用候选' : '保留原计划'}}</el-descriptions-item>
            <el-descriptions-item label="决策原因">{{adoptionReason[cpSatAudit.adoption?.reason] || cpSatAudit.adoption?.reason || '—'}}</el-descriptions-item>
          </el-descriptions>
          <p v-if="cpSatAudit.executionMode === 'SHADOW'">影子结果仅用于验算与比较，不进入待确认计划。</p>
          <p v-if="cpSatAudit.message">{{cpSatAudit.message}}</p>
        </el-card>
        <div class="footer-box">
          <el-button v-if="!isShadow && processInfo && ['SUCCESS', 'NO_SCHEDULED'].indexOf(processInfo.status) > -1" v-permission="'aps:schedule:confirm'" type="primary" :loading="confirmLoading" @click="confirmHandle">{{t(`form.submit`)}}</el-button>
          <el-button v-permission="'aps:schedule:cancel'" :loading="abandonLoading" @click="abandonHandle">{{t(`aps.programOfProduction.productionSchedulingPlan.abandon`)}}</el-button>
          <el-button v-if="!isShadow && processInfo && ['SUCCESS'].indexOf(processInfo.status) > -1" v-permission="'aps:schedule:preview'" plain @click="openPreview">预览</el-button>
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
import { hasPermission } from '@/util/permission'

import tabsCom from './component'
const componentList = shallowRef({
  ...tabsCom
})

import productionSchedulingStrategy from '../productionSchedulingStrategy/index.vue'
import previewInfo from './preview.vue'

import { getProgress, generate, cancel, confirm, getSolveAudit } from './api'

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
const cpSatAudit = ref(null)
const auditId = ref('')
const adoptionReason = {FIRST_FEASIBLE_PLAN:'首次可行计划',ACCEPTED:'达到采用门槛',UNCHANGED_BASELINE:'方案未发生变化',INSUFFICIENT_NET_BENEFIT:'净收益不足',CHANGE_LIMIT_EXCEEDED:'调整比例超过限制',TARDINESS_THRESHOLD_NOT_MET:'逾期改善未达门槛'}
const isShadow = computed(() => cpSatAudit.value?.adoption?.accepted === false
  || cpSatAudit.value?.executionMode === 'SHADOW'
  || processInfo.value?.contents?.some(line => line.includes('影子验算') || line.includes('保留原计划')))
const processVisible = ref(false)
const confirmLoading = ref(false)
const abandonLoading = ref(false)
const generateVisible = ref(false)
const confirmVisible = ref(false)
const chooseRow = ref(null)
const freshRandom = ref(-1)
const previewVisible = ref(false)
const canScheduleRun = computed(() => hasPermission('aps:schedule:run'))

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
      const line = [...(res.data.data?.contents || [])].reverse().find(text => text.includes('审计ID'))
      const id = line?.match(/[a-f0-9-]{36}/)?.[0]
      if (!id || id !== auditId.value) cpSatAudit.value = null
      if (id && id !== auditId.value && hasPermission('aps:schedule:preview') && ['SUCCESS','FAILURE'].includes(res.data.data.status)) {
        auditId.value = id
        cpSatAudit.value = null
        getSolveAudit(id).then(response => {
          const report = JSON.parse(response.data?.data?.resultJson || '{}')
          if (auditId.value === id) cpSatAudit.value = report.cpSat ? {
            ...report.cpSat,
            // Preserve the decimal string; a Long business score may exceed JavaScript's safe integer range.
            exactCost: report.score?.split('/')[1]?.replace(/soft$/, '').replace(/^-/, '')
          } : null
        }).catch(() => { if (auditId.value === id) auditId.value = '' })
      }
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
  if(!canScheduleRun.value) {
    return
  }
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
  height: 100%;
  box-sizing: border-box;
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
  display: flex;
  flex-direction: column;
  overflow: hidden;
  > .el-card { flex-shrink: 0; }
  .prcess-ratio{
    display: flex;
    flex-shrink: 0;
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
    flex: 1;
    height: auto;
    min-height: 0;
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
    flex-shrink: 0;
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
