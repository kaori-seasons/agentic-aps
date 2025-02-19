<template>
  <div class="wel-index">
    <!-- 顶部菜单nav -->
    <menuNav :menuNavClose="menuNavClose" />
    <div class="wel-content" @click="closeMenuNav">
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  ref,
  onMounted,
  nextTick,
  reactive,
} from 'vue' 
import { useRouter } from 'vue-router'

import menuNav from '@/views/home/main/top/menuNav.vue'

const router = useRouter()
const emit = defineEmits([''])
const props = defineProps({})

const menuNavClose = ref(-1)

function closeMenuNav () {
  nextTick(() => {
    menuNavClose.value = Math.random()
  })
}
</script>
<style lang="scss" scoped>
.wel-index{
  height: 100%;
  .wel-content{
    height: calc(100% - 32px);
    overflow: hidden;
    overflow-y: auto;
    box-sizing: border-box;
  }
}
.el-scrollbar__bar.is-vertical{
  display: none;
}
</style>