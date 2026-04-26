<template>
  <div class="basic-layout-box aps-system-operation-audit">
    <jvs-table
      :option="tableOption"
      :formData="queryParams"
      :loading="tableLoading"
      :data="tableData"
      :page="page"
      @on-load="getListHandle"
      @search-change="searchChange"
    >
      <template #success="scope">
        <div :class="['status-tag', scope.row.success ? 'success' : 'fail']">
          {{ scope.row.success ? '成功' : '失败' }}
        </div>
      </template>
    </jvs-table>
  </div>
</template>

<script lang="ts" setup name="operationAudit">
import { ref } from 'vue'
import { getList } from './api'

const queryParams = ref({})
const tableLoading = ref(false)
const tableData = ref([])
const page = ref({
  total: 0,
  currentPage: 1,
  pageSize: 20,
})
const tableOption = ref({
  page: true,
  search: true,
  cancal: false,
  showOverflow: true,
  addBtn: false,
  menu: false,
  viewBtn: false,
  editBtn: false,
  column: [
    {
      label: '用户名',
      prop: 'username',
      placeholder: '请输入用户名',
      search: true,
      searchSpan: 4,
    },
    {
      label: '用户ID',
      prop: 'userId',
      placeholder: '请输入用户ID',
      search: true,
      searchSpan: 4,
    },
    {
      label: '操作名称',
      prop: 'operationName',
      placeholder: '请输入操作名称',
      search: true,
      searchSpan: 4,
    },
    {
      label: '权限编码',
      prop: 'permissionCode',
      placeholder: '请输入权限编码',
      search: true,
      searchSpan: 4,
    },
    {
      label: '执行结果',
      prop: 'success',
      placeholder: '请选择执行结果',
      search: true,
      searchSpan: 4,
      slot: true,
      type: 'select',
      dicData: [
        { label: '成功', value: true },
        { label: '失败', value: false },
      ],
    },
    {
      label: '请求路径',
      prop: 'requestUri',
    },
    {
      label: 'HTTP方法',
      prop: 'httpMethod',
    },
    {
      label: '客户端IP',
      prop: 'clientIp',
    },
    {
      label: '耗时(ms)',
      prop: 'durationMs',
    },
    {
      label: '失败原因',
      prop: 'errorMessage',
    },
    {
      label: '创建时间',
      prop: 'createTime',
    },
  ]
})

function getListHandle(pageInfo?) {
  tableLoading.value = true
  const params = {
    current: pageInfo && pageInfo.current ? pageInfo.current : page.value.currentPage,
    size: page.value.pageSize,
  }
  getList(Object.assign({}, queryParams.value, params)).then(res => {
    if (res.data && res.data.code === 0 && res.data.data) {
      tableData.value = res.data.data.records
      page.value.currentPage = res.data.data.current
      page.value.total = res.data.data.total
    }
    tableLoading.value = false
  }).catch(() => {
    tableLoading.value = false
  })
}

function searchChange(form) {
  queryParams.value = form
  getListHandle()
}
</script>

<style lang="scss" scoped>
.aps-system-operation-audit{
  .status-tag{
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 52px;
    height: 24px;
    padding: 0 10px;
    border-radius: 12px;
    font-size: 12px;
    &.success{
      color: #159947;
      background: rgba(21, 153, 71, 0.12);
    }
    &.fail{
      color: #d33b3b;
      background: rgba(211, 59, 59, 0.12);
    }
  }
}
</style>
