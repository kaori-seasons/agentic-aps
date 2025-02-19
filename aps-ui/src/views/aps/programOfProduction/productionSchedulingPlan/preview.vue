<template>
  <div class="content-box">
    <el-tabs v-model="activeName" class="demo-tabs" @tab-change="handleClick">
      <el-tab-pane v-for="tab in tabList" :key="tab.name" :label="tab.label" :name="tab.name">
        <component v-if="activeName == tab.name" :is="componentList[tab.name]" :disableHandle="true"></component>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<script lang="ts" setup name="previewInfo">
import {
  ref,
  reactive,
  watch ,
  onMounted,
  computed,
  getCurrentInstance,
  nextTick,
  shallowRef,
} from 'vue'
import { useI18n } from 'vue-i18n'
import { ElNotification } from 'element-plus'

import tabsCom from './component'
const componentList = shallowRef({
  ...tabsCom
})

const { proxy } = getCurrentInstance()
const emit = defineEmits([])
const { t } = useI18n()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

const activeName = ref('resourceGantt')
const tabList = ref([
  {label: '资源甘特图', name: 'resourceGantt'},
  {label: '订单甘特图', name: 'orderGantt'},
  {label: '物料需求甘特图', name: 'materialGantt'},
  {label: '计划任务', name: 'task'},
])

function handleClick (tab) {
  activeName.value = tab
}
</script>
<style lang="scss" scoped>
.content-box{
  height: 100%;
  ::v-deep(.el-tabs){
    --el-tabs-header-height: 50px;
    height: 100%;
    .el-tabs__header{
      margin-bottom: 16px;
      .el-tabs__nav-wrap{
        padding: 0 8px;
        .el-tabs__nav{
          .el-tabs__active-bar{
            background: #1E6FFF;
            border-radius: 2px 0px 2px 0px;
          }
          .el-tabs__item{
            padding: 0 16px;
            @include SourceHanSansCN-Regular;
            font-size: 14px;
            color: #6F7588;
            &.is-active{
              color: #1E6FFF;
              @include SourceHanSansCN-Bold;
            }
          }
        }
        &::after{
          height: 1px;
          background: #EEEFF0;
        }
      }
    }
    .el-tabs__content{
      height: calc(100% - 49px);
      .el-tab-pane{
        height: 100%;
      }
    }
  }
}
</style>