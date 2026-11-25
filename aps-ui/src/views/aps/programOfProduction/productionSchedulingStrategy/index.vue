<template>
  <div class="basic-layout-box aps-programOfProduction-productionSchedulingStrategy">
    <jvs-table
      :option="tableOption"
      :loading="tableLoading"
      :data="tableData"
      :page="page"
      @on-load="getListHandle"
      @search-change="searchChange"
      @delRow="delHandle"
      @row-click="rowClick"
    >
      <template #menuLeft>
        <el-button v-if="!props.openOprate" v-permission="'aps:strategy:edit'" type="primary" :icon="Plus" size="small" @click="addEditRow(null)">{{t(`aps.programOfProduction.productionSchedulingStrategy.add`)}}</el-button>
      </template>
      <template #menu="scope">
        <el-button v-permission="'aps:strategy:edit'" :text="true" size="small" @click="addEditRow(scope.row)">{{t(`table.edit`)}}</el-button>
      </template>
      <template #direction="scope">
        <span>{{getLabel(scope.row, 'direction')}}</span>
      </template>
      <template #active="scope">
        <div v-permission="'aps:strategy:edit'" :class="{'status-box': true, 'active': scope.row.active}" @click="editActiveHandle(scope.row)">
          <el-icon v-if="scope.row.active" color="#fff"><Select /></el-icon>
        </div>
      </template>
    </jvs-table>

    <!-- 新增 修改 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="52%"
      append-to-body
      :close-on-click-modal="false"
      :before-close="handleClose"
    >
      <div v-if="dialogVisible" class="dialog-form-box">
        <jvs-form :option="formOption" :formData="rowData" @submit="submitHandle" @cancelClick="handleClose">
          <template #orderSchedulingRulesForm>
            <div class="jvs-form-item">
              <div class="tab-table-box">
                <el-tabs v-model="activeTab" type="card" :style="'flex:1;width:100%;'">
                  <el-tab-pane label="排产规则" name="orderSchedulingRules">
                    <div class="orderSchedulingRules-box">
                      <div class="orderSchedulingRules-list">
                        <div class="title">
                          <div class="title-item">属性名</div>
                          <div class="title-item">排序规则</div>
                        </div>
                        <div v-for="(item, index) in rowData.orderSchedulingRules" :key="activeTab+'-key-item-'+index" class="orderSchedulingRules-list-item">
                          <el-select v-model="item.fieldKey" @change="fieldKeyChange(index, rowData.orderSchedulingRules)">
                            <el-option v-for="(field, fix) in fieldList" :key="field.fieldKey+'-'+index+'-'+fix" :label="field.fieldName" :value="field.fieldKey" :disabled="isRepeat(field.fieldKey, index)">
                              <span style="float: left">{{ field.fieldName }}</span>
                              <!-- <span style="float: right;color: var(--el-text-color-secondary);font-size: 13px;">{{ field.fieldKey }}</span> -->
                            </el-option>
                          </el-select>
                          <el-select v-model="item.sortRule">
                            <el-option label="升序" value="ASC"></el-option>
                            <el-option label="降序" value="DESC"></el-option>
                          </el-select>
                          <div class="delete-icon-button" @click="deleteRowHandle(index, rowData.orderSchedulingRules)" style="margin-left: 16px;">
                            <span class="border-line"></span>
                        </div>
                        </div>
                      </div>
                      <div class="bottom-add-button">
                        <div class="button" @click="addRowHandle('orderSchedulingRules', rowData)">
                          <div class="icon">
                            <svg aria-hidden="true">
                              <use xlink:href="#jvs-ui-icon-xinjian"></use>
                            </svg>
                          </div>
                          <span>{{t(`aps.programOfProduction.productionSchedulingStrategy.addOne`)}}</span>
                        </div>
                      </div>
                    </div>
                  </el-tab-pane>
                </el-tabs>
              </div>
            </div>
          </template>
        </jvs-form>
      </div>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup name="productionSchedulingStrategy">
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
import { Plus, Select } from '@element-plus/icons-vue'

import useCommonStore from '@/store/common.js'
import { getStore } from '@/util/store.js'

import { getList, getOptions, add, edit, editActive, del } from './api'

const { proxy } = getCurrentInstance()
const emit = defineEmits(['row-click'])
const { t } = useI18n()
const commonStore = useCommonStore()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

const props = defineProps({
  openOprate: {
    type: String
  }
})

const tableLoading = ref(false)
const queryParams = ref({})
const page = ref({
  total: 0, // 总页数
  currentPage: 1, // 当前页数
  pageSize: 20, // 每页显示多少条
})
const tableData = ref([])
const tableOption = ref({
  page: true,
  search: true,
  cancal: false,
  showOverflow: true,
  addBtn: false,
  menu: props.openOprate ? false : true,
  menuWidth: '120px',
  viewBtn: false,
  editBtn: false,
  column: [
    {
      label: '策略名称',
      prop: 'name',
      placeholder: '请输入策略名称',
      search: true,
    },
    {
      label: '开始时间',
      prop: 'beginTime',
      placeholder: '请选择开始时间',
    },
    {
      label: '排产方向',
      prop: 'direction',
      placeholder: '请选择排产方向',
      slot: true,
      type: 'select',
      dicData: [
        {label: '正排', value: 'FORWARD'},
        {label: '逆排', value: 'BACKWARD'}
      ],
    },
    {
      label: '创建时间',
      prop: 'createTime',
      placeholder: '请选择创建时间',
    },
    {
      label: '修改时间',
      prop: 'updateTime',
      placeholder: '请选择修改时间',
    },
    {
      label: '是否有效',
      prop: 'active',
      slot: true,
      hide: props.openOprate ? true : false
    },
  ]
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const rowData = ref({})
const formOption = ref({
  emptyBtn: false,
  submitLoading: false,
  formAlign: 'top',
  column: [
    {
      label: '策略名称',
      prop: 'name',
      placeholder: '请输入策略名称',
      span: 12,
      rules: [{ required: true, message: '请输入策略名称', trigger: 'blur' }]
    },
    {
      label: '开始时间',
      prop: 'beginTime',
      placeholder: '请选择开始时间',
      type: 'datePicker',
      datetype: 'datetime',
      span: 12,
      rules: [{ required: true, message: '请选择开始时间', trigger: 'cahnge' }]
    },
    {
      label: '排产方向',
      prop: 'direction',
      placeholder: '请选择排产方向',
      type: 'select',
      dicData: [
        {label: '正排', value: 'FORWARD'},
        {label: '逆排', value: 'BACKWARD'}
      ],
      span: 12,
      rules: [{ required: true, message: '请选择排产方向', trigger: 'change' }],
    },
    {
      label: '求解引擎',
      prop: 'solverEngine',
      type: 'select',
      dicData: [
        {label: 'OptaPlanner', value: 'OPTAPLANNER'},
        {label: 'Timefold', value: 'TIMEFOLD'},
        {label: 'OR-Tools CP-SAT', value: 'ORTOOLS_CP_SAT'}
      ],
      defaultValue: 'OPTAPLANNER',
      span: 12,
    },
    {
      label: '优化策略',
      prop: 'solverProfile',
      type: 'select',
      dicData: [
        {label: '默认策略', value: 'DEFAULT'},
        {label: '构造初排与禁忌搜索', value: 'FFD_TABU'},
        {label: '构造初排与晚期接受', value: 'FFD_LATE_ACCEPTANCE'}
      ],
      defaultValue: 'DEFAULT',
      span: 12,
    },
    { label: 'CP-SAT运行方式', prop: 'cpSatMode', type: 'select', span: 12,
      defaultValue: 'SHADOW', dicData: [{label: '影子验算（仅审计）',value: 'SHADOW'}, {label: '生成候选（需服务端授权）',value: 'CANDIDATE'}] },
    { label: 'CP-SAT计划窗口（天）', prop: 'cpSatHorizon', type: 'inputNumber', span: 12, min: 1, max: 180, precision: 0, defaultValue: 30 },
    { label: 'CP-SAT计算线程', prop: 'cpSatWorkers', type: 'inputNumber', span: 12, min: 1, max: 8, precision: 0, defaultValue: 1 },
    { label: 'CP-SAT可行基线保护', prop: 'cpSatBaseline', type: 'switch', span: 12, defaultValue: true },
    { label:'Timefold热启动与局部精修', prop:'cpSat_timefoldWarmStart', type:'switch', span:12, defaultValue:false },
    { label:'按规模分配热启动预算', prop:'cpSat_sizeAwareBudget', type:'switch', span:12, defaultValue:true },
    { label:'固定热启动预算比例（%）', prop:'cpSat_timefoldBudgetPercent', type:'inputNumber', span:12, defaultValue:50, min:10,max:80,precision:0 },
    { label:'CP-SAT搜索方式', prop:'cpSat_searchMode', type:'select', span:12, defaultValue:'AUTOMATIC', dicData:[{label:'自动搜索',value:'AUTOMATIC'},{label:'组合搜索',value:'PORTFOLIO'}] },
    { label:'复用求解进程', prop:'cpSat_reuseWorker', type:'switch', span:12, defaultValue:true },
    { label:'优化范围', prop:'cpSat_optimizationScope', type:'select', span:12, defaultValue:'FULL', dicData:[{label:'全量优化',value:'FULL'},{label:'局部优化（需要可行基线）',value:'LOCAL'}] },
    { label:'局部任务编码（逗号分隔）', prop:'cpSat_focusTaskCodes', type:'input', span:12, defaultValue:'' },
    { label:'初始邻域扩展层数', prop:'cpSat_neighborhoodDepth', type:'inputNumber', span:12, defaultValue:1, precision:0, min:0,max:5 },
    { label:'邻域任务上限', prop:'cpSat_maxNeighborhoodTasks', type:'inputNumber', span:12, defaultValue:60, precision:0, min:1,max:300 },
    { label:'每项任务调整成本', prop:'cpSat_changedTaskWeight', type:'inputNumber', span:12, defaultValue:1000, precision:0, min:0,max:1000000000 },
    { label:'每次设备变更成本', prop:'cpSat_resourceChangeWeight', type:'inputNumber', span:12, defaultValue:3000, precision:0, min:0,max:1000000000 },
    { label:'每分钟开工偏移成本', prop:'cpSat_startShiftMinuteWeight', type:'inputNumber', span:12, defaultValue:1, precision:0, min:0,max:1000000000 },
    { label:'最低净收益', prop:'cpSat_minNetImprovement', type:'inputNumber', span:12, defaultValue:1, precision:0, min:0,max:1000000000000 },
    { label:'最低逾期改善（分钟）', prop:'cpSat_minTardinessImprovementMinutes', type:'inputNumber', span:12, defaultValue:0, precision:0, min:0,max:259200 },
    { label:'最大调整任务比例（%）', prop:'cpSat_maxChangedTaskPercent', type:'inputNumber', span:12, defaultValue:100, precision:0, min:0,max:100 },
    {
      label: '求解上限(秒)',
      prop: 'solveTimeSeconds',
      type: 'inputNumber',
      precision: 0,
      min: 1,
      max: 3600,
      defaultValue: 120,
      span: 12,
    },
    {
      label: '自适应组合求解',
      prop: 'adaptiveEnabled',
      type: 'switch',
      defaultValue: false,
      span: 12,
    },
    {
      label: '复用本次最佳方案',
      prop: 'adaptiveWarmStartEnabled',
      type: 'switch',
      defaultValue: true,
      span: 12,
    },
    {
      label: '组合预算分配',
      prop: 'adaptiveBudgetMode',
      type: 'select',
      dicData: [
        {label: '按任务规模分配', value: 'SCALE_AWARE'},
        {label: '固定单候选预算', value: 'FIXED'}
      ],
      defaultValue: 'SCALE_AWARE',
      span: 12,
    },
    {
      label: '组合总预算(秒)',
      prop: 'adaptiveTotalSeconds',
      type: 'inputNumber',
      min: 15,
      max: 3600,
      precision: 0,
      defaultValue: 180,
      span: 12,
    },
    {
      label: '随机种子',
      prop: 'randomSeed',
      type: 'inputNumber',
      precision: 0,
      span: 12,
    },
    {
      label: '无改进时长(秒)',
      prop: 'maxNoImprovementTime',
      type: 'inputNumber',
      precision: 0,
      min: 0,
      span: 12,
    },
    {
      label: '约束物料',
      prop: 'materialConstrained',
      type: 'switch',
      span: 12,
      defaultValue: true,
    },
    {
      label: '是否有效',
      prop: 'active',
      type: 'switch',
      span: 12,
      defaultValue: false,
    },
    {
      label: '排产规则',
      prop: 'orderSchedulingRules',
      type: 'tableForm',
      formSlot: true,
      tableColumn: [
        {
          label: '属性名',
          prop: 'fieldKey',
        },
        {
          label: '排序规则',
          prop: 'sortRule',
          type: 'select',
          dicData: [
            {label: '升序', value: 'ASC'},
            {label: '降序', value: 'DESC'}
          ]
        }
      ]
    }
  ],
})
const activeTab = ref('orderSchedulingRules')
const fieldList = ref([])

getOptionsHandle()

function getLabel (row, prop) {
  let str = ''
  if(prop == 'direction') {
    if(row.config && row.config.direction) {
      tableOption.value.column.filter((col:any) => {
        if(col.prop == prop) {
          col['dicData'].filter(dit => {
            if(dit.value == row.config.direction) {
              str = dit.label
            }
          })
        }
      })
    }
  }
  return str
}

function getListHandle (pageInfo?) {
  tableLoading.value = true
  let tp = {
    current: pageInfo && pageInfo.current ? pageInfo.current : page.value.currentPage,
    size: page.value.pageSize,
  }
  if(props.openOprate == 'dialog') {
    tp['active'] = true
  }
  getList(Object.assign(queryParams.value, tp)).then( res => {
    tableLoading.value = false
    if(res.data && res.data.code == 0 && res.data.data) {
      tableData.value = res.data.data.records
      page.value.currentPage = res.data.data.current
      page.value.total = res.data.data.total
    }
  }).catch(err => {
    tableLoading.value = false
  })
}

function searchChange (form) {
  queryParams.value = form
  getListHandle()
}

function getOptionsHandle () {
  getOptions().then(res => {
    if(res.data && res.data.code == 0) {
      fieldList.value = res.data.data
    }
  })
}

function addEditRow (row) {
  if(row) {
    rowData.value = JSON.parse(JSON.stringify(row))
    if(row.config) {
      for(let k in row.config) {
        rowData.value[k] = row.config[k]
      }
      delete rowData.value['config']
      rowData.value.adaptiveEnabled = !!row.config?.adaptive?.enabled
      rowData.value.cpSatMode = row.config?.cpSat?.executionMode || 'SHADOW'
      rowData.value.cpSatHorizon = row.config?.cpSat?.horizonDays || 30
      rowData.value.cpSatWorkers = row.config?.cpSat?.numWorkers || 1
      rowData.value.cpSatBaseline = row.config?.cpSat?.useBaselineHint ?? true
      rowData.value.cpSat_timefoldWarmStart = row.config?.cpSat?.timefoldWarmStart ?? false
      rowData.value.cpSat_sizeAwareBudget = row.config?.cpSat?.sizeAwareBudget ?? true
      rowData.value.cpSat_timefoldBudgetPercent = row.config?.cpSat?.timefoldBudgetPercent ?? 50
      rowData.value.cpSat_searchMode = row.config?.cpSat?.searchMode ?? 'AUTOMATIC'
      rowData.value.cpSat_reuseWorker = row.config?.cpSat?.reuseWorker ?? true
      rowData.value.cpSat_optimizationScope = row.config?.cpSat?.optimizationScope ?? 'FULL'
      rowData.value.cpSat_focusTaskCodes = (row.config?.cpSat?.focusTaskCodes || []).join(', ')
      rowData.value.cpSat_neighborhoodDepth = row.config?.cpSat?.neighborhoodDepth ?? 1
      rowData.value.cpSat_maxNeighborhoodTasks = row.config?.cpSat?.maxNeighborhoodTasks ?? 60
      rowData.value.cpSat_changedTaskWeight = row.config?.cpSat?.changedTaskWeight ?? 1000
      rowData.value.cpSat_resourceChangeWeight = row.config?.cpSat?.resourceChangeWeight ?? 3000
      rowData.value.cpSat_startShiftMinuteWeight = row.config?.cpSat?.startShiftMinuteWeight ?? 1
      rowData.value.cpSat_minNetImprovement = row.config?.cpSat?.minNetImprovement ?? 1
      rowData.value.cpSat_minTardinessImprovementMinutes = row.config?.cpSat?.minTardinessImprovementMinutes ?? 0
      rowData.value.cpSat_maxChangedTaskPercent = row.config?.cpSat?.maxChangedTaskPercent ?? 100
      rowData.value.adaptiveTotalSeconds = row.config?.adaptive?.totalSeconds || 180
      rowData.value.adaptiveWarmStartEnabled = row.config?.adaptive?.warmStartEnabled ?? true
      rowData.value.adaptiveBudgetMode = row.config?.adaptive?.budgetMode || 'SCALE_AWARE'
      if(!row.orderSchedulingRules) {
        rowData.value['orderSchedulingRules'] = []
      }
    }
    dialogTitle.value = t(`aps.programOfProduction.productionSchedulingStrategy.edit`)
  }else{
    rowData.value = {
      direction: 'FORWARD',
      orderSchedulingRules: []
    }
    dialogTitle.value = t(`aps.programOfProduction.productionSchedulingStrategy.add`)
  }
  dialogVisible.value = true
}

function submitHandle (form) {
  let fun = add
  if(rowData.value['id']) {
    fun = edit
  }
  let temp = JSON.parse(JSON.stringify(rowData.value))
  temp.config = {}
  const configKeys = [
    'direction', 'materialConstrained', 'maxNoImprovementTime',
    'solverEngine', 'solverProfile', 'solveTimeSeconds', 'randomSeed',
    'entityTabuSize', 'lateAcceptanceSize', 'acceptedCountLimit',
    'scoreCalculationCountLimit', 'constraintWeightConfig', 'adaptive', 'cpSat'
  ]
  for (const key of configKeys) {
    if (rowData.value[key] !== undefined && rowData.value[key] !== null) {
      temp.config[key] = rowData.value[key]
    }
    delete temp[key]
  }
  temp.config.materialConstrained = rowData.value['materialConstrained'] || false
  temp.config.cpSat = { executionMode: rowData.value.cpSatMode || 'SHADOW', horizonDays: rowData.value.cpSatHorizon || 30,
    numWorkers: rowData.value.cpSatWorkers || 1, useBaselineHint: rowData.value.cpSatBaseline ?? true,
    timefoldWarmStart: rowData.value.cpSat_timefoldWarmStart ?? false,
    sizeAwareBudget: rowData.value.cpSat_sizeAwareBudget ?? true,
    timefoldBudgetPercent: rowData.value.cpSat_timefoldBudgetPercent ?? 50,
    searchMode: rowData.value.cpSat_searchMode ?? 'AUTOMATIC',
    reuseWorker: rowData.value.cpSat_reuseWorker ?? true,
    optimizationScope: rowData.value.cpSat_optimizationScope ?? 'FULL',
    focusTaskCodes: (rowData.value.cpSat_focusTaskCodes || '').split(/[,，\n]/).map(x => x.trim()).filter(Boolean),
    neighborhoodDepth: rowData.value.cpSat_neighborhoodDepth ?? 1,
    maxNeighborhoodTasks: rowData.value.cpSat_maxNeighborhoodTasks ?? 60,
    changedTaskWeight: rowData.value.cpSat_changedTaskWeight ?? 1000,
    resourceChangeWeight: rowData.value.cpSat_resourceChangeWeight ?? 3000,
    startShiftMinuteWeight: rowData.value.cpSat_startShiftMinuteWeight ?? 1,
    minNetImprovement: rowData.value.cpSat_minNetImprovement ?? 1,
    minTardinessImprovementMinutes: rowData.value.cpSat_minTardinessImprovementMinutes ?? 0,
    maxChangedTaskPercent: rowData.value.cpSat_maxChangedTaskPercent ?? 100 }
  for (const key of ['cpSat_timefoldWarmStart','cpSat_sizeAwareBudget','cpSat_timefoldBudgetPercent','cpSat_searchMode','cpSatMode','cpSatHorizon','cpSatWorkers','cpSatBaseline','cpSat_reuseWorker','cpSat_optimizationScope','cpSat_focusTaskCodes','cpSat_neighborhoodDepth','cpSat_maxNeighborhoodTasks','cpSat_changedTaskWeight','cpSat_resourceChangeWeight','cpSat_startShiftMinuteWeight','cpSat_minNetImprovement','cpSat_minTardinessImprovementMinutes','cpSat_maxChangedTaskPercent']) delete temp[key]
  if (temp.config.solverEngine === 'ORTOOLS_CP_SAT') {
    if (temp.config.direction !== 'FORWARD' || !temp.config.materialConstrained || temp.config.solverProfile !== 'DEFAULT'
        || rowData.value.adaptiveEnabled || (temp.config.maxNoImprovementTime || 0) > 0) {
      ElNotification({type: 'warning', message: 'CP-SAT需要正排、物料约束、默认优化策略，关闭自适应组合与无改进预算'})
      return
    }
    temp.config.scoreCalculationCountLimit = null
  }
  temp.config.adaptive = {
    ...(temp.config.adaptive || {}),
    enabled: !!rowData.value.adaptiveEnabled,
    totalSeconds: rowData.value.adaptiveTotalSeconds || 180,
    warmStartEnabled: rowData.value.adaptiveWarmStartEnabled ?? true,
    budgetMode: rowData.value.adaptiveBudgetMode || 'SCALE_AWARE'
  }
  delete temp.adaptiveEnabled
  delete temp.adaptiveTotalSeconds
  delete temp.adaptiveWarmStartEnabled
  delete temp.adaptiveBudgetMode
  if (temp.config.adaptive.enabled &&
      (temp.config.solverEngine !== 'TIMEFOLD' || !temp.config.materialConstrained)) {
    ElNotification({type: 'warning', message: '自适应组合求解需要选择 Timefold 并启用物料约束'})
    return
  }
  formOption.value.submitLoading = true
  fun(temp).then(res => {
    if(res.data && res.data.code == 0) {
      ElNotification.closeAll()
      ElNotification({
        title: t(`common.tip`),
        message: (rowData.value['id'] ? t(`aps.programOfProduction.productionSchedulingStrategy.edit`) : t(`aps.programOfProduction.productionSchedulingStrategy.add`)) + t(`aps.programOfProduction.productionSchedulingStrategy.success`),
        position: 'bottom-right',
        type: 'success',
      })
      formOption.value.submitLoading = false
      handleClose()
      getListHandle()
    }
  }).catch(e => {
    formOption.value.submitLoading = false
  })
}

function handleClose () {
  dialogVisible.value = false
  rowData.value = {}
}

function editActiveHandle (row) {
  if(row.id) {
    editActive(row.id, {active: !row.active}).then(res => {
      if(res.data && res.data.code == 0) {
        ElNotification.closeAll()
        ElNotification({
          title: t(`common.tip`),
          message: t(`common.setSuccess`),
          position: 'bottom-right',
          type: 'success',
        })
        getListHandle()
      }
    })
  }
}

function delHandle (row, index) {
  if(row.id) {
    del(row.id).then(res => {
      if(res.data && res.data.code == 0) {
        ElNotification.closeAll()
        ElNotification({
          title: t(`common.tip`),
          message: t(`common.delSuccess`),
          position: 'bottom-right',
          type: 'success',
        })
        getListHandle()
      }
    })
  }
}

function addRowHandle (prop, form) {
  form[prop].push({
    sortRule: 'ASC'
  })
}

function deleteRowHandle (index, list) {
  list.splice(index, 1)
}

function fieldKeyChange (index, list) {
  if(list[index].fieldKey) {
    fieldList.value.filter(field => {
      if(field.fieldKey == list[index].fieldKey) {
        list[index].fieldName = field.fieldName
      }
    })
  }else{
    list[index].fieldName = ''
  }
}

function isRepeat (key, index) {
  let bool = false
  if(rowData.value['orderSchedulingRules'] && rowData.value['orderSchedulingRules'].length > 0) {
    rowData.value['orderSchedulingRules'].filter((it, ix) => {
      if(it.fieldKey == key && index != ix) {
        bool = true
      }
    })
  }
  return bool
}

function rowClick (data) {
  emit('row-click', data)
} 
</script>
<style lang="scss" scoped>
.aps-programOfProduction-productionSchedulingStrategy{
  height: 100%;
  .status-box{
    width: 40px;
    height: 24px;
    background: #FFFFFF;
    border-radius: 4px;
    border: 1px solid #D7D8DB;
    display: flex;
    justify-content: center;
    align-items: center;
    cursor: pointer;
    &.active{
      background: #1E6FFF;
      border-color: #1E6FFF;
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
.dialog-form-box{
  ::v-deep(.el-form){
    .form-column-tableForm{
      .el-form-item{
        margin: 0!important;
      }
    }
  }
  .tab-table-box{
    border-top: 8px solid #F5F6F7;
    .el-tabs.el-tabs--top {
      margin-top: 12px;
      overflow: hidden;
      ::v-deep(.el-tabs__header){
        height: 36px;
        margin: 0;
        background: #fff;
        .el-tabs__nav-wrap {
          height: 100%;
          box-sizing: border-box;
          padding-top: 4px;
          .el-tabs__nav-scroll {
            height: 100%;
            .el-tabs__nav {
              height: 100%;
              display: flex;
              padding-left: 20px;
              border: 0;
              .el-tabs__item {
                line-height: 100%;
                height: 100%;
                display: flex;
                align-items: center;
                justify-content: center;
                border-left: 0;
                font-size: 14px;
                color: #6F7588;
                font-family: Microsoft YaHei-Regular;
                border-radius: 6px 6px 0 0;
                border: 0;
                box-shadow: none!important;
                background-color: #F5F6F7;
                border: 1px solid #EEEFF0;
                margin-left: 16px;
                position: relative;
              }
              .el-tabs__item.is-active{
                font-family: Microsoft YaHei-Bold, Microsoft YaHei;
                font-weight: 700;
                color: #1E6FFF;
                background-color: #fff;
                border-radius: 6px 6px 0px 0px;
                position: relative;
                .el-icon-close:hover{
                  color: #fff;
                }
                &::after{
                  content: '';
                  position: absolute;
                  bottom: -1px;
                  left: 0;
                  width: 100%;
                  height: 2px;
                  background: #fff;
                }
              }
              .el-tabs__item:nth-of-type(1){
                padding: 0 15px;
              }
            }
          }
          .el-tabs__nav-prev, .el-tabs__nav-next{
            height: 100%;
            display: flex;
            align-items: center;
          }
        }
      }
      ::v-deep(.el-tabs__content){
        padding: 16px 0;
      }
    }
    .orderSchedulingRules-box{
      margin: 0 32px;
      .orderSchedulingRules-list{
        background: #F5F6F7;
        border-radius: 4px;
        padding: 12px 16px;
        .title{
          display: flex;
          align-items: center;
          margin-bottom: 8px;
          .title-item{
            width: calc(50% - 24px);
            font-family: Microsoft YaHei-Regular, Microsoft YaHei;
            font-weight: 400;
            font-size: 14px;
            color: #363B4C;
            line-height: 20px;
            &+.title-item{
              margin-left: 16px;
            }
          }
        }
        .orderSchedulingRules-list-item{
          display: flex;
          align-items: center;
          .el-select{
            width: calc(50% - 24px);
            ::v-deep(.el-select__wrapper){
              background-color: #fff!important;
            }
            &+.el-select{
              margin-left: 16px;
            }
          }
          .bottom-add-button{
            margin-left: 16px;
          }
          &+.orderSchedulingRules-list-item{
            margin-top: 12px;
          }
        }
      }
    }
  }
}
</style>
