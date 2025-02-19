<template>
  <div class="top-nav-bar-box" @click="hideContextMenu">
    <!-- 顶部tab关闭按钮 -->
    <div
      v-if="contextmenuFlag"
      class="jvs-tags__contentmenu"
      :style="{left:contentmenuX+'px',top:contentmenuY+'px'}"
    >
      <div class="item" @click="closeOthersTags">关闭其他</div>
      <div class="item" @click="closeAllTags">关闭全部</div>
    </div>
    <el-tabs
      v-model="active"
      type="card"
      :style="'flex:1;width:100%;'"
      @contextmenu="handleContextmenu"
      @tab-change="openTag"
      @edit="menuTag"
    >
      <el-tab-pane
        v-for="(item, index) in tagsStore.tagList"
        :key="(item.hash+index) || (item.name+index)"
        :label="item.label"
        :closable="item.label!=='首页' && index != 0"
        :name="item.hash ? (item.value + item.hash) : item.value"
      >
        <template #label>
          <svg v-if="item.label=='首页' && index == 0" class="icon" aria-hidden="true" style="width: 16px;height: 16px;">
            <use :xlink:href="(active == '/wel/index') ? '#icon-jvs-shouye-xuanzhong' : '#icon-jvs-shouye-weixuanzhong'"></use>
          </svg>
          <span v-else>{{ (item.hash && item.value == '/jvs-aps-ui/') ? $langt(`localMenu.${localmenuLang[item.value+item.hash]}`) : item.label}}</span>
        </template>
      </el-tab-pane>
    </el-tabs>
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
import useCommonStore from '@/store/common.js'
import useTagsStore from '@/store/tags.js'
import jvsRouter from '@/router/jvs-router'

const emit = defineEmits([])
const props = defineProps({
  menuNavClose: {
    type: Number
  }
})

const { proxy } = getCurrentInstance()
const router = useRouter()
const route = useRoute()
const commonStore = useCommonStore()
const tagsStore = useTagsStore()
const $jvsRouter = new jvsRouter()

const tagLen = computed(() => {
  return tagsStore.tagList.length || 0
})

const active = ref('')
const contextmenuFlag = ref(false)
const contentmenuX = ref('')
const contentmenuY = ref('')

onMounted(() => {
  setActive()
})

function watchContextmenu (event) {
  if(!proxy.$el.contains(event.target) || event.button!==0 ) {
    contextmenuFlag.value = false
  }
  window.removeEventListener("mousedown", watchContextmenu)
}

function handleContextmenu (event) {
  let target = event.target
  let flag = false
  if(typeof target.className == 'string' && target.className.indexOf("el-tabs__item")>-1) {
    flag = true
  }else if(typeof target.parentNode.className == 'string' && target.parentNode.className.indexOf("el-tabs__item")>-1) {
    target = target.parentNode
    flag = true
  }else if(typeof target.parentNode.className == 'object' && target.parentNode.parentNode.className.indexOf("el-tabs__item")>-1) {
    target = target.parentNode.parentNode
    flag = true
  }
  if(flag) {
    event.preventDefault()
    event.stopPropagation()
    contentmenuX.value = event.clientX
    contentmenuY.value = event.clientY
    contextmenuFlag.value = true
  }else{
    contextmenuFlag.value = false
  }
  // event.stopPropagation()
}

//激活当前选项
function setActive () {
  active.value = tagsStore.tag.hash ? (tagsStore.tag.value + tagsStore.tag.hash) : tagsStore.tag.value
}

function menuTag (value, action) {
  if(action === 'remove') {
    let { tag, key } = findTag(value)
    tagsStore.DEL_TAG(tag)
    if(tag.hash) {
      if (tag.hash === tagsStore.tag.hash) {
        tag = tagsStore.tagList[key === 0 ? key : key-1] //如果关闭本标签让前推一个
        openTag(tag.value)
      }
    }else{
      if(tag.value === tagsStore.tag.value) {
        tag = tagsStore.tagList[key === 0 ? key : key-1] //如果关闭本标签让前推一个
        openTag(tag.value)
      }
    }
  }
}

function openTag (name) {
  // 重复点击不处理
  if(name == tagsStore.tag.value + tagsStore.tag.hash) {
    return false
  }
  let tag;
  if(name) {
    tag = findTag(name).tag
  }else{
    tag = name
  }
  if(tag.label == '首页') {
    tag = commonStore.website.fistPage
  }
  let rpath = $jvsRouter.getPath({
    name: tag.label,
    src: tag.value + tag.hash
  })
  router.push({
    path: rpath.path,
    query: tag.label == '首页' ? {} : Object.assign(tag.query, rpath.query)
  })
}

function closeOthersTags () {
  contextmenuFlag.value = false
  tagsStore.DEL_TAG_OTHER()
}

function findTag (value) {
  let tag, key;
  tagsStore.tagList.map((item, index) => {
    if(item.hash) {
      if((item.value + item.hash) === value) {
        tag = item
        key = index
      }
    }else{
      if(item.value === value) {
        tag = item
        key = index
      }
    }
  });
  return { tag: tag, key: key }
}

function closeAllTags () {
  contextmenuFlag.value = false
  tagsStore.DEL_ALL_TAG()
  if(tagsStore.tag.value != '/wel/index') {
    let rpath = $jvsRouter.getPath({
      src: tagsStore.tagWel.value
    })
    router.push({
      path: rpath.path,
      query: Object.assign(tagsStore.tagWel.query, rpath.query)
    })
  }
}

function hideContextMenu () {
  if(contextmenuFlag.value) {
    contextmenuFlag.value = false
  }
}

watch(() => tagsStore.tag, () => {
  setActive()
})

watch(() => contextmenuFlag, () => {
  window.addEventListener("mousedown", watchContextmenu)
})
watch(() => props.menuNavClose, (newVal, oldVal) => {
  if(newVal > -1) {
    hideContextMenu()
  }
})
</script>
<style lang="scss">
.jvs-main{
  >.el-scrollbar{
    >.el-scrollbar__wrap{
      >.el-scrollbar__view{
        height: 100%;
      }
    }
  }
  .top-nav-bar-box {
    position: relative;
    display: flex;
    justify-content: space-between;
    height: 32px;
    font-size: 14px;
    background: #F5F6F7;
    overflow: hidden;
    border-left: 1px solid #fff;
    box-sizing: border-box;
    .jvs-tags__contentmenu{
      position: fixed;
      width: 120px;
      background-color: #fff;
      z-index: 1024;
      border-radius: 5px;
      box-shadow: 1px 2px 10px #ccc;
      .item{
        cursor: pointer;
        font-size: 14px;
        padding: 8px 20px 8px 15px;
        color: #606266;
        &:first-child {
          border-top-left-radius: 5px;
          border-top-right-radius: 5px;
        }
        &:last-child {
          border-bottom-left-radius: 5px;
          border-bottom-right-radius: 5px;
        }
        &:hover{
          background-color: #409eff;
          color: #fff;
        }
      }
    }
    .el-tabs.el-tabs--top {
      overflow: hidden;
      .el-tabs__header {
        height: 100%;
        margin: 0;
        border: none;
        .el-tabs__nav-wrap {
          height: 100%;
          box-sizing: border-box;
          padding-top: 4px;
          .el-tabs__nav-scroll {
            height: 100%;
            .el-tabs__nav {
              height: 100%;
              display: flex;
              padding-left: 10px;
              border: 0;
              .el-tabs__item {
                line-height: 100%;
                height: 100%;
                display: flex;
                align-items: center;
                justify-content: center;
                border-left: 0;
                font-size: 14px;
                color: #6F7588;
                font-family: Microsoft YaHei-Regular;
                border-radius: 0px 0px 6px 6px;
                border: 0;
                box-shadow: none!important;
              }
              .el-tabs__item.is-active{
                color: #363B4C;
                background-color: #fff;
                border-radius: 6px 6px 0px 0px;
                position: relative;
                .el-icon-close:hover{
                  color: #fff;
                }
              }
              .el-tabs__item.is-active::before{
                content: "";
                display: block;
                width: 10px;
                height: 10px;
                box-sizing: border-box;
                border-radius: 0 0 20px 0;
                border: 4px solid #fff;
                border-left: 0;
                border-top: 0;
                background-color: #F5F6F7;
                position: absolute;
                left: -7px;
                bottom: -4px;
              }
              .el-tabs__item.is-active::after{
                content: "";
                display: block;
                width: 10px;
                height: 10px;
                box-sizing: border-box;
                border-radius: 0 0 0 20px;
                border: 4px solid #fff;
                border-right: 0;
                border-top: 0;
                background-color: #F5F6F7;
                position: absolute;
                right: -7px;
                bottom: -4px;
              }
              .el-tabs__item:nth-of-type(1){
                padding: 0 15px;
              }
            }
          }
          .el-tabs__nav-prev, .el-tabs__nav-next{
            height: 100%;
            display: flex;
            align-items: center;
          }
        }
      }
    }
  }
  .top-nav-bar-box::before{
    position: absolute;
    content: "";
    width: 100%;
    height: 1px;
    background: linear-gradient(270deg, #EEEFF0 0%, rgba(238,239,240,0) 100%);
  }
}
</style>
