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
      @search-reset="searchReset"
    >
      <template #taskStatus="scope">
        <div class="orderStatus-item">
          <div :class="['dot', scope.row.taskStatus]"></div>
          <div>{{dicFormatter(scope.row.taskStatus, 'taskStatus')}}</div>
        </div>
      </template>
    </jvs-table>
  </div>
</template>
<script lang="ts" setup name="task">
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
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

const dicFormatter = (val, prop) => {
  let str = val
  tableOption.value.column.filter((col:any) => {
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

const tableLoading = ref(false)
const queryParams = ref({
  // beginDate: dateFormat(new Date((new Date().getTime()) - (10 * 24 * 60 * 60 * 1000)), 'yyyy-MM-dd'),
})
const page = ref({
  total: 0, // 总页数
  currentPage: 1, // 当前页数
  pageSize: 20, // 每页显示多少条
})
const tableData = ref([])
const tableOption = ref({
  page: false,
  search: true,
  cancal: false,
  showOverflow: true,
  addBtn: false,
  menu: false,
  column: [
    {
      label: '编码',
      prop: 'code'
    },
    {
      label: '订单号',
      prop: 'orderCode'
    },
    {
      label: '产品',
      prop: 'materialName'
    },
    {
      label: '数量',
      prop: 'scheduledQuantity'
    },
    {
      label: '工序编码',
      prop: 'processCode'
    },
    {
      label: '工序名',
      prop: 'processName'
    },
    {
      label: '计划主资源',
      prop: 'mainResourceName'
    },
    {
      label: '计划开始时间',
      prop: 'beginDate',
      placeholder: '请选择计划开始时间',
      type: 'datePicker',
      datetype: 'date',
      // search: true,
      clearable: false,
      hide: true
    },
    {
      label: '计划开始时间',
      prop: 'startTime',
    },
    {
      label: '计划结束时间',
      prop: 'endDate',
      placeholder: '请选择计划结束时间',
      type: 'datePicker',
      datetype: 'date',
      // search: true,
      clearable: false,
      hide: true
    },
    {
      label: '计划结束时间',
      prop: 'endTime',
    },
    {
      label: '状态',
      prop: 'taskStatus',
      type: 'select',
      dicData: [
        {label: '已完成', value: 'COMPLETED'},
        {label: '部分完成', value: 'PARTIALLY_COMPLETED'},
        {label: '待完成', value: 'PENDING'},
      ],
      slot: true,
      hide: props.disableHandle,
    },
    {
      label: '逾期时长',
      prop: 'overdueTimeString',
    }
  ]
})

function getListHandle (pageInfo?) {
  tableLoading.value = true
  let tp = {
    current: pageInfo && pageInfo.current ? pageInfo.current : page.value.currentPage,
    size: page.value.pageSize,
    // beginDate: dateFormat(new Date((new Date().getTime()) - (10 * 24 * 60 * 60 * 1000)), 'yyyy-MM-dd'),
  }
  getList(Object.assign(tp, queryParams.value), props.disableHandle).then( res => {
    tableLoading.value = false
    if(res.data && res.data.code == 0 && res.data.data) {
      tableData.value = res.data.data // res.data.data.records
      // page.value.currentPage = res.data.data.current
      // page.value.total = res.data.data.total
    }
  }).catch(err => {
    tableLoading.value = false
  })
}

function searchChange (form) {
  queryParams.value = form
  getListHandle()
}
function searchReset () {
  // queryParams.value['beginDate'] = dateFormat(new Date((new Date().getTime()) - (10 * 24 * 60 * 60 * 1000)), 'yyyy-MM-dd')
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
.aps-programOfProduction-productionSchedulingPlan-orders{
  width: 100%;
  height: 100%;
  padding: 0 24px;
  box-sizing: border-box;
  .orderStatus-item{
    display: flex;
    align-items: center;
    .dot{
      width: 12px;
      height: 12px;
      border-radius: 50%;
      margin-right: 8px;
      &.COMPLETED{
        background: #6F7588;
      }
      &.PARTIALLY_COMPLETED{
        background: #36B452;
      }
      &.PENDING{
        background: #FF9736;
      }
    }
  }
}
</style>