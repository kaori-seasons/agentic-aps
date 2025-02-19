<template>
  <div class="basic-layout-box aps-dataBase-materialPlan">
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
        <el-button type="primary" :icon="Plus" size="small" @click="addEditRow(null)">{{t(`aps.dataBase.materialPlan.add`)}}</el-button>
        <el-button v-if="false" type="primary" :icon="Plus" size="small">{{t(`aps.dataBase.materialPlan.import`)}}</el-button>
      </template>
      <template #menu="scope">
        <el-button :text="true" size="small" @click="addEditRow(scope.row)">{{t(`table.edit`)}}</el-button>
      </template>
      <template #orderStatus="scope">
        <div class="orderStatus-item">
          <div :class="['dot', scope.row.orderStatus]"></div>
          <div>{{dicFormatter(scope.row.orderStatus, 'orderStatus')}}</div>
        </div>
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
        </jvs-form>
      </div>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup name="materialPlan">
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
import { Plus } from '@element-plus/icons-vue'

import useCommonStore from '@/store/common.js'
import { getStore } from '@/util/store.js'

import { getList, add, edit, del, getMaterialList } from './api'

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
  menuWidth: '120px',
  viewBtn: false,
  editBtn: false,
  column: [
    {
      label: '物料编码',
      prop: 'materialCode',
      placeholder: '请输入物料编码',
      search: true,
      searchSpan: 4,
    },
    {
      label: '数量',
      prop: 'quantity',
      placeholder: '请输入数量',
    },
    {
      label: '预计到货时间',
      prop: 'deliveryTime',
    },
    {
      label: '状态',
      prop: 'orderStatus',
      placeholder: '请选择状态',
      search: true,
      searchSpan: 4,
      slot: true,
      type: 'select',
      dicData: [
        {label: '在途', value: 'PROCESSING'},
        {label: '已关闭', value: 'CLOSED'}
      ]
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
      label: '物料编码',
      prop: 'materialCode',
      placeholder: '请输入物料编码',
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
      label: '状态',
      prop: 'orderStatus',
      placeholder: '请选择状态',
      type: 'select',
      dicData: [
        {label: '在途', value: 'PROCESSING'},
        {label: '已关闭', value: 'CLOSED'}
      ],
      rules: [{ required: true, message: '请选择状态', trigger: 'blur' }]
    },
    {
      label: '预计到货时间',
      prop: 'deliveryTime',
      type: 'datePicker',
      datetype: 'datetime',
      rules: [{ required: true, message: '请选择预计到货时间', trigger: 'blur' }]
    },
  ],
})
const materialList = ref([])
const materialLoading = ref(false)

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
    dialogTitle.value = t(`aps.dataBase.materialPlan.edit`)
  }else{
    rowData.value = {}
    dialogTitle.value = t(`aps.dataBase.materialPlan.add`)
  }
  dialogVisible.value = true
}

function submitHandle (form) {
  let fun = add
  if(rowData.value['id']) {
    fun = edit
  }
  formOption.value.submitLoading = true
  fun(rowData.value).then(res => {
    if(res.data && res.data.code == 0) {
      ElNotification.closeAll()
      ElNotification({
        title: t(`common.tip`),
        message: (rowData.value['id'] ? t(`aps.dataBase.materialPlan.edit`) : t(`aps.dataBase.materialPlan.add`) + t(`aps.dataBase.materialPlan.success`)),
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
</script>
<style lang="scss" scoped>
.aps-dataBase-materialPlan{
  .orderStatus-item{
    display: flex;
    align-items: center;
    .dot{
      width: 12px;
      height: 12px;
      border-radius: 50%;
      margin-right: 8px;
      &.CLOSED{
        background: #FF194C;
      }
      &.SCHEDULED{
        background: #1E6FFF;
      }
      &.PROCESSING{
        background: #36B452;
      }
    }
    .icon{
      margin-right: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }
}
</style>