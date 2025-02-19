<template>
  <div :class="{
    'jvs-contail jvs-contail-font': true,
    'jvs--collapse': commonStore.isCollapse,
    'jvs-contail-loading': !aleadyGeted,
    'match-loading': matchingLoading,
  }">
    <!-- 顶部导航栏 -->
    <topBar 
      v-if="aleadyGeted"
      ref="tags"
      @changeTheme="changeTheme"
    >
      <!-- logo -->
      <template #left>
        <logo ref="menuLogo" @childMenu="childMenu" @closeOther="closeOther"></logo>
      </template>
    </topBar>
    <div :class="{'jvs-layout': true, 'jvs-layout-tempOpen': false}" :style="`height:calc(100% - ${commonStore.theme.logo.height});`">
      <!-- 顶部标签卡 -->
      <div class="jvs-left" :style="'width:'+ commonStore.theme.logo.width">
        <!-- 左侧导航栏 -->
        <sidebar
          ref="sideBar"
          v-if="aleadyGeted" 
          :menuType="menuType"
          :isCollapse="commonStore.isCollapse"
          @freshAllMenu="freshAllMenu"
          @closeOther="closeOther"
          @topNavChange="topNavChange"
          @reGetAllMenu="reGetAllMenu">
        </sidebar>
      </div>
      <div v-show="aleadyGeted" class="jvs-main" :style="'width:calc(100% - '+commonStore.theme.logo.width+');left:'+commonStore.theme.logo.width+';'">
        <div :class="{'jvs-main-loading': !alreadyLoad}"></div>
        <!-- 主体视图层 -->
        <div v-if="alreadyLoad" style="height:100%">
          <keep-alive v-if="$route.meta.$keepAlive">
            <router-view class="jvs-view" @openEvent="openEvent" />
          </keep-alive>
          <router-view v-if="!$route.meta.$keepAlive" class="jvs-view" @openEvent="openEvent" />
        </div>
      </div>
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
const emit = defineEmits(['changeTheme', 'closeOther'])
import useCommonStore from '@/store/common.js'
import { getStore } from '@/util/store.js'
import { validatenull } from '@/util/validate.js'
import { getScreen } from '@/util/admin.js'

import topBar from '@/views/topBar/index.vue'
import logo from '@/views/home/main/logo.vue'
import sidebar from '@/views/home/main/sidebar/index.vue'

const router = useRouter()
const route = useRoute()
const commonStore = useCommonStore()

const tags = ref()
const menuLogo= ref()

const dialogVisible = ref(false)
const imgVisible = ref(false)
const bulletin = ref({})
const refreshLock = ref(false)
const refreshTime = ref()
const systemId = ref('-1')
const alreadyLoad = ref(true)
const thisSystem = ref(null)
const tempOpen = ref(false)
const tempMoreOpen = ref(false)
const menuType = ref('platform')
const freshAllMenuBool = ref(-1)
const aleadyGeted = ref(true)
const menuChildren = ref([])

onMounted(() => {
  init()
})

function handleClose() {
  dialogVisible.value = false
}

function showCollapse () {
  commonStore.SET_COLLAPSE()
}

// 屏幕检测
function init () {
  commonStore.SET_SCREEN(getScreen())
  window.onresize = () => {
    setTimeout(() => {
      commonStore.SET_SCREEN(getScreen())
    }, 0);
  }
}

// 通知改变主题
function changeTheme (bool) {
  emit('changeTheme', bool)
}

// 刷新所有菜单
function freshAllMenu (bool) {
  if(bool) {
    freshAllMenuBool.value = Math.random()
  }
}

function openEvent (data) {
  if(typeof data == 'string') {
    // switch(data) {
    //   default: ;break;
    // }
  }
}

// 设置点击的子菜单
function childMenu (obj) {
  menuChildren.value = obj
}

// 点击了logo关闭应用相关
function closeOther (bool) {
  emit('closeOther', bool)
}

function topNavChange (id) {
  tags.value.entrySystem({id: id}, true)
}

function reGetAllMenu () {
  menuLogo.value.getAllMenuList()
}

</script>
<style lang="scss" scoped>
$collapseWidth: 64px;
$collapseJvsMainWidth: calc(100% - 64px);
.jvs-contail{
  height: 100%;
  .jvs-layout{
    position: relative;
    .jvs-left{
      height: 100%;
    }
    .jvs-main{
      position: absolute;
      top: 0;
      height: 100%;
    }
  }
}
.jvs-contail-loading{
  width: 100%;
  height: 100%;
  background-image: url('/jvs-ui-public/img/loading.gif');
  background-position: center;
  background-repeat: no-repeat;
  background-size: auto;
  background-color: #fff;
}
.jvs-main-loading{
  position: absolute;
  width: 100%;
  height: 100%;
  background-image: url('/jvs-ui-public/img/loading.gif');
  background-position: center;
  background-repeat: no-repeat;
}
.jvs-layout-tempOpen{
  ::v-deep(.el-menu-scrollbar){
    display: none;
  }
  .jvs-main{
    display: none;
  }
  .template-content-box{
    position: absolute;
    width: 100%;
    z-index: 1025;
  }
}
::v-deep(.dialog-box){
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  width: 100vw;
  z-index: 9999;
  text-align: center;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 6px;
  img{
    max-width: 800px;
    min-width: 400px;
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translateY(-50%) translateX(-50%);
    z-index: 10000;
  }
}
.jvs-contail.only-app{
  .jvs-tags{
    display: none;
  }
  .jvs-layout{
    .jvs-left{
      ::v-deep(.jvs-logo){
        display: none;
      }
      ::v-deep(.el-menu-scrollbar){
        height: 100%!important;
        margin-top: 0!important;
      }
    }
    .jvs-main{
      top: 0!important;
      height: 100%!important;
    }
  }
  &.match-loading{
    background-image: url('/jvs-ui-public/img/loading.gif');
    background-position: center;
    background-repeat: no-repeat;
    background-size: auto;
    background-color: #fff;
    .jvs-layout{
      visibility: hidden;
    }
  }
}
</style>