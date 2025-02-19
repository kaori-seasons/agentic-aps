<template>
  <div :class="['vc-hue-slider']">
    <div ref="barElement" class="vc-hue-slider__bar" @click="onClickSider">
      <div
        :class="['vc-hue-slider__bar-pointer']"
        ref="cursorElement"
        :style="getCursorStyle"
      >
        <div class="vc-hue-slider__bar-handle"></div>
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
  nextTick
} from 'vue' 
import propTypes from 'vue-types'
import { Color } from './utils/color'
import { DOMUtils, DragEventOptions } from '@aesoper/normal-utils'
import { merge } from 'lodash'

const emit = defineEmits(['change'])
const props = defineProps({
  color: propTypes.instanceOf(Color),
  show:{
    type:Boolean,
    default:false
  }
})
const color = ref()
const state = ref({
  hue: color.value?.hue || 0,
  show:{
    type:Boolean,
    default:false
  }
})
const barElement = ref<HTMLElement | null>(null)
const cursorElement = ref<HTMLElement | null>(null)
const barRect = ref({})
onMounted(()=>{
  color.value = props.color || new Color();
  state.value.hue = color.value?.hue || 0
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
    DOMUtils.triggerDragEvent(barElement.value, dragConfig)
  }
})

const getCursorLeft = () => {
  if(barElement.value && cursorElement.value) {
    const offsetWidth = cursorElement.value.offsetWidth
    if (state.value.hue === 360) {
      return barRect.value.width - offsetWidth / 2
    }
    return ((state.value.hue % 360) * (barRect.value.width - offsetWidth)) / 360 + offsetWidth / 2
  }
  return 0
};
const getCursorStyle = computed(() => {
  const left = getCursorLeft()
  return {
    left: left + "px",
    top: 0,
  }
})

const onClickSider = (event: Event) => {
  const target = event.target;
  if(target !== barElement.value) {
    onMoveBar(event as MouseEvent)
  }
}

const onMoveBar = (event: MouseEvent) => {
  event.stopPropagation();
  if (barElement.value && cursorElement.value) {
    const rect = barElement.value.getBoundingClientRect()
    const offsetWidth = cursorElement.value.offsetWidth
    let left = event.clientX - rect.left
    left = Math.min(left, rect.width - offsetWidth / 2)
    left = Math.max(offsetWidth / 2, left)
    const hue = Math.round(((left - offsetWidth / 2) / (rect.width - offsetWidth)) * 360)
    color.value.hue = hue
    state.value.hue = hue
    emit('change', hue)
  }
}

watch(()=>props.show, (value) => {
  if(value){
    barRect.value = barElement.value.getBoundingClientRect()
  }
})

watch(() => props.color, (value) => {
  if (value) {
    color.value = value
    merge(state.value, { hue: color.value.hue })
  }
  }, { deep: true })
</script>
<style lang="scss" scoped>
.vc-hue-slider{
  position: relative;
  margin-bottom: 12px;
  width: 100%;
  height: 14px;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.08);
  border-radius: 15px;

  &.is-vertical{
    width: 14px;
    height: 100%;
    display: inline-block;
    transform: rotate(180deg);
  }

  &.transparent{
    background-image: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAIAAADZF8uwAAAAGUlEQVQYV2M4gwH+YwCGIasIUwhT25BVBADtzYNYrHvv4gAAAABJRU5ErkJggg==);
    background-repeat: repeat;
  }

  &__bar{
    position: relative;
    width: 100%;
    height: 100%;
    border-radius: 15px;
    background: linear-gradient(
      to right,
      rgb(255, 0, 0) 0%,
      rgb(255, 255, 0) 16.66%,
      rgb(0, 255, 0) 33.33%,
      rgb(0, 255, 255) 50%,
      rgb(0, 0, 255) 66.66%,
      rgb(255, 0, 255) 83.33%,
      rgb(255, 0, 0) 100%
    );
    background: -webkit-linear-gradient(
      left,
      rgb(255, 0, 0) 0%,
      rgb(255, 255, 0) 16.66%,
      rgb(0, 255, 0) 33.33%,
      rgb(0, 255, 255) 50%,
      rgb(0, 0, 255) 66.66%,
      rgb(255, 0, 255) 83.33%,
      rgb(255, 0, 0) 100%
    );
    background: -moz-linear-gradient(
      left,
      rgb(255, 0, 0) 0%,
      rgb(255, 255, 0) 16.66%,
      rgb(0, 255, 0) 33.33%,
      rgb(0, 255, 255) 50%,
      rgb(0, 0, 255) 66.66%,
      rgb(255, 0, 255) 83.33%,
      rgb(255, 0, 0) 100%
    );
    background: -ms-linear-gradient(
      left,
      rgb(255, 0, 0) 0%,
      rgb(255, 255, 0) 16.66%,
      rgb(0, 255, 0) 33.33%,
      rgb(0, 255, 255) 50%,
      rgb(0, 0, 255) 66.66%,
      rgb(255, 0, 255) 83.33%,
      rgb(255, 0, 0) 100%
    );

    &-pointer{
      position: absolute;
      width: 14px;
      height: 14px;
    }
    &-handle{
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
  &.small-slider{
    height: 10px !important;
    .small-bar{
      height: 10px !important;
      width: 10px !important;
      div{
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