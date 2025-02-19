<template>
  <div class="aps-programOfProduction-productionSchedulingPlan-orders">
    <jvs-table
      :option="tableOption"
      :formData="queryParams"
      :loading="tableLoading"
      :data="tableData"
      :page="page"
      @on-load="getListHandle"
      @search-change="searchChange"
    >
      <template #menu="scope">
        <el-button :text="true" size="small" @click="moveHandle(scope.row)">{{t(`aps.programOfProduction.productionSchedulingPlan.orders.move`)}}</el-button>
        <el-button v-if="scope.row.hasSupplement" :text="true" size="small" @click="supplementHandle(scope.row)">{{t(`aps.dataBase.productionOrder.hasSupplement`)}}</el-button>
      </template>
      <template #priority="scope">
        <div :class="['priority-box', `p${scope.row.priority}`]">P{{scope.row.priority}}</div>
      </template>
      <template #schedulingStatus="scope">
        <div class="orderStatus-item">
          <div :class="['dot', scope.row.schedulingStatus]"></div>
          <div>{{dicFormatter(scope.row.schedulingStatus, 'schedulingStatus')}}</div>
        </div>
      </template>
      <template #canSchedule="scope">
        <div :class="{'canSchedule-item': true, 'active': scope.row.canSchedule}" @click="editScheduleHandle(scope.row)" style="cursor: pointer;">
          <el-icon v-if="scope.row.canSchedule" size="16" color="#fff"><Select /></el-icon>
        </div>
      </template>
      <template #color="scope">
        <div class="color-item" :style="{background: scope.row.color}"></div>
      </template>
    </jvs-table>

    <!-- 移动 -->
    <el-dialog
      v-model="moveVisible"
      :title="t(`aps.programOfProduction.productionSchedulingPlan.orders.move`)"
      width="520"
      append-to-body
      :close-on-click-modal="false"
      :before-close="moveClose"
    >
      <div v-if="moveVisible" class="dialog-form-box">
        <jvs-form :option="formOption" :formData="moveData" @submit="moveSubmit" @cancelClick="moveClose">
          <template #moveOrderIdForm>
            <div class="jvs-form-item">
              <el-input v-model="rowData.code" :disabled="true"></el-input>
            </div>
          </template>
          <template #targetOrderIdForm>
            <div class="jvs-form-item">
              <el-input v-model="moveData.targetOrderCode" :disabled="true">
                <template #append>
                  <el-icon style="cursor: pointer;" @click.stop="oepnSelect"><Search /></el-icon>
                </template>
              </el-input>
            </div>
          </template>
        </jvs-form>
      </div>
    </el-dialog>

    <!-- 选择目标订单 -->
    <el-dialog
      v-model="selectVisible"
      :title="t(`aps.programOfProduction.productionSchedulingPlan.orders.select`)"
      width="52%"
      append-to-body
      :close-on-click-modal="false"
      :before-close="selectClose"
    >
      <div v-if="selectVisible" class="dialog-page-box">
        <jvs-table
          :option="selectOption"
          :formData="selectParams"
          :loading="selectLoading"
          :data="selectData"
          :page="selectPage"
          @on-load="getSelectList"
          @search-change="selectChange"
          @row-click="selectClick"
        >
          <template #priority="scope">
            <div :class="['priority-box', `p${scope.row.priority}`]">P{{scope.row.priority}}</div>
          </template>
          <template #schedulingStatus="scope">
            <div class="orderStatus-item">
              <div :class="['dot', scope.row.schedulingStatus]"></div>
              <div>{{dicFormatter(scope.row.schedulingStatus, 'schedulingStatus')}}</div>
            </div>
          </template>
          <template #canSchedule="scope">
            <div :class="{'canSchedule-item': true, 'active': scope.row.canSchedule}">
              <el-icon v-if="scope.row.canSchedule" size="16" color="#fff"><Select /></el-icon>
            </div>
          </template>
          <template #color="scope">
            <div class="color-item" :style="{background: scope.row.color}"></div>
          </template>
        </jvs-table>
      </div>
    </el-dialog>

    <!-- 补充订单 -->
    <el-dialog
      v-model="editScheduleVisible"
      title="补充订单"
      fullscreen
      append-to-body
      :before-close="editScheduleClose"
    >
      <div v-if="editScheduleVisible" class="aps-dataBase-productionOrder supplement-box">
        <jvs-table
          :option="editScheduleOption"
          :loading="editScheduleLoading"
          :data="editScheduleTableData"
          @on-load="getEditScheduleHandle"
        >
        <template #priority="scope">
          <div :class="['priority-box', `p${scope.row.priority}`]">P{{scope.row.priority}}</div>
        </template>
        <template #orderStatus="scope">
          <div class="orderStatus-item">
            <div class="icon">
              <el-icon v-if="scope.row.orderStatus == 'COMPLETED'" size="14" color="#36B452"><SuccessFilled /></el-icon>
              <el-icon v-if="scope.row.orderStatus == 'CANCELLED'" size="14" color="#FF194C"><RemoveFilled /></el-icon>
              <el-icon v-if="scope.row.orderStatus == 'PENDING'" size="14" color="#1E6FFF"><WarningFilled /></el-icon>
            </div>
            <div>{{dicFormatter(scope.row.orderStatus, 'orderStatus')}}</div>
          </div>
        </template>
        <template #schedulingStatus="scope">
          <div class="orderStatus-item">
            <div :class="['dot', scope.row.schedulingStatus]"></div>
            <div>{{dicFormatter(scope.row.schedulingStatus, 'schedulingStatus')}}</div>
          </div>
        </template>
        <template #canSchedule="scope">
          <div :class="{'canSchedule-item': true, 'active': scope.row.canSchedule}">
            <el-icon v-if="scope.row.canSchedule" size="16" color="#fff"><Select /></el-icon>
          </div>
        </template>
        <template #color="scope">
          <div class="color-item" :style="{background: scope.row.color}"></div>
        </template>
        </jvs-table>
      </div>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup name="orders">
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
import { Select, Search, SuccessFilled, RemoveFilled, WarningFilled } from '@element-plus/icons-vue'

import { getList, sortOrder, getSupplement, editSchedule } from './api'

const { proxy } = getCurrentInstance()
const emit = defineEmits([])
const { t } = useI18n()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

const props = defineProps({
  freshRandom: {
    type: Number
  }
})

const dicFormatter = (val, prop) => {
  let str = val
  tableOption.value.column.filter(col => {
    if(col.prop == prop && col.dicData) {
      let dicData = [].concat(col.dicData)
      for(let i in dicData) {
        if(dicData[i].value == val) {
          str = dicData[i].label
          return str
        }
      }
    }
  })
  return str
}

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
  menu: true,
  menuWidth: '140px',
  viewBtn: false,
  editBtn: false,
  delBtn: false,
  column: [
    {
      label: '订单号',
      prop: 'code',
      placeholder: '请输入订单号',
      search: true,
      searchSpan: 4,
    },
    {
      label: '产品编码',
      prop: 'materialCode',
      placeholder: '请输入产品编码',
    },
    {
      label: '数量',
      prop: 'quantity',
      placeholder: '请输入数量',
    },
    {
      label: '需求日期',
      prop: 'deliveryTime',
      placeholder: '请选择需求日期',
      type: 'datePicker',
      datetype: 'date',
    },
    {
      label: '优先级',
      prop: 'priority',
      slot: true,
    },
    {
      label: '排产状态',
      prop: 'schedulingStatus',
      type: 'select',
      clearable: true,
      dicData: [
        {label: '未排产', value: 'UNSCHEDULED'},
        {label: '已排产', value: 'SCHEDULED'},
        {label: '已完成', value: 'COMPLETED'},
        {label: '无需排产', value: 'NO_SCHEDULED'},
      ],
      search: true,
      searchSpan: 4,
      slot: true,
    },
    {
      label: '排产',
      prop: 'canSchedule',
      type: 'switch',
      slot: true,
    },
    {
      label: '显示颜色',
      prop: 'color',
      type: 'colorSelect',
      slot: true,
    },
  ]
})

const moveVisible = ref(false)
const rowData = ref(null)
const moveData = ref(null)
const formOption = ref({
  emptyBtn: false,
  submitLoading: false,
  formAlign: 'top',
  column: [
    {
      label: '移动订单',
      prop: 'moveOrderId',
      formSlot: true,
      rules: [{ required: true, message: '请选择移动订单', trigger: 'blur' }],
    },
    {
      label: '移动位置',
      prop: 'position',
      type: 'select',
      dicData: [
        {label: '移动到目标订单之前', value: 'BEFORE'},
        {label: '移动到目标订单之后', value: 'AFTER'},
      ],
      rules: [{ required: true, message: '请选择移动位置', trigger: 'change' }],
    },
    {
      label: '目标订单',
      prop: 'targetOrderId',
      formSlot: true,
      rules: [{ required: true, message: '请选择目标订单', trigger: 'blur' }],
    }
  ],
})

const selectVisible = ref(false)
const selectLoading = ref(false)
const selectParams = ref({})
const selectPage = ref({
  total: 0, // 总页数
  currentPage: 1, // 当前页数
  pageSize: 20, // 每页显示多少条
})
const selectData = ref([])
const selectOption = ref({
  page: true,
  search: true,
  cancal: false,
  showOverflow: true,
  addBtn: false,
  menu: false,
  column: tableOption.value.column
})

const editScheduleVisible = ref(false)
const editScheduleData = ref(null)
const editScheduleLoading = ref(false)
const editScheduleTableData = ref([])
const editScheduleOption = ref({
  page: false,
  search: false,
  cancal: false,
  showOverflow: true,
  addBtn: false,
  menu: false,
  column: [
    {
      label: '订单号',
      prop: 'code',
    },
    {
      label: '产品编码',
      prop: 'materialCode',
    },
    {
      label: '数量',
      prop: 'quantity',
    },
    {
      label: '需求日期',
      prop: 'deliveryTime',
    },
    {
      label: '优先级',
      prop: 'priority',
      slot: true,
    },
    {
      label: '订单状态',
      prop: 'orderStatus',
      slot: true,
    },
    {
      label: '排产状态',
      prop: 'schedulingStatus',
      slot: true,
    },
    {
      label: '排产',
      prop: 'canSchedule',
      slot: true,
    },
    {
      label: '显示颜色',
      prop: 'color',
      slot: true,
    },
  ]
})

function getListHandle (pageInfo?) {
  tableLoading.value = true
  let tp = {
    current: pageInfo && pageInfo.current ? pageInfo.current : page.value.currentPage,
    size: page.value.pageSize,
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

function moveHandle (row) {
  rowData.value = JSON.parse(JSON.stringify(row))
  moveData.value = {
    moveOrderId: row.id,
    position: 'BEFORE',
    targetOrderId: ''
  }
  moveVisible.value = true
}

function moveSubmit () {
  let temp = JSON.parse(JSON.stringify(moveData.value))
  delete temp.targetOrderCode
  formOption.value.submitLoading = true
  sortOrder(temp).then(res => {
    if(res.data && res.data.code == 0) {
      ElNotification.closeAll()
      ElNotification({
        title: t(`common.tip`),
        message: t(`aps.programOfProduction.productionSchedulingPlan.orders.move`) + t(`aps.programOfProduction.productionSchedulingPlan.success`),
        position: 'bottom-right',
        type: 'success',
      })
      moveClose()
      getListHandle()
    }
    formOption.value.submitLoading = false
  }).catch(e => {
    formOption.value.submitLoading = false
  })
}

function moveClose () {
  moveVisible.value = false
  rowData.value = null
  moveData.value = null
}

function oepnSelect () {
  selectOption.value.column.filter(col => {
    if(col.search) {
      col.searchSpan = 8
    }
  })
  selectVisible.value = true
}

function getSelectList (pageInfo?) {
  selectLoading.value = true
  let tp = {
    current: pageInfo && pageInfo.current ? pageInfo.current : selectPage.value.currentPage,
    size: selectPage.value.pageSize,
    excludeId: rowData.value.id
  }
  getList(Object.assign(selectParams.value, tp)).then( res => {
    selectLoading.value = false
    if(res.data && res.data.code == 0 && res.data.data) {
      selectData.value = res.data.data.records
      selectPage.value.currentPage = res.data.data.current
      selectPage.value.total = res.data.data.total
    }
  }).catch(err => {
    selectLoading.value = false
  })
}

function selectChange (form) {
  selectParams.value = form
  getSelectList()
}

function selectClick (data) {
  let { row } = data
  if(row.id) {
    moveData.value.targetOrderId = row.id
    moveData.value.targetOrderCode = row.code
    selectClose()
  }
}

function selectClose () {
  selectVisible.value = false
}

function supplementHandle (row) {
  editScheduleData.value = JSON.parse(JSON.stringify(row))
  editScheduleVisible.value = true
}

function editScheduleClose () {
  editScheduleVisible.value = false
  editScheduleData.value = null
}

function getEditScheduleHandle () {
  editScheduleLoading.value = true
  getSupplement(editScheduleData.value['id']).then(res => {
    if(res.data && res.data.code == 0) {
      editScheduleTableData.value = res.data.data
    }
    editScheduleLoading.value = false
  }).catch(e => {
    editScheduleLoading.value = false
  })
}

function editScheduleHandle (row) {
  editSchedule(row.id, {canSchedule: !row.canSchedule}).then(res => {
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

watch(() => props.freshRandom, (newVal, oldVal) => {
  if(newVal > -1) {
    getListHandle()
  }
})
</script>
<style lang="scss" scoped>
.priority-box{
  width: 40px;
  height: 24px;
  border-radius: 4px;
  line-height: 24px;
  text-align: center;
  overflow: hidden;
  &.p5{
    background: rgba(167,16,0,0.16);
    color: #A71000;
  }
  &.p4{
    background: rgba(255,25,76,0.16);
    color: #FF194C;
  }
  &.p3{
    background: rgba(249,185,0,0.16);
    color: #F9B900;
  }
  &.p2{
    background: rgba(24,208,213,0.16);
    color: #18D0D4;
  }
  &.p1{
    background: rgba(210, 210, 213, 0.2);
    color: #d2d2d5;
  }
}
.orderStatus-item{
  display: flex;
  align-items: center;
  .dot{
    width: 12px;
    height: 12px;
    border-radius: 50%;
    margin-right: 8px;
    &.UNSCHEDULED{
      background: #FF194C;
    }
    &.SCHEDULED{
      background: #1E6FFF;
    }
    &.COMPLETED{
      background: #36B452;
    }
    &.NO_SCHEDULED{
      background: #D7D8DB;
    }
  }
  .icon{
    margin-right: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}
.canSchedule-item{
  width: 40px;
  height: 24px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #D7D8DB;
  box-sizing: border-box;
  &.active{
    background: #1E6FFF;
  }
}
.color-item{
  width: 40px;
  height: 24px;
  border-radius: 4px;
}
.aps-programOfProduction-productionSchedulingPlan-orders{
  width: 100%;
  height: 100%;
  padding: 0 24px;
  box-sizing: border-box;
}
.dialog-page-box{
  height: calc(70vh - 48px);
  padding: 20px;
  box-sizing: border-box;
  overflow: hidden;
}

.aps-dataBase-productionOrder{
  .orderStatus-item{
    display: flex;
    align-items: center;
    .dot{
      width: 12px;
      height: 12px;
      border-radius: 50%;
      margin-right: 8px;
      &.UNSCHEDULED{
        background: #FF194C;
      }
      &.SCHEDULED{
        background: #1E6FFF;
      }
      &.COMPLETED{
        background: #36B452;
      }
      &.NO_SCHEDULED{
        background: #D7D8DB;
      }
    }
    .icon{
      margin-right: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }
  .canSchedule-item{
    width: 40px;
    height: 24px;
    border-radius: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px solid #D7D8DB;
    box-sizing: border-box;
    &.active{
      background: #1E6FFF;
    }
  }
  .color-item{
    width: 40px;
    height: 24px;
    border-radius: 4px;
  }
}
.supplement-box{
  width: 100%;
  height: calc(100vh - 48px);
  padding: 20px;
  box-sizing: border-box;
}
</style>