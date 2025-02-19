<template>
  <div :class="{'table-form': true, 'table-form-noteditable': !item.editable}">
    <jvs-table
      v-if="readyShow"
      :option="options"
      :page="page"
      :data="tableData"
      :index="true"
      :editable="item.editable"
      @on-load="getList"
    >
      <template #menu="scope">
        <div :style="'width: 100%;display: flex;justify-content: '+ option.menuAlign == 'left' ? 'left' : 'center' + ';'">
          <slot name="menuBtn" :row="scope.row" :index="scope.index"></slot>
          <jvs-button v-if="!item.editable && item.delBtn" size="small" text @click="delRowHandle(scope.row, scope.index)">删除</jvs-button>
        </div>
      </template>
      <template v-for="(titem, tindex) in options.tableColumn" #[slotItem(titem.prop)]="scope" :key="item.prop+scope.index+'-'+titem.prop+'node'+tindex">
        <div>
          <div v-if="titem.text && titem.text.label && scope.row[titem.prop] == titem.text.value" :key="titem.prop+'text'">
            <span>{{titem.text.label}}</span>
          </div>
          <div v-if="titem.needSlot !== true && !(titem.text && titem.text.label && scope.row[titem.prop] == titem.text.value) && displayByBind(titem, scope.row)" :key="item.prop+scope.index+'-'+titem.prop+'item'">
            <el-form
              v-if="!(scope.row === null || scope.row === undefined)"
              :model="scope.row"
              :ref="(formRef || 'ruleForm')+(scope.index*options.tableColumn.length+tindex)"
              class="demo-dynamic"
              size='small'
              @submit.prevent
            >
              <el-form-item label="" :prop='titem.prop' :rules='requireExpressHandle(titem.rules, titem, tindex, scope.row, scope.index, options.tableColumn.length)' style="margin-bottom: 0;">
                <slot v-if="titem.formSlot" :name="titem.prop+'TableFormColumn'" :index="scope.index" :row="scope.row"></slot>
                <tableFormItem
                  v-else
                  :tableRowAIndex="scope.index"
                  :style="'justify-content:'+ (options.align == 'center' ? 'center' : 'flex-start')"
                  :form="scope.row"
                  :item="{...titem, disabledControl: disabledExpressHandle(titem, scope.row, scope.row)}"
                  :roleOption="roleOption"
                  :userList="userList"
                  :departmentList="departmentList"
                  :postList="postList"
                  :designId="designId"
                  :isView="isView"
                  :execsList="execsList"
                  :jvsAppId="jvsAppId"
                  :originForm="forms"
                  :dataTriggerFresh="dataTriggerFresh"
                  :changeRandom="changeRandom"
                  :changeDomItem="changeDomItem"
                  @formChange="formChange"
                  @reInitData="reInitData"
                  @validateHandle="validateHandle"
                ></tableFormItem>
              </el-form-item>
            </el-form>
          </div>
          <div v-if="displayByBind(titem, scope.row) && !(titem.text && titem.text.label && scope.row[titem.prop] == titem.text.value) && titem.needSlot === true" :key="titem.prop+'needslotitem'">
            <slot :name="titem.prop+'Item'" :row="scope.row" :index="scope.index"></slot>
          </div>
        </div>
      </template>
    </jvs-table>
  </div>
</template>
<script lang="ts" setup name="table-Form">
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
import moment from 'moment'

import useCommonStore from '@/store/common.js'
import { getDefaultData } from '@/util/common.js'
import { areaList } from '@/const/chinaArea.js'

import tableFormItem from '@/components/basic-assembly/formitem.vue'

import { getSelectData, sendMyRequire } from '@/components/api'

const { proxy } = getCurrentInstance()
const emit = defineEmits(['formChange', 'setTable', 'reInitData', 'resetAddIndex'])
const { t } = useI18n()

const commonStore = useCommonStore()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

const props =  defineProps({
  formRef: {
    type: String,
    default: 'ruleForm'
  },
  item: {
    type: Object
  },
  option: {
    type: Object
  },
  data: {
    type: Array,
    default: () => {
      return []
    }
  },
  originOption: {
    type: Object
  },
  defalutSet: {
    type: Object
  },
  rowData: {
    type: Object
  },
  // 用户列表
  userList : {
    type: Array,
    default: () => {
      return []
    }
  },
  // 角色列表
  roleOption: {
    type: Array,
    default: () => {
      return []
    }
  },
  // 部门列表
  departmentList: {
    type: Array,
    default: () => {
      return []
    }
  },
  // 岗位列表
  postList: {
    type: Array,
    default: () => {
      return []
    }
  },
  resetRadom: {
    type: Number
  },
  designId: {
    type: String
  },
  forms: {
    type: Object
  },
  originForm: {
    type: Object
  },
  dataModelId: {
    type: String
  },
  changeRandom: {
    type: Number
  },
  changeDomItem: {
    type: Object
  },
  isView: {
    type: Boolean
  },
  execsList: {
    type: Array
  },
  jvsAppId:  {
    type: String
  },
  dataTriggerFresh: {
    type: Number
  },
  tableFormAddHandleIndex: {
    type: Number
  }
})

const options = computed(() => {
  let temp = props.option // 不可深拷贝，会丢失函数属性字段(自定义正则校验)
  if(temp.column && !temp.tableColumn) {
    temp.tableColumn = temp.column
  }
  if(props.item.editable) {
    for(let i in temp.tableColumn) {
      temp.tableColumn[i].slot = true
    }
  }
  if(!temp.column) {
    temp.column = temp.tableColumn
  }
  temp.tableColumn = displayExpressHandle(temp.tableColumn)
  temp.column = temp.tableColumn
  if(props.item.editable) {
    if(props.item.delBtn) {
      temp.menu = true
    }else{
      temp.menu = false
    }
  }else{
    if(props.item.editBtn || props.item.viewBtn || props.item.delBtn) {
      temp.menu = true
    }else if(temp.menu === true) {
      temp.menu = true
    }else{
      temp.menu = false
    }
  }
  if(props.item.page && props.item.editable) {
    temp.page = true
  }else{
    temp.page = false
  }
  if(props.item.menuFix) {
    temp.menuFix = props.item.menuFix
  }
  if(temp.menuAlign != 'left') {
    temp.menuAlign = 'center'
  }
  temp.indexLabel = '序号'
  temp.border = false
  if(props.item && props.item.iconBtn) {
    temp.menuWidth = '20px'
    temp.menuText = ''
    temp.indexWidth = '44'
  }
  return temp
})

const tableData = ref([])
const page = ref({
  total: 0, // 总页数
  currentPage: 1, // 当前页数
  pageSize: 20, // 每页显示多少条
  pageSizes: [20, 50, 100, 200, 500, 1000]
})
const openType = ref('') // 弹框类型
const rowIndex = ref(-1) // 行数据index
const loadTimes = ref(-1) // 加载次数
const initData = ref([])
const readyShow = ref(false)
const reInitIndex = ref(-1)

createHandle()

function slotItem (prop) {
  return prop
}

function createHandle () {
  init()
}

async function init () {
  let deptBool = false
  let roleBool = false
  let postBool = false
  for(let i in options.value.tableColumn) {
    if(options.value.tableColumn[i].type == 'department') {
      deptBool = true
    }
    if(options.value.tableColumn[i].type == 'role') {
      roleBool = true
    }
    if(options.value.tableColumn[i].type == 'post') {
      postBool = true
    }
    // 接口字典
    if(options.value.tableColumn[i].dicUrl) {
      await getSelectData(options.value.tableColumn[i].dicUrl).then(res => {
        if(res.data.code == 0) {
            for(let sitem in res.data.data){
            if(typeof sitem == 'string') {
              options.value.tableColumn[i].dicData.push({
                label: res.data.data[sitem],
                value: res.data.data[sitem]
              })
            }else{
              options.value.tableColumn[i].dicData.push({
                label: res.data.data[sitem][options.value.tableColumn[i].props.label ? options.value.tableColumn[i].props.label : 'label'],
                value: res.data.data[sitem][options.value.tableColumn[i].props.value ? options.value.tableColumn[i].props.value : 'value']
              })
            }
          }
        }
      })
    }
    // 级联选择类
    if(options.value.tableColumn[i].type == 'cascader' && options.value.tableColumn[i].dictName) {
      await getClassifyDictTree(options.value.tableColumn[i].dictName).then(res => {
        if(res.data.code == 0 && res.data.data && res.data.data.children) {
          options.value.tableColumn[i].dicData = res.data.data.children
          options.value.tableColumn[i].emitKey = 'uniqueName'
          options.value.tableColumn[i].props = {
            label: 'name',
            value: 'uniqueName',
            children: 'children'
          }
        }
      })
    }
    // 配置字典
    if(options.value.tableColumn[i].dicData) {
      let temp = []
      let bool = false
      for(let j in options.value.tableColumn[i].dicData) {
        if(typeof options.value.tableColumn[i].dicData[j] == 'string') {
          bool = true
          temp.push({
            label: options.value.tableColumn[i].dicData[j],
            value: options.value.tableColumn[i].dicData[j]
          })
        }
      }
      if(bool) {
        options.value.tableColumn[i].dicData = temp
      }
    }
    // 上传
    if(['imageUpload', 'fileUpload'].indexOf(options.value.tableColumn[i].type) > -1) {
      options.value.tableColumn[i].parent = props.item
    }
    // 地区回显
    if(options.value.tableColumn[i].type == 'chinaArea') {
      options.value.tableColumn[i].dicData = areaList
      options.value.tableColumn[i].props = {
        label: 'name',
        value: options.value.tableColumn[i].emitKey ? options.value.tableColumn[i].emitKey : 'code',
        children: 'children'
      }
    }
  }
  readyShow.value = true
}

// 下拉选择change
function valueChange (item, row) {
  emit((item.prop+'Change'), {
    item: item,
    row: row
  })
}

// 根据绑定字段值决定显隐
function displayByBind (item, row) {
  let temp = true
  if(item.display) {
    if(typeof item.display.value == 'object' && (item.display.value instanceof Array)) {
      if(item.display.value.indexOf(row[item.display.key]) > -1) {
        temp = true
      }else{
        temp = false
      }
    }else{
      if(row[item.display.key] == item.display.value) {
        temp = true
      }else{
        temp = false
      }
    }
  }else{
    temp = true
  }
  return temp
}

async function formChange (form, item) {
  if(dynamicRefs.value[props.formRef || 'ruleForm'] && dynamicRefs.value[props.formRef || 'ruleForm'].length > 0) {
    for(let r in dynamicRefs.value[props.formRef || 'ruleForm']) {
      dynamicRefs.value[props.formRef || 'ruleForm'][r].validate( (valid) => {
        // 
      })
    }
  }
  emit('formChange', form, item)
}

// 表格数据api来源
function getList () {
  if(props.item.tableEchoRequest && !props.item.editable) {
    // 请求接口
    let tp = JSON.parse(JSON.stringify(props.item.tableEchoRequest))
    if(commonStore.labelValue && commonStore.labelValue.requestContentType) {
      tp.requestContentType = commonStore.labelValue.requestContentType[tp.requestContentType]
    }
    if(tp && tp.url) {
      let query = {}
      if(props.item.page) {
        query['current'] = page.value.currentPage
        query['size'] = page.value.pageSize
      }
      if(props.rowData) {
        query = Object.assign(query, props.rowData)
      }
      sendMyRequire(tp, query).then(res => {
        if(res.data.code == 0) {
          if(props.item.page) {
            tableData.value = res.data.data.records
            page.value.currentPage = res.data.data.current
            page.value.total = res.data.data.total
            initData.value = JSON.parse(JSON.stringify(res.data.data.records))
          }else{
            tableData.value = res.data.data
            initData.value = JSON.parse(JSON.stringify(res.data.data))
          }
        }
      })
    }
  }else{
    if(loadTimes.value == -1 && props.data) {
      tableData.value = JSON.parse(JSON.stringify(props.data))
      initData.value = JSON.parse(JSON.stringify(props.data))
    }
  }
  loadTimes.value++
}

// 删除
function delRowHandle (row, index) {
  if(props.item.editable) {
    tableData.value.splice(index, 1)
  }else{
    if(props.item.tableDeleteRequest && props.item.tableDeleteRequest.url) {
      // 请求接口
      let tp = JSON.parse(JSON.stringify(props.item.tableDeleteRequest))
      if(commonStore.labelValue.requestContentType) {
        tp.requestContentType = commonStore.labelValue.requestContentType[tp.requestContentType]
      }
      if(tp && tp.url) {
        sendMyRequire(tp, row).then(res => {
          if(res.data.code == 0) {
            getList()
          }
        })
      }
    }else{
      tableData.value.splice(index, 1)
    }
  }
}

function reset () {
  tableData.value = props.data
  page.value = {
    total: 0, // 总页数
    currentPage: 1, // 当前页数
    pageSize: 20, // 每页显示多少条
    pageSizes: [20, 50, 100, 200, 500, 1000]
  }
}

async function getDataByFilter () {
  let tprop = []
  for(let i in props.item.tableColumn) {
    tprop.push(props.item.tableColumn[i].prop)
  }
  let postData = {
    fieldList: tprop,
  }
  let nomptyValue = true
  if(props.item.dataFilterGroupList && props.item.dataFilterGroupList.length > 0) {
    postData['groupConditions'] = []
    for(let gi in props.item.dataFilterGroupList) {
      let tgarr = []
      for(let df in props.item.dataFilterGroupList[gi]) {
        let dfit = {
          enabledQueryTypes: props.item.dataFilterGroupList[gi][df].enabledQueryTypes,
          fieldKey: props.item.dataFilterGroupList[gi][df].fieldKey,
        }
        if(['cust', 'role', 'department', 'job', 'user'].indexOf(props.item.dataFilterGroupList[gi][df].type) > -1) {
          dfit['value'] = props.item.dataFilterGroupList[gi][df].value
        }else{
          let tval = null
          if(typeof props.item.dataFilterGroupList[gi][df].value == 'object' && (props.item.dataFilterGroupList[gi][df].value instanceof Array)) {
            if(props.item.dataFilterGroupList[gi][df].value.length > 0) {
              if(props.item.dataFilterGroupList[gi][df].value.length == 1) {
                if(props.item.parentType && props.item.parentKey && props.originForm) {
                  tval = props.originForm[props.item.dataFilterGroupList[gi][df].value[0]]
                }
              }else{
                let pdomList = []
                let pk = props.item.dataFilterGroupList[gi][df].value.slice(0, props.item.dataFilterGroupList[gi][df].value.length - 1)
                findParentList(props.originOption.column, pk.join('.'), pdomList)
                if(pdomList.length > 0 && pdomList[0].type == 'tab' && pdomList[0].detachData) {
                  if(pdomList.length >  1) {
                    if(pdomList[1].prop) {
                      tval = props.originForm[pdomList[1].prop]
                      if(tval) {
                        tval = tval[props.item.dataFilterGroupList[gi][df].value[props.item.dataFilterGroupList[gi][df].value.length-1]]
                      }
                    }else{
                      tval = props.originForm[props.item.dataFilterGroupList[gi][df].value[props.item.dataFilterGroupList[gi][df].value.length-1]]
                    }
                  }
                }else{
                  tval = props.originForm
                  props.item.dataFilterGroupList[gi][df].value.filter(vc => {
                    if(tval && tval[vc]) {
                      tval = tval[vc]
                    }else{
                      tval = null
                    }
                  })
                }
              }
            }
          }else{
            tval = props.originForm[props.item.dataFilterGroupList[gi][df].value]
          }
          if((tval == undefined || tval == null || tval == '') &&  props.item.dataFilterGroupList[gi][df].enabledQueryTypes != 'isNull') {
            nomptyValue = false
          }else{
            dfit['value'] = tval
          }
        }
        tgarr.push(dfit)
      }
      postData['groupConditions'].push(tgarr)
    }
  }else{
    postData['conditions'] = []
    if(props.item.dataFilterList) {
      for(let df in props.item.dataFilterList) {
        let dfit = {
          enabledQueryTypes: props.item.dataFilterList[df].enabledQueryTypes,
          fieldKey: props.item.dataFilterList[df].fieldKey,
        }
        if(props.item.dataFilterList[df].type == 'cust') {
          dfit['value'] = props.item.dataFilterList[df].value
        }else{
          dfit['value'] = props.forms[props.item.dataFilterList[df].value]
          if((dfit['value'] == undefined || dfit['value'] == null || dfit['value'] == '') && props.item.dataFilterList[df].enabledQueryTypes != 'isNull' ) {
            nomptyValue = false
          }
        }
        postData['conditions'].push(dfit)
      }
    }
  }
  if(nomptyValue) {
    // 表格的筛选
  }
}

function reInitData (prop, parentKey, index, tableType) {
  reInitIndex.value = index
  emit('reInitData', prop, parentKey, index, tableType)
  emit('resetAddIndex')
}

// 表达式控制显示
function disabledExpressHandle (item, row, form) {
  let bool = false
  let formStr = 'row' // 表单值参数名
  if(item.disabledExpress && item.disabledExpress.length > 0) {
    let list = item.disabledExpress
    let temp = []
    for(let i in list) {
      let prop = (formStr + '.') // 控制字段名
      if(list[i].parent && list[i].parent.length > 0) {
        prop += list[i].parent.join('.')
        prop += '.'
      }
      prop += list[i].prop

      // 校验层级表单值是否为undefined
      let tpr = ''
      let exValidate = true
      tpr += formStr
      for(let p in list[i].parent) {
        tpr += ('.' + list[i].parent[p])
        if(eval(tpr) == undefined) {
          exValidate = false
          break;
        }
      }
      if(exValidate) {
        let tv = JSON.stringify(list[i].value.split(','))
        tv += '.indexOf( '
        let tp = (tv + prop + ' + ' + "''" + ')')
        tp += (' > -1')
        temp.push(tp)
      }
    }
    if(temp.length > 0) {
      if(eval(temp.join(` ${item.showOperator || '||'} `))) {
        bool = true
      }
    }
  }else{
    bool = false
  }
  if(bool && !item.disabled && item.disabledEmpty) {
    form[item.prop] = ''
  }
  if(props.item.disabled) {
    bool = true
  }
  return bool
}

function dealInitData (list) {
  for(let i in list) {
    options.value.tableColumn.filter(tit => {
      if(list[i][tit.prop] === null || list[i][tit.prop]  === undefined) {
        switch(tit.type) {
          case 'switch':
            list[i][tit.prop] = false;
            break;
          case 'select':
            if(tit.multiple) {
              list[i][tit.prop] = [];
            }else{
              list[i][tit.prop] = '';
            }
            break;
          case 'checkbox':
            list[i][tit.prop] = [];
            break;
          case 'datePicker':
            if(['dates', 'daterange', 'monthrange', 'datetimerange'].indexOf(tit.datetype) > -1) {
              list[i][tit.prop] = []
            }else{
              list[i][tit.prop] = ''
            }
            break;
          case 'timePicker':
            if(tit.isrange) {
              list[i][tit.prop] = []
            }else{
              list[i][tit.prop] = ''
            }
            break;
          case 'imageUpload':
          case 'fileUpload':
            if(tit.limit > 1) {
              list[i][tit.prop] = []
            }else{
              list[i][tit.prop] = ''
            }
            break;
          default: list[i][tit.prop] = '';break;
        }
      }
    })
  }
}

// 表达式控制必填校验
function requireExpressHandle (rules, item, index, row, rowIndex, length) {
  if(item.requireExpress && item.requireExpress.length > 0) {
    let bool = false
    let formStr = `row`
    let list = item.requireExpress
    let temp = []
    for(let i in list) {
      let prop = (formStr + '.') // 控制字段名
      if(list[i].parent && list[i].parent.length > 0) {
        prop += list[i].parent.join('.')
        prop += '.'
      }
      prop += list[i].prop
      // 校验层级表单值是否为undefined
      let tpr = ''
      let exValidate = true
      tpr += formStr
      for(let p in list[i].parent) {
        tpr += ('.' + list[i].parent[p])
        if(eval(tpr) == undefined) {
          exValidate = false
          break;
        }
      }
      if(exValidate) {
        let tv = JSON.stringify(list[i].value.split(','))
        tv += '.indexOf( '
        let tp = (tv + prop + ' + ' + "''" + ')')
        tp += (' > -1')
        temp.push(tp)
      }
    }
    if(temp.length > 0) {
      if(eval(temp.join(` ${item.showOperator || '||'} `))) {
        bool = true
      }
    }
    if(bool) {
      if(rules && rules.length > 0) {
        rules[0]['required'] = true
        if(dynamicRefs.value[(props.formRef || 'ruleForm')+(rowIndex * length + index)] && dynamicRefs.value[(props.formRef || 'ruleForm')+(rowIndex * length + index)].length > 0) {
          dynamicRefs.value[(props.formRef || 'ruleForm')+(rowIndex * length + index)][0].validateField(item.prop)
        }
      }
    }else{
      if(rules && rules.length > 0) {
        rules[0]['required'] = false
        if(dynamicRefs.value[(props.formRef || 'ruleForm')+(rowIndex * length + index)] && dynamicRefs.value[(props.formRef || 'ruleForm')+(rowIndex * length + index)].length > 0) {
          if(rules.length == 1) {
            dynamicRefs.value[(props.formRef || 'ruleForm')+(rowIndex * length + index)][0].clearValidate(item.prop)
          }else{
            dynamicRefs.value[(props.formRef || 'ruleForm')+(rowIndex * length + index)][0].validateField(item.prop)
          }
        }
      }
    }
  }
  return rules
}

// 表达式控制显隐
function displayExpressHandle (tableColumn) {
  let tpList = []
  let originForm = props.originForm || props.forms
  tableColumn.filter(item => {
    let bool = false
    let formStr = 'originForm' // 表单值参数名
    if(item.displayExpress && item.displayExpress.length > 0) {
      let list = item.displayExpress
      let temp = []
      let needValidateSpecial = false // 是否单独校验tab项的值
      for(let i in list) {
        let prop = (formStr + '.') // 控制字段名
        if(list[i].parent && list[i].parent.length > 0) {
          let pdomList = []
          findParentList(props.originOption.column, list[i].parent.join('.'), pdomList)
          if(pdomList.length > 0 && pdomList[0].type == 'tab' && pdomList[0].detachData) {
            if(pdomList.length >  1) {
              if(pdomList[1].prop) {
                prop += (pdomList[1].prop + '.')
                needValidateSpecial = true
              }
            }
          }else{
            prop += list[i].parent.join('.')
            prop += '.'
          }
        }
        prop += list[i].prop
        // 校验层级表单值是否为undefined
        let tpr = ''
        let exValidate = true
        // 存在父级且需要父级数据
        if(prop.split('.').length > 3) {
          if(needValidateSpecial) {
            let pt = []
            let pl = prop.split('.')
            pt = pl.slice(2, pl.length-1)
            tpr += formStr
            for(let p in pt) {
              tpr += ('.' + pt[p])
              if(eval(tpr) == undefined) {
                exValidate = false
                break;
              }
            }
          }else{
            tpr += formStr
            for(let p in list[i].parent) {
              tpr += ('.' + list[i].parent[p])
              if(eval(tpr) == undefined) {
                exValidate = false
                break;
              }
            }
          }
        }
        if(exValidate) {
          let tv = JSON.stringify(list[i].value.split(','))
          tv += '.indexOf( '
          let tp = (tv + prop + ' + ' + "''" + ')')
          tp += (' > -1')
          temp.push(tp)
        }
      }
      if(temp.length > 0) {
        if(eval(temp.join(` ${item.showOperator || '||'} `))) {
          bool = true
        }
      }
    }else{
      bool = true
    }
    if(bool) {
      tpList.push(item)
    }
  })
  return tpList
}

function validateHandle (data) {
  let type = data.type
  let item = data.item
  nextTick(() => {
    for(let index in tableData.value) {
      for(let tindex in options.value.tableColumn) {
        if(dynamicRefs.value[(props.formRef || 'ruleForm')+(Number(index)*options.value.tableColumn.length+Number(tindex))]) {
          if(type == 'clear') {
            dynamicRefs.value[(props.formRef || 'ruleForm')+(Number(index)*options.value.tableColumn.length+Number(tindex))][0].clearValidate(item.prop)
          }else{
            dynamicRefs.value[(props.formRef || 'ruleForm')+(Number(index)*options.value.tableColumn.length+Number(tindex))][0].validateField(item.prop)
          }
        }
      }
    }
  })
}

// 获取所有父节点信息
function findParentList (list, key, result) {
  if(list && list.length > 0) {
    list.filter(item => {
      if(key && item.parentKey == key) {
        item.parentDom.filter(ppit => {
          result.push(ppit)
        })
      }
      if(['tab', 'step'].indexOf(item.type) > -1) {
        for(let j in item.column) {
          if(item.column[j] && item.column[j].length > 0) {
            findParentList(item.column[j], key, result)
          }
        }
      }
    })
  }
}

watch(() => tableData.value, (newVal, oldVal) => {
  emit('setTable', newVal)
})

watch(() => props.resetRadom, (newVal, oldVal) => {
  if(newVal > -1) {
    reset()
  }
})

watch(() => props.data, (newVal, oldVal) => {
  if(props.item.editable) {
    // 优化👌 表格联动或公式返回操作的一行
    if(newVal && newVal.length == 1 && newVal[0]) {
      if(!tableData.value || tableData.value.length == 0) {
        tableData.value = [ {} ]
      }
      if(props.forms[props.item.prop+'_line'] && props.forms[props.item.prop+'_line'].length > 0) {
        for(let i in props.forms[props.item.prop+'_line'][0]) {
          tableData.value[reInitIndex.value > -1 ? reInitIndex.value : (tableData.value.length- 1)][i] = props.forms[props.item.prop+'_line'][0][i]
        }
        emit('setTable', tableData.value)
      }else{
        tableData.value = props.data
      }
    }else{
      tableData.value = props.data
    }
    dealInitData(tableData.value)
  }else{
    tableData.value = props.data
  }
})

watch(() => props.changeRandom, (newVal, oldVal) => {
  if(newVal > -1 && props.item.formId && !props.isView) {
    let bool = true
    if(props.changeDomItem) {
      // 本身子组件触发不请求
      if(props.changeDomItem.parentType == props.item.type) {
        let pks = props.changeDomItem.parentKey.split('.')
        if(pks[pks.length - 1] == props.item.prop) {
          bool = false
        }
      }
      // 触发组件不作为条件的不请求
      if(props.item.dataFilterList && props.item.dataFilterList.length > 0) {
        let pool = false
        props.item.dataFilterList.filter(dit => {
          if(dit.type == 'prop') {
            if(dit.value == props.changeDomItem.prop) {
              pool = true
            }
          }
        })
        if(!pool) {
          bool = false
        }
      }else{
        bool = false
      }
      if(props.item.dataFilterGroupList && props.item.dataFilterGroupList.length > 0) {
        bool = true
        let pool = false
        for(let i  in props.item.dataFilterGroupList) {
          if(props.item.dataFilterGroupList[i].length > 0) {
            props.item.dataFilterGroupList[i].filter(dgit => {
              if(typeof dgit.value == 'string') {
                if(dgit.value == props.changeDomItem.prop) {
                  pool = true
                }
              }else{
                if(dgit.value instanceof Array) {
                  if(dgit.value.join('.') == (props.changeDomItem.parentKey ? `${props.changeDomItem.parentKey}.${props.changeDomItem.prop}` : props.changeDomItem.prop)) {
                    pool = true
                  }
                }
              }
            })
          }
        }
        if(!pool) {
          bool = false
        }
      }else{
        if(props.item.dataFilterList && props.item.dataFilterList.length > 0 && bool) {
          bool = true
        }else{
          bool = false
        }
      }
    }
    if(bool) {
      getDataByFilter()
    }
  }
})

watch(() => props.tableFormAddHandleIndex, (newVal, oldVal) => {
  if(newVal > -1) {
    reInitIndex.value = newVal
  }
})
</script>
<style lang="scss" scoped>
$jvsTableFormRowHeight: 32px;
.table-form{
  .jvs-table{
    ::v-deep(.table-body-box){
      .el-table__body-wrapper{
        .el-form-item{
          .el-form-item__content{
            line-height: $jvsTableFormRowHeight;
            .jvs-form-item{
              min-height: $jvsTableFormRowHeight;
              .el-input{
                height: $jvsTableFormRowHeight;
              }
            }
            .el-form-item__error{
              display: none;
            }
          }
        }
      }
    }
  }
}
</style>