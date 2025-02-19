<template>
  <div :class="['vc-alpha-slider', 'transparent']">
    <div ref="barElement" class="vc-alpha-slider__bar" :style="getBackgroundStyle" @click="onClickSider">
      <div ref="cursorElement" :class="['vc-alpha-slider__bar-pointer']" :style="getCursorStyle">
        <div class="vc-alpha-slider__bar-handle"></div>
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
  getCurrentInstance
} from 'vue' 
import propTypes from 'vue-types'
import { Color, rgbaColor } from './utils/color'
import { DOMUtils, DragEventOptions } from '@aesoper/normal-utils'
import { merge } from 'lodash'

const emit = defineEmits(["change"])
const props = defineProps({
  color: propTypes.instanceOf(Color),
  show:{
    type: Boolean,
    default: false
  }
})
const barElement = ref<HTMLElement | null>(null)
const cursorElement = ref<HTMLElement | null>(null)
const color = ref()
const state = ref({
  red: null,
  green: null,
  blue: null,
  alpha: null,
})
const barRect = ref({})
const cursorWidth = ref()
onMounted(()=>{
  color.value = props.color || new Color()
  state.value = {
    red: color.red,
    green: color.green,
    blue: color.blue,
    alpha: color.alpha,
  }
  const dragConfig: DragEventOptions = {
    drag: (event: Event) => {
      onMoveBar(event as MouseEvent)
    },
    end: (event: Event) => {
      onMoveBar(event as MouseEvent)
    },
  }
  if(barElement.value && cursorElement.value) {
    barRect.value = barElement.value.getBoundingClientRect()
    cursorWidth.value = cursorElement.value.offsetWidth
    DOMUtils.triggerDragEvent(barElement.value, dragConfig)
  }
})

const getBackgroundStyle = computed(() => {
  const startColor = rgbaColor(state.value.red, state.value.green, state.value.blue, 0)
  const endColor = rgbaColor(state.value.red, state.value.green, state.value.blue, 100)
  return {
    background: `linear-gradient(to right, ${startColor} , ${endColor})`,
  }
});
const getCursorStyle = computed(() => {
  const left = getCursorLeft()
  return {
    left: left + "px",
    top: 0,
  }
})

const getCursorLeft = () => {
  if (barElement.value && cursorElement.value) {
    const alpha = state.value.alpha / 100;
    return Math.round(alpha * (barRect.value.width - cursorWidth.value) + cursorWidth.value / 2);
  }
  return 0
}

const onClickSider = (event: Event) => {
  const target = event.target
  if(target !== barElement.value) {
    onMoveBar(event as MouseEvent)
  }
}

const onMoveBar = (event: MouseEvent) => {
  event.stopPropagation()
  if(barElement.value && cursorElement.value) {
    cursorWidth.value = cursorElement.value.offsetWidth
    let left = event.clientX - barRect.value.left
    left = Math.max(cursorWidth.value / 2, left)
    left = Math.min(left, barRect.value.width - cursorWidth.value / 2)
    const alpha = Math.round(((left - cursorWidth.value / 2) / (barRect.value.width - cursorWidth.value)) * 100)
    color.value.alpha = alpha
    state.value.alpha = alpha
    emit('change', alpha)
  }
}

watch(()=>props.show,(value)=>{
  if(value){
    cursorWidth.value = cursorElement.value.offsetWidth;
    barRect.value = barElement.value.getBoundingClientRect();
  }
})

watch(() => props.color, (value) => {
  if (value) {
    color.value = value
    merge(state.value, {
      red: value.red,
      green: value.green,
      blue: value.blue,
      alpha: value.alpha,
    })
    }
  },
  { deep: true }
)
</script>
<style lang="scss" scoped>
.vc-alpha-slider {
  position: relative;
  // margin-bottom: 12px;
  width: 100%;
  height: 14px;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.08);
  border-radius: 15px;
  &.is-vertical {
    width: 14px;
    height: 100%;
    display: inline-block;
    transform: rotate(180deg);
  }
  &.transparent {
    background-image: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAIAAADZF8uwAAAAGUlEQVQYV2M4gwH+YwCGIasIUwhT25BVBADtzYNYrHvv4gAAAABJRU5ErkJggg==);
    background-repeat: repeat;
  }
  &__bar {
    position: relative;
    width: 100%;
    height: 100%;
    border-radius: 15px;
    &-pointer {
      position: absolute;
      width: 14px;
      height: 14px;
    }
    &-handle {
      width: 12px;
      height: 12px;
      border-radius: 50%;
      transform: translate(-7px, -2px);
      // background-color: #f8f8f8;
      border: 2px solid #FFFFFF;
      margin-top: 1px;
      // box-shadow: 0 1px 4px 0 rgba(0, 0, 0, 0.37);
      box-shadow: 0px 0px 2px 0px rgba(0,0,0,0.3);
      cursor: pointer;
      &.vertical {
        transform: translate(0, -7px);
        margin-top: 0;
      }
    }
  }

  &.small-slider {
    height: 10px !important;
    .small-bar {
      height: 10px !important;
      width: 10px !important;
      div {
        width: 12px !important;
        height: 12px !important;
        border-radius: 5px !important;
        transform: translate(-6px, -2px);
        margin-top: 1px !important;
      }
    }
  }
}
</style>