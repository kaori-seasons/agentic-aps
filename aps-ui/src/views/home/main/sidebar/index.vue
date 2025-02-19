<template>
  <div class="jvs-sidebar">
    <el-scrollbar class="el-menu-scrollbar">
      <el-menu
        :default-active="nowTagValue"
        mode="vertical"
        :show-timeout="200"
        :collapse="commonStore.isCollapse"
        ref="menuRef"
      >
        <sidebar-item
          :menu="menuChildren"
          :screen="commonStore.screen"
          first
          :props="commonStore.website.menu.props"
          :collapse="commonStore.isCollapse"
          @freshAllMenu="freshAllMenu"
          @topNavChange="topNavChange"
        ></sidebar-item>
      </el-menu>
    </el-scrollbar>
    <!-- 收缩菜单 logo -->
    <div :class="{'brevity-button': true, 'brevity': isBrevity}" @click="changeBrevity">
      <svg class="svg-icon" aria-hidden="true">
        <use :xlink:href="`#${!isBrevity ? 'icon-jvs-shouqi' : 'icon-jvs-shousuo'}`"></use>
      </svg>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {
  ref,
  reactive,
  toRefs,
  watch,
  onMounted,
  computed,
  getCurrentInstance,
} from 'vue'
import { useRouter, useRoute } from 'vue-router'

import { getStore } from '@/util/store/js'
import useCommonStore from '@/store/common.js'
import useTagsStore from '@/store/tags.js'
import jvsRouter from '@/router/jvs-router'
import sidebarItem from './sidebarItem.vue'

const router = useRouter()
const route = useRoute()
const commonStore = useCommonStore()
const tagsStore = useTagsStore()
const $jvsRouter = new jvsRouter()
const emit = defineEmits(['freshAllMenu', 'reGetAllMenu', 'topNavChange'])

const props = defineProps({
  isCollapse: {
    type: Boolean,
    default: false
  },
  freshSide: {
    type: Number
  },
})

const nowTagValue = computed(() => {
  let str =  $jvsRouter.formatMenuPath(route)
  return str
})

const menuChildren = ref([])
const isBrevity = ref(false)

// 刷新所有系统菜单
function freshAllMenu (bool) {
  emit('freshAllMenu', bool)
}

function changeBrevity () {
  isBrevity.value = !isBrevity.value
  commonStore.SET_COLLAPSE()
}

function topNavChange (id) {
  emit('topNavChange', id)
}

watch(() => props.freshSide, (newVal, oldVal) => {
  if(newVal != -1) {
    emit('reGetAllMenu', true)
  }
})
</script>
<style lang="scss" scoped>
.divider-line{
  height: 1px;
  background: #303133;
  opacity: 0.2;
  display: none;
}
.el-menu-scrollbar {
  height: 100%;
  position: relative;
  overflow: unset;
  background: #F5F6F7;
  .app-item-info{
    height: 64px;
    font-size: 15px;
    display: flex;
    align-items: center;
    padding: 8px 12px;
    cursor: pointer;
    display: flex;
    justify-content: space-between;
    box-sizing: border-box;
    background: linear-gradient(179deg, rgba(30,111,255,0.05) 0%, rgba(30,111,255,0) 100%);
  }
  .app-item-info-onlyshow{
    cursor: auto;
    .text-info{
      flex: 1;
    }
  }
}
.add-catalogue{
  padding: 6px 38px;
  cursor: pointer;
  transition: 0.3s;
  &:hover{
    transition: 0.3s;
    background-color: #eff2f7;
  }
}
::v-deep(.el-popper){
  .more-box{
    padding: 0 6px;
    cursor: pointer;
  }
}
.custom-header-dialog-body-box{
  ::v-deep(.jvs-form){
    margin-top: 16px;
    .el-form-item:not(.form-btn-bar) {
      margin-left: 20px;
      margin-right: 20px;
    }
    .el-form-item__label{
      font-family: Microsoft YaHei-Bold, Microsoft YaHei;
      font-weight: 700;
      font-size: 14px;
      color: #363B4C;
    }
    .form-item-btn{
      border-top: 1px solid #EEEFF0;
      height: 60px;
      line-height: 60px;
      .form-btn-bar{
        height: 100%;
        margin: 0;
        margin-right: 24px;
        display: flex;
        justify-content: flex-end;
        align-items: center;
        .el-form-item__content{
          line-height: 60px;
          display: flex;
          flex-direction: row-reverse;
          .el-button{
            margin-left: 16px;
          }
        }
      }
    }
  }
}
</style>
<style lang="scss">
.jvs-sidebar{
  position: relative;
  height: 100%;
  overflow: hidden;
  .el-menu{
    border-right: 0;
    .menu-wrapper{
      .el-menu-item, .el-submenu__title {
        height: 36px;
        line-height: 36px;
      }
      .menu-item-li{
        box-sizing: border-box;
        span{
          overflow: hidden;
          white-space: pre;
          text-overflow: ellipsis;
        }
      }
    }
  }
  .el-menu-item, .el-submenu__title {
    font-size: 14px;
  }
  .brevity-button{
    cursor: pointer;
    position: absolute;
    right: 0;
    bottom: 55px;
    width: 32px;
    height: 32px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all .5s;
    background-color: #fff;
    .svg-icon{
      width: 13px;
      height: 13px;
    }
  }
}
</style>

