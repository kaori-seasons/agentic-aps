<template>
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
        <template #codeForm>
          <div class="jvs-form-item">
            <el-input v-model="rowData.code">
              <template #append>
                <el-icon style="cursor: pointer;" @click.stop="oepnSearch"><Search /></el-icon>
              </template>
            </el-input>
          </div>
        </template>
        <template #preIntervalDurationForm="scope">
          <div class="jvs-form-item" style="width: 100%;display: flex;align-items: center;">
            <el-input-number v-model="rowData.preIntervalDuration" :placeholder="scope.item.placeholder" :controls="false" style="width: calc(100% - 90px);" />
            <el-select v-model="rowData[scope.item.prop+'_unit']" style="flex: unset;width: 80px;margin-left: 10px;">
              <el-option label="天" value="D"></el-option>
              <el-option label="时" value="H"></el-option>
              <el-option label="分" value="M"></el-option>
              <el-option label="秒" value="S"></el-option>
            </el-select>
          </div>
        </template>
        <template #postIntervalDurationForm="scope">
          <div class="jvs-form-item" style="width: 100%;display: flex;align-items: center;">
            <el-input-number v-model="rowData.postIntervalDuration" :placeholder="scope.item.placeholder" :controls="false" style="width: calc(100% - 90px);" />
            <el-select v-model="rowData[scope.item.prop+'_unit']" style="flex: unset;width: 80px;margin-left: 10px;">
              <el-option label="天" value="D"></el-option>
              <el-option label="时" value="H"></el-option>
              <el-option label="分" value="M"></el-option>
              <el-option label="秒" value="S"></el-option>
            </el-select>
          </div>
        </template>
        <template #bufferTimeForm="scope">
          <div class="jvs-form-item" style="width: 100%;display: flex;align-items: center;">
            <el-input-number v-model="rowData.bufferTime" :placeholder="scope.item.placeholder" :controls="false" style="width: calc(100% - 90px);" />
            <el-select v-model="rowData[scope.item.prop+'_unit']" style="flex: unset;width: 80px;margin-left: 10px;">
              <el-option label="天" value="D"></el-option>
              <el-option label="时" value="H"></el-option>
              <el-option label="分" value="M"></el-option>
              <el-option label="秒" value="S"></el-option>
            </el-select>
          </div>
        </template>
        <template #useMainResourcesForm>
          <div class="jvs-form-item">
            <div class="childMaterials-list">
              <div class="left">
                <div :class="{'left-tab': true, 'active': tabKey == 'useMainResources'}" @click="changeResourceHandle('useMainResources')">主资源</div>
                <!-- <div :class="{'left-tab': true, 'active': tabKey == 'useAuxiliaryResources'}" @click="changeResourceHandle('useAuxiliaryResources')">辅助资源</div> -->
                <div :class="{'left-tab': true, 'active': tabKey == 'useMaterials'}" @click="changeResourceHandle('useMaterials')">使用物料</div>
              </div>
              <div class="slot-table-form-box">
                <el-row class="add-row">
                  <el-button size="small" :icon="Plus" @click="addRowHandle">{{t(`table.add`)}}</el-button>
                </el-row>
                <tableForm
                  :item="{prop: tabKey, type: 'tableForm', editable: false}"
                  :option="tableFormOption"
                  :data="rowData[tabKey]"
                  :forms="rowData"
                  :originForm="rowData"
                  @setTable="setResource">
                  <template #menuBtn="scope">
                    <el-button v-if="tabKey == 'useMainResources'" text @click="editResource(scope.index, scope.row)">{{t(`table.edit`)}}</el-button>
                    <el-button text @click="deleteRow(scope.row, scope.index, tabKey)"><span style="color: #F56C6C;">{{t(`table.delete`)}}</span></el-button>
                  </template>
                  <template #capacityItem="scope">
                    <div>{{(scope.row.capacity ? `${scope.row.capacity}${scope.row.unit || ''}` : '') || '--'}}</div>
                  </template>
                  <template #quantityItem="scope">
                    <div :class="{'quantity-item': true, 'error': !(scope.row.quantity && scope.row.quantity > 0)}">
                      <el-input-number v-model="scope.row.quantity" :placeholder="useMaterialsColumn[2].placeholder" :controls="false" :precision="6">
                        <template #suffix>
                          <span>{{scope.row.unit || ''}}</span>
                        </template>
                      </el-input-number>
                    </div>
                  </template>
                  <template #useItem="scope">
                    <el-switch v-model="scope.row.use" />
                  </template>
                </tableForm>
              </div>
            </div>
          </div>
        </template>
      </jvs-form>
    </div>
  </el-dialog>

  <!-- 选择资源 -->
  <el-dialog
    v-model="resourceVisible"
    :title="t(`aps.dataBase.resource.add`)"
    width="52%"
    append-to-body
    :close-on-click-modal="false"
    :before-close="resourceClose"
  >
    <div v-if="resourceVisible" class="dialog-page-box">
      <jvs-table
        :option="{...resourceOption, column: tabKey == 'useMaterials' ? mateialColumn : resourceColumn}"
        :formData="resourceParams"
        :loading="resourceLoading"
        :data="resourceData"
        :page="resourcePage"
        :selectable="true"
        :selectedRows="selectedRows"
        @on-load="getResourceHandle"
        @search-change="searchResChange"
        @selection-change="selectChange"
      >
        <template #capacity="scope">
          <div>{{(scope.row.capacity ? `${scope.row.capacity}${scope.row.unit}` : '') || '--'}}</div>
        </template>
        <!-- 多选底部左侧确认按钮 -->
        <template #menuLeftBottom>
          <div>
            <el-button type="primary" @click="selectSubmit">确认</el-button>
            <el-button @click="resourceClose">取消</el-button>
          </div>
        </template>
      </jvs-table>
    </div>
  </el-dialog>

  <!-- 修改资源 -->
  <el-dialog
    v-model="editVisible"
    :title="t(`aps.dataBase.resource.edit`)"
    width="520"
    append-to-body
    :close-on-click-modal="false"
    :before-close="editClose"
  >
    <div v-if="editVisible" class="dialog-form-box">
      <jvs-form :option="{
          emptyBtn: false,
          formAlign: 'top',
          column: tableFormOption.tableColumn
        }"
        :formData="editData"
        @submit="editSubmit"
        @cancelClick="editClose"
      >
        <template #capacityForm="scope">
          <div class="capacity-box">
            <el-input-number v-model="editData.capacity" :precision="1" :placeholder="scope.item.placeholder" style="width: 100%;" @change="checkNumber(editData.capacity, 'capacity')" />
            <div v-if="errTip.capacity" class="el-form-item__error">{{errTip.capacity}}</div>
          </div>
        </template>
        <template #throughputForm>
          <div class="capacity-box" style="width: 100%;display: flex;align-items: center;">
            <el-input-number v-model="editData['throughput']" :precision="editData['throughputBefore'] ? 0 : 1" :min="rowData['throughputBefore'] ? 1 : 0.1" :controls="false" style="width: calc(100% - 270px);" />
            <el-select v-model="editData['throughputBefore']" clearable :class="{'P-sel': !editData['throughputBefore']}" @change="throughputBeforeAfterChange('before')" style="width: 80px;margin-left: 10px;">
              <el-option label="每个" value="P"></el-option>
            </el-select>
            <el-select v-model="editData['throughputUnit']" style="width: 80px;margin-left: 10px;">
              <el-option label="天" value="D"></el-option>
              <el-option label="时" value="H"></el-option>
              <el-option label="分" value="M"></el-option>
              <el-option label="秒" value="S"></el-option>
            </el-select>
            <el-select v-model="editData['throughputAfter']" clearable :class="{'P-sel': !editData['throughputAfter']}" @change="throughputBeforeAfterChange('after')" style="width: 80px;margin-left: 10px;">
              <el-option label="每个" value="P"></el-option>
            </el-select>
          </div>
        </template>
      </jvs-form>
    </div>
  </el-dialog>

  <!-- 选择工序模板 -->
  <el-dialog
    v-model="gxVisible"
    :title="t(`aps.productionEngineering.routing.choose`)"
    width="52%"
    append-to-body
    :close-on-click-modal="false"
    :before-close="gxClose"
  >
    <div v-if="gxVisible" class="dialog-page-box">
      <jvs-table
        :option="gxOption"
        :formData="gxParams"
        :loading="gxLoading"
        :data="gxData"
        :page="gxPage"
        @on-load="getGXHandle"
        @search-change="searchGXChange"
        @row-click="gxSubmit"
      >
      </jvs-table>
    </div>
  </el-dialog>
</template>
<script lang="ts" setup name="technologyForm">
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
import { Plus, Search } from '@element-plus/icons-vue'


import tableForm from '@/components/basic-assembly/tableForm.vue'

import { getProcessInfo, add, edit, getResourceList, getMatterList, getGXModelList, getGroupList } from './api'

const emit = defineEmits(['fresh', 'submit', 'close'])
const { t } = useI18n()

const props = defineProps({
  visible: {
    type: Boolean
  },
  dialogTitle: {
    type: String
  },
  row: {
    type: Object
  },
  static: {
    type: Boolean
  }
})

const errTip = ref({})
function checkNumber (value: any, prop: string) {
  if(value) {
    if(!(value > 0)) {
      errTip.value[prop] = '数字应该大于0'
    }else{
      errTip.value[prop] = ''
    }
  }
}

const dialogVisible = ref(false)
const rowData = ref({})
const tableFormOption = computed(() => {
  return {
    addBtn: false,
    viewBtn: false,
    editBtn: false,
    delBtn: false,
    page: false,
    border: false,
    align: 'left',
    menuAlign: 'left',
    cancal: false,
    showOverflow: true,
    hideTop: false,
    menuWidth: (tabKey.value == 'useMainResources') ? 110 : 80,
    menu: true,
    tableColumn: (tabKey.value == 'useMaterials') ? useMaterialsColumn.value : useMainResourcesColumn.value
  }
})

const selectedRows = computed(() => {
  let temp = []
  if(rowData.value && rowData.value[tabKey.value] && rowData.value[tabKey.value].length > 0) {
    rowData.value[tabKey.value].filter(fit => {
      temp.push({
        id: fit.id
      })
    })
  }
  return temp
})

const tabKey = ref('useMainResources')
const formOption = ref({
  emptyBtn: false,
  submitLoading: false,
  formAlign: 'top',
  column: [
    {
      label: '工序编码',
      prop: 'code',
      placeholder: '请输入工序编码',
      span: 12,
      rules: [{ required: true, message: '请输入工序编码', trigger: 'blur' }],
      regularExpression: `^\\S.*\\S$|(^\\S{0,1}\\S$)`,
      regularMessage: '工序编码前后不能存在空格',
      formSlot: props.static ? true : false,
    },
    {
      label: '工序名称',
      prop: 'name',
      placeholder: '请输入工序名称',
      span: 12,
      rules: [{ required: true, message: '请输入工序名称', trigger: 'blur' }]
    },
    {
      label: '前间隔时长',
      prop: 'preIntervalDuration',
      placeholder: '请输入前间隔时长',
      span: 12,
      formSlot: true,
    },
    {
      label: '后间隔时长',
      prop: 'postIntervalDuration',
      placeholder: '请输入后间隔时长',
      span: 12,
      formSlot: true,
    },
    {
      label: '工序关系',
      prop: 'processRelationship',
      placeholder: '请选择工序关系',
      clearable: false,
      type: 'select',
      dicData: [
        {label: 'EE', value: 'EE', secTitle: '前工序未结束，后工序可以提前开始'},
        {label: 'ES', value: 'ES', secTitle: '前工序结束，后工序才能开始'},
      ],
      props: {
        label: 'label',
        value: 'value',
        secTitle: 'secTitle'
      },
      span: 12,
      rules: [{ required: true, message: '请选择工序关系', trigger: 'blur' }]
    },
    {
      label: '缓冲时长',
      prop: 'bufferTime',
      placeholder: '请输入缓冲时长',
      span: 12,
      formSlot: true,
    },
    {
      label: '',
      prop: 'useMainResources',
      type: 'tableForm',
      formSlot: true,
    }
  ],
})

const resourceVisible = ref(false)
const resourceLoading = ref(false)
const resourceData = ref([])
const resourcePage = ref({
  total: 0, // 总页数
  currentPage: 1, // 当前页数
  pageSize: 20, // 每页显示多少条
})
const selectedList = ref([])
const resourceOption = ref({
  page: true,
  search: true,
  cancal: false,
  showOverflow: true,
  addBtn: false,
  menu: false,
})
const resourceParams = ref({})
const resourceColumn = ref([
  {
    label: '编码',
    prop: 'code',
    placeholder: '请输入编码',
    search: true,
    searchSpan: 6,
    rules: [{ required: true, message: '请输入编码', trigger: 'blur' }]
  },
  {
    label: '名称',
    prop: 'name',
    placeholder: '请输入名称',
    search: true,
    searchSpan: 6,
    rules: [{ required: true, message: '请输入名称', trigger: 'blur' }]
  },
  {
    label: '资源组',
    prop: 'resourceGroup',
    placeholder: '请选择资源组',
    search: true,
    searchSpan: 6,
    type: 'select',
    filterable: true,
    allowcreate: true,
    dicData: []
  },
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
])
const mateialColumn = ref([
  {
      label: '编码',
      prop: 'code',
      placeholder: '请输入编码',
      search: true,
      searchSpan: 6,
    },
    {
      label: '名称',
      prop: 'name',
      placeholder: '请输入名称',
      search: true,
      searchSpan: 6,
    },
    {
      label: '类型',
      prop: 'type',
      placeholder: '请选择类型',
      search: true,
      searchSpan: 6,
      type: 'select',
      dicData: [
        {label: '成品', value: 'FINISHED'},
        {label: '原材料', value: 'RAW_MATERIAL'},
        {label: '半成品', value: 'SEMI_FINISHED'},
      ],
    },
    {
      label: '计量单位',
      prop: 'unit',
      placeholder: '请输入计量单位',
    },
])
const useMainResourcesColumn = ref([
  {
    label: '编码',
    prop: 'code',
    placeholder: '请输入编码',
    disabled: true,
  },
  {
    label: '名称',
    prop: 'name',
    placeholder: '请输入名称',
    disabled: true,
  },
  {
    label: '资源组',
    prop: 'resourceGroup',
    placeholder: '请选择资源组',
    search: true,
    searchSpan: 6,
    span: 24,
    type: 'select',
    filterable: true,
    allowcreate: true,
    dicData: []
  },
  {
    label: '容量',
    prop: 'capacity',
    placeholder: '请输入容量',
    type: 'inputNumber',
    precision: 6,
    slot: true,
    needSlot: true,
    formSlot: true
  },
  {
    label: '产能',
    prop: 'throughput',
    formSlot: true
  },
])
const useMaterialsColumn = ref([
  {
    label: '编码',
    prop: 'code',
    placeholder: '请输入编码',
  },
  {
    label: '名称',
    prop: 'name',
    placeholder: '请输入名称',
  },
  {
    label: '数量',
    prop: 'quantity',
    placeholder: '请输入数量',
    type: 'inputNumber',
    slot: true,
    needSlot: true,
  },
  {
    label: '是否使用',
    prop: 'use',
    type: 'switch',
    slot: true,
    needSlot: true,
  }
])

const editVisible = ref(false)
const editIndex = ref(-1)
const editData = ref(null)

const gxVisible = ref(false)
const gxLoading = ref(false)
const gxData = ref([])
const gxPage = ref({
  total: 0, // 总页数
  currentPage: 1, // 当前页数
  pageSize: 20, // 每页显示多少条
})
const gxParams = ref({})
const gxOption = ref({
  page: true,
  search: true,
  cancal: false,
  showOverflow: true,
  addBtn: false,
  menu: false,
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

createHandle()
function createHandle () {
  getGroupList().then(res => {
    if(res.data && res.data.code == 0) {
      let list = []
      for(let i in res.data.data) {
        list.push({
          label: res.data.data[i],
          value: res.data.data[i]
        })
      }
      resourceColumn.value.filter(col => {
        if(col.prop == 'resourceGroup') {
          col.dicData = list
        }
      })
      useMainResourcesColumn.value.filter(col => {
        if(col.prop == 'resourceGroup') {
          col.dicData = list
        }
      })
    }
  })
}

function initHandle () {
  if(props.row) {
    let row = props.row
    if(row.id) {
      getProcessInfo(row.id).then(res => {
        if(res.data && res.data.code == 0) {
          rowData.value = JSON.parse(JSON.stringify(res.data.data))
          if(row.preIntervalDuration) {
            rowData.value['preIntervalDuration'] = Number.parseFloat(row.preIntervalDuration)
            rowData.value['preIntervalDuration_unit'] = row.preIntervalDuration.charAt(row.preIntervalDuration.length-1)
          }else{
            delete rowData.value['preIntervalDuration']
            rowData.value['preIntervalDuration_unit'] = 'S'
          }
          if(row.postIntervalDuration) {
            rowData.value['postIntervalDuration'] = Number.parseFloat(row.postIntervalDuration)
            rowData.value['postIntervalDuration_unit'] = row.postIntervalDuration.charAt(row.postIntervalDuration.length-1)
          }else{
            delete rowData.value['postIntervalDuration']
            rowData.value['postIntervalDuration_unit'] = 'S'
          }
          if(row.bufferTime) {
            rowData.value['bufferTime'] = Number.parseFloat(row.bufferTime)
            rowData.value['bufferTime_unit'] = row.bufferTime.charAt(row.bufferTime.length-1)
          }else{
            delete rowData.value['bufferTime']
            rowData.value['bufferTime_unit'] = 'S'
          }
          dialogVisible.value = true
        }
      })
    }else{
      rowData.value = JSON.parse(JSON.stringify(row))
      if(row.preIntervalDuration) {
        rowData.value['preIntervalDuration'] = Number.parseFloat(row.preIntervalDuration)
        rowData.value['preIntervalDuration_unit'] = row.preIntervalDuration.charAt(row.preIntervalDuration.length-1)
      }else{
        delete rowData.value['preIntervalDuration']
        rowData.value['preIntervalDuration_unit'] = 'S'
      }
      if(row.postIntervalDuration) {
        rowData.value['postIntervalDuration'] = Number.parseFloat(row.postIntervalDuration)
        rowData.value['postIntervalDuration_unit'] = row.postIntervalDuration.charAt(row.postIntervalDuration.length-1)
      }else{
        delete rowData.value['postIntervalDuration']
        rowData.value['postIntervalDuration_unit'] = 'S'
      }
      if(row.bufferTime) {
        rowData.value['bufferTime'] = Number.parseFloat(row.bufferTime)
        rowData.value['bufferTime_unit'] = row.bufferTime.charAt(row.bufferTime.length-1)
      }else{
        delete rowData.value['bufferTime']
        rowData.value['bufferTime_unit'] = 'S'
      }
      dialogVisible.value = true
    }
  }else{
    rowData.value = {
      processRelationship: 'EE',
      preIntervalDuration_unit: 'S',
      postIntervalDuration_unit: 'S',
      bufferTime_unit: 'S',
      useMainResources: [],
      useAuxiliaryResources: [],
      useMaterials: []
    }
    dialogVisible.value = true
  }
}

function submitHandle (form) {
  let temp = JSON.parse(JSON.stringify(rowData.value))
  let validate = true
  if(temp.bufferTime) {
    temp.bufferTime = rowData.value['bufferTime'] + rowData.value['bufferTime_unit']
  }
  delete temp['bufferTime_unit']
  if(temp.preIntervalDuration) {
    temp.preIntervalDuration = rowData.value['preIntervalDuration'] + rowData.value['preIntervalDuration_unit']  
  }
  delete temp['preIntervalDuration_unit']
  if(temp.postIntervalDuration) {
    temp.postIntervalDuration = rowData.value['postIntervalDuration'] + rowData.value['postIntervalDuration_unit']
  }
  delete temp['postIntervalDuration_unit']
  if(!props.static) {
    if(temp.useMainResources && temp.useMainResources.length > 0) {
      let tp = []
      temp.useMainResources.filter(it => {
        let obj = {
          id: it.id
        }
        if(it.capacity) {
          obj['capacity'] = it.capacity
        }
        if(it.throughput) {
          obj['throughput'] = it.throughput
        }
        tp.push(obj)
      })
      temp.useMainResources = tp
    }
    if(temp.useAuxiliaryResources && temp.useAuxiliaryResources.length > 0) {
      let tp = []
      temp.useAuxiliaryResources.filter(it => {
        tp.push({
          id: it.id
        })
      })
      temp.useAuxiliaryResources = tp
    }
    if(temp.useMaterials && temp.useMaterials.length > 0) {
      let tp = []
      temp.useMaterials.filter(it => {
        if(!(it.quantity && it.quantity > 0)) {
          validate = false
        }
        tp.push({
          id: it.id,
          quantity: it.quantity,
          use: it.use ? true : false,
        })
      })
      temp.useMaterials = tp
    }
  }
  if(validate) {
    if(props.static) {
      emit('submit', (rowData.value['id'] || rowData.value['nodeId']) ? 'edit' : 'add', temp)
    }else{
      let fun = add
      if(rowData.value['id']) {
        fun = edit
      }
      formOption.value.submitLoading = true
      fun(temp).then(res => {
        if(res.data && res.data.code == 0) {
          ElNotification.closeAll()
          ElNotification({
            title: t(`common.tip`),
            message: (rowData.value['id'] ? t(`aps.productionEngineering.technology.edit`) : t(`aps.productionEngineering.technology.add`)) + t(`aps.productionEngineering.technology.success`),
            position: 'bottom-right',
            type: 'success',
          })
          formOption.value.submitLoading = false
          handleClose()
          emit('fresh')
        }
      }).catch(e => {
        formOption.value.submitLoading = false
      })
    }
  }
}

function handleClose () {
  dialogVisible.value = false
  rowData.value = {}
  tabKey.value = 'useMainResources'
  emit('close')
}

function changeResourceHandle (key) {
  tabKey.value = key
}

function setResource (data) {
  if(tabKey.value == 'useMaterials') {
    if(data && data.length > 0) {
      data.filter(dit => {
        if(dit.use !== false) {
          dit.use = true
        }
      })
    }
  }
  rowData.value[tabKey.value] = data
}

function addRowHandle () {
  resourceVisible.value = true
}

function deleteRow (row, index, prop) {
  rowData.value[prop].splice(index, 1)
}

function resourceClose () {
  resourceVisible.value = false
  selectedList.value = []
  resourcePage.value = {
    total: 0,
    currentPage: 1,
    pageSize: 20,
  }
}

function getResourceHandle (pageInfo?) {
  resourceLoading.value = true
  let tp = {
    current: pageInfo && pageInfo.current ? pageInfo.current : resourcePage.value.currentPage,
    size: resourcePage.value.pageSize,
  }
  // if(tabKey.value == 'useMainResources') {
  //   tp['type'] = 'MAIN'
  // }else if(tabKey.value == 'useAuxiliaryResources') {
  //   tp['type'] = 'AUXILIARY'
  // }else{
  //   delete tp['type']
  // }
  let func = getResourceList
  if(tabKey.value == 'useMaterials') {
    func = getMatterList
  }
  func({...resourceParams.value, ...tp}).then( res => {
    resourceLoading.value = false
    if(res.data && res.data.code == 0 && res.data.data) {
      resourceData.value = res.data.data.records
      resourcePage.value.currentPage = res.data.data.current
      resourcePage.value.total = res.data.data.total
    }
  }).catch(err => {
    resourceLoading.value = false
  })
}

function searchResChange (form) {
  resourceParams.value = form
  getResourceHandle()
}

function selectChange (data) {
  if(data.length == 0) {
    let ids = []
    resourceData.value.filter(sit => {
      ids.push(sit.id)
    })
    let temp = []
    selectedList.value.filter(lit => {
      if(ids.indexOf(lit.id) == -1) {
        temp.push(lit)
      }
    })
    selectedList.value = temp
  }else{
    let hasIds = []
    selectedList.value.filter(sit => {
      hasIds.push(sit.id)
    })
    let dataIds = []
    
    for(let i in data) {
      dataIds.push(data[i].id)
      if(hasIds.indexOf(data[i].id) == -1) {
        selectedList.value.push(data[i])
      }
    }
    let moveIds = []
    resourceData.value.filter(sit => {
      if(dataIds.indexOf(sit.id) == -1) {
        moveIds.push(sit.id)
      }
    })
    let temp = []
    selectedList.value.filter(lit => {
      if(moveIds.indexOf(lit.id) == -1) {
        temp.push(lit)
      }
    })
    selectedList.value = temp
  }
}

function selectSubmit () {
  let tp = JSON.parse(JSON.stringify(selectedList.value))
  if(rowData.value[tabKey.value] && rowData.value[tabKey.value].length > 0) {
    for(let i in tp) {
      for(let j in rowData.value[tabKey.value]) {
        if(tp[i].id == rowData.value[tabKey.value][j].id) {
          if(tabKey.value == 'useMainResources') {
            tp[i].capacity = rowData.value[tabKey.value][j].capacity
            tp[i].throughput = rowData.value[tabKey.value][j].throughput
          }
          if(tabKey.value == 'useMaterials') {
            tp[i].quantity = rowData.value[tabKey.value][j].quantity
            tp[i].use = rowData.value[tabKey.value][j].use
          }
        }
      }
    }
  }
  setResource(tp)
  resourceClose()
}

function editResource (index, row) {
  editIndex.value = index
  editData.value = JSON.parse(JSON.stringify(row))
  if(row.throughput) {
    editData.value['throughput'] = Number.parseFloat(row.throughput)
    if(row.throughput.includes('P')) {
      if(row.throughput.endsWith('P')) {
        editData.value['throughputUnit'] = row.throughput.charAt(row.throughput.length-2)
        editData.value['throughputAfter'] = row.throughput.charAt(row.throughput.length-1)
      }else{
        editData.value['throughputUnit'] = row.throughput.charAt(row.throughput.length-1)
        editData.value['throughputBefore'] = row.throughput.charAt(row.throughput.length-2)
      }
    }else{
      editData.value['throughputUnit'] = row.throughput.charAt(row.throughput.length-1)
    }
  }else{
    delete editData.value['throughput']
    editData.value['throughputUnit'] = 'S'
  }
  editVisible.value = true
}

function throughputBeforeAfterChange (type) {
  if(type == 'before' && editData.value['throughputBefore']) {
    editData.value['throughputAfter'] = ''
  }
  if(type == 'after' && editData.value['throughputAfter']) {
    editData.value['throughputBefore'] = ''
  }
}

function editSubmit () {
  let temp = JSON.parse(JSON.stringify(editData.value))
  delete temp.throughputUnit
  delete temp.throughputBefore
  delete temp.throughputAfter
  if(temp.throughput) {
    temp.throughput = (temp.throughput + (editData.value['throughputBefore'] || '') + editData.value['throughputUnit'] + (editData.value['throughputAfter'] || ''))
  }else{
    temp.throughput = ''
  }
  rowData.value['useMainResources'][editIndex.value] = temp
  editClose()
}

function editClose () {
  editData.value = null
  editIndex.value = -1
  editVisible.value = false
}

function oepnSearch () {
  gxVisible.value = true
}

function gxClose () {
  gxVisible.value = false
  gxPage.value.currentPage = 1
  gxPage.value.total = 0
  gxData.value = []
  gxParams.value = {}
}

function getGXHandle (pageInfo?) {
  gxLoading.value = true
  let tp = {
    current: pageInfo && pageInfo.current ? pageInfo.current : gxPage.value.currentPage,
    size: gxPage.value.pageSize,
  }
  getGXModelList(Object.assign(gxParams.value, tp)).then( res => {
    gxLoading.value = false
    if(res.data && res.data.code == 0 && res.data.data) {
      gxData.value = res.data.data.records
      gxPage.value.currentPage = res.data.data.current
      gxPage.value.total = res.data.data.total
    }
  }).catch(err => {
    gxLoading.value = false
  })
}

function searchGXChange (form) {
  gxParams.value = form
  getGXHandle()
}

function gxSubmit (data) {
  let { row } = data
  for(let k in row) {
    if(k != 'id') {
      rowData.value[k] = row[k]
    }
  }
  if(row.preIntervalDuration) {
    rowData.value['preIntervalDuration'] = Number.parseFloat(row.preIntervalDuration)
    rowData.value['preIntervalDuration_unit'] = row.preIntervalDuration.charAt(row.preIntervalDuration.length-1)
  }else{
    delete rowData.value['preIntervalDuration']
    rowData.value['preIntervalDuration_unit'] = 'S'
  }
  if(row.postIntervalDuration) {
    rowData.value['postIntervalDuration'] = Number.parseFloat(row.postIntervalDuration)
    rowData.value['postIntervalDuration_unit'] = row.postIntervalDuration.charAt(row.postIntervalDuration.length-1)
  }else{
    delete rowData.value['postIntervalDuration']
    rowData.value['postIntervalDuration_unit'] = 'S'
  }
  if(row.bufferTime) {
    rowData.value['bufferTime'] = Number.parseFloat(row.bufferTime)
    rowData.value['bufferTime_unit'] = row.bufferTime.charAt(row.bufferTime.length-1)
  }else{
    delete rowData.value['bufferTime']
    rowData.value['bufferTime_unit'] = 'S'
  }
  gxClose()
}

watch(() => props.visible, (newVal, oldVal) => {
  if(newVal) {
    initHandle()
  }else{
    dialogVisible.value = false
  }
})
</script>
<style lang="scss" scoped>
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
    