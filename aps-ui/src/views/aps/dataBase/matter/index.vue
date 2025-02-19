<template>
  <div class="basic-layout-box aps-dataBase-matter">
    <jvs-table
      :formData="queryParams"
      :option="tableOption"
      :loading="tableLoading"
      :data="tableData"
      :page="page"
      @on-load="getListHandle"
      @search-change="searchChange"
      @delRow="delHandle"
    >
      <template #menuLeft>
        <el-button type="primary" :icon="Plus" size="small" @click="addEditRow(null)">{{t(`aps.dataBase.matter.add`)}}</el-button>
      </template>
      <template #menu="scope">
        <el-button :text="true" size="small" @click="addEditRow(scope.row)">{{t(`table.edit`)}}</el-button>
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
          <template v-for="sit in slotItems" :key="sit.prop+'formSlot'" v-slot:[formItemSlot(sit.prop)]>
            <div class="timeUnit-box" style="width: 100%;display: flex;align-items: center;">
              <el-input-number v-model="leadBufferTime[sit.prop]['value']" :precision="1" :min="0.1" :controls="false" style="width: calc(100% - 90px);" />
              <el-select v-model="leadBufferTime[sit.prop]['unit']" style="width: 80px;margin-left: 10px;">
                <el-option label="天" value="D"></el-option>
                <el-option label="时" value="H"></el-option>
                <el-option label="分" value="M"></el-option>
                <el-option label="秒" value="S"></el-option>
              </el-select>
            </div>
          </template>
        </jvs-form>
      </div>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup name="matter">
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

import { getList, add, edit, del } from './api'

const { proxy } = getCurrentInstance()
const emit = defineEmits([])
const { t } = useI18n()
const commonStore = useCommonStore()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
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
      label: '编码',
      prop: 'code',
      placeholder: '请输入编码',
      search: true,
      searchSpan: 4,
      rules: [{ required: true, message: '请输入编码', trigger: 'blur' }]
    },
    {
      label: '名称',
      prop: 'name',
      placeholder: '请输入名称',
      search: true,
      searchSpan: 4,
      rules: [{ required: true, message: '请输入名称', trigger: 'blur' }]
    },
    {
      label: '类型',
      prop: 'type',
      placeholder: '请选择类型',
      search: true,
      searchSpan: 4,
      type: 'select',
      dicData: [
        {label: '成品', value: 'FINISHED'},
        {label: '原材料', value: 'RAW_MATERIAL'},
        {label: '半成品', value: 'SEMI_FINISHED'},
      ],
      rules: [{ required: true, message: '请选择类型', trigger: 'change' }]
    },
    {
      label: '来源',
      prop: 'source',
      placeholder: '请选择来源',
      search: true,
      searchSpan: 4,
      type: 'select',
      dicData: [
        {label: '制造', value: 'PRODUCED'},
        {label: '采购', value: 'PURCHASED'},
      ],
      rules: [{ required: true, message: '请选择来源', trigger: 'change' }]
    },
    {
      label: '库存',
      prop: 'quantity',
      placeholder: '请输入库存',
      type: 'inputNumber',
      min: 0,
      rules: [{ required: true, message: '请输入库存', trigger: 'blur' }]
    },
    {
      label: '安全库存',
      prop: 'safetyStock',
      placeholder: '请输入安全库存',
      type: 'inputNumber',
      min: 0,
    },
    {
      label: '计量单位',
      prop: 'unit',
      placeholder: '请输入计量单位',
      rules: [{ required: true, message: '请输入计量单位', trigger: 'blur' }],
      hide: true
    },
    {
      label: '提前期',
      prop: 'leadTime',
      placeholder: '请选择提前期',
      formSlot: true
    },
    {
      label: '缓冲期',
      prop: 'bufferTime',
      placeholder: '请选择缓冲期',
      formSlot: true
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
  column: [],
})
const slotItems = ref([])
const leadBufferTime = ref({
  leadTime: {unit: 'D'},
  bufferTime: {unit: 'D'}
})

createHandle()

function createHandle () {
  tableOption.value.column.filter(col => {
    if(col.formSlot) {
      slotItems.value.push(col)
    }
  })
}

function formItemSlot(prop) {
  return prop + 'Form'
}

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
  formOption.value.column = JSON.parse(JSON.stringify(tableOption.value.column))
  formOption.value.column.filter(col => {
    col.span = 24
  })
  if(row) {
    rowData.value = JSON.parse(JSON.stringify(row))
    if(row['leadTime']) {
      leadBufferTime.value['leadTime'] = {
        value: Number.parseFloat(row['leadTime']),
        unit: rowData.value['leadTime'].charAt(rowData.value['leadTime'].length - 1)
      }
    }
    if(row['bufferTime']) {
      leadBufferTime.value['bufferTime'] = {
        value: Number.parseFloat(row['bufferTime']),
        unit: row['bufferTime'].charAt(row['bufferTime'].length - 1)
      }
    }
    dialogTitle.value = t(`aps.dataBase.matter.edit`)
  }else{
    rowData.value = {}
    dialogTitle.value = t(`aps.dataBase.matter.add`)
  }
  dialogVisible.value = true
}

function submitHandle (form) {
  if(leadBufferTime.value) {
    if(leadBufferTime.value['leadTime'] && leadBufferTime.value['leadTime']['value']) {
      rowData.value['leadTime'] = leadBufferTime.value['leadTime']['value'] + leadBufferTime.value['leadTime']['unit']
    }else{
      rowData.value['leadTime'] = ''
    }
    if(leadBufferTime.value['bufferTime'] && leadBufferTime.value['bufferTime']['value']) {
      rowData.value['bufferTime'] = leadBufferTime.value['bufferTime']['value'] + leadBufferTime.value['bufferTime']['unit']
    }else{
      rowData.value['bufferTime'] = ''
    }
  }
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
        message: (rowData.value['id'] ? t(`aps.dataBase.matter.edit`) : t(`aps.dataBase.matter.add`)) + t(`aps.dataBase.matter.success`),
        position: 'bottom-right',
        type: 'success',
      })
      formOption.value.submitLoading = false
      getListHandle()
      handleClose()
    }
  }).catch(e => {
    formOption.value.submitLoading = false
  })
}

function handleClose () {
  dialogVisible.value = false
  rowData.value = {}
  leadBufferTime.value = {
    leadTime: {unit: 'D'},
    bufferTime: {unit: 'D'}
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
</script>
<style lang="scss" scoped>
.aps-dataBase-matter{

}
::v-deep(.timeUnit-box){
  .el-input__wrapper, .el-select__wrapper{
    background-color: #F5F6F7;
    box-shadow: none;
    // border-radius: 4px 0 0 4px;
    .el-input__inner{
      text-align: left;
    }
  }
  .el-select__wrapper{
    // border-radius: 0 4px 4px 0;
  }
}
</style>