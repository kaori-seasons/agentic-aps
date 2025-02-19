<template>
  <div class="basic-layout-box aps-dataBase-resource">
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
        <el-button type="primary" :icon="Plus" size="small" @click="addEditRow(null)">{{t(`aps.dataBase.resource.add`)}}</el-button>
      </template>
      <template #menu="scope">
        <el-button :text="true" size="small" @click="addEditRow(scope.row)">{{t(`table.edit`)}}</el-button>
      </template>
      <template #capacity="scope">
        <div>{{(scope.row.capacity ? `${scope.row.capacity}${scope.row.unit}` : '') || '--'}}</div>
      </template>
      <template #resourceStatus="scope">
        <div :class="['resourceStatus', scope.row.resourceStatus]">
          <i class="color-dot"></i>
          <span>{{dicFormat(scope.row.resourceStatus)}}</span>
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
          <template #capacityForm="scope">
            <div class="capacity-box">
              <el-input-number v-model="rowData.capacity" :precision="1" :placeholder="scope.item.placeholder" style="width: 100%;" @change="checkNumber(rowData.capacity, 'capacity')" />
              <div v-if="errTip.capacity" class="el-form-item__error">{{errTip.capacity}}</div>
            </div>
          </template>
          <template #throughputForm>
            <div class="capacity-box" style="width: 100%;display: flex;align-items: center;">
              <el-input-number v-model="rowData['throughput']" :precision="rowData['throughputBefore'] ? 0 : 1" :min="rowData['throughputBefore'] ? 1 : 0.1" :controls="false" style="width: calc(100% - 270px);" />
              <el-select v-model="rowData['throughputBefore']" clearable :class="{'P-sel': !rowData['throughputBefore']}" @change="throughputBeforeAfterChange('before')" style="width: 80px;margin-left: 10px;">
                <el-option label="每个" value="P"></el-option>
              </el-select>
              <el-select v-model="rowData['throughputUnit']" style="width: 80px;margin-left: 10px;">
                <el-option label="天" value="D"></el-option>
                <el-option label="时" value="H"></el-option>
                <el-option label="分" value="M"></el-option>
                <el-option label="秒" value="S"></el-option>
              </el-select>
              <el-select v-model="rowData['throughputAfter']" clearable :class="{'P-sel': !rowData['throughputAfter']}" @change="throughputBeforeAfterChange('after')" style="width: 80px;margin-left: 10px;">
                <el-option label="每个" value="P"></el-option>
              </el-select>
            </div>
          </template>
        </jvs-form>
      </div>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup name="resource">
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

const errTip = ref({})
function checkNumber (value: any, prop: string) {
  if(value) {
    if(!(value > 0)) {
      errTip.value[prop] = '数字应该大于0'
    }else{
      errTip.value[prop] = ''
    }
  }
  console.log(value, !(value > 0), errTip.value)
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
      label: '资源组',
      prop: 'resourceGroup',
      placeholder: '请选择资源组',
      search: true,
      searchSpan: 4,
      type: 'select',
      filterable: true,
      allowcreate: true,
      dicUrl: '/mgr/jvs-aps/production-resource/group/list',
    },
    // {
    //   label: '种类',
    //   prop: 'type',
    //   placeholder: '请选择种类',
    //   search: true,
    //   searchSpan: 4,
    //   type: 'select',
    //   dicData: [
    //     {label: '辅资源', value: 'AUXILIARY'},
    //     {label: '主资源', value: 'MAIN'}
    //   ],
    //   rules: [{ required: true, message: '请输入种类', trigger: 'blur' }]
    // },
    {
      label: '容量',
      prop: 'capacity',
      placeholder: '请输入容量',
      type: 'inputNumber',
      precision: 6,
      slot: true,
      formSlot: true
    },
    {
      label: '容量计量单位',
      prop: 'unit',
      placeholder: '请输入容量计量单位',
      hide: true
    },
    {
      label: '产能',
      prop: 'throughput',
      formSlot: true
    },
    {
      label: '状态',
      prop: 'resourceStatus',
      placeholder: '请选择资源状态',
      type: 'select',
      dicData: [
        {label: '正常', value: 'NORMAL'},
        {label: '维修', value: 'MAINTENANCE'},
        {label: '报废', value: 'SCRAPPED'},
      ],
      slot: true,
      rules: [{ required: true, message: '请选择资源状态', trigger: 'change' }]
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
const capacityUnit = ref({
  unit: 'D'
})

function dicFormat (val) {
  let temp = val
  let dicData = [
    {label: '正常', value: 'NORMAL'},
    {label: '维修', value: 'MAINTENANCE'},
    {label: '报废', value: 'SCRAPPED'},
  ]
  dicData.filter(dic => {
    if(dic.value == val) {
      temp = dic.label
    }
  })
  return temp
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
    console.log(row)
    if(row.throughput) {
      rowData.value['throughput'] = Number.parseFloat(row.throughput)
      if(row.throughput.includes('P')) {
        if(row.throughput.endsWith('P')) {
          rowData.value['throughputUnit'] = row.throughput.charAt(row.throughput.length-2)
          rowData.value['throughputAfter'] = row.throughput.charAt(row.throughput.length-1)
        }else{
          rowData.value['throughputUnit'] = row.throughput.charAt(row.throughput.length-1)
          rowData.value['throughputBefore'] = row.throughput.charAt(row.throughput.length-2)
        }
      }else{
        rowData.value['throughputUnit'] = row.throughput.charAt(row.throughput.length-1)
      }
    }else{
      delete rowData.value['throughput']
      rowData.value['throughputUnit'] = 'S'
    }
    dialogTitle.value = t(`aps.dataBase.resource.edit`)
  }else{
    rowData.value = {
      throughputUnit: 'S'
    }
    dialogTitle.value = t(`aps.dataBase.resource.add`)
  }
  dialogVisible.value = true
}

function submitHandle (form) {
  let fun = add
  if(rowData.value['id']) {
    fun = edit
  }
  let temp = JSON.parse(JSON.stringify(rowData.value))
  delete temp.throughputUnit
  delete temp.throughputBefore
  delete temp.throughputAfter
  if(temp.throughput) {
    temp.throughput = (temp.throughput + (rowData.value['throughputBefore'] || '') + rowData.value['throughputUnit'] + (rowData.value['throughputAfter'] || ''))
  }else{
    temp.throughput = ''
  }
  formOption.value.submitLoading = true
  fun(temp).then(res => {
    if(res.data && res.data.code == 0) {
      ElNotification.closeAll()
      ElNotification({
        title: t(`common.tip`),
        message: (rowData.value['id'] ? t(`aps.dataBase.resource.edit`) : t(`aps.dataBase.resource.add`) + t(`aps.dataBase.resource.success`)),
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

function throughputBeforeAfterChange (type) {
  if(type == 'before' && rowData.value['throughputBefore']) {
    rowData.value['throughputAfter'] = ''
  }
  if(type == 'after' && rowData.value['throughputAfter']) {
    rowData.value['throughputBefore'] = ''
  }
}
</script>
<style lang="scss" scoped>
.aps-dataBase-resource{
  .resourceStatus{
    display: flex;
    align-items: center;
    .color-dot{
      width: 12px;
      height: 12px;
      border-radius: 50%;
      margin-right: 8px;
      
    }
    &.NORMAL{
      .color-dot{
        background: #36B452;
      }
    }
    &.MAINTENANCE{
      .color-dot{
        background: #FF9736;
      }
    }
    &.SCRAPPED{
      .color-dot{
        background: #FF194C;
      }
    }
  }
}
::v-deep(.capacity-box){
  .el-input__wrapper, .el-select__wrapper{
    background-color: #F5F6F7;
    box-shadow: none;
    // border-radius: 4px 0 0 4px;
    .el-input__inner{
      text-align: left;
    }
  }
  .P-sel{
    .el-select__wrapper{
      .el-select__selection{
        .el-select__placeholder{
          >span{
            display: none;
          }
        }
      }
    }
  }
}
</style>