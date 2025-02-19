<template>
  <div class="menu-wrapper">
    <div class="notmove local-menu">
      <div v-for="(item, key) in localMenu" :key="key">
        <div class="menu-item-head" @click="openClose(item)">
          <svg :class="{'openclose-icon': true, 'trans': collapseBool[item.langKey]}" aria-hidden="true">
            <use xlink:href="#jvs-ui-icon-a-zu6027"></use>
          </svg>
          <svg v-if="item.icon" class="svg-icon" aria-hidden="true" style="width: 16px;height: 16px;margin-right: 8px;">
            <use :xlink:href="`#${item.icon}`"></use>
          </svg>
          <i v-else class="el-icon-menu" style="font-size: 18px;margin-right: 8px;"></i>
          <span>{{ t(`localMenu.${item.langKey}`) || item.name }}</span>
        </div>
        <el-collapse-transition>
          <div v-show="!collapseBool[item.langKey]">
            <el-menu-item
              v-for="(it, itindex) in item.children"
              :key="'navmenu'+itindex"
              :index="it.extend.url"
              @click="open(it.extend, it)"
              :class="{'menu-item-li':true, 'is-active-item': vaildAvtive(it.extend)}"
            >
              <el-popover v-if="collapse" placement="right" trigger="hover" :content="$t(`localMenu.${it.langKey}`) || it.extend.name" popper-class="custom-right-tool-poper">
                <template #reference>
                  <div>
                    <svg v-if="it.extend.icon" class="svg-icon" aria-hidden="true" style="width: 16px;height: 16px;">
                      <use :xlink:href="`#${it.extend.icon}`"></use>
                    </svg>
                    <div v-else class="word-icon">{{$t(`localMenu.${it.langKey}`)[0] || it.extend.name[0]}}</div>
                  </div>
                </template>
              </el-popover>
              <div v-else style="display: flex;align-items: center;">
                <svg v-if="it.extend.icon" class="svg-icon" aria-hidden="true" style="width: 16px;height: 16px;margin-right: 8px;">
                  <use :xlink:href="`#${it.extend.icon}`"></use>
                </svg>
                <b v-else class="local-menu-item-dot"></b>
                <span :title="$t(`localMenu.${it.langKey}`) || it.extend.name" :alt="it.extend.url">{{$t(`localMenu.${it.langKey}`) || it.extend.name}}</span>
              </div>
            </el-menu-item>
          </div>
        </el-collapse-transition>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup name="sidebarItem">
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
import { useI18n } from 'vue-i18n'

import useCommonStore from '@/store/common.js'
import jvsRouter from '@/router/jvs-router'

import { localMenuData } from './localMenu.js'
import configData from './config.js'

const { $openUrl }  = getCurrentInstance().appContext.config.globalProperties
const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const commonStore = useCommonStore()
const $jvsRouter = new jvsRouter()
const emit = defineEmits([])
const collapseBool = ref({})

const props = defineProps({
  menu: {
    type: Array
  },
  screen: {
    type: Number
  },
  first: {
    type: Boolean,
    default: false
  },
  props: {
    type: Object,
    default: () => {
      return {};
    }
  },
  collapse: {
    type: Boolean
  },
})

const config = ref(configData)

const labelKey = computed(() => {
  return props.props.label || config.value.propsDefault.label
})
const pathKey = computed(() => {
  return props.props.path
})
const nowTagValue = computed(() => {
  return $jvsRouter.formatMenuPath(route)
})
const localMenu = computed(() => {
  let temp = localMenuData.defaultMenu
  let list = []
  temp.filter(tp => {
    if(tp.children && tp.children.length > 0) {
      let obj = {
        name: tp.name,
        icon: tp.icon,
        langKey: tp.langKey,
        children: []
      }
      collapseBool.value[tp.langKey] = false
      tp.children.filter(cit => {
        obj.children.push(cit)
      })
      if(obj.children && obj.children.length > 0) {
        list.push(obj)
      }
    }
  })
  return list
})

onMounted(() => {
  // 防止火狐浏览器拖拽的时候以新标签打开
  document.body.ondrop = function (event) {
    event.preventDefault()
    event.stopPropagation()
  }
})

function vaildAvtive (item) {
  const groupFlag=(item["group"]||[]).some(ele => route.path.includes(ele))
  if(item[pathKey.value] || groupFlag) {
    return nowTagValue.value === item[pathKey.value] || groupFlag
  }else{
    if(nowTagValue.value.includes('?') && nowTagValue.value.startsWith('/')) {
      let tp = nowTagValue.value.split('?')[1]
      let tarr = tp.split('&')
      let boolTemp = true
      for(let i in tarr) {
        let oba = tarr[i].split('=')
        if(item[oba[0]] != oba[1] && JSON.stringify(item[oba[0]]) != oba[1]) {
          boolTemp = false
        }
      }
      return boolTemp
    }else{
      return false
    }
  }
}

function open (item, data) {
  // 重复点击不跳转
  let tempStr = ""
  if(item.url.indexOf('#') > -1){
    tempStr = (item.url && ('#' + item.url.split('#')[1])) || ''
  }else{
    let indx = item.url.indexOf('-ui')
    if(indx > -1) {
      tempStr = item.url.slice(0, indx+3) + '/#' + item.url.slice(indx+3, item.url.length)
    }
  }
  if(route.hash && route.query && route.query.src  && tempStr == (route.query.src  + route.hash)) {
    return false
  }
  if(props.screen <= 1) commonStore.SET_COLLAPSE()
  $jvsRouter.group = item.group
  if(item.newWindow === true) {
    if(item.url.includes('http') || item.url.includes('https') || item.url.includes('ftp')) {
      $openUrl(item[pathKey.value], '_blank')
    }else{
      if(item.url.indexOf('-ui') == -1) {
        $openUrl(item[pathKey.value], '_blank')
      }else{
        let tinx = item.url.indexOf('-ui')
        let tpStr = item.url.slice(0, (tinx+3)) + '/#' + item.url.slice(tinx+3, item.url.length)
        $openUrl(tpStr, '_blank')
      }
    }
  }else{
    let rpath = $jvsRouter.getPath({
      name: item[labelKey.value],
      src: item[pathKey.value]
    })
    router.push({
      path: rpath.path,
      query: Object.assign((item.query || {}), rpath.query),
      params: item.params || {}
    })
  }
}

function openClose (item) {
  collapseBool.value[item.langKey] = !collapseBool.value[item.langKey]
}
</script>
<style lang="scss">
.menu-item-li{
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  position: relative;
  border-radius: 6px;
  box-sizing: border-box;
  .more{
    position: absolute;
    right: 0px;
    display: none;
  }
  .el-tooltip{
    display: flex!important;
    align-items: center;
  }
  .dragicon{
    cursor: move;
    position: absolute;
    right: 0;
    display: none;
  }
}
.menu-item-li:hover .dragicon{
  display: block;
}
.menu-item-li:hover .more{
  display: block;
}
.menu-item-li:hover .menu-item-it{
  overflow:hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.notmove{
  margin: 0 8px;
  padding-bottom: 10px;
  li{
    margin-top: 8px;
    .local-menu-item-dot{
      display: inline-block;
      width: 0;
      height: 0;
      border: 2px solid #363B4C;
      border-radius: 50%;
      border-color: transparent;
    }
    .svg-icon{
      fill: #363B4C;
    }
    &:hover{
      .svg-icon{
        fill: #1E6FFF;
      }
    }
  }
  .menu-item-li.is-active-item{
    background: #DDEAFF;
    color: #1E6FFF;
    span{
      color: #1E6FFF!important;
    }
    .local-menu-item-dot{
      // border-color: #1E6FFF;
    }
    .svg-icon{
      fill: #1E6FFF;
    }
    .menu-item-it{
      color: #303133;
    }
  }
  .el-submenu__title{
    border-radius: 6px;
  }
  .catalogue-item{
    height: 36px;
    line-height: 36px;
    display:flex;
    align-items:center;
    width: calc(100% - 10px);
    justify-content: space-between;
    .more{
      display: none;
      .el-popover__reference-wrapper{
        display: flex;
      }
    }
  }
  .catalogue-item:hover{
    .more{
      display: block;
    }
  }
}
.local-menu{
  margin: 0;
  background: linear-gradient(179deg, rgba(30,111,255,0.05) 0%, rgba(30,111,255,0) 64px);
  padding-top: 8px;
  >div{
    margin-bottom: 8px;
  }
  .menu-item-head{
    margin-top: 16px;
    font-size: 14px;
    color: #363B4C;
    height: 18px;
    box-sizing: border-box;
    line-height: 18px;
    display: flex;
    align-items: center;
    @include SourceHanSansCN-Regular;
    cursor: pointer;
    .openclose-icon{
      width: 14px;
      height: 14px;
      margin-left: 8px;
      margin-right: 9px;
      
      transition: all .3s;
      fill: #363B4C;
      &.trans{
        transform: rotate(-90deg);
      }
    }
  }
  li{
    margin-left: 8px;
    margin-right: 8px;
    .local-menu-item-dot{
      margin-right: 24px;
    }
  }
}
.menu-ghost {
  background: #e5e5e5 !important;
  div {
    visibility: hidden;
  }
}
.menu-children-ghost {
  border-left: 3px solid #e5e5e5 !important;
  background: #e5e5e5;
  * {
    visibility: hidden;
    //display: none;
  }
}
</style>

