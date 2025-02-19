<template>
  <div class="basic-layout-box aps-dataBase-productionOrder">
    <jvs-table
      :option="tableOption"
      :formData="queryParams"
      :loading="tableLoading"
      :data="tableData"
      :page="page"
      @on-load="getListHandle"
      @search-change="searchChange"
      @delRow="delHandle"
    >
      <template #menuLeft>
        <el-button type="primary" :icon="Plus" size="small" @click="addEditRow(null)">{{t(`aps.dataBase.productionOrder.add`)}}</el-button>
      </template>
      <template #menu="scope">
        <el-button :text="true" size="small" @click="addEditRow(scope.row)">{{t(`table.edit`)}}</el-button>
        <el-button v-if="scope.row.hasSupplement" :text="true" size="small" @click="supplementHandle(scope.row)">{{t(`aps.dataBase.productionOrder.hasSupplement`)}}</el-button>
      </template>
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
        <div :class="{'canSchedule-item': true, 'active': scope.row.canSchedule}" @click="editScheduleHandle(scope.row)" style="cursor: pointer;">
          <el-icon v-if="scope.row.canSchedule" size="16" color="#fff"><Select /></el-icon>
        </div>
      </template>
      <template #color="scope">
        <div class="color-item" :style="{background: scope.row.color}"></div>
      </template>
    </jvs-table>

    <!-- 新增 修改 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520"
      append-to-body
      :close-on-click-modal="false"
      :before-close="handleClose"
    >
      <div v-if="dialogVisible" class="dialog-form-box">
        <jvs-form :option="formOption" :formData="rowData" @submit="submitHandle" @cancelClick="handleClose">
          <template #materialCodeForm>
            <div class="jvs-form-item">
              <el-select
                v-model="rowData.materialCode"
                filterable
                remote
                reserve-keyword
                placeholder="请输入物料名称搜索"
                remote-show-suffix
                :remote-method="materialSearch"
                :loading="materialLoading"
              >
                <el-option
                  v-for="item in materialList"
                  :key="item.code"
                  :label="item.code"
                  :value="item.code"
                >
                <span style="float: left">{{item.code}}</span>
                <span style="float: right;">{{item.name}}</span>
                </el-option>
              </el-select>
            </div>
          </template>
          <template #priorityForm>
            <el-popover
              v-model:visible="priorityPopover"
              width="50"
              placement="bottom-start"
              trigger="click"
              popper-class="custom-right-tool-poper"
            >
              <div class="priority-list">
                <div v-for="i in 5" :key="'priority'+i" :class="['priority-box', `p${i}`]" @click="priorityChange(i)">P{{i}}</div>
              </div>
              <template #reference>
                <div class="priority-item-form">
                  <div :class="['priority-box', `p${rowData.priority}`]">{{rowData.priority ? `P${rowData.priority}` : ''}}</div>
                  <el-icon size="14" color="#6F7588"><ArrowDown /></el-icon>
                </div>
              </template>
            </el-popover>
          </template>
        </jvs-form>
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
<script lang="ts" setup name="productionOrder">
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
import { Plus, SuccessFilled, RemoveFilled, WarningFilled, Select, Calendar, ArrowDown, CircleClose } from '@element-plus/icons-vue'

import useCommonStore from '@/store/common.js'
import { getStore } from '@/util/store.js'

import { getList, add, edit, del, getMaterialList, editSchedule, getSupplement } from './api'

const { proxy } = getCurrentInstance()
const emit = defineEmits([])
const { t } = useI18n()
const commonStore = useCommonStore()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

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
  menuWidth: '170px',
  viewBtn: false,
  editBtn: false,
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
      label: '订单状态',
      prop: 'orderStatus',
      type: 'select',
      dicData: [
        {label: '已取消', value: 'CANCELLED'},
        {label: '已完成', value: 'COMPLETED'},
        {label: '待处理', value: 'PENDING'},
      ],
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

const dialogVisible = ref(false)
const dialogTitle = ref('')
const rowData = ref({})
const formOption = ref({
  emptyBtn: false,
  submitLoading: false,
  formAlign: 'top',
  column: [
    {
      label: '订单号',
      prop: 'code',
      placeholder: '请输入订单号',
      rules: [{ required: true, message: '请输入订单号', trigger: 'blur' }]
    },
    {
      label: '产品编码',
      prop: 'materialCode',
      placeholder: '请选择产品编码',
      type: 'select',
      formSlot: true,
      rules: [{ required: true, message: '请选择产品编码', trigger: 'blur' }]
    },
    {
      label: '数量',
      prop: 'quantity',
      placeholder: '请输入数量',
      type: 'inputNumber',
      rules: [{ required: true, message: '请输入数量', trigger: 'blur' }]
    },
    {
      label: '订单状态',
      prop: 'orderStatus',
      type: 'select',
      clearable: false,
      dicData: [
        {label: '已取消', value: 'CANCELLED'},
        {label: '已完成', value: 'COMPLETED'},
        {label: '待处理', value: 'PENDING'},
      ],
    },
    {
      label: '需求交付时间',
      prop: 'deliveryTime',
      placeholder: '请选择需求日期',
      type: 'datePicker',
      datetype: 'datetime',
      prefixicon: Calendar,
      rules: [{ required: true, message: '请输入需求交付时间', trigger: 'blur' }]
    },
    {
      label: '优先级',
      prop: 'priority',
      formSlot: true,
    },
    {
      label: '显示颜色',
      prop: 'color',
      type: 'colorSelect',
    },
  ],
})
const priorityPopover = ref(false)
const materialList = ref([])
const materialLoading = ref(false)

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

function addEditRow (row) {
  if(row) {
    rowData.value = JSON.parse(JSON.stringify(row))
    dialogTitle.value = t(`aps.dataBase.productionOrder.edit`)
  }else{
    rowData.value = {
      orderStatus: 'PENDING',
      priority: 1,
      schedulingStatus: 'UNSCHEDULED',
    }
    dialogTitle.value = t(`aps.dataBase.productionOrder.add`)
  }
  dialogVisible.value = true
}

function submitHandle (form) {
  let fun = add
  if(rowData.value['id']) {
    fun = edit
  }
  rowData.value['type'] = 'MANUFACTURE'
  formOption.value.submitLoading = true
  fun(rowData.value).then(res => {
    if(res.data && res.data.code == 0) {
      ElNotification.closeAll()
      ElNotification({
        title: t(`common.tip`),
        message: (rowData.value['id'] ? t(`aps.dataBase.productionOrder.edit`) : t(`aps.dataBase.productionOrder.add`) + t(`aps.dataBase.productionOrder.success`)),
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

function priorityChange (i) {
  rowData.value['priority'] = i
  priorityPopover.value = false
}

function materialSearch (query: string) {
  materialLoading.value = true
  let obj = {
    size: 100,
    current: 1
  }
  if(query) {
    obj['code'] = query
  }
  getMaterialList(obj).then(res => {
    if(res.data && res.data.code == 0 && res.data.data) {
      materialList.value = res.data.data.records
    }
    materialLoading.value = false
  }).catch(e => {
    materialList.value = []
    materialLoading.value = false
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
.dialog-form-box{
  .priority-item-form{
    width: 100%;
    height: 36px;
    background: #F5F6F7;
    border-radius: 4px;
    padding: 0 12px;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    justify-content: space-between;
    cursor: pointer;
    .el-icon:nth-of-type(1){
      display: inline-flex;
    }
    .el-icon:nth-last-of-type(1){
      display: none;
    }
    &:hover{
      .el-icon:nth-last-of-type(1){
        display: inline-flex;
      }
      .el-icon:nth-of-type(1){
        display: none;
      }
    }
  }
}
.priority-list{
  .priority-box{
    cursor: pointer;
    &+.priority-box{
      margin-top: 6px;
    }
  }
}
.supplement-box{
  width: 100%;
  height: calc(100vh - 48px);
  padding: 20px;
  box-sizing: border-box;
}
</style>