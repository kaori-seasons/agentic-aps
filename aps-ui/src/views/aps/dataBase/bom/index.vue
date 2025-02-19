<template>
  <div class="basic-layout-box aps-dataBase-bom">
    <div class="left">
      <div class="title-box">
        <span>{{t(`aps.dataBase.bom.title`)}}</span>
      </div>
      <div class="search-box">
        <el-input v-model="keyword" :prefix-icon="Search" :placeholder="t(`aps.dataBase.bom.searchPlaceholder`)" @input="getBomList"></el-input>
      </div>
      <div class="bom-list">
        <div class="bom-header">
          <div v-for="col in bomOption.column" :key="'header-'+col.prop" class="cell">
            <span>{{col.label}}</span>
          </div>
        </div>
        <el-scrollbar v-if="bomList && bomList.length > 0" class="bom-body">
          <div v-if="bomLoading" class="loading"></div>
          <div v-for="(bom, index) in bomList"
            :key="'bom-item-'+index"
            :class="{'bom-list-item': true, 'active': bom.id == currentBom.id}"
            @click="bomChange(bom)"
          >
            <div v-for="col in bomOption.column" :key="'bom-item-'+index+'-'+col.prop" class="cell">
              <span>{{bom[col.prop]}}</span>
            </div>
          </div>
        </el-scrollbar>
      </div>
    </div>
    <div class="right">
      <div class="top">
        <div class="left-box">
          <el-button type="primary" :icon="Plus" @click="addEditRow(null)">{{t(`table.add`)}}</el-button>
        </div>
        <div class="right-box">
          <div :class="{'svg-icon-box': true, 'active': showType == 'list'}" @click="changeShowType('list')">
            <svg>
              <use xlink:href="#icon-jvs-liebiaoshitu"></use>
            </svg>
          </div>
          <div :class="{'svg-icon-box': true, 'active': showType == 'map'}" @click="changeShowType('map')">
            <svg>
              <use xlink:href="#icon-jvs-yingyongshezhi-weixuanzhong"></use>
            </svg>
          </div>
        </div>
      </div>
      <div :class="{'bottom': true, 'map': showType == 'map'}">
        <div class="bottom-body">
          <jvs-table
            v-if="showType == 'list'"
            :option="tableOption"
            :loading="tableLoading"
            :data="tableData"
            rowKey="jvsTableKey"
          >
            <template #menu="scope">
              <el-button :text="true" size="small" @click="addEditRow(scope.row)">{{t(`table.edit`)}}</el-button>
              <el-popconfirm v-if="scope.row.level === 1" :title="t('common.deleteConfirm')" width="200px" :icon="WarningFilled" @confirm="delHandle(scope.row)">
                <template #reference>
                  <el-button text size="small"><span style="color: #F56C6C;">{{t('table.delete')}}</span></el-button>
                </template>
              </el-popconfirm>
            </template>
            <template #quantity="scope">
              <span>{{scope.row['quantity']}}{{(scope.row.node && scope.row.node.unit) ? scope.row.node.unit : ''}}</span>
            </template>
          </jvs-table>
          <div v-if="showType == 'map'" class="map-box">
            <relationMap :data="mapData" @clickNode="addEditRow" @deleteNode="delHandle"></relationMap>
          </div>
        </div>
      </div>
    </div>

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
        <jvs-form :ref="el => dynamicRefMap(el, 'materialForm')" :option="formOption" :formData="rowData" @init="initHandle" @submit="submitHandle" @cancelClick="handleClose">
          <template #materialCodeForm>
            <div class="jvs-form-item">
              <el-input v-if="rowData.id" v-model="rowData.materialCode" disabled></el-input>
              <div v-else class="el-input__wrapper select-material-code" @click="selectMaterial('')">
                <div :class="{'el-input__inner': true, 'placeholder': !rowData.materialCode}">{{rowData.materialCode || '请选择父件物料编码'}}</div>
              </div>
            </div>
          </template>
          <template #childMaterialsForm="scope">
            <div class="jvs-form-item">
              <div class="childMaterials-list">
                <div class="title-box">
                  <svg>
                    <use xlink:href="#icon-jvs-rongqi"></use>
                  </svg>
                  <span>子件清单</span>
                </div>
                <div class="slot-table-form-box">
                  <tableForm
                    :item="scope.item"
                    :option="{...tableFormOption, tableColumn: scope.item.tableColumn}"
                    :data="rowData[scope.item.prop]"
                    :forms="rowData"
                    :originForm="rowData"
                    @setTable="setTableHandle">
                    <template #menuBtn="scope">
                      <jvs-button text @click="deleteRow(scope.row, scope.index)">删除</jvs-button>
                    </template>
                    <template #quantityTableFormColumn="scope">
                      <div :class="{'el-form-item': true, 'is-error': (!scope.row.quantity && scope.row.quantity !== 0)}">
                        <div class="el-form-item__content">
                          <div class="jvs-form-item">
                            <el-input-number v-model="scope.row.quantity" />
                          </div>
                        </div>
                      </div>
                    </template>
                  </tableForm>
                  <el-row style="margin-top:10px;">
                    <jvs-button size="small" @click="addChildMaterials">新增</jvs-button>
                  </el-row>
                </div>
              </div>
            </div>
          </template>
        </jvs-form>
      </div>
    </el-dialog>
    <!-- 选择父级物料编码 -->
    <el-dialog
      v-model="materialVisible"
      title="选择物料编码"
      width="52%"
      append-to-body
      :close-on-click-modal="false"
      :before-close="materialClose"
    >
      <div v-if="materialVisible" style="height: 70vh;padding: 20px;box-sizing: border-box;overflow: hidden;">
        <jvs-table
          :formData="queryParams"
          :loading="materialLoading"
          :data="materialList"
          :option="materialCodeOption"
          :page="page"
          @on-load="getMaterialHandle"
          @search-change="searchChange"
          @row-click="rowClick"
        >
        </jvs-table>
      </div>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup name="bom">
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
import { Plus, Search, WarningFilled } from '@element-plus/icons-vue'

import useCommonStore from '@/store/common.js'
import { getStore } from '@/util/store.js'

import tableForm from '@/components/basic-assembly/tableForm.vue'
import relationMap from './map.vue'

import { getList, getBomTree, getMaterialList, getInfo, add, edit, del } from './api'

const { proxy } = getCurrentInstance()
const emit = defineEmits([])
const { t } = useI18n()
const commonStore = useCommonStore()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}
const materialForm = ref()

const keyword = ref('')
const currentBom = ref({})
const bomLoading = ref(false)
const bomOption = ref({
  column: [
    {
      label: '物料名称',
      prop: 'materialName'
    },
    {
      label: '物料编码',
      prop: 'materialCode'
    }
  ]
})
const bomList = ref([])
const showType = ref('list')
const tableLoading = ref(false)
const queryParams = ref({})
const tableData = ref([])
const mapData = ref({
  nodes: [],
  edges: []
})
const tableOption = ref({
  page: false,
  search: true,
  cancal: false,
  showOverflow: true,
  addBtn: false,
  delBtn: false,
  menu: true,
  menuWidth: '120px',
  viewBtn: false,
  editBtn: false,
  column: [
    {
      label: '层级',
      prop: 'level',
    },
    {
      label: '物料编码',
      prop: 'materialCode',
    },
    {
      label: '物料名称',
      prop: 'materialName',
    },
    {
      label: '数量',
      prop: 'quantity',
      slot: true
    },
  ]
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const rowData = ref({})
const formOption = ref({
  emptyBtn: false,
  submitLoading: false,
  formAlign: 'top',
  column: [
    {
      label: '父件物料编码',
      prop: 'materialCode',
      disabled: true,
      span: 12,
      formSlot: true,
      rules: [{ required: true, message: '请选择父件物料编码', trigger: 'blur' }]
    },
    {
      label: '父件物料名称',
      prop: 'materialName',
      disabled: true,
      span: 12,
    },
    {
      label: '',
      prop: 'childMaterials',
      type: 'tableForm',
      formSlot: true,
      addBtn: true,
      delBtn: true,
      editable: true,
      tableColumn: [
        {
          label: '物料编码',
          prop: 'materialCode',
          disabled: true
        },
        {
          label: '物料名称',
          prop: 'materialName',
          disabled: true
        },
        {
          label: '计量单位',
          prop: 'unit',
          disabled: true
        },
        {
          label: '单件用量',
          prop: 'quantity',
          type: 'inputNumber',
          formSlot: true,
          rules: [{ required: true, message: '请输入单件用量', trigger: 'blur' }]
        }
      ]
    }
  ],
})

const materialVisible = ref(false)
const materialType = ref('')
const page = ref({
  total: 0, // 总页数
  currentPage: 1, // 当前页数
  pageSize: 20, // 每页显示多少条
})
const materialList = ref([])
const materialLoading = ref(false)
const materialCodeOption = ref({
  addBtn: false,
  menu: false,
  page: true,
  search: true,
  column: [
    {
      label: '编码',
      prop: 'code',
      search: true,
      searchSpan: 6,
    },
    {
      label: '名称',
      prop: 'name',
      search: true,
      searchSpan: 6,
    },
  ]
})

const tableFormOption = computed(() => {
  return {
    addBtn: false,
    viewBtn: false,
    delBtn: false,
    editBtn: false,
    page: false,
    border: false,
    align: 'left',
    menuAlign: 'left',
    cancal: false,
    showOverflow: true,
    hideTop: false,
    menuWidth: 80
  }
})

getBomList()

function getBomList () {
  let obj = {
    current: 1,
    size: 1000
  }
  if(keyword.value) {
    obj['keywords'] = keyword.value
  }
  bomLoading.value = true
  bomList.value = []
  getList(obj).then(res => {
    if(res.data && res.data.code == 0 && res.data.data) {
      bomList.value = res.data.data.records
    }
    bomLoading.value = false
  }).catch(e => {
    bomLoading.value = false
  })
}

function bomChange (bom) {
  if(currentBom.value && currentBom.value['id'] == bom.id) {
    currentBom.value = {}
    tableData.value = []
    mapData.value = {
      nodes: [],
      edges: []
    }
  }else{
    currentBom.value = JSON.parse(JSON.stringify(bom))
    getListHandle()
  }
}

function changeShowType (type) {
  if(showType.value != type) {
    showType.value = type
  }
}

function getListHandle (pageInfo?) {
  if(currentBom.value && currentBom.value['id']) {
    tableLoading.value = true
    getBomTree(currentBom.value['id']).then(res => {
      if(res.data && res.data.code == 0 && res.data.data) {
        mapData.value = {
          nodes: [],
          edges: []
        }
        let temp = [res.data.data]
        eachTree(temp)
        tableData.value = temp
      }
      tableLoading.value = false
    }).catch(e => {
      tableLoading.value = false
    })
  }
}

function eachTree (list, key?, pk?) {
  for(let i in list) {
    list[i].jvsTableKey = (key ? `${key}` : '') + i + ''
    if(list[i].node) {
      if(list[i].node.id) {
        list[i].id = list[i].node.id
      }
      if(list[i].node.materialCode) {
        list[i].materialCode = list[i].node.materialCode
      }
      if(list[i].node.materialName) {
        list[i].materialName = list[i].node.materialName
      }
      if(list[i].node.quantity) {
        list[i].quantity = list[i].node.quantity
      }
    }
    mapData.value.nodes.push({
      id: list[i].node.materialId + list[i].jvsTableKey,
      data: {...list[i].node, levelValue: list[i].level}
    })
    if(pk) {
      mapData.value.edges.push({
        source: pk,
        target: list[i].node.materialId + list[i].jvsTableKey,
      })
    }
    if(list[i].children && list[i].children.length > 0) {
      eachTree(list[i].children, list[i].jvsTableKey, list[i].node.materialId + list[i].jvsTableKey)
    }else{
      list[i].hasChildren = false
    }
  }
}

async function addEditRow (row) {
  if(row && row.id) {
    await getInfo(row.id).then(res => {
      if(res.data && res.data.code == 0) {
        rowData.value = res.data.data
      }
    })
    dialogTitle.value = t(`aps.dataBase.bom.edit`)
  }else{
    if(row && row.node && row.node.materialId) {
      rowData.value = JSON.parse(JSON.stringify(row.node))
    }else{
      rowData.value = {}
    }
    dialogTitle.value = t(`aps.dataBase.bom.add`)
  }
  if(!rowData.value['childMaterials']) {
    rowData.value['childMaterials'] = []
  }
  formOption.value.column.filter(col => {
    if(['materialCode'].indexOf(col.prop) > -1) {
      col.disabled = (rowData.value && rowData.value['id']) ? true : false
    }
  })
  dialogVisible.value = true
}

function initHandle (ref) {
  formRef.value = ref
}

function submitHandle (form) {
  let fun = add
  if(rowData.value['id']) {
    fun = edit
  }
  let validate = true
  let temp = {
    materialId: rowData.value['materialId'],
    quantity: rowData.value['quantity'],
    childMaterials: []
  }
  if(rowData.value['id']) {
    temp['id'] = rowData.value['id']
  }
  if(!rowData.value['materialId']) {
    validate = false
  }
  if(rowData.value && rowData.value['childMaterials'] && rowData.value['childMaterials'].length > 0) {
    rowData.value['childMaterials'].filter(cit => {
      if(cit.materialId && cit.quantity !== null && cit.quantity !== undefined) {
        temp.childMaterials.push({
          materialId: cit.materialId,
          quantity: cit.quantity
        })
      }else{
        validate = false
      }
    })
  }else{
    validate = false
  }
  if(validate) {
    formOption.value.submitLoading = true
    fun(temp).then(res => {
      if(res.data && res.data.code == 0) {
        ElNotification.closeAll()
        ElNotification({
          title: t(`common.tip`),
          message: (rowData.value['id'] ? t(`aps.dataBase.bom.edit`) : t(`aps.dataBase.bom.add`)) + t(`common.success`),
          position: 'bottom-right',
          type: 'success',
        })
        formOption.value.submitLoading = false
        handleClose()
        if(!temp['id']) {
          getBomList()
        }
        getListHandle()
      }
    }).catch(e => {
      formOption.value.submitLoading = false
    })
  }
}

function handleClose () {
  dialogVisible.value = false
  rowData.value = {}
}

function delHandle (row) {
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
        getBomList()
        if(row.id == currentBom.value['id']) {
          currentBom.value = {}
          tableData.value = []
          mapData.value = {
            nodes: [],
            edges: []
          }
        }else{
          getListHandle()
        }
      }
    })
  }
}

function selectMaterial (type) {
  materialType.value = type
  materialVisible.value = true
}

function getMaterialHandle (pageInfo?) {
  materialLoading.value = true
  let tp = {
    current: pageInfo && pageInfo.current ? pageInfo.current : page.value.currentPage,
    size: page.value.pageSize,
  }
  getMaterialList(Object.assign(queryParams.value, tp)).then( res => {
    materialLoading.value = false
    if(res.data && res.data.code == 0 && res.data.data) {
      materialList.value = res.data.data.records
      page.value.currentPage = res.data.data.current
      page.value.total = res.data.data.total
    }
  }).catch(err => {
    materialLoading.value = false
  })
}

function searchChange (form) {
  queryParams.value = form
  getMaterialHandle()
}

function rowClick (data) {
  const { row } = data
  if(row.code) {
    if(materialType.value == 'child') {
      rowData.value['childMaterials'].push({
        materialId: row.id,
        materialCode: row.code,
        materialName: row.name,
        unit: row.unit
      })
    }else{
      rowData.value['materialId'] = row.id
      rowData.value['materialCode'] = row.code
      rowData.value['materialName'] = row.name
      formRef.value.clearValidate('materialCode')
    }
    materialClose()
  }
}

function materialClose () {
  materialVisible.value = false
  queryParams.value = {}
}

function addChildMaterials () {
  selectMaterial('child')
}

function setTableHandle (data) {
  rowData.value['childMaterials'] = data
}

function deleteRow (row, index) {
  rowData.value['childMaterials'].splice(index, 1)
}

</script>
<style lang="scss" scoped>
.aps-dataBase-bom{
  width: 100%;
  height: 100%;
  padding: 0!important;
  display: flex;
  box-sizing: border-box;
  overflow: hidden;
  .left{
    width: 280px;
    height: 100%;
    border-right: 1px solid #EEEFF0;
    box-sizing: border-box;
    .title-box{
      width: 100%;
      height: 64px;
      background: linear-gradient( 180deg, rgba(30,111,255,0.05) 0%, rgba(30,111,255,0) 100%);
      span{
        display: inline-block;
        height: 21px;
        margin-left: 23px;
        margin-top: 24px;
        @include SourceHanSansCN-Bold;
        font-size: 16px;
        color: #363B4C;
        line-height: 21px;
      }
    }
    .search-box{
      height: 30px;
      padding: 0 24px;
      box-sizing: border-box;
      ::v-deep(.el-input){
        font-size: 14px;
        .el-input__wrapper{
          box-shadow: none;
          border-bottom: 1px solid #EEEFF0;
        }
      }
    }
    .bom-list{
      padding: 16px 0;
      height: calc(100% - 120px);
      box-sizing: border-box;
      .bom-header, .bom-list-item{
        margin: 0 16px;
        padding: 0 16px;
        height: 36px;
        display: flex;
        align-items: center;
        box-sizing: border-box;
        @include SourceHanSansCN-Regular;
        font-size: 12px;
        color: #363B4C;
        .cell:not(&:nth-last-of-type(1)){
          flex: 1;
        }
        .cell:nth-last-of-type(1){
          width: 56px;
          flex: unset;
        }
      }
      .bom-header{
        background: #F5F6F7;
        border-radius: 4px;
      }
      .bom-body{
        height: calc(100% - 36px);
        .loading{
          position: absolute;
          left: 0;
          top: 0;
          width: 100%;
          height: 100%;
          background-image: url('/jvs-ui-public/img/loading.gif');
          background-position: center;
          background-repeat: no-repeat;
          background-size: auto;
          background-color: #fff;
        }
      }
      .bom-list-item{
        border-bottom: 1px solid #EEEFF0;
        cursor: pointer;
        .cell{
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: pre;
        }
        &.active{
          background: #DDEAFF;
          color: #1E6FFF;
          border-radius: 4px;
        }
        &:hover{
          background: #DDEAFF;
          border-radius: 4px;
        }
      }
    }
  }
  .right{
    width: calc(100% - 280px);
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
      .right-box{
        width: 64px;
        height: 30px;
        background: #F0F1F2;
        border-radius: 4px;
        padding: 0 8px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        box-sizing: border-box;
        .svg-icon-box{
          width: 20px;
          height: 20px;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          svg{
            width: 16px;
            height: 16px;
          }
          &.active{
            background: #fff;
            box-shadow: 0px 2px 8px 0px rgba(73,79,106,0.15);
            border-radius: 2px;
          }
        }
      }
    }
    .bottom{
      background: #F5F6F7;
      padding: 16px 24px 16px 16px;
      height: calc(100% - 68px);
      box-sizing: border-box;
      overflow: hidden;
      .bottom-body{
        width: 100%;
        height: 100%;
        background: #fff;
        padding: 16px;
        box-sizing: border-box;
        .jvs-table{
          ::v-deep(.table-body-box){
            padding-top: 0;
          }
        }
        .map-box{
          width: 100%;
          height: 100%;
          background: #F5F6F7;
        }
      }
      &.map{
        padding: 0;
        .bottom-body{
          padding: 0;
        }
      }
    }
  }
}
.dialog-form-box{
  .select-material-code{
    width: 100%;
    height: 36px;
    cursor: pointer;
    .placeholder{
      @include SourceHanSansCN-Regular;
      font-size: 14px;
      color: #6F7588;
    }
  }
  ::v-deep(.el-form){
    .form-column-tableForm{
      .el-form-item{
        margin: 0!important;
      }
    }
  }
  .childMaterials-list{
    border-top: 8px solid #F5F6F7;
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
        &::before{
          color: var(--el-color-danger);
          content: "*";
          margin-right: 4px;
        }
      }
    }
    .slot-table-form-box{
      height: calc(70vh - 336px);
      padding: 0 24px;
      padding-bottom: 16px;
      box-sizing: border-box;
      .table-form{
        height: calc(100% - 34px);
        ::v-deep(.el-table__body-wrapper){
          min-height: unset; 
          .el-scrollbar__view{
            height: 100%;
            .el-table__empty-text::after{
              content: '请新增一个子件';
              color: var(--el-color-danger);
            }
          }
        }
      }
    }
  }
}
</style>