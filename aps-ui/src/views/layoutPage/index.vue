<template>
  <div class="layout-page">
    <!-- 顶部菜单nav -->
    <menuNav ref="tabMenuNav" :menuNavClose="menuNavClose"></menuNav>
    <div class="outer-container" id="outerbox" @click="closeMenuNav">
      <div v-for="item in tagListData" :key="item.value+item.hash" :style="`height:${(item.value+item.hash) == ($route.query.src+$route.hash) ? '100%' : '0'};position:relative;`" v-show="(item.value+item.hash) == ($route.query.src+$route.hash)" :class="{'visible-page': (item.value+item.hash) == ($route.query.src+$route.hash)}">
        <keep-alive>
          <component v-if="getComName(item)" :is="componentList[getComName(item)]" :routerQuery="getUrlQuery(item)"></component>
        </keep-alive>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  ref,
  reactive,
  toRefs,
  watch ,
  onMounted,
  computed,
  getCurrentInstance,
  nextTick,
  defineAsyncComponent,
  shallowRef
} from 'vue'
import menuNav from '@/views/home/main/top/menuNav.vue'
import { useRouter, useRoute } from "vue-router"
import useTagsStore from '@/store/tags'

// 动态路由组件
import apsCom from '@/views/aps/component'
const componentList = shallowRef({
  ...apsCom
})

const tagsStore = useTagsStore()
const router = useRouter();
const route = useRoute()

const emit = defineEmits([''])
const props = defineProps({
})

const currentVal = computed(()=>{
  return route.path
})
const tagListData = computed(() => {
  return tagsStore.tagList
})

const tabMenuNav = ref()
const menuNavClose = ref(-1)

function closeMenuNav () {
  nextTick(() => {
    menuNavClose.value = Math.random()
  })
}

function getComName (item) {
  let hash = item.value
  let str = ''
  if(hash.includes('/#/')) {
    str = hash
    str = str.split('/#/')[1]
  }
  return str
}

function getUrlQuery (item) {
  let obj = null
  if(item.value && item.value.includes('?')) {
    obj = {}
    let str = item.value.split('?')[1]
    let params = str.split('&')
    for(let i in params) {
      let temp = params[i].split('=')
      obj[temp[0]] = temp[1]
    }
  }
  return obj
}
</script>
<style lang="scss" scoped>
.outer-container{
  width: 100%;
  height: calc(100% - 32px);
  .basic-layout-box{
    width: 100%;
    height: 100%;
    padding: 24px;
    box-sizing: border-box;
  }
}
</style>
