<template>
  <div class="basic-layout-box aps-dataBase-technology">
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
        <el-button type="primary" :icon="Plus" size="small" @click="addEditRow(null)">{{t(`aps.productionEngineering.technology.add`)}}</el-button>
      </template>
      <template #menu="scope">
        <el-button :text="true" size="small" @click="addEditRow(scope.row)">{{t(`table.edit`)}}</el-button>
      </template>
    </jvs-table>
    <!-- 新增 修改 -->
    <technologyForm :visible="dialogVisible" :dialogTitle="dialogTitle" :row="rowData" @fresh="freshHandle" @close="closeHandle"></technologyForm>
  </div>
</template>
<script lang="ts" setup name="technology">
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

import technologyForm from '../component/technology.vue'

import { getList, del } from './api'

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
      label: '工序编码',
      prop: 'code',
      placeholder: '请输入工序编码',
      search: true,
      searchSpan: 4,
    },
    {
      label: '工序名称',
      prop: 'name',
      placeholder: '请输入工序名称',
      search: true,
      searchSpan: 4,
    },
    {
      label: '工序关系',
      prop: 'processRelationship',
      placeholder: '请选择工序关系',
      type: 'select',
      dicData: [
        {label: 'EE', value: 'EE', secTitle: '前工序未结束，后工序可以提前开始'},
        {label: 'ES', value: 'ES', secTitle: '前工序结束，后工序才能开始'},
      ],
      props: {
        label: 'label',
        value: 'value',
        secTitle: 'secTitle'
      }
    },
    {
      label: '前间隔时长',
      prop: 'preIntervalDuration',
    },
    {
      label: '后间隔时长',
      prop: 'postIntervalDuration',
    },
    {
      label: '缓冲时长',
      prop: 'bufferTime',
      placeholder: '请输入缓冲时长',
    },
  ]
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const rowData = ref(null)

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
    dialogTitle.value = t(`aps.productionEngineering.technology.edit`)
    dialogVisible.value = true
  }else{
    rowData.value = null
    dialogTitle.value = t(`aps.productionEngineering.technology.add`)
    dialogVisible.value = true
  }
}

function freshHandle () {
  dialogTitle.value = ''
  dialogVisible.value = false
  getListHandle()
}

function closeHandle () {
  rowData.value = null
  dialogTitle.value = ''
  dialogVisible.value = false
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
.aps-dataBase-technology{

}
.dialog-form-box{
  ::v-deep(.el-form){
    .form-column-tableForm{
      .el-form-item{
        margin: 0!important;
      }
    }
  }
  .childMaterials-list{
    border-top: 8px solid #F5F6F7;
    display: flex;
    .left{
      width: 163px;
      border-right: 1px solid #EEEFF0;
      padding-top: 7px;
      box-sizing: border-box;
      .left-tab{
        font-family: Microsoft YaHei-Regular, Microsoft YaHei;
        font-weight: 400;
        font-size: 14px;
        color: #6F7588;
        line-height: 18px;
        padding: 24px;
        cursor: pointer;
        &.active{
          font-family: Microsoft YaHei-Bold, Microsoft YaHei;
          font-weight: 700;
          color: #1E6FFF;
        }
      }
    }
    .slot-table-form-box{
      flex: 1;
      height: calc(70vh - 366px);
      padding: 0 30px;
      padding-top: 18px;
      padding-bottom: 16px;
      box-sizing: border-box;
      overflow: hidden;
      .add-row{
        margin-bottom: 8px;
        .el-button{
          width: 88px;
          height: 36px;
          background: #1E6FFF;
          color: #fff;
          font-size: 14px;
        }
      }
      .table-form{
        padding: 0;
        height: calc(100% - 34px);
        ::v-deep(.el-table__body-wrapper){
          min-height: unset; 
          .el-scrollbar__view{
            height: 100%;
          }
        }
      }
      .quantity-item.error{
        .el-input-number{
          ::v-deep(.el-input){
            .el-input__wrapper{
              box-shadow: 0 0 0 1px var(--el-color-danger) inset!important;
            }
          }
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
}
.dialog-page-box{
  height: calc(70vh - 48px);
  padding: 20px;
  box-sizing: border-box;
  overflow: hidden;
}
</style>