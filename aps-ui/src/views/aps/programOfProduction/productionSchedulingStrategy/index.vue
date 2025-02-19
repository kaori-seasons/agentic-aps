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
        <el-button v-if="!props.openOprate" type="primary" :icon="Plus" size="small" @click="addEditRow(null)">{{t(`aps.programOfProduction.productionSchedulingStrategy.add`)}}</el-button>
      </template>
      <template #menu="scope">
        <el-button :text="true" size="small" @click="addEditRow(scope.row)">{{t(`table.edit`)}}</el-button>
      </template>
      <template #direction="scope">
        <span>{{getLabel(scope.row, 'direction')}}</span>
      </template>
      <template #active="scope">
        <div :class="{'status-box': true, 'active': scope.row.active}" @click="editActiveHandle(scope.row)">
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
        {label: '正排', value: 'FORWARD'}
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
        {label: '正排', value: 'FORWARD'}
      ],
      span: 12,
      rules: [{ required: true, message: '请选择排产方向', trigger: 'change' }],
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
  temp.config = {
    direction: rowData.value['direction'],
    materialConstrained: rowData.value['materialConstrained'] || false,
  }
  delete temp.direction
  delete temp.materialConstrained
  if(rowData.value['maxNoImprovementTime'] !== undefined && rowData.value['maxNoImprovementTime'] !== null) {
    temp.config['maxNoImprovementTime'] = rowData.value['maxNoImprovementTime']
    delete temp.maxNoImprovementTime
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