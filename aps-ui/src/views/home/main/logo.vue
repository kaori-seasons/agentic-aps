<template>
  <div
    class="jvs-logo"
    :style="
      'width: '+ commonStore.theme.logo.width+
      ';height:calc( '+ commonStore.theme.logo.height+ ' - 0px )' +
      ';line-height:'+ commonStore.theme.logo.height+
      ';color:'+ commonStore.theme.logo.color+
      ';font-size:'+ commonStore.theme.logo.fontSize+
      ';font-weight:'+ commonStore.theme.logo.fontWeight+
      ';background-color:'+ commonStore.theme.logo.backgroundColor+';'
    "
  >
    <div class="logo-image" :style="'background-image:url('+ (commonStore.isCollapse ? iconUrl : logoUrl) +')'" @click="indexgo"></div>
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

const router = useRouter()
const route = useRoute()
const commonStore = useCommonStore()
const emit = defineEmits(['closeOther'])

const iconUrl = ref('/jvs-ui-public/img/icon.png')
const logoUrl = ref('/jvs-ui-public/img/logo.png')

// 跳转主页
function indexgo () {
  emit('closeOther', true)
  if(route.path == '/wel/index') {
    return false
  }
  router.push({
    path: "/"
  })
}
</script>
<style lang="scss" scoped>
.dialog-box{
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
</style>
<style lang="scss">
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-active {
  transition: opacity 2.5s;
}
.fade-enter,
.fade-leave-to {
  opacity: 0;
}
.jvs-logo {
  position: fixed;
  top: 0;
  left: 0;
  height: 64px;
  line-height: 64px;
  background-color: #fff;
  font-size: 20px;
  overflow: hidden;
  box-sizing: border-box;
  color: #1890ff;
  z-index: 1024;
  display: flex;
  justify-content: center;
  align-items: center;
  &_title {
    display: block;
    text-align: center;
    font-size: 16px;
    cursor: pointer;
  }
  &_subtitle {
    display: block;
    text-align: center;
    font-size: 18px;
    font-weight: bold;
    color: #fff;
  }
  .logo-image{
    display: block;
    width: 200px;
    height: 50px;
    overflow: hidden;
    cursor: pointer;
    background-size: contain;
    background-repeat: no-repeat;
    background-position: center;
    // margin-right: 5px;
  }
  .jvs-logo_title{
    line-height: 20px;
  }
}
.cardtipsbox {
  position: fixed;
  height: 100%;
  top: 0;
  left: 0;
  width: 100%;
  background: rgba(0, 0, 0, 0);
  box-shadow: 3px 0px 6px #888888;
  .cardtipsbox-top {
    background: #fff;
    // display: flex;
    span {
      display: block;
      text-align: center;
      background: #c5c5c5;
    }
  }
  .ulList {
    background: #fff;
    // width: 60%;
    box-shadow: 0 0 10px #ccc;
    min-height: 100%;
    // overflow-y: scroll;
    height: 100%;
    padding: 20px 40px 60px 40px;
    h3 {
      font-size: 17px;
      color: #333;
      line-height: 20px;
      margin: 0;
      margin: 15px 0px 15px 0px;
    }
    ul {
      display: flex;
      flex-wrap: wrap;
      padding: 0;
      margin: 0;
    }
  }
  ul,
  li {
    list-style: none;
    background: #fff;
    li {
      line-height: 18px;
      display: flex;
      align-items: center;
      width: 25%;
      margin: 0px 0 0px 0px;
      display: flex;
      align-items: center;
      div {
        padding: 8px;
        /*border-radius: 10px;*/
        width: 80%;
        display: flex;
        justify-content: space-between;
      }
      div:hover {
        background: #f5f5f5;
        a {
          color: #000;
          cursor: pointer;
        }
        i {
          display: block !important;
        }
      }
    }
    a {
      font-size: 12px;
      color: #666;
    }
    i {
      margin-left: 5px;
      color: #c5c5c5;
      cursor: pointer;
    }
    .collected {
      color: #1890ff;
    }
  }
}
</style>
