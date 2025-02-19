<template>
  <el-form
    :model="formDatas"
    :ref="el => dynamicRefMap(el, refs || defalutSet.refs) "
    :option="option || defalutSet.option"
    :class="{'jvs-form': true, 'jvs-form-autoflexable': (option.labelWidth == 'auto' || option.labelwidth == 'auto' || option.formAuto ), 'jvs-form-transparent': option.useElStyle}"
    :size="commonStore.theme.form.size || option.size || option.formsize || 'small'"
    :inline="option.inline || defalutSet.option.inline"
    :label-position="option.formAlign || defalutSet.option.formAlign"
    :label-width="option.labelWidth || defalutSet.option.labelWidth"
    :disabled='option.disabled'
    :validate-on-rule-change="false"
    @submit.prevent
  >
    <slot name="formTop"></slot>
    <el-row style="width:100%;">
      <template v-for="item in option.column">
        <el-col
          :key="['childrenForm', 'connectForm'].indexOf(item.type) > -1 ? ('children-form' + item.prop) : item.prop"
          :span="isSearch==true?(item.searchSpan || option.searchSpan || 24):(item.span || option.span || 24)"
          v-show="item.display == false ? item.display : true"
          :class="[item.type && ('form-column-'+item.type), item.hideLabel && 'no-label-form-item']"
          v-if="displayExpressHandle(item)"
        >
          <el-form-item
            :class='{
              "form-item-no-label": ( (!item.label && item.type != "tab") || (["tableForm","divider","p","section"].indexOf(item.type) > -1) ),
              "form-item-no-label-tab": (!item.label && item.type == "tab"),
              "reportTable-item": item.type == "reportTable",
              "before-append-item": item.beforeSlot,
              "form-item-no-label-nopadding": (!item.label && ["childrenForm","connectForm"].indexOf(item.type) > -1),
              "slot-label-item": item.labelSlot
            }'
            :label='item.type == "tableForm" ? (item.editable ? item.label : "") : item.label'
            :prop="item.prop"
            v-if="(item.prop !== freshDom) && (item.type !='title' && (!item.children || item.children.length == 0) || item.type == 'formbox') && (item.permisionFlag ? permissionsList.indexOf(item.permisionFlag) > -1 : true) && (item.display == false ? item.display : true)"
            :rules="requireExpressHandle(item.rules, item)"
          >
            <template v-if="item.labelSlot" #label>
              <span class="slot-label">
                <slot :name="item.prop+'Label'"></slot>
              </span>
            </template>
            <span v-if="item.beforeSlot" class="before-append-content">
              <slot :name="item.prop+'Before'"></slot>
            </span>
            <FormItem
              v-if="!item.formSlot && !item.appendSlot"
              :form="formDatas"
              :item="{...item, disabledControl: disabledExpressHandle(item, 'formDatas')}"
              :originOption="option"
              :defalutSet="defalutSet"
              @formChange="formChange"
              :roleOption="roleOption"
              :userList="userList"
              :departmentList="departmentList"
              :postList="postList"
              :rowData="rowData"
              :resetRadom="resetRadom"
              :designId="designId"
              :dataModelId="dataModelId"
              :changeRandom="changeRandom"
              :changeDomItem="changeDomItem"
              :isView="isView"
              :execsList="execsList"
              :jvsAppId="jvsAppId"
              :dataTriggerFresh="dataTriggerFresh"
              @validateHandle="validateHandle"
              @reInitData="reInitData"
            />
            <!-- 自定义列插槽 -->
            <div v-if="item.formSlot" class="form-item-slot">
              <slot :name="item.prop+'Form'" :item="item"></slot>
              <!-- 右侧提示 -->
              <el-tooltip
                v-if="item.tips && item.tips.position == 'right' && item.tips.text"
                class="form-item-tooltip"
                effect="dark"
                :content="item.tips.text"
                placement="top"
              >
                <i class="el-icon-question"></i>
              </el-tooltip>
            </div>
            <!-- 后置插槽 -->
            <div v-if="item.appendSlot">
              <span class="form-append">
                <FormItem
                  v-if="!item.formSlot"
                  :form="formDatas"
                  :item="{...item, disabledControl: disabledExpressHandle(item, 'formDatas')}"
                  :originOption="option"
                  :defalutSet="defalutSet"
                  @formChange="formChange"
                  :roleOption="roleOption"
                  :userList="userList"
                  :departmentList="departmentList"
                  :postList="postList"
                  :rowData="rowData"
                  :resetRadom="resetRadom"
                  :designId="designId"
                  :dataModelId="dataModelId"
                  :changeRandom="changeRandom"
                  :changeDomItem="changeDomItem"
                  :isView="isView"
                  :execsList="execsList"
                  :jvsAppId="jvsAppId"
                  :dataTriggerFresh="dataTriggerFresh"
                  @validateHandle="validateHandle"
                  @reInitData="reInitData"
                >
                  <template #[slotAppendItem(item.prop)]>
                    <slot :name="item.prop+'Append'"></slot>
                  </template>
                </FormItem>
              </span>
            </div>
            <!-- 换行提示 -->
            <el-row
              v-if="item.tips && item.tips.position == 'bottom'"
              class="form-item-tips"
            >
              <span v-html="item.tips.text"></span>
            </el-row>
          </el-form-item>
          <h5 v-if="item.type == 'title'">{{item.label}}</h5>
          <!-- 子表单项 -->
          <el-row v-if="item.type != 'formbox' && item.children && item.children.length > 0 && displayExpressHandle(item)" style="display:flex;flex-wrap:wrap;">
            <el-form-item
              :label="item.label"
              :prop="item.prop"
              :rules="requireExpressHandle(item.rules, item)"
              v-if="(item.prop !== freshDom) && (item.display == false ? item.display : true)"
              :class='(!item.label || (["tableForm","divider","p","tab","section"].indexOf(item.type) > -1))? "form-item-no-label" : ""'
              style="width:100%;"
            >
              <FormItem
                v-if="!item.formSlot"
                :form="formDatas"
                :item="{...item, disabledControl: disabledExpressHandle(item, 'formDatas')}"
                :originOption="option"
                :defalutSet="defalutSet"
                @formChange="formChange"
                :roleOption="roleOption"
                :userList="userList"
                :departmentList="departmentList"
                :postList="postList"
                :rowData="rowData"
                :resetRadom="resetRadom"
                :designId="designId"
                :dataModelId="dataModelId"
                :changeRandom="changeRandom"
                :changeDomItem="changeDomItem"
                :isView="isView"
                :execsList="execsList"
                :jvsAppId="jvsAppId"
                :dataTriggerFresh="dataTriggerFresh"
                @validateHandle="validateHandle"
                @reInitData="reInitData"
              >
                <!-- 后置插槽 -->
                <template v-if="item.appendSlot" #[slotAppendItem(item.prop)]>
                  <slot :name="item.prop+'Append'"></slot>
                </template>
              </FormItem>
              <!-- 自定义列插槽 -->
              <div v-if="item.formSlot">
                <slot :name="item.prop+'Form'"></slot>
                <!-- 右侧提示 -->
                <el-tooltip
                  v-if="item.tips && item.tips.position == 'right' && item.tips.text"
                  class="form-item-tooltip"
                  effect="dark"
                  :content="item.tips.text"
                  placement="top"
                >
                  <i class="el-icon-question"></i>
                </el-tooltip>
              </div>
              <!-- 换行提示 -->
              <el-row
                v-if="item.tips && item.tips.position == 'bottom'"
                class="form-item-tips"
              >
                <span v-html="item.tips.text"></span>
              </el-row>
            </el-form-item>
            <template v-for="it in item.children">
              <el-col
                :key="it.prop"
                :span="isSearch==true?(it.searchSpan || option.searchSpan || 24):(it.span || option.span || 24)"
                v-if="linkbindHandle(formDatas[item.prop], it.linkbind) && displayExpressHandle(it)"
              >
                <el-form-item
                  :label="it.label"
                  :prop="it.prop"
                  v-if="(it.prop !== freshDom) && (it.permisionFlag ? permissionsList.indexOf(it.permisionFlag) > -1 : true) && (!option.isSearch || (option.isSearch && it.search == true)) && (it.display == false ? it.display : true)"
                  :rules="requireExpressHandle(it.rules, it)"
                  :class='{
                    "form-item-no-label": (!it.label || (["tableForm","divider","p","tab","section"].indexOf(it.type) > -1)),
                    "reportTable-item": it.type == "reportTable"
                  }'
                >
                  <FormItem
                    v-if="!it.formSlot"
                    :form="formDatas"
                    :item="{...it, disabledControl: disabledExpressHandle(it, 'formDatas')}"
                    :formRef="refs || defalutSet.refs"
                    :originOption="option"
                    :defalutSet="defalutSet"
                    @formChange="formChange"
                    :roleOption="roleOption"
                    :userList="userList"
                    :departmentList="departmentList"
                    :postList="postList"
                    :rowData="rowData"
                    :resetRadom="resetRadom"
                    :designId="designId"
                    :dataModelId="dataModelId"
                    :changeRandom="changeRandom"
                    :changeDomItem="changeDomItem"
                    :isView="isView"
                    :execsList="execsList"
                    :jvsAppId="jvsAppId"
                    :dataTriggerFresh="dataTriggerFresh"
                    @validateHandle="validateHandle"
                    @reInitData="reInitData"
                  >
                    <!-- 后置插槽 -->
                    <template v-if="it.appendSlot" #[slotAppendItem(it.prop)]>
                      <slot :name="it.prop+'Append'"></slot>
                    </template>
                  </FormItem>
                  <!-- 自定义列插槽 -->
                  <div v-if="it.formSlot">
                    <slot :name="it.prop+'Form'"></slot>
                    <!-- 右侧提示 -->
                    <el-tooltip
                      v-if="it.tips && it.tips.position == 'right' && it.tips.text"
                      class="form-item-tooltip"
                      effect="dark"
                      :content="it.tips.text"
                      placement="top"
                    >
                      <i class="el-icon-question"></i>
                    </el-tooltip>
                  </div>
                  <!-- 换行提示 -->
                  <el-row
                    v-if="it.tips && it.tips.position == 'bottom'"
                    class="form-item-tips"
                  >
                    <span v-html="it.tips.text"></span>
                  </el-row>
                </el-form-item>
              </el-col>
            </template>
          </el-row>
        </el-col>
      </template>
      <el-col
        :span="option.isSearch ? 6 :  24"
        v-if="option.column.length > 0 && option.btnHide!==true"
        class="form-item-btn"
      >
        <el-form-item class="form-btn-bar">
          <template v-if="isSearch">
            <el-button
              size="small"
              v-if="!(option.searchBtn == false)"
              type="primary"
              :icon="Search"
              @click="submitForm(refs || defalutSet.refs)"
              :loading="option.submitLoading"
            >{{option.searchBtnText || defalutSet.option.searchBtnText}}</el-button>
            <el-button
              size="small"
              v-if="!(option.emptyBtn == false)"
              @click="resetForm(refs || defalutSet.refs)"
            >{{option.emptyBtnText || defalutSet.option.emptyBtnText}}</el-button>
          </template>
          <template v-if="!isSearch">
            <el-button
              size="small"
              v-if="!(option.submitBtn == false)"
              type="primary"
              @click="submitForm(refs || defalutSet.refs)"
              :loading="option.submitLoading"
            >{{option.submitBtnText || defalutSet.option.submitBtnText}}</el-button>
            <el-button
              size="small"
              v-if="!(option.emptyBtn == false)"
              @click="resetForm(refs || defalutSet.refs)"
            >{{option.emptyBtnText || defalutSet.option.emptyBtnText}}</el-button>
            <el-button
              size="small"
              v-if="!(option.cancal == false)"
              @click="emit('cancelClick')"
            >{{option.cancelBtnText || defalutSet.option.cancelBtnText}}</el-button>
          </template>
          <slot name="formButton"></slot>
        </el-form-item>
      </el-col>
    </el-row>
    <div v-if="formLoading && !isSearch" class="jvs-form-loading-modal"></div>
  </el-form>
</template>

<script lang="ts" setup name="jvsForm">
import {
  ref,
  reactive,
  watch ,
  onMounted,
  computed,
  getCurrentInstance,
  nextTick
} from 'vue'
import { Search } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import useCommonStore from '@/store/common.js'
import { getStore } from '@/util/store.js'

import FormItem from './formitem.vue'

const { proxy } = getCurrentInstance()
const emit = defineEmits(['submit', 'reset', 'formChange', 'cancelClick', 'init'])
const { t } = useI18n()
const commonStore = useCommonStore()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

const props = defineProps({
  // 是否为搜索表单
  isSearch: {
    type: Boolean,
    default: false
  },
  // 表单绑定
  refs: {
    type: String,
    default: 'ruleForm'
  },
  // 表单对象
  formData: {
    type: Object,
    default: () => {
      return {}
    }
  },
  // 表单数据
  defalutFormData: {
    type: Object,
    default: () => {
      return {}
    }
  },
  // 表单设置
  option: {
    type: Object,
    default: () => {
      return {
        formAlign: 'right', //对其方式
        inline: false, // 表单项是否可以同行,当垂直方向空间受限且表单较简单时，可以在一行内放置表单
        labelWidth: 'auto', // label宽
        searchBtn: true,
        searchBtnText: '',
        submitBtn: true, // 提交按钮是否显示，默认显示
        submitBtnText: '保存', // 提交按钮文字，默认 提交
        emptyBtn: true, // 重置按钮，默认显示
        emptyBtnText: '', // 重置按钮文字，默认 重置
        isSearch: false, // 是否为搜索表单
        cancal: true, // 取消按钮， 默认显示
        cancelBtnText: '取消',
        column: [ // 字段
          {
            label: '', // 文字
            prop: '', // 字段
            type: '', // 文本类型，默认input
            dicData: [], // 字典数据
            span: 24, // 表单项栅格比，默认24
            formSlot: false, // 是否自定义
            permisionFlag: '', // 权限标识
            rules: [], // 校验规则
            linkbind: '', // 父级联动控制对应的表单值
          }
        ],
      }
    }
  },
  rowData: {
    type: Object
  },
  designId: {
    type: String
  },
  dataModelId: {
    type: String
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
  associationSettingsFields: {
    type: Array
  },
})

const slotAppendItem = computed((prop) => {
  return prop + 'AppendItem'
})

let formDatas = reactive({})
const defalutSet = ref({
  refs: 'ruleForm',
  option: {
    formAlign: 'right', //对其方式
    inline: false, // 表单项是否可以同行,当垂直方向空间受限且表单较简单时，可以在一行内放置表单
    labelWidth: 'auto', // label宽
    searchBtn: true, // 搜索按钮是否显示，默认显示
    searchBtnText: t('form.search'), // 搜索按钮，默认查询
    submitBtn: true, // 提交按钮是否显示，默认显示
    submitBtnText: t('form.submit'), // 提交按钮文字，默认 提交
    emptyBtn: true, // 重置按钮，默认显示
    emptyBtnText: t('form.reset'), // 重置按钮文字，默认 重置
    cancelBtnText: t('form.cancel'), // 取消按钮文字， 默认 取消
  }
})
const roleOption = ref([]) // 角色列表
const userList = ref([]) // 部门列表
const departmentList = ref([]) // 部门列表
const postList = ref([]) // 岗位列表
const clearAll = ref(false) // 重置是否为初始对象{}
const resetRadom = ref(-1) // 通知子项重置随机数 -1不重置
const resetData = ref('') // 原始数据，用于重置
const freshDom = ref('')
const changeRandom = ref(-1)
const changeDomItem = ref(null)
const deptBool = ref(false)
const roleBool = ref(false)
const postBool = ref(false)
const userBool = ref(false)
const dataLinkageBool = ref(false)
const formulaBool = ref(false)
const parentDom = ref(null)
const dataTriggerFresh = ref(-1)
const permissionsList = ref([])
const formLoading = ref(false)

createHandle()

async function createHandle () {
  formLoading.value = true
  permissionsList.value = getStore({name: 'permissions'})
  resetData.value = JSON.stringify(props.formData)
  formDatas = props.formData
  if(props.defalutFormData) {
    for(let i in props.defalutFormData) {
      formDatas[i] = props.defalutFormData[i]
    }
  }
  if(JSON.stringify(formDatas) == '{}') {
    clearAll.value = true
  }
  await getConst()
  if(!props.isView && (dataLinkageBool.value || formulaBool.value)) {
    dataInitHandle(null, null, null, 'init')
  }else{
    formLoading.value = false
  }
  let formName = props.refs || defalutSet.value.refs
  emit('init', dynamicRefs.value[formName])
}

async function getConst () {
  // 优化：表单内无对应公共组件不发请求
  eachDomTree(props.option.column)
}

function validateForm () {
  let validBool = false
  let formName = props.refs || defalutSet.value.refs
  dynamicRefs.value[formName].validate((valid, obj) => {
    if(valid) {
      validBool = true
    }else{
      // 二次校验
      let submit = true
      Object.keys(obj).forEach(item => {
        if (formDatas[item] === undefined || formDatas[item] === null || formDatas[item].length === 0) {
          submit = false
        } else {
          let comType = ''
          let itemCom = null
          props.option.column.filter(comItem => {
            if(comItem.prop == item) {
              comType = comItem.type
              itemCom = JSON.parse(JSON.stringify(comItem))
            }
          })
          if(['user', 'role', 'department', 'group', 'job'].indexOf(comType) > -1 || (comType == 'cascader' && itemCom && itemCom.pickType == 'tree')){
            dynamicRefs.value[formName].clearValidate(item)
          }else{
            submit = false
          }              
        }
      })
      if(!submit) {
        console.log('error submit!!')
        validBool = false
      }else{
        validBool = true
      }
    }
  })
  return validBool
}

function submitForm (formName) {
  dynamicRefs.value[formName].validate((valid, obj) => {
    if (valid) {
      emit('submit', formDatas)
    } else {
      // 二次校验
      let submit = true
      Object.keys(obj).forEach(item => {
        if (formDatas[item] === undefined || formDatas[item] === null || formDatas[item].length === 0) {
          submit = false
        } else {
          let comType = ''
          let itemCom = null
          props.option.column.filter(comItem => {
            if(comItem.prop == item) {
              comType = comItem.type
              itemCom = JSON.parse(JSON.stringify(comItem))
            }
          })
          if(['user', 'role', 'department', 'group', 'job'].indexOf(comType) > -1 || (comType == 'cascader' && itemCom && itemCom.pickType == 'tree')){
            dynamicRefs.value[formName].clearValidate(item)
          }else{
            submit = false
          }              
        }
      })
      if (!submit) {
        console.log('error submit!!')
        return false;
      }
      emit('submit', formDatas)
    }
  })
}

function resetForm (formName) {
  if (props.option.isSearch === true) {
    resetRadom.value = Math.random()
    for(let i in formDatas) {
      formDatas[i] = null
    }
  } else {
    if(clearAll.value) {
      for(let i in formDatas) {
        formDatas[i] = null
      }
    }else{
      dynamicRefs.value[formName].resetFields()
    }
    resetRadom.value = Math.random()
    if(resetData.value.startsWith('{')) {
      formDatas = JSON.parse(resetData.value)
    }
  }
  emit('reset', formName)
}

function formChange (form, item, beforeValue) {
  emit('formChange', form, item, beforeValue)
  // 仅 第一层级文本数字组件判断失焦后的值，改变才触发change
  if(item && form[item.prop] != beforeValue) {
    props.formData[item.prop] = form[item.prop] // 重置后需要赋值
    changeRandom.value = Math.random()
    changeDomItem.value = item
  }
}
// 联动控制
function linkbindHandle (val, bind) {
  let bool = true
  if(bind && ['boolean', 'number'].indexOf(typeof bind) == -1) {
    let arr = (bind instanceof Array) ? bind : bind.split(',')
    if(val instanceof Array) {
      let tb = false
      for(let i in val) {
        if(arr.indexOf(val[i]) > -1 || arr.indexOf((val[i] + '')) > -1) {
          tb = true
        }
      }
      bool = tb
    }else{
      if(arr.indexOf(val) > -1 || arr.indexOf((val + '')) > -1) {
        bool = true
      }else{
        bool = false
      }
    }
  }else{
    if(bind || bind === 0 || bind === false) {
      if(val || val === 0 || val === false) {
        if(bind !== val) {
          bool = false
        }
      }else{
        bool = true
      }
    }else{
      bool = true
    }
  }
  return bool
}

function validateHandle (data) {
  let type = data.type
  let item = data.item
  if(['user', 'role', 'department', 'group', 'job', 'htmlEditor', 'cascader'].indexOf(item.type) === -1) {
    freshDom.value = item.prop
  }
  nextTick( () => {
    if(type == 'clear') {
      dynamicRefs.value[props.refs || defalutSet.value.refs].clearValidate(item.prop)
    }else{
      dynamicRefs.value[props.refs || defalutSet.value.refs].validateField(item.prop)
    }
    freshDom.value = ''
  })
}

// 表达式控制显示
function displayExpressHandle (item:any) {
  let bool = false
  let formStr = 'formDatas' // 表单值参数名
  if(item && item.displayExpress && item.displayExpress.length > 0) {
    let list = item.displayExpress
    let temp = []
    let needValidateSpecial = false // 是否单独校验tab项的值
    for(let i in list) {
      let prop = (formStr + '.') // 控制字段名
      if(list[i].parent && list[i].parent.length > 0) {
        let pdomList = []
        findParentList(props.option.column, list[i].parent.join('.'), pdomList)
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
  return bool
}

// 联动或公式初始化
function dataInitHandle (prop, parentKey, index?, optype?, tableType?) {
  return false
  if(props.designId) {
    let hasDataTrigger = false // 是否执行联动
    if(prop) {
      let tp = {
        prop: prop,
      }
      if(parentKey) {
        tp['parentKey'] = parentKey
      }
      let tlist = dataTriggerEnableDom(tp) // 同级的联动组件
      tlist.filter(tit => {
        if(tit.dataLinkageList) {
          tit.dataLinkageList.filter(tid => {
            // 当前组件作为同级中的联动条件
            if(tid.value == prop) {
              hasDataTrigger = true
            }
          })
        }
      })
    }
    if(optype == 'init' || tableType) {
      hasDataTrigger = true
    }
    let formTemp = {}
    for(let k in formDatas) {
      if(!(k.endsWith('_1')) && ((props.associationSettingsFields && props.associationSettingsFields.length > 0) ? (props.associationSettingsFields.indexOf(k) > -1) : true)) {
        formTemp[k] = formDatas[k]
      }
    }
    if(props.dataModelId && hasDataTrigger) {
      let triobj = {
        params: formTemp
      }
      if(prop) {
        triobj['modifiedField'] = prop
      }
      if(parentKey) {
        triobj['parentKey'] = parentKey.split('.')
      }
      if(index > -1) {
        triobj['index'] = index
      }
    }else{
      let obp = {
        params: formTemp
      }
      if(prop) {
        obp['modifiedField'] = prop
      }
      let tempParentKey = ''
      if(parentKey) {
        tempParentKey = parentKey
        findParentDom(props.option.column, parentKey, 'each')
        if(parentDom.value) {
          if(parentDom.value.parentDetachDataProp) {
            if(parentDom.value.parentDetachDataProp == 'thisIsEmptyStringValue') {
              if(parentDom.value.detachData) {
                tempParentKey = ''
              }else{
                tempParentKey = `${parentDom.value.prop}`
              }
            }else{
              if(parentDom.value.detachData) {
                tempParentKey = `${parentDom.value.parentDetachDataProp}`
              }else{
                tempParentKey = `${parentDom.value.parentDetachDataProp}.${parentDom.value.prop}`
              }
            }
          }
        }
        obp['parentKey'] = tempParentKey.split('.')
      }
      if(index > -1) {
        obp['index'] = index
      }
    }
  }else{
    formLoading.value = false
  }
}

function reInitData (prop, parentKey, index, tableType) {
  if(prop) {
    dataInitHandle(prop, parentKey, index, null, tableType)
    changeRandom.value = Math.random()
    changeDomItem.value = {prop: prop, parentKey: parentKey}
  }
  if(prop == '' && parentKey == '') {
    dataInitHandle('', '')
  }
}

function eachDomTree (list, parentDom?) {
  for(let i in list) {
    // 加入自定义校验
    if(list[i].regularExpression) {
      let required = false
      if(list[i].rules && list[i].rules.length > 0 && list[i].rules[0].required) {
        required = true
      }
      if(list[i].rules && list[i].rules.length < 2) {
        let str = '/' + list[i].regularExpression + '/'
        list[i].rules.push({
          validator: function(rule, value, callback) {
            if(eval(str).test(value)) {
              callback()
            }else{
              let msg = '正则校验不通过'
              if(list[i].regularMessage) {
                msg = list[i].regularMessage
              }
              if(required == false && !value) {
                callback()
              }else{
                callback(new Error(msg));
              }
            }
          },
          trigger: ['blur', 'change']
        })
      }
    }
    if(['department'].indexOf(list[i].type) > -1) {
      deptBool.value = true
    }
    if(['role', 'flowNode'].indexOf(list[i].type) > -1) {
      roleBool.value = true
    }
    if(['job', 'flowNode'].indexOf(list[i].type) > -1) {
      postBool.value = true
    }
    if(['user'].indexOf(list[i].type) > -1) {
      userBool.value = true
    }
    if(list[i].dataLinkageModelId) {
      dataLinkageBool.value = true
    }
    if(list[i].formula) {
      formulaBool.value = true
    }
    if(parentDom && parentDom.length > 0) {
      list[i].parentDom = parentDom
    }
    if(!list[i].parentDom && ['tab', 'tableForm', 'pageTable'].indexOf(list[i].type) == -1 && list[i].display === false) {
      let mul = false
      if(['checkbox'].indexOf(list[i].type) > -1 || list[i].multiple) {
        mul = true
      }
      if(list[i].defaultValue || list[i].defaultValue === false || list[i].defaultValue === "" || list[i].defaultValue === 0) {
        if(formDatas[list[i].prop] === null || formDatas[list[i].prop] === undefined) {
          formDatas[list[i].prop] = (mul ? list[i].defaultValue.split(',') : list[i].defaultValue)
        }
      }
    }
    if(['tab', 'step'].indexOf(list[i].type) > -1) {
      for(let c in list[i].column) {
        let tl = [
          {
            prop: list[i].prop,
            label: list[i].label,
            type: list[i].type,
            detachData: list[i].detachData || false
          } 
        ]
        list[i].dicData.filter(ddt => {
          if(ddt.name == c) {
            tl.push(ddt)
          }
        })
        eachDomTree(list[i].column[c], tl)
      }
    }
    if(['tableForm', 'reportTable'].indexOf(list[i].type) > -1 && list[i].tableColumn && list[i].tableColumn.length > 0){
      if(list[i].formId) {
        dataLinkageBool.value = true
      }
      eachDomTree(list[i].tableColumn)
    }
    if(list[i].children && list[i].children.length > 0) {
      eachDomTree(list[i].children)
    }
  }
}

function dataTriggerEnableDom (dom) {
  if(dom.parentKey) {
    let list = []
    findParentDom(props.option.column, dom.parentKey)
    if(parentDom.value) {
      let parentType = parentDom.value.type
      switch(parentType) {
        case 'tableForm':
          if(parentDom.value.tableColumn) {
            list = parentDom.value.tableColumn.filter(item => {
              return (item.dataLinkageList && item.dataLinkageList.length > 0)
            })
          }
        break;
        default: list = props.option.column.filter(item => { return (item.dataLinkageList && item.dataLinkageList.length > 0)});break;
      }
    }else{
      list = props.option.column.filter(item => { return (item.dataLinkageList && item.dataLinkageList.length > 0)})
    }
    return list
  }else{
    return props.option.column.filter(item => {
      return (item.dataLinkageList && item.dataLinkageList.length > 0)
    })
  }
}

function findParentDom (list, key, eachType?, parent?) {
  if(list && list.length > 0) {
    list.filter(item => {
      if(key) {
        let pks = key.split('.')
        if(pks && pks.length > 0 && item.prop == pks[pks.length - 1]) {
          if(eachType == 'each' && parent) {
            if(parent.detachData) {
              let parentDetachDataProp = ''
              let pk = item.parentKey.split('.')
              parent.dicData.filter(dit => {
                if(dit.name == pk[pk.length-1] && dit.prop) {
                  parentDetachDataProp = dit.prop
                }
              })
              let tp = []
              for(let p in pk) {
                if(pk[p] == parent.prop) {
                  break
                }
                tp.push(pk[p])
              }
              if(parentDetachDataProp) {
                tp.push(parentDetachDataProp)
              }
              item.parentDetachDataProp = tp.join('.') || 'thisIsEmptyStringValue'
            }
          }
          parentDom.value = item
        }
      }
      if(['tab', 'step'].indexOf(item.type) > -1) {
        if(item.detachData) {
          let pks = key.split('.')
          let parentDetachDataProp = ''
          item.dicData.filter(dit => {
            if(dit.name == pks[pks.length-1]) {
              if(dit.prop) {
                parentDetachDataProp = dit.prop
              }
              let tp = []
              if(item.parentKey) {
                let pk = item.parentKey.split('.')
                for(let p in pk) {
                  if(pk[p] == parent.prop) {
                    break
                  }
                  tp.push(pk[p])
                }
              }
              if(parentDetachDataProp) {
                tp.push(parentDetachDataProp)
              }
              item.parentDetachDataProp = tp.join('.') || 'thisIsEmptyStringValue'
              parentDom.value = item
            }
          })
        }
        for(let j in item.column) {
          if(item.column[j] && item.column[j].length > 0) {
            findParentDom(item.column[j], key, eachType, item)
          }
        }
      }
    })
  }
}

// 表达式控制禁用
function disabledExpressHandle (item, formDatas) {
  let bool = false
  let formStr = `${formDatas}` // 表单值参数名
  if(item.disabledExpress && item.disabledExpress.length > 0) {
    let list = item.disabledExpress
    let temp = []
    let needValidateSpecial = false // 是否单独校验tab项的值
    for(let i in list) {
      let prop = (formStr + '.') // 控制字段名
      if(list[i].parent && list[i].parent.length > 0) {
        let pdomList = []
        findParentList(props.option.column, list[i].parent.join('.'), pdomList)
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
    bool = false
  }
  return bool
}

// 表达式控制必填校验
function requireExpressHandle (rules, item) {
  if(item.requireExpress && item.requireExpress.length > 0) {
    let bool = false
    let formStr = `formDatas`
    let list = item.requireExpress
    let temp = []
    let needValidateSpecial = false // 是否单独校验tab项的值
    for(let i in list) {
      let prop = (formStr + '.') // 控制字段名
      if(list[i].parent && list[i].parent.length > 0) {
        let pdomList = []
        findParentList(props.option.column, list[i].parent.join('.'), pdomList)
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
    if(bool) {
      if(rules && rules.length > 0) {
        rules[0]['required'] = true
        if(dynamicRefs.value[props.refs || defalutSet.value.refs]) {
          dynamicRefs[props.refs || defalutSet.value.refs].validateField(item.prop)
        }
      }
    }else{
      rules[0]['required'] = false
      if(dynamicRefs.value[props.refs || defalutSet.value.refs]) {
        dynamicRefs.value[props.refs || defalutSet.value.refs].clearValidate(item.prop)
      }
    }
  }
  return rules
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
</script>

<style lang="scss">
.jvs-form {
  h5{
    margin: 0;
    padding: 0;
    margin-bottom: 10px;
  }
  .el-form-item.form-btn-bar{
    .el-form-item__content{
      height: 36px;
      text-align: center;
      margin-left: 0 !important;
    }
  }
  .no-label-form-item{
    >.el-form-item{
      >.el-form-item__label{
        display: none;
      }
      >.el-form-item__content{
        margin-left: 0!important;
      }
    }
  }
  .form-column-box, .form-column-p{
    .el-form-item{
      margin-bottom: 0;
    }
  }
}
.jvs-form-autoflexable{
  .el-form-item {
    display: flex;
    margin: 0 10px;
    .el-form-item__label-wrap{
      margin-left: 0 !important;
      word-break: keep-all;
    }
    .el-form-item__content{
      margin-left: 0 !important;
      flex: 1;
      div {
        .el-input,
        .el-select {
          width: 100%;
        }
      }
    }
  }
}
.form-item-tooltip{
  display: inline-block;
  margin: 0 10px;
}
.form-item-no-label-tab{
  >.el-form-item__content{
    margin-left: 0!important;
  }
}
.form-item-no-label{
  .el-form-item__content{
    margin-left: 0!important;
  }
}
.form-item-no-label-nopadding{
  padding: 0!important;
}
.form-item-tips{
  font-size: 12px;
  color: #c3c3c3;
  width: 100%;
}
.reportTable-item{
  display: flex;
  flex-wrap: wrap;
  .el-form-item__content{
    margin-left: 0!important;
    width: 100%;
  }
}
.before-append-item{
  .el-form-item__content{
    display: flex;
    align-items: center;
  }
}
.jvs-form-loading-modal{
  width: 100%;
  height: 100%;
  position: absolute;
  left: 0;
  top: 0;
  background-color: #fff;
  background-image: url('/jvs-ui-public/img/loading.gif');
  background-repeat: no-repeat;
  background-position: center;
  z-index: 9;
}
</style>
