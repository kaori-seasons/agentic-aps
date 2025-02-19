<template>
  <div class="top-bar-box" :style="'height: ' + commonStore.theme.logo.height + ';'">
    <div class="top-bar-left">
      <slot name="left"></slot>
    </div>
    <div class="top-base-center">
      <ul class="system-list"></ul>
    </div>
    <div class="top-bar-right">
      <slot name="right"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import useCommonStore from '@/store/common.js'

const router = useRouter()
const route = useRoute()
const commonStore = useCommonStore()
</script>
<style lang="scss" scoped>
.setting-icon{
  padding: 5px;
  border-radius: 4px;
  transition: 0.2s;
  cursor: pointer;
  margin-left: 9px;
  width: 32px;
  height: 32px;
  overflow: hidden;
  box-sizing: border-box;
  i{
    font-size: 20px;
    color: #575E73;
  }
  .icon{
    width: 22px;
    height: 22px;
  }
  .hover-show{
    display: none;
  }
  &:hover{
    transition: 0.2s;
    background-color: #F5F6F7;
    .icon{
      display: none;
    }
    .hover-show{
      display: block;
    }
  }
}
.top-bar-box {
	display: flex;
  align-items: center;
  justify-content: space-between;
  user-select: none;
	position: relative;
	box-sizing: border-box;
	overflow: hidden;
	background-color: #fff;
	box-shadow: 0 1px 2px 0 rgba(0, 0, 0, .05);

  .top-base-center{
    height:100%;
    display: flex;
    align-items: center;
    flex:1;
    box-sizing: border-box;
    .system-list{
      display: flex;
      margin: 0;
      padding: 0;
      box-sizing: border-box;
      overflow: hidden;
      overflow-x: auto;
      width: auto;
      height: 100%;
      margin-left: 1px;
      margin-top: 1px;
      li{
        display: flex;
        align-items: center;
        justify-content: center;
        color: #363B4C;
          font-size: 14px;
        cursor: pointer;
        padding: 0 25px;
        background: linear-gradient(180deg, #F5F6F7 0%, rgba(245,246,247,0) 100%);
        span{
          word-break: keep-all;
          @include SourceHanSansCN-Regular;
        }
        .normal-svg-icon{
          display: block;
        }
        .active-svg-icon{
          display: none;
        }
        &:hover, &.active-li{
          background: linear-gradient(180deg, #E4EDFF 0%, rgba(228,237,255,0) 100%);
          span{
            color: #1E6FFF;
          }
          .normal-svg-icon{
            display: none;
          }
          .active-svg-icon{
            display: block;
          }
        }
      }
    }
  }

  .top-bar-right {
    display: flex;
    align-items: center;
    padding-right: 12px;
    .search-input-box {
      background-color: #f5f6f7;
      border-radius: 4px;
      height: 36px;
      width: 224px;
      display: flex;
      align-items: center;
      cursor: pointer;
      margin-right: 2px;
      .icon {
        width: 14px;
        height: 14px;
        margin: 0px 14px;
      }
      .h-line {
        height: 14px;
        background-color: #d7d8db;
        width: 1px;
        margin-left: 0px;
        margin-right: 0px;
      }
      .text {
        margin-left: 14px;
        font-size: 14px;
        color: #6f7588;
        @include SourceHanSansCN-Regular;
      }
    }
    .h-line {
      background-color: #f0f1f2;
      height: 24px;
      width: 1px;
      margin-left: 24px;
      margin-right: 24px;
    }
    .bar-icon {
      // width: 21px;
      height: 32px;
      cursor: pointer;
      margin-left: 16px;
      font-size: 21px;
      color: #6f7588;
      display: flex;
      align-items: center;
      &:hover {
        color: #1d6bf5;
      }
      &:nth-of-type(1) {
        margin-left: 0px !important;
      }
    }
    .hover-back {
      margin-left: 9px;
    }
    .active {
      color: #1d6bf5;
    }
    .header-img-box {
      width: 36px;
      height: 36px;
      border-radius: 6px;
      overflow: hidden;
      margin-left: 17px;
      img {
        width: 100%;
        height: 100%;
        cursor: pointer;
      }
    }
  }
}
.sys-list-box {
  padding: 24px;
  box-sizing: border-box;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  column-gap: 24px;
  row-gap: 24px;
  .link-item {
    width: 88px;
    height: 88px;
    background: #f5f6f7;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    cursor: pointer;
    &:hover {
      background-color: #eeeff0;
    }
    .sys-icon {
      width: 32px;
      height: 32px;
    }
    .sys-name {
      margin-top: 3px;
      font-size: 14px;
      @include SourceHanSansCN-Regular;
      color: #363b4c;
    }
  }
}
</style>
<style lang="scss">
.link-popover {
  padding: 0px !important;
}
</style>
