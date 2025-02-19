<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from "vue-router"

import useCommonStore from '@/store/common.js'
import {simpleStyle, darkblueStyle, darkredStyle} from '@/const/theme'
import { getStore } from '@/util/store.js'

const commonStore = useCommonStore()
const router = useRouter()
const themeStyle = ref(document.createElement("style"))

// 默认设置深蓝主题
if(!getStore({name: 'themeName'})) {
  commonStore.SET_THEME_NAME(darkblueStyle.theme)
  commonStore.SET_THEME(darkblueStyle.params)
}
changeTheme()

onMounted(()=>{
  nextTick(()=>{
    var link = document.createElement('link')
    link.type = 'image/x-icon'
    link.rel = 'shortcut icon'
    link.href = '/jvs-ui-public/img/icon.png'
    document.getElementsByTagName('head')[0].appendChild(link);
  })
})

// 切换主题
function changeTheme () {
  if(!commonStore.theme.basic) {
    return false
  }
  let head = document.getElementsByTagName("head")[0]
  head.appendChild(themeStyle.value)
  if(themeStyle.value) {
    head.removeChild(themeStyle.value)
  }
  themeStyle.value = document.createElement("style")
  themeStyle.value.type = 'text/css'
  try{
    addTheme(themeStyle.value)
  }catch(ex){
    addThemeIE(themeStyle.value)
  }
  head.appendChild(themeStyle.value)
}

// 全局 logo样式
function setLogoStyle () {
  let logoStyle =
  `
    #app {
      font-size: ${commonStore.theme.font.size};
      color: ${commonStore.theme.font.color};
    }
    .jvs-left .jvs-logo:hover{
      color: ${commonStore.theme.basic.activeFont};
      // background: ${commonStore.theme.basic.activeColor}!important;
    }
    .cardtipsbox .ulList{
      background: ${commonStore.theme.basic.themeColor};
      color: ${commonStore.theme.basic.fontColor};
    }
    .cardtipsbox .ulList div h3{
      color: ${commonStore.theme.basic.fontColor};
    }
    .cardtipsbox .ulList div ul, .cardtipsbox .ulList div ul li {
      background: ${commonStore.theme.basic.themeColor};
    }
    .cardtipsbox .ulList div ul li div a, .cardtipsbox .ulList div ul li div i{
      color: ${commonStore.theme.basic.fontColor};
    }
    .cardtipsbox .ulList div ul li:hover div{
      background: ${commonStore.theme.basic.activeColor};addThemeIE

    }
    .cardtipsbox ul li div:hover a {
      color: #fff !important
    },
    .cardtipsbox .ulList div ul li div .collected{
      color: ${commonStore.theme.basic.activeFont};
    }
  `
  return logoStyle
}

// 菜单样式
function setMenuStyle () {
  let menuStyle =
  // 正常状态
  `
    .divider-line{
      background: ${commonStore.theme.basic.fontColor};
    }
    .el-menu-scrollbar {
      background: ${commonStore.theme.basic.themeColor};
      color: ${commonStore.theme.basic.fontColor};
    }
    .el-menu-scrollbar .el-submenu__title i, .el-menu-scrollbar .el-submenu__title span{
      color: ${commonStore.theme.basic.fontColor};
      font-size: 15px;
    }
    .el-menu-scrollbar .el-menu .el-menu-item span{
      color: ${commonStore.theme.basic.fontColor};
    }
    .el-menu-scrollbar .el-menu .el-menu-item i {
      color: ${commonStore.theme.basic.fontColor};
    }
    .el-menu-scrollbar .el-menu .el-menu-item::before {
      background: ${commonStore.theme.basic.themeColor};
    }
    .el-menu-scrollbar .el-menu .el-menu-item:hover, .el-menu-scrollbar .el-menu .el-menu-item:focus{
      background: ${commonStore.theme.basic.activeColor};
    }
    .el-menu-scrollbar .el-menu .el-menu-item:hover::before{
      background: ${commonStore.theme.basic.activeBefore};
    }
    .el-menu-scrollbar .el-menu .el-menu-item:hover span, .el-menu-scrollbar .el-menu .el-menu-item:focus span {
      color: ${commonStore.theme.basic.activeFont};
    }
    .el-menu-scrollbar .el-submenu__title:hover {
      background: ${commonStore.theme.basic.activeColor};
    }
    .el-menu-scrollbar .el-submenu__title:hover span{
      color: ${commonStore.theme.basic.activeFont};
    }
    .el-menu-scrollbar .el-menu .el-menu-item:hover i, .el-menu-scrollbar .el-menu .el-menu-item:focus i{
      color: ${commonStore.theme.basic.activeBefore};
    }
    .el-menu-scrollbar .el-submenu__title:hover i:not(.el-submenu__icon-arrow){
      color: ${commonStore.theme.basic.activeBefore};
    }
    .el-menu--vertical{
      background: ${commonStore.theme.basic.themeColor};
      color: ${commonStore.theme.basic.fontColor};
    }
    .el-menu--vertical .el-menu-item span, .el-menu--vertical .el-menu-item i{
      color: ${commonStore.theme.basic.fontColor};
    }
    .el-menu--vertical .el-menu-item:hover i, el-menu--vertical .el-menu-item:hover span,
    .el-menu--vertical .el-menu-item:focus i, el-menu--vertical .el-menu-item:focus span {
      color: ${commonStore.theme.basic.fontColor};
    }
    .el-menu--vertical .el-menu-item:hover::before {
      background: ${commonStore.theme.basic.activeBefore || commonStore.theme.basic.fontColor};
    }
    .el-menu--vertical .el-menu-item:hover, .el-menu--vertical .el-menu-item:focus {
      background: ${commonStore.theme.basic.activeColor};
    }
    .el-menu-scrollbar .el-menu .is-active-item, .jvs-sidebar .el-submenu .el-menu-item.is-active-item{
      background: ${commonStore.theme.basic.activeColor};
    }
    .el-menu-scrollbar .el-menu .is-active-item::before {
      background: ${commonStore.theme.basic.activeBefore || commonStore.theme.basic.fontColor};
    }
    .el-menu-scrollbar .el-menu .is-active-item, .jvs-sidebar .el-submenu .el-menu-item.is-active-item span{
      color: ${commonStore.theme.basic.activeFont};
    }
    .jvs-sidebar .el-submenu .el-menu-item.is-active-item i{
      color: ${commonStore.theme.basic.activeBefore || commonStore.theme.basic.fontColor};
    }
  `
  return menuStyle
}

// 其他样式
function setOtherStyle () {
  // 顶部系统栏
  // 个人中心
  // 顶部菜单栏
  // 面包屑
  let otherStyle =
  `
    .jvs-tags .jvs-tags__box .lineBox{
      background-color: ${commonStore.theme.topNav.backgroundColor};
      color: ${commonStore.theme.topNav.fontColor};
    }
    .jvs-tags .jvs-tags__box .lineBox .system-list li span, .jvs-tags .jvs-tags__box .lineBox .system-list li i{
      color: ${commonStore.theme.topNav.fontColor};
    }
    .jvs-tags .jvs-tags__box .lineBox .system-list .activeSysItem{
      color: ${commonStore.theme.topNav.activeColor};
      background-color: ${commonStore.theme.topNav.activeBackgroundColor};
    }
    .jvs-tags .jvs-tags__box .lineBox .system-list .activeSysItem span{
      color: ${commonStore.theme.topNav.activeColor};
    }
    .el-dropdown-menu .el-dropdown-menu__item:hover {
      background-color: ${commonStore.theme.basic.activeColor};
      color: ${commonStore.theme.basic.activeFont};
    }
  `
  return otherStyle
}

// 加入主题样式
function addTheme (themeStyle) {
  // 菜单
  themeStyle.appendChild(document.createTextNode(setMenuStyle()))
  // logo
  themeStyle.appendChild(document.createTextNode(setLogoStyle()))
  // 其他
  themeStyle.appendChild(document.createTextNode(setOtherStyle()))
}

// 加入主题兼容ie
function addThemeIE (themeStyle) {
  // 菜单
  themeStyle.innerHTML = setMenuStyle()
  // logo
  themeStyle.innerHTML= setLogoStyle()
  // 其他
  themeStyle.innerHTML = setOtherStyle()
}
</script>

<template>
   <div class="basic-cont-box">
    <router-view />
  </div>
</template>

<style scoped lang="scss">
.basic-cont-box{
  // padding: 8px 10px;
  position: relative;
  height: 100%;
  box-sizing: border-box;
}
</style>
