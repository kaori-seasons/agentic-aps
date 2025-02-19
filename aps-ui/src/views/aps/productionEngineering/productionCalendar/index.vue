<template>
  <div class="basic-layout-box aps-productionEngineering-productionCalendar">
    <div class="left">
      <div class="title-box">
        <svg>
          <use xlink:href="#icon-jvs-rongqi"></use>
        </svg>
        <span>工作模式</span>
      </div>
      <div class="mode-tool">
        <el-button type="primary" :icon="Plus" @click="addEditMode(null)">{{t(`table.add`)}}</el-button>
      </div>
      <el-scrollbar :class="{'mode-list': true, 'loading': modeLoading}">
        <div v-for="mode in modelList" :key="mode.id" class="mode-list-item">
          <div class="header">
            <span>{{mode.name}}</span>
            <div>
              <el-icon size="16" color="#1E6FFF" @click="addEditMode(mode)"><Edit /></el-icon>
              <span class="slide-line"></span>
              <el-popconfirm
                :title="t('common.deleteConfirm')"
                :icon="WarningFilled"
                :width="300"
                @confirm="deleteMode(mode)"
              >
                <template #reference>
                  <el-icon size="16" color="#1E6FFF"><Delete /></el-icon>
                </template>
              </el-popconfirm>
            </div>
          </div>
          <div class="body">
            <span class="label">时间：</span>
            <span>{{mode.workingMode}}</span>
          </div>
        </div>
      </el-scrollbar>
    </div>
    <div class="right">
      <div class="top">
        <div class="left-box">
          <el-button type="primary" :icon="Plus" @click="addEditRow(null)">{{t(`table.add`)}}</el-button>
        </div>
      </div>
      <div class="bottom">
        <div class="bottom-body">
          <jvs-table
            :option="tableOption"
            :loading="tableLoading"
            :data="tableData"
            @on-load="getListHandle"
            @search-change="searchChange"
            @delRow="delHandle"
          >
            <template #menu="scope">
              <el-button :text="true" size="small" @click="addEditRow(scope.row)">{{t(`table.edit`)}}</el-button>
            </template>
            <template #workModeId="scope">
              <span>{{scope.row.workModeName}}</span>
            </template>
            <template #enabled="scope">
              <span>{{scope.row.enabled ? '启用' : '禁用'}}</span>
            </template>
          </jvs-table>
        </div>
      </div>
    </div>
    <!-- 工作模式 -->
    <el-dialog
      v-model="modeVisible"
      :title="modeTitle"
      width="520"
      append-to-body
      :close-on-click-modal="false"
      :before-close="modeClose"
    >
      <div v-if="modeVisible" class="dialog-form-box">
        <jvs-form :option="modeOption" :formData="modeData" @submit="modeSubmit" @cancelClick="modeClose">
          <template #workingModeForm>
            <div class="jvs-form-item working-mode-box">
              <div class="working-mode-list">
                <div v-for="(time, tix) in modeData.workingMode" :key="'working-mode'+tix" class="working-mode-list-item">
                  <el-popover placement="bottom" :width="96" trigger="click" @show="setVisible(('start_'+tix), true)" @hide="setVisible(('start_'+tix), false)">
                    <template #reference>
                      <el-input v-model="time.start" @blur="formatTime(time, 'start')" :class="{'error': !time.start}"></el-input>
                    </template>
                    <hourMinutePicker :visible="pickerVisible['start_'+tix]" :form="time" prop="start" :index="tix" @change="changeHandle"></hourMinutePicker>
                  </el-popover>
                  <div class="split-bar">-</div>
                  <el-popover placement="bottom" :width="96" trigger="click"  @show="setVisible(('end_'+tix), true)" @hide="setVisible(('end_'+tix), false)">
                    <template #reference>
                      <el-input v-model="time.end" @blur="formatTime(time, 'end')" :class="{'error': !time.end}"></el-input>
                    </template>
                    <hourMinutePicker :visible="pickerVisible['end_'+tix]" :form="time" prop="end" :index="tix" @change="changeHandle"></hourMinutePicker>
                  </el-popover>
                  <span class="delete-icon-button" @click="deleteModeTime(time, tix)">
                    <span class="border-line"></span>
                  </span>
                </div>
              </div>
              <div class="bottom-add-button">
                <div class="button" @click="addModeTimeHandle">
                  <div class="icon">
                    <svg aria-hidden="true">
                      <use xlink:href="#jvs-ui-icon-xinjian"></use>
                    </svg>
                  </div>
                  <span>{{t(`aps.productionEngineering.productionCalendar.addTime`)}}</span>
                </div>
              </div>
            </div>
          </template>
        </jvs-form>
      </div>
    </el-dialog>

    <!-- 工作日历 新增 修改 -->
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
          <template #workDaySettingForm>
            <div class="jvs-form-item">
              <el-checkbox v-for="item in workDays" :key="item.value" v-model="rowData.workDaySetting[item.value]" :label="item.label" />
            </div>
          </template>
          <template #resourceIdsForm>
            <div class="slot-table-form-box">
              <el-row class="add-row">
                <el-button size="small" :icon="Plus" @click="addRowHandle">{{t(`table.add`)}}</el-button>
              </el-row>
              <tableForm
                :item="{prop: 'resourceIds', type: 'tableForm', editable: false}"
                :option="tableFormOption"
                :data="rowData['resourceIds']"
                :forms="rowData"
                :originForm="rowData"
                @setTable="setResource"
              >
                <template #menuBtn="scope">
                  <el-button text @click="deleteRow(scope.row, scope.index, 'resourceIds')"><span style="color: #F56C6C;">{{t(`table.delete`)}}</span></el-button>
                </template>
                <template #capacityItem="scope">
                  <div>{{(scope.row.capacity ? `${scope.row.capacity}${scope.row.unit || ''}` : '') || '--'}}</div>
                </template>
              </tableForm>
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
        :option="{...resourceOption, column: resourceColumn}"
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
  </div>
</template>
<script lang="ts" setup name="productionCalendar">
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
import { Plus, Search, Edit, Delete, WarningFilled } from '@element-plus/icons-vue'

import useCommonStore from '@/store/common.js'
import { getStore } from '@/util/store.js'

import hourMinutePicker from './hourMinutePicker.vue'
import tableForm from '@/components/basic-assembly/tableForm.vue'

import { getModeList, addMode, getInfoById, editMode, delMode, getList, getResourceList, add, edit, del, getGroupList } from './api'

const { proxy } = getCurrentInstance()
const emit = defineEmits([])
const { t } = useI18n()
const commonStore = useCommonStore()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

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
    menuWidth: 80,
    menu: true,
    tableColumn: resourcesColumn.value
  }
})

const selectedRows = computed(() => {
  let temp = []
  if(rowData.value && rowData.value['resourceIds'] && rowData.value['resourceIds'].length > 0) {
    rowData.value['resourceIds'].filter(fit => {
      temp.push({
        id: fit.id
      })
    })
  }
  return temp
})

const modeLoading = ref(false)
const modelList = ref([])
const modeVisible = ref(false)
const modeTitle = ref('')
const modeData = ref(null)
const modeOption = ref({
  emptyBtn: false,
  submitLoading: false,
  formAlign: 'top',
  column: [
    {
      label: '名称',
      prop: 'name',
      rules: [{ required: true, message: '请输入名称', trigger: 'blur' }]
    },
    {
      label: '工作时间',
      prop: 'workingMode',
      formSlot: true
    }
  ]
})
const pickerVisible = ref({})

const tableLoading = ref(false)
const queryParams = ref({})
const tableData = ref([])
const tableOption = ref({
  page: false,
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
      label: '名称',
      prop: 'name',
    },
    {
      label: '模式',
      prop: 'workModeId',
      slot: true
    },
    {
      label: '开始时间',
      prop: 'beginTime',
    },
    {
      label: '结束时间',
      prop: 'endTime',
    },
    {
      label: '是否启用',
      prop: 'enabled',
      slot: true
    },
  ]
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const rowData = ref({})
const workDays = ref([
  {label: '星期日', value: 'SUNDAY'},
  {label: '星期一', value: 'MONDAY'},
  {label: '星期二', value: 'TUESDAY'},
  {label: '星期三', value: 'WEDNESDAY'},
  {label: '星期四', value: 'THURSDAY'},
  {label: '星期五', value: 'FRIDAY'},
  {label: '星期六', value: 'SATURDAY'},
])
const formOption = ref({
  emptyBtn: false,
  submitLoading: false,
  formAlign: 'top',
  column: [
    {
      label: '名称',
      prop: 'name',
      rules: [{ required: true, message: '请输入名称', trigger: 'blur' }],
      span: 12,
    },
    {
      label: '模式',
      prop: 'workModeId',
      type: 'select',
      dicData: [],
      props: {
        label: 'name',
        value: 'id'
      },
      span: 12,
    },
    {
      label: '开始时间',
      prop: 'beginTime',
      type: 'datePicker',
      datetype: 'datetime',
      span: 12,
    },
    {
      label: '结束时间',
      prop: 'endTime',
      type: 'datePicker',
      datetype: 'datetime',
      span: 12,
    },
    {
      label: '是否启用',
      prop: 'enabled',
      type: 'switch',
      defaultValue: false,
      span: 12,
    },
    {
      label: '优先级',
      prop: 'priority',
      type: 'inputNumber',
      min: 0,
      precision: 0,
      rules: [{ required: true, message: '请输入优先级', trigger: 'blur' }],
      span: 12,
    },
    {
      label: '工作日设置',
      prop: 'workDaySetting',
      formSlot: true
    },
    {
      label: '资源',
      prop: 'resourceIds',
      formSlot: true,
    }
  ],
})

const resourcesColumn = ref([
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
    searchSpan: 4,
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
    searchSpan: 4,
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

getModeListHandle()

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
      resourcesColumn.value.filter(col => {
        if(col.prop == 'resourceGroup') {
          col.dicData = list
        }
      })
    }
  })
}

function getModeListHandle () {
  modeLoading.value = true
  getModeList({ current: 1, size: 100 }).then(res => {
    if(res.data && res.data.code == 0) {
      modelList.value = res.data.data.records
    }
    modeLoading.value = false
  }).catch(e => {
    modeLoading.value = false
  })
}

function addEditMode (mode) {
  if(mode) {
    let temp = []
    if(mode.workingMode) {
      let arr = mode.workingMode.split(';')
      for(let i in arr) {
        if(arr[i].includes('-')) {
          let sd = arr[i].split('-')
          temp.push({
            start: sd[0],
            end: sd[1]
          })
        }
      }
    }
    modeData.value = {
      id: mode.id,
      name: mode.name,
      workingMode: temp
    }
    modeTitle.value = t(`aps.productionEngineering.productionCalendar.editMode`)
  }else{
    modeData.value = {
      workingMode: []
    }
    modeTitle.value = t(`aps.productionEngineering.productionCalendar.addMode`)
  }
  modeVisible.value = true
}

function addModeTimeHandle () {
  modeData.value['workingMode'].push({
    start: '',
    end: ''
  })
}

function deleteModeTime (time, index) {
  modeData.value['workingMode'].splice(index, 1)
}

function formatTime (obj, attr) {
  if(obj[attr]) {
    let str = ''
    for(let i in obj[attr]) {
      if(!isNaN(obj[attr][i])) {
        str += obj[attr][i]
      }
    }
    if(str.length > 0) {
      if(Number(str.charAt(0)) > 2) {
        str = ('0' + str)
      }
      let res = ''
      for(let k = 0; k < 5; k++) {
        if(k < 2) {
          if(k == 1) {
            if(Number(str.charAt(0)) == 2 && Number(str.charAt(1)) > 4) {
              res +=  '4'
            }else{
              res +=  str.charAt(k) || '0'
            }
          }else{
            res +=  str.charAt(k) || '0'
          }
        }else if(k == 2){
          res += ':'
        }else{
          if(Number(str.charAt(0)) == 2 && Number(str.charAt(1)) == 4) {
            res += '0'
          }else{
            if(k == 3 && Number(str.charAt(k-1)) > 5) {
              res += '5'
            }else{
              res += str.charAt(k-1) || '0'
            }
          }
        }
      }
      obj[attr] = res
    }else{
      obj[attr] = ''
    }
  }
}

function modeSubmit (form) {
  let validate = true
  let workingModeStrList = []
  if(modeData.value.workingMode && modeData.value.workingMode.length > 0) {
    modeData.value.workingMode.filter(it => {
      if(it.start && it.end) {
        workingModeStrList.push(it.start+'-'+it.end)
      }else{
        validate = false
      }
    })
  }
  if(validate) {
    let temp = {
      name: modeData.value.name,
      workingMode: workingModeStrList.join(';')
    }
    let fun = addMode
    if(modeData.value.id) {
      fun = editMode
      temp['id'] = modeData.value.id
    }
    modeOption.value.submitLoading = true
    fun(temp).then(res => {
      if(res.data && res.data.code == 0) {
        ElNotification.closeAll()
        ElNotification({
          title: t(`common.tip`),
          message: (modeData.value['id'] ? t(`aps.productionEngineering.productionCalendar.editMode`) : t(`aps.productionEngineering.productionCalendar.addMode`)) + t(`aps.productionEngineering.productionCalendar.success`),
          position: 'bottom-right',
          type: 'success',
        })
        modeOption.value.submitLoading = false
        modeClose()
        getModeListHandle()
      }
    }).catch(e => {
      modeOption.value.submitLoading = false
    })
  }
}

function modeClose () {
  modeVisible.value = false
  modeData.value = null
  modeTitle.value = ''
}

function setVisible (key, bool) {
  pickerVisible.value[key] = bool
}

function deleteMode (row) {
  if(row.id) {
    delMode(row.id).then(res => {
      if(res.data && res.data.code == 0) {
        ElNotification.closeAll()
        ElNotification({
          title: t(`common.tip`),
          message: t(`aps.productionEngineering.productionCalendar.delMode`) + t(`aps.productionEngineering.productionCalendar.success`),
          position: 'bottom-right',
          type: 'success',
        })
        getModeListHandle()
      }
    })
  }
}


function getListHandle (pageInfo?) {
  tableLoading.value = true
  let query = JSON.parse(JSON.stringify(queryParams.value))
  getList(Object.assign(query, { current: 1, size: 1000 })).then( res => {
    tableLoading.value = false
    if(res.data && res.data.code == 0 && res.data.data) {
      tableData.value = res.data.data.records
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
  formOption.value.column.filter(col => {
    if(col.prop == 'workModeId') {
      col.dicData = modelList
    }
  })
  let set = {}
  workDays.value.filter(it => {
    set[it.value] = false
  })
  if(row) {
    getInfoById(row.id).then(res => {
      if(res.data && res.data.code == 0) {
        rowData.value = JSON.parse(JSON.stringify(row))
        if(!rowData.value['workDaySetting']) {
          rowData.value['workDaySetting'] = set
        }
        rowData.value['resourceIds'] = res.data.data || []
        console.log(rowData.value)
        dialogTitle.value = t(`aps.productionEngineering.productionCalendar.edit`)
        dialogVisible.value = true
      }
    })
  }else{
    rowData.value = {
      enabled: false,
      priority: 1,
      workDaySetting: set,
      resourceIds: [],
    }
    dialogTitle.value = t(`aps.productionEngineering.productionCalendar.add`)
    dialogVisible.value = true
  }
}

function submitHandle (form) {
  let fun = add
  if(rowData.value['id']) {
    fun = edit
  }
  let subData = JSON.parse(JSON.stringify(rowData.value))
  if(subData.resourceIds && subData.resourceIds.length > 0) {
    let ids = []
    subData.resourceIds.filter(it => {
      ids.push(it.id)
    })
    subData.resourceIds = ids
  }
  formOption.value.submitLoading = true
  fun(subData).then(res => {
    if(res.data && res.data.code == 0) {
      ElNotification.closeAll()
      ElNotification({
        title: t(`common.tip`),
        message: (rowData.value['id'] ? t(`aps.productionEngineering.productionCalendar.edit`) : t(`aps.productionEngineering.productionCalendar.add`)) + t(`aps.productionEngineering.productionCalendar.success`),
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

function changeHandle (data) {
  let {index, prop, value} = data
  modeData.value.workingMode[index][prop] = value
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
  getResourceList({...resourceParams.value, ...tp}).then( res => {
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
  setResource(tp)
  resourceClose()
}

function setResource (data) {
  rowData.value['resourceIds'] = data
}
</script>
<style lang="scss" scoped>
.aps-productionEngineering-productionCalendar{
  width: 100%;
  height: 100%;
  padding: 0!important;
  display: flex;
  box-sizing: border-box;
  overflow: hidden;
  .left{
    width: 320px;
    height: 100%;
    border-right: 1px solid #EEEFF0;
    box-sizing: border-box;
    .title-box{
      display: flex;
      align-items: center;
      padding: 16px 24px;
      svg{
        width: 16px;
        height: 16px;
      }
      span{
        line-height: 21px;
        margin-left: 8px;
        font-size: 16px;
        color: #363B4C;
        @include SourceHanSansCN-Regular;
      }
    }
    .mode-tool{
      padding: 0 24px;
      ::v-deep(.el-button){
        min-width: 72px;
        height: 32px;
        font-size: 14px;
      }
    }
    .mode-list{
      padding: 16px 24px;
      height: calc(100% - 85px);
      box-sizing: border-box;
      overflow: hidden;
      .mode-list-item{
        min-height: 104px;
        border-radius: 4px;
        border: 1px solid #EEEFF0;
        .header{
          height: 36px;
          padding: 0 16px;
          background: #F5F6F7;
          border-radius: 4px 4px 0px 0px;
          display: flex;
          align-items: center;
          justify-content: space-between;
          box-sizing: border-box;
          .el-icon{
            cursor: pointer;
          }
          .slide-line{
            display: inline-block;
            width: 1px;
            height: 14px;
            background: #EEEFF0;
            margin: 0 8px;
          }
        }
        .body{
          display: flex;
          padding: 12px 16px;
          font-size: 14px;
          color: #6F7588;
          line-height: 20px;
          .label{
            word-break: keep-all;
          }
        }
      }
      .mode-list-item+.mode-list-item{
        margin-top: 16px;
      }
      &.loading{
        background-image: url("/jvs-ui-public/img/loading.gif");
        background-repeat: no-repeat;
        background-position: center;
        background-position: center;
        .mode-list-item{
          display: none;
        }
      }
    }
  }
  .right{
    width: calc(100% - 320px);
    height: 100%;
    overflow: hidden;
    .top{
      height: 68px;
      padding-left: 16px;
      padding-right: 24px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      .left-box{
        .el-button{
          min-width: 88px;
          height: 36px;
          font-size: 14px;
          &.el-button--primary.is-plain{
            background: #E4EDFF;
            color: #1E6FFF;
          }
          &.el-button--danger.is-plain{
            background: #FFE3E9;
            color: #FF194C;
          }
        }
      }
    }
    .bottom{
      height: calc(100% - 68px);
      box-sizing: border-box;
      overflow: hidden;
      .bottom-body{
        width: 100%;
        height: 100%;
        background: #fff;
        padding: 0 16px;
        box-sizing: border-box;
        .jvs-table{
          ::v-deep(.table-body-box){
            padding-top: 0;
          }
        }
      }
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
.working-mode-box{
  .working-mode-list{
    .working-mode-list-item{
      display: flex;
      align-items: center;
      .split-bar{
        width: 24px;
        text-align: center;
      }
      .el-input{
        width: 96px;
        &.error{
          ::v-deep(.el-input__wrapper){
            box-shadow: 0 0 0 1px var(--el-color-danger) inset!important;
          }
        }
      }
      .delete-icon-button{
        margin-left: 16px;
      }
    }
    .working-mode-list-item+.working-mode-list-item{
      margin-top: 8px;
    }
  }
}
.slot-table-form-box{
  flex: 1;
  height: calc(70vh - 382px); // 539
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
.dialog-page-box{
  height: calc(70vh - 48px);
  padding: 20px;
  box-sizing: border-box;
  overflow: hidden;
}
</style>