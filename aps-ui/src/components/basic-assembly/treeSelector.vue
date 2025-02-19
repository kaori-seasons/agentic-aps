<template>
  <div class="jvs-tree-select" :ref="el => dynamicRefMap(el, key)">
    <div :class="{'el-cascader': true, 'fixed-height': collapsetags, 'empty-height': (props.multiple ? !(nametagList && nametagList.length > 0) : true)}">
      <el-popover
        v-model="openTree"
        :width="pupopWidth"
        placement="bottom-start"
        trigger="click">
        <div class="jvs-tree-select-list">
          <el-tree
            v-if="optionsData && optionsData.length > 0"
            :data="optionsData" :props="props"
            :expand-on-click-node="false"
            @node-expand="openNode"
            @node-collapse="closeNode"
            @node-click="handleNodeClick">
            <template #default="{ node, data }">
              <span :class="{'custom-tree-node': true, 'isCheck': (checkIds.indexOf(node.data[props.value]) > -1 && !isIndeterminate(node)), 'indeterminate': isIndeterminate(node)}">
                <span v-if="props.multiple" :class="{'check-label': true}">
                  <span class="check"></span>
                </span>
                <span class="check-text">{{node.label}}</span>
              </span>
            </template>
          </el-tree>
          <div v-else class="el-select-dropdown__empty">暂无数据</div>
        </div>
        <template #reference>
          <div :class="{'el-input el-input--suffix': true, 'input-withvalue' : (nametagList && nametagList.length > 0)}">
            <div class="el-input__wrapper">
              <div class="input el-input__inner">
                <span v-if="!(nametagList && nametagList.length > 0)" class="placeholder">{{item.placeholder || '请选择'}}</span>
                <span v-if="props.multiple" :class="{'el-cascader__tags': true, 'name-tag-list': true, 'name-tag-list-more': collapsetags}">
                  <el-tag v-for="(name, inx) in filterTagList(nametagList)" :key="'tree-select-tag-'+inx" :closeable="true" size="small" type="info" effect="light">
                    <span>{{name}}</span>
                    <i class="el-tag__close el-icon-close" @click.stop="delTagItem(name, inx)"></i>
                  </el-tag>
                  <el-tag v-if="nametagList && nametagList.length > 1 && collapsetags" size="small" type="info" effect="light" class="tag-length-item">+ {{nametagList.length-1}}</el-tag>
                </span>
                <span v-else>{{userStr}}</span>
              </div>
              <span class="el-input__suffix">
                <span class="el-input__suffix-inner">
                  <el-icon :class="{'el-input__icon': true, 'is-reverse': openTree}"><ArrowDown /></el-icon>
                  <el-icon class="el-input__icon" @click.stop="clearHandle"><CircleClose /></el-icon>
                </span>
              </span>
            </div>
          </div>
        </template>
      </el-popover>
    </div>
  </div>
</template>
<script lang="ts" setup>
import {
  ref,
  reactive,
  watch ,
  onMounted,
  computed,
  getCurrentInstance,
  nextTick
} from 'vue'
import { ArrowDown, CircleClose } from '@element-plus/icons-vue'

const { proxy } = getCurrentInstance()
const emit = defineEmits(['change'])
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

const props = defineProps({
  form: {
    type: Object
  },
  item: {
    type: Object
  },
  options: {
    type: Array
  },
  showalllevels: {
    type: Boolean
  },
  collapsetags: {
    type: Boolean
  },
  disabled: {
    type: Boolean,
  },
  props: {
    type: Object,
    default: () => {
      return {
        multiple: true,
        label: 'label',
        value: 'value',
        children: 'children',
        emitPath: true,
        checkStrictly: false
      }
    }
  },
  createInit: {
    type: Boolean
  }
})

const key = ref(new Date().getTime())
const pupopWidth = ref(180)
const openTree = ref(false)
const userStr = ref('')
const userList = ref([])
const userNameList = ref([])
const optionsData = ref([])
const checkList = ref([]) // 已选择节点
const lastWidth = ref(180)

const nametagList = computed(() => {
  let list = []
  if(userStr.value) {
    list = userStr.value.split(',')
  }
  return list
})

const checkIds = computed(() => {
  let tp = []
  checkList.value.filter(cit => {
    tp.push(cit.id)
  })
  return tp
})

createHandle()
onMounted(() => {
  pupopWidth.value = dynamicRefs.value[key.value].clientWidth
})

function createHandle ()  {
  init()
}

function filterTagList (list) {
  return list.filter((li, lx) => {
    if(nametagList.value && nametagList.value.length > 0 && (props.collapsetags ? (lx < 1) : true)) {
      return li
    }
  })
}

function init () {
  optionsData.value = JSON.parse(JSON.stringify(props.options))
  for(let i in optionsData.value) {
    optionsData.value[i].path = [optionsData.value[i][props.props.value]]
    if(props.showalllevels) {
      optionsData.value[i].namePath = [optionsData.value[i][props.props.label]]
    }
    getInitData(optionsData.value[i])
    if(optionsData.value[i][props.props.children] && optionsData.value[i][props.props.children].length > 0) {
      eachOptions(optionsData.value[i][props.props.children], optionsData.value[i])
    }
  }
  if(checkList.value && checkList.value.length > 0) {
    dealSub(checkList.value)
  }
}

function eachOptions (list, parent) {
  for(let i in list) {
    if(!list[i].path) {
      list[i].path = parent.path ? JSON.parse(JSON.stringify(parent.path)) : []
    }
    if(props.showalllevels) {
      if(!list[i].namePath) {
        list[i].namePath = parent.namePath ? JSON.parse(JSON.stringify(parent.namePath)) : []
      }
    }
    if(list[i][props.props.value]) {
      list[i].path.push(list[i][props.props.value])
      if(props.showalllevels) {
        list[i].namePath.push(list[i][props.props.label])
      }
    }
    getInitData(list[i])
    if(list[i][props.props.children] && list[i][props.props.children].length > 0) {
      eachOptions(list[i][props.props.children], list[i])
    }
  }
}

function getInitData (data) {
  if(props.props.multiple) {
    if(props.form[props.item.prop] && props.form[props.item.prop].length > 0) {
      props.form[props.item.prop].filter(fot => {
        if(props.props.emitPath) {
          if(fot[fot.length-1] == data[props.props.value]) {
            setInitCheck(data)
          }
        }else{
          if(fot == data[props.props.value]) {
            setInitCheck(data)
          }
        }
      })
    }
  }else{
    if(props.props.emitPath) {
      if(props.form[props.item.prop] && props.form[props.item.prop].length > 0) {
        if(props.form[props.item.prop][props.form[props.item.prop].length - 1] == data[props.props.value]) {
          setInitCheck(data)
        }
      }
    }else{
      if(props.form[props.item.prop] == data[props.props.value]) {
        setInitCheck(data)
      }
    }
  }
}

function setInitCheck (data) {
  let obj = { id: data[props.props.value], name: data[props.props.label], path: data.path}
  if(data.namePath) {
    obj['namePath'] = data.namePath
  }
  if(data[props.props.value]) {
    obj[props.props.value] = data[props.props.value]
  }
  checkList.value.push(obj)
}

function handleNodeClick (data) {
  let obj = { id: data[props.props.value], name: data[props.props.label], path: data.path}
  if(data.namePath) {
    obj['namePath'] = data.namePath
  }
  if(data[props.props.value]) {
    obj[props.props.value] = data[props.props.value]
  }
  const index = checkList.value.findIndex(item => {
    return item.id === data[props.props.value]
  })
  if(props.props.multiple) {
    if(index === -1) {
      checkList.value.push(obj)
    }else{
      checkList.value.splice(index, 1)
    }
    if(!props.props.checkStrictly) {
      // 全选所有子节点
      if(data[props.props.children] && data[props.props.children].length > 0) {
        selectChildren(data[props.props.children], index > -1 ? 'del' : 'add')
      }
      // 根据父级id设置父节点状态
      if(data.parentId && data.path.length > 1) {
        deepEachTree(data.parentId, optionsData.value)
      }
    }
    dealSub(checkList.value)
  }else{
    checkList.value = [obj]
    submit()
  }
}

// 提交数据
function submit () {
  dealSub(checkList.value)
  closeDialog()
}

function dealSub (list) {
  if(props.props.multiple) {
    selectChange(list)
    userStr.value = userNameList.value.join(',')
    let temp = []
    if(props.props.emitPath) {
      list.filter(li => { temp.push(li.path) })
    }else{
      list.filter(li => { temp.push(li[props.props.value]) })
    }
    props.form[props.item.prop] = temp
  }else{
    if(list && list.length > 0) {
      if(props.props.emitPath) {
        props.form[props.item.prop] = list[0].path
      }else{
        props.form[props.item.prop] = list[0].id
      }
      userStr.value = props.showalllevels ? list[0].namePath.join('/') : list[0].name
    }else{
      props.form[props.item.prop] = ''
      userStr.value = ''
    }
  }
  emit('change', props.form)
}

function selectChange (data) {
  let temp = []
  let nm = []
  for(let i in data) {
    temp.push(data[i].id)
    nm.push(props.showalllevels ? data[i].namePath.join('/') : data[i][props.props.label])
  }
  userList.value = temp
  userNameList.value = nm
}

function closeDialog () {
  openTree.value = false
}

function delTagItem (name, index) {
  if(checkList.value[index].name == name) {
    checkList.value.splice(index, 1)
    dealSub(checkList.value)
  }
}

function clearHandle () {
  checkList.value = []
  dealSub(checkList.value)
}

function selectChildren (list, type) {
  for(let i in list) {
    let obj = { id: list[i][props.props.value], name: list[i][props.props.label], path: list[i].path}
    if(list[i].namePath) {
      obj['namePath'] = list[i].namePath
    }
    if(list[i][props.props.value]) {
      obj[props.props.value] = list[i][props.props.value]
    }
    let index = checkList.value.findIndex(cit => {
      return cit.id === list[i][props.props.value]
    })
    if(type == 'add' && index == -1) {
      checkList.value.push(obj)
    }
    // 强关联删除子节点
    if(type == 'del' && index > -1) {
      checkList.value.splice(index, 1)
    }
    if(list[i][props.props.children] && list[i][props.props.children].length > 0) {
      selectChildren(list[i][props.props.children], type)
    }
  }
}

function setParentNode (item) {
  if(item.children && item.children.length > 0) {
    let allchild = true
    let allCancel = true
    item[props.props.children].filter(cit => {
      if(checkIds.value.indexOf(cit[props.props.value]) == -1) {
        allchild = false
      }else{
        allCancel = false
      }
    })
    // 强关联选取上级
    if(allchild && checkIds.value.indexOf(item[props.props.value]) == -1) {
      let obj = { id: item[props.props.value], name: item[props.props.label], path: item.path}
      if(item.namePath) {
        obj['namePath'] = item.namePath
      }
      if(item[props.props.value]) {
        obj[props.props.value] = item[props.props.value]
      }
      checkList.value.push(obj)
    }
    // 强关联取消上级
    if(allCancel) {
      let index = checkIds.value.indexOf(item[props.props.value])
      if(index > -1) {
        checkList.value.splice(index, 1)
      }
    }
    if(item.parentId && item.path.length > 1) {
      deepEachTree(item.parentId, optionsData.value)
    }
  }
}

function deepEachTree (id, list) {
  for(let i in list) {
    if(list[i][props.props.value] == id) {
      setParentNode(list[i])
    }
    if(list[i][props.props.children] && list[i][props.props.children].length > 0) {
      deepEachTree(id, list[i][props.props.children])
    }
  }
}

function isIndeterminate (node) {
  let bool = false
  let childBool = false
  let allchild = true
  if(node.childNodes && node.childNodes.length > 0) {
    node.childNodes.filter(cn => {
      if(checkIds.value.indexOf(cn.data[props.props.value]) > -1) {
        childBool = true
      }else{
        allchild = false
      }
    })
  }
  if(childBool && !allchild) {
    bool = true
  }
  return bool
}

function openNode (data, node, dom) {
  openTree.value = false
  nextTick(() => {
    openTree.value = true
  })
}

function closeNode (data, node, dom) {
  openTree.value = false
  nextTick(() => {
    openTree.value = true
  })
}

watch(() => props.options, (newVal, oldVal) => {
  if(newVal.length > 0) {
    init()
  }
})
</script>
<style lang="scss" scoped>
.jvs-tree-select{
  width: 100%;
  line-height: 0;
  ::v-deep(.el-cascader){
    width: 100%;
    line-height: 36px;
    .el-input{
      .input.el-input__inner{
        position: relative;
        .placeholder{
          color: #C0C4CC;
        }
        .name-tag-list{
          position: unset;
          transform: none;
          margin: 2px 0;
          margin-left: -16px;
        }
      }
      .el-input__suffix{
        .el-input__icon:nth-last-of-type(1){
          display: none;
        }
      }
    }
  }
  .el-cascader:hover{
    .input-withvalue{
      .el-input__suffix{
        .el-input__icon:nth-last-of-type(1){
          display: inline-block;
        }
        .el-input__icon:nth-of-type(1){
          display: none;
        }
      }
    }
  }
}
.jvs-tree-select-list{
  padding: 0 10px;
  max-height: 210px;
  overflow: auto;
  max-width: calc(100vw - 55px);
  ::v-deep(.el-tree-node.is-current > .el-tree-node__content){
    background-color: #F5F7FA;
  }
  .custom-tree-node{
    display: flex;
    align-items: center;
    .check-label{
      display: flex;
      align-items: center;
      cursor: pointer;
      margin-right: 5px;
      .check{
        display: inline-block;
        position: relative;
        border: 1px solid #dcdfe6;
        border-radius: 2px;
        box-sizing: border-box;
        width: 14px;
        height: 14px;
        background-color: #fff;
        z-index: 1;
        transition: border-color .25s cubic-bezier(.71,-.46,.29,1.46),background-color .25s cubic-bezier(.71,-.46,.29,1.46);
      }
      .check::after {
        box-sizing: content-box;
        content: "";
        border: 1px solid #fff;
        border-left: 0;
        border-top: 0;
        height: 7px;
        left: 4px;
        position: absolute;
        top: 1px;
        transform: rotate(45deg) scaleY(0);
        width: 3px;
        transition: transform .15s ease-in .05s;
        transform-origin: center;
      }
    }
  }
  .isCheck{
    .check-label{
      .check{
        background: #3471FF;
        border-color: #3471FF;
      }
      .check::after{
        transform: rotate(45deg) scaleY(1);
      }
    }
    .check-text{
      color: #409EFF;
      font-weight: 700;
    }
  }
  .indeterminate{
    .check-label{
      .check{
        background: #3471FF;
        border-color: #3471FF;
      }
      .check::before{
        content: "";
        position: absolute;
        display: block;
        background-color: #fff;
        height: 2px;
        transform: scale(.5);
        left: 0;
        right: 0;
        top: 5px;
      }
    }
    .check-text{
      color: #409EFF;
      font-weight: 700;
    }
  }
  .isDisabled{
    .check-label{
      .check{
        background-color: #edf2fc;
        border-color: #dcdfe6;
        cursor: not-allowed;
      }
      .check::after{
        cursor: not-allowed;
        border-color: #c0c4cc;
      }
    }
    .check-text{
      color: #edf2fc;
    }
  }
}
</style>