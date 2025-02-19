<template>
  <div class="hour-minutes-picker">
    <div class="hour-minutes">
      <el-scrollbar ref="hourScrollRef" class="wrapper" @scroll="hourScroll">
        <div class="spinner-list hour">
          <div :class="{'spinner-list-item': true, 'active': startH === 0}" @click="setStartH(0)">00</div>
          <div v-for="i in 24" :key="'h-item-'+i" :class="{'spinner-list-item': true, 'active': startH === i}" @click="setStartH(i)">{{i < 10 ? `0${i}` : `${i}`}}</div>
        </div>
      </el-scrollbar>
      <el-scrollbar ref="minuteScrollRef" :class="{'wrapper minute': true, 'disabled': startH === 24}" @scroll="minuteScroll">
        <div class="spinner-list minutes">
          <div :class="{'spinner-list-item': true, 'active': startM === 0}" @click="setStartM(0)">00</div>
          <div v-for="i in 59" :key="'min-item-'+i" :class="{'spinner-list-item': true, 'active': startM === i}" @click="setStartM(i)">{{i < 10 ? `0${i}` : `${i}`}}</div>
        </div>
      </el-scrollbar>
    </div>
  </div>
</template>
<script lang="ts" setup name="hourMinutePicker">
import {
  ref,
  reactive,
  watch ,
  onMounted,
  computed,
  getCurrentInstance,
  nextTick
} from 'vue'

import { ElScrollbar } from 'element-plus'

const hourScrollRef = ref<InstanceType<typeof ElScrollbar>>()
const minuteScrollRef = ref<InstanceType<typeof ElScrollbar>>()
const emit = defineEmits(['change'])

const props = defineProps({
  visible: {
    type: Boolean
  },
  form: {
    type: Object
  },
  prop: {
    type: String
  },
  index: {
    type: Number
  },
})

const startH = ref(0)
const startM = ref(0)

function init () {
  if(props.form[props.prop] && props.form[props.prop].length == 5) {
    let tp = props.form[props.prop].split(':')
    setStartH(Number(tp[0]))
    setStartM(Number(tp[1]))
  }else{
    setStartH(0)
    setStartM(0)
  }
}

function hourScroll (data) {
  let { scrollTop } = data
  let h = Math.ceil(scrollTop / 32)
  if(!(startH.value < h) && (h * 32 - scrollTop > 0)) {
    h -= 1
  }
  setStartH(h)
}

function setStartH (num) {
  startH.value = num
  hourScrollRef.value!.setScrollTop(num * 32)
  if(num === 24) {
    setStartM(0)
  }
  emit('change', {
    index: props.index,
    prop: props.prop,
    value: `${startH.value > 9 ? ('' + startH.value) : ('0' + startH.value)}:${startM.value > 9 ? ('' + startM.value) : ('0' + startM.value)}`
  })
}

function minuteScroll (data) {
  let { scrollTop } = data
  let h = Math.ceil(scrollTop / 32)
  if(!(startM.value < h) && (h * 32 - scrollTop > 0)) {
    h -= 1
  }
  setStartM(h)
}

function setStartM (num) {
  if(startH.value === 24) {
    startM.value = 0
    minuteScrollRef.value!.setScrollTop(0 * 32)
  }else{
    startM.value = num
    minuteScrollRef.value!.setScrollTop(num * 32)
  }
  emit('change', {
    index: props.index,
    prop: props.prop,
    value: `${startH.value > 9 ? ('' + startH.value) : ('0' + startH.value)}:${startM.value > 9 ? ('' + startM.value) : ('0' + startM.value)}`
  })
}

watch(() => props.visible, (newVal, oldVal) => {
  if(newVal) {
    init()
  }
})
</script>
<style lang="scss">
.hour-minutes-picker{
  border-radius: 2px;
  border: 1px solid var(--el-datepicker-border-color);
  position: relative;
  overflow: hidden;
  &::before{
    content: "";
    top: 50%;
    position: absolute;
    margin-top: -16px;
    height: 32px;
    z-index: -1;
    left: 0;
    right: 0;
    box-sizing: border-box;
    padding-top: 6px;
    text-align: left;
    padding-left: 50%;
    margin-right: 12%;
    margin-left: 12%;
    border-top: 1px solid var(--el-border-color-light);
    border-bottom: 1px solid var(--el-border-color-light);
  }
  .hour-minutes{
    width: 100%;
    white-space: nowrap;
    height: 192px;
    .wrapper{
      width: 50%;
      display: inline-block;
      position: relative;
      height: 100%;
      max-height: 192px;
      vertical-align: top;
      .spinner-list{
        text-align: center;
        .spinner-list-item{
          height: 32px;
          line-height: 32px;
          font-size: 12px;
          color: var(--el-text-color-regular);
          cursor: pointer;
          &.active{
            color: var(--el-text-color-primary);
            font-weight: 700;
          }
        }
        &::before, &::after{
          content: "";
          display: block;
          width: 100%;
          height: 80px;
        }
      }
      &.minute.disabled{
        .el-scrollbar__wrap{
          overflow: hidden;
        }
        .el-scrollbar__bar{
          display: none;
        }
      }
    }
  }
}
</style>