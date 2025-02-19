<template>
  <div class="basic-layout-box aps-productionEngineering-routing">
    <div class="left">
      <div class="title-box">
        <span>{{t(`aps.productionEngineering.routing.leftTitle`)}}</span>
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
        <el-scrollbar class="bom-body">
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
      <div v-if="tableData && tableData.routeDesign && tableData.routeDesign.nodes" class="map-box">
        <relationMap :data="mapData" :saveLoading="saveLoading" @save="saveHandle">
          <template #right>
            <div class="right-slot">
              <el-button v-if="tableData && tableData.id" plain :loading="enabledLoading" @click="enabledHandle">{{(tableData && tableData.enabled) ? t(`aps.productionEngineering.routing.disable`) : t(`aps.productionEngineering.routing.enable`)}}</el-button>
              <el-popconfirm
                v-if="tableData && tableData.id"
                :title="t('common.deleteConfirm')"
                :icon="WarningFilled"
                :width="300"
                @confirm="delHandle"
              >
                <template #reference>
                  <el-button plain type="danger" :loading="enabledLoading">{{t(`table.delete`)}}</el-button>
                </template>
              </el-popconfirm>
            </div>
          </template>
        </relationMap>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup name="routing">
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

import relationMap from './map.vue'

import { getProducedList, getList, add, edit, enable, disable, del } from './api'
import { table } from 'console'

const { proxy } = getCurrentInstance()
const emit = defineEmits([])
const { t } = useI18n()
const commonStore = useCommonStore()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

const mapData = computed(() => {
  let data = {}
  if(tableData.value && tableData.value['routeDesign'] && tableData.value['routeDesign']) {
    data = {
      nodes: tableData.value['routeDesign'].nodes.filter(node => {
        node.data['nodeId'] = node.id
        return node
      }),
      edges: tableData.value['routeDesign'].edges,
    }
  }
  return data
})

const keyword = ref('')
const currentBom = ref({})
const bomLoading = ref(false)
const bomOption = ref({
  column: [
    {
      label: '物料名称',
      prop: 'name'
    },
    {
      label: '物料编码',
      prop: 'code'
    }
  ]
})
const bomList = ref([])
const tableData = ref(null)
const saveLoading = ref(false)
const enabledLoading = ref(false)

getBomList()

function getBomList () {
  let obj = {
    current: 1,
    size: 5000
  }
  if(keyword.value) {
    obj['keywords'] = keyword.value
  }
  bomLoading.value = true
  bomList.value = []
  getProducedList(obj).then(res => {
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
  }else{
    currentBom.value = JSON.parse(JSON.stringify(bom))
  }
  getListHandle()
}

function getListHandle () {
  tableData.value = {}
  if(currentBom.value && currentBom.value['id']) {
    getList(currentBom.value['id']).then(res => {
      if(res.data && res.data.code == 0) {
        if(!res.data.data.routeDesign) {
          res.data.data.routeDesign = {
            nodes: [],
            edges: []
          }
        }
        tableData.value = res.data.data
      }
    })
  }
}

function saveHandle (data) {
  let edges = []
  if(data.edges && data.edges.length > 0) {
    data.edges.filter(edge => {
      edges.push({
        source: edge.source,
        target: edge.target
      })
    })
  }
  let submitData = {
    materialId: tableData.value.materialId,
    routeDesign: {
      edges: edges,
      nodes: data.nodes || []
    }
  }
  if(tableData.value && tableData.value.id) {
    submitData['id'] = tableData.value.id
  }
  let fun = add
  if(submitData['id']) {
    fun = edit
  }
  saveLoading.value = true
  fun(submitData).then(res => {
    if(res.data && res.data.code == 0) {
      ElNotification.closeAll()
      ElNotification({
        title: t(`common.tip`),
        message: (submitData['id'] ? t(`aps.productionEngineering.routing.edit`) : t(`aps.productionEngineering.routing.add`)) + t(`aps.productionEngineering.routing.success`),
        position: 'bottom-right',
        type: 'success',
      })
      saveLoading.value = false
      getListHandle()
    }
  }).catch(e => {
    saveLoading.value = false
  })
}

function enabledHandle () {
  let fun = enable
  if(tableData.value && tableData.value.enabled) {
    fun = disable
  }
  enabledLoading.value = true
  fun(tableData.value.id).then(res => {
    if(res.data && res.data.code == 0) {
      ElNotification.closeAll()
      ElNotification({
        title: t(`common.tip`),
        message: (tableData.value['enabled'] ? t(`aps.productionEngineering.routing.disable`) : t(`aps.productionEngineering.routing.enable`)) + t(`aps.productionEngineering.routing.success`),
        position: 'bottom-right',
        type: 'success',
      })
    }
    enabledLoading.value = false
    getListHandle()
  }).catch(e => {
    enabledLoading.value = false
  })
}

function delHandle () {
  del(tableData.value.id).then(res => {
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
</script>
<style lang="scss" scoped>
.aps-productionEngineering-routing{
  width: 100%;
  height: 100%;
  padding: 0!important;
  display: flex;
  box-sizing: border-box;
  overflow: hidden;
  .left{
    width: 280px;
    height: 100%;
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
    background: #F5F6F7;
    .map-box{
      width: 100%;
      height: 100%;
      overflow: hidden;
    }
    .right-slot{
      display: flex;
      align-items: center;
      margin-left: 10px;
      .el-button.is-plain{
        width: 76px;
        height: 36px;
        background: var(--el-color-primary-light-9);
        color: var(--el-color-primary);
        border: 1px solid var(--el-color-primary-light-5);
        &.el-button--danger{
          background: var(--el-color-danger-light-9);
          color: var(--el-color-danger);
          border-color: var(--el-color-danger-light-5);
        }
      }
    }
  }
}
</style>