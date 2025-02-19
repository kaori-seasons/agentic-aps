<template>
  <div
    ref="boardElement"
    :class="['vc-saturation']"
    :style="{ backgroundColor: state.hueColor }"
    @mousedown="onClickBoard"
    @mousemove="onDrag"
    @mouseup="onDragEnd"
  >
    <div class="vc-saturation__white"></div>
    <div class="vc-saturation__black"></div>
    <div class="vc-saturation__cursor" ref="cursorElement" :style="getCursorStyle">
      <div></div>
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
  getCurrentInstance,
  computed,
  nextTick
} from 'vue' 
import propTypes from 'vue-types'
import { clamp,Color } from './utils/color'
import { whenever, tryOnMounted } from '@vueuse/core'
import { merge } from 'lodash'

const emit = defineEmits(['change'])
const props = defineProps({
  color: propTypes.instanceOf(Color),
  show:{
    type:Boolean,
    default:false
  }
})
const hueHsv = {
  h: props.color?.hue || 0,
  s: 1,
  v: 1,
}
const colorInstance = props.color || new Color()
const state = reactive({
  color: colorInstance,
  hex: colorInstance.toHexString(),
  rgb: colorInstance.toRgbString(),
})
const hueColor = new Color(hueHsv).toHexString()
const boardElement = ref<HTMLElement | null>()
const cursorElement = ref<HTMLElement | null>()
const cursorTop = ref(0)
const cursorLeft = ref(0)
const getCursorStyle = computed(() => {
  return {
    top: cursorTop.value + "px",
    left: cursorLeft.value + "px",
  }
})
let mousedown = false
const onClickBoard = (event: MouseEvent) => {
  mousedown = true
  handleDrag(event as MouseEvent)
  document.body.style.userSelect = 'none'
}

const onDrag = (event: MouseEvent) => {
  if (mousedown) {
    handleDrag(event as MouseEvent)
  }
}

const onDragEnd = () => {
  mousedown = false
  document.body.style.userSelect = 'auto'
}
const handleDrag = (event: MouseEvent) => {
  if(boardElement.value) {
    const rect = boardElement.value?.getBoundingClientRect()
    let left = event.clientX - rect.left
    let top = event.clientY - rect.top
    left = clamp(left, 0, rect.width)
    top = clamp(top, 0, rect.height)
    const saturation = left / rect.width
    const bright = clamp(-(top / rect.height) + 1, 0, 1)
    cursorLeft.value = left
    cursorTop.value = top
    state.saturation = saturation
    state.brightness = bright
    emit('change', saturation, bright)
  }
}
const updatePosition = () => {
  if(boardElement.value) {
    // const el = instance.vnode.el;
    cursorLeft.value = state.saturation * boardElement.value?.clientWidth;
    cursorTop.value = (1 - state.brightness) * boardElement.value?.clientHeight;
  }
}

watch(() => props.show, (value) => {
  if(value) {
    updatePosition()
  }
})

whenever(() => props.color, (value) => {
  merge(state, {
    hueColor: new Color({ h: value.hue, s: 1, v: 1 }).toHexString(),
    saturation: value.saturation,
    brightness: value.brightness,
  })
  updatePosition()
}, { deep: true })
</script>
<style lang="scss" scoped>
.vc-saturation {
  position: relative;
  margin-top: 8px;
  margin-bottom: 18px;
  width: 100%;
  height: 200px;
  border-radius: 4px 4px 4px 4px;
  border: 1px solid #EEEFF0;
  &__chrome {
    border-top-left-radius: 5px;
    border-top-right-radius: 5px;
    border-color: transparent;
  }
  &__hidden {
    overflow: hidden;
  }
  &__white,
  &__black {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    border-radius: 2px 4px 4px 4px;
  }
  &__black {
    background: linear-gradient(0deg, #000, transparent);
  }
  &__white {
    background: linear-gradient(90deg, #fff, hsla(0, 0%, 100%, 0));
  }
  &__cursor {
    position: absolute;
    div {
      transform: translate(-5px, -5px);
      box-shadow: 0 1px 4px 0 rgba(0, 0, 0, 0.37);
      width: 10px;
      height: 10px;
      border: 1px solid white;
      border-radius: 50%;
      cursor: pointer;
    }
  }
}
</style>