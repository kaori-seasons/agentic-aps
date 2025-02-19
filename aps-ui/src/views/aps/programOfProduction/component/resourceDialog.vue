<template>
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
        :option="resourceOption"
        :formData="resourceParams"
        :loading="resourceLoading"
        :data="resourceData"
        :page="resourcePage"
        @on-load="getResourceHandle"
        @search-change="searchResChange"
        @row-click="clickRowHandle"
      >
        <template #capacity="scope">
          <div>{{(scope.row.capacity ? `${scope.row.capacity}${scope.row.unit}` : '') || '--'}}</div>
        </template>
      </jvs-table>
    </div>
  </el-dialog>
</template>
<script lang="ts" setup name="resourceDialog">
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

import { getResourceList } from './api'

const emit = defineEmits(['row-click', 'close'])
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
})

const resourceVisible = ref(false)
const resourceLoading = ref(false)
const resourceParams = ref({})
const resourceData = ref([])
const resourcePage = ref({
  total: 0, // 总页数
  currentPage: 1, // 当前页数
  pageSize: 20, // 每页显示多少条
})
const resourceOption = ref({
  page: true,
  search: true,
  cancal: false,
  showOverflow: true,
  addBtn: false,
  menu: false,
  column: [
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
  ]
})

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

function clickRowHandle (data) {
  let { row } = data
  emit('row-click', row)
  resourceClose()
}

function resourceClose () {
  resourceVisible.value = false
  emit('close')
}

watch(() => props.visible, (newVal, oldVal) => {
  resourceVisible.value = newVal
})
</script>
<style lang="scss" scoped>
.dialog-page-box{
  height: calc(70vh - 48px);
  padding: 20px;
  box-sizing: border-box;
  overflow: hidden;
}
</style>