<template>
  <div class="display-color-box">
    <div class="hex-color-box">
      <div class="hex-color-input">
        <template v-if="colorType == 'hex'">
          <el-input ref="hexInputRef" v-model="hex" @blur="changeHex" @keyup.enter="hexEnter" maxlength="9"></el-input>
        </template>
        <template v-if="colorType == 'css'">
          <el-input ref="hexInputRef" v-model="rgba" @blur="changeCss" @keyup.enter="hexEnter" ></el-input>
        </template>
      </div>
      <div class="color-type-box" ref="colorTypeRef" @click="openPopover('colorType')">
        <span>{{colorTypeLabel}}</span>
        <svg class="down-icon">
          <use xlink:href="#icon-jvs-xiala"></use>
        </svg>
      </div>
    </div>
    <div class="rgb-color-box">
      <div class="rgb-color-input">
        <el-input v-model="R" @blur="rgbBlur" @keyup.enter="rgbEnter('r')" maxlength="3"></el-input>
        <el-input v-model="G" @blur="rgbBlur" @keyup.enter="rgbEnter('g')" maxlength="3"></el-input>
        <el-input v-model="B" @blur="rgbBlur" @keyup.enter="rgbEnter('b')" maxlength="3"></el-input>
        <el-input v-model="A" @blur="aBlur" maxlength="4"></el-input>
      </div>
      <div class="down-box" ref="rgbTypeRef" @click="openPopover('rgbType')">
        <svg class="down-icon">
          <use xlink:href="#icon-jvs-xiala"></use>
        </svg>
      </div>
    </div>
    <div class="rgb-color-box rgb-tips-box">
      <div class="rgb-color-input">
        <div class="tips-item" v-for="(item,index) in getRgbItem.strArr" :key="index">
          {{item}}
        </div>
      </div>
      <div class="down-box"></div>
    </div>
    <el-popover
      ref="polymerPopRef"
      :virtual-ref="buttonRef"
      virtual-triggering
      :show-arrow="false"
      trigger="click"
      placement="bottom"
      :visible="popoverVisible"
      popper-class="color-popover">
      <div v-click-outside="onClickOutside">
        <div class="popover-item" :class="[getPopperItem.value==item.value && 'active']" v-for="(item,index) in getPopperList" :key="index" @click="setValue(item)">
          {{item.label}}
        </div>
      </div>
    </el-popover>
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
  computed
} from 'vue' 
import propTypes from 'vue-types'
import { Color } from './utils/color'
import tinycolor from 'tinycolor2'
import { ClickOutside as vClickOutside } from 'element-plus'
import { cloneDeep } from 'lodash'

const emit = defineEmits(['change','open'])
const props = defineProps({
  color: propTypes.instanceOf(Color),
  colorType:{
    type:String,
    default:'hex'
  },
  rgbType:{
    type:String,
    default:'rgb'
  }
})

const colorType = ref('hex')
const colorList = ref([
  {
    label:"HEX",
    value:'hex'
  },
  {
    label:"CSS",
    value:'css'
  }
])
const rgbType = ref('rgb')
const rgbList = ref([
  {
    label:"RGB",
    value:'rgb',
    strArr:['R','G','B','A']
  },
  {
    label:"HSL",
    value:'hsl',
    strArr:['H','S','L','A']
  },
  {
    label:"HSB",
    value:'hsb',
    strArr:['H','S','B','A']
  }
])
const R =  ref(0)
const G =  ref(0)
const B =  ref(0)
const copyRGB = ref([])
const A =  ref('100%')
const colorTypeRef = ref()
const rgbTypeRef = ref()
const buttonRef = ref()
const popoverVisible = ref(false)
const clickButton = ref('')
const getPopperList = computed(() => {
  if(clickButton.value=='colorType') {
    return colorList.value
  }else{
    return rgbList.value
  }
})
const getPopperItem = computed(() => {
  if(clickButton.value=='colorType'){
    return colorList.value.find(item=>item.value === colorType.value)
  }else{
    return  rgbList.value.find(item=>item.value === rgbType.value)
  }
})
const getRgbItem = computed(() => {
  return  rgbList.value.find(item=>item.value === rgbType.value)
})
const hex = ref('')
const rgba = ref('')
const hexInputRef = ref()
const state = ref({
  color: props?.color,
  hex: props?.color?.hex,
  alpha: Math.round(props?.color?.alpha || 100),
  rgba: props?.color?.RGB,
  previewBgColor: props?.color?.toRgbString(),
})

const colorTypeLabel = computed(()=>{
  return colorList.value.find(item=>item.value === colorType.value)?.label
})

onMounted(()=>{
  state.value = {
    color: props?.color,
    hex: props?.color?.hex,
    alpha: Math.round(props?.color?.alpha || 100),
    rgba: props?.color?.RGB,
    previewBgColor: props?.color?.toRgbString(),
  }
  colorType.value = props.colorType
  rgbType.value = props.rgbType
  setColor()
})

function setColor () {
  if(state.value.hex) {
    hex.value = state.value.hex.startsWith('#')?state.value.hex:'#'+state.value.hex
  }
  setRgb()
}
function setRgb () {
  const newState = cloneDeep(state.value)
  if(newState.rgba){
    copyRGB.value = newState.rgba
    rgba.value = `${rgbType.value}a(`
    if(rgbType.value == 'rgb'){
      R.value = copyRGB.value[0]
      G.value = copyRGB.value[1]
      B.value = copyRGB.value[2]
      rgba.value += `${newState.rgba[0]},`
      rgba.value += ` ${newState.rgba[1]},`
      rgba.value += ` ${newState.rgba[2]},`
    }else{
      R.value = newState.color.hueValue
      rgba.value += `${newState.color.hueValue},`
      if(rgbType.value == 'hsb'){
        G.value = parseInt(newState.color.saturationValue*100)
        B.value = parseInt(newState.color.brightnessValue*100)
      }
      if(rgbType.value == 'hsl'){
        G.value = parseInt(newState.color.hslSaturationValue*100)
        B.value = parseInt(newState.color.lightnessValue*100)
      }
      rgba.value += ` ${G.value},`
      rgba.value += ` ${B.value},`
    }        
    rgba.value += ` ${newState.alpha}%)`
    A.value = newState.alpha+'%'
  }
}

function changeHex () {
  if(hex.value!=state.value.hex){
    if(!hex.value){
      setColor()
    }else{
      const _hex = hex.value.replace('#','').replace(/[^a-zA-Z0-9]/g, '')
      // state.value.hex = hex.value.replace('#','')
      if(tinycolor(_hex).isValid()) {
        // if ([3, 4].includes(_hex.length)) {
          state.value.color.hex = _hex;
        // }
      }else{
        state.value.color.hex = "000000";
      }
      state.value.color.alpha = state.value.alpha
    }
  }
}

function hexEnter () {
  hexInputRef.value.blur()
}

function changeCss (){
  const {isPass,valArr} = checkColorValue(rgba.value)
  if(isPass){
    switch (rgbType.value) {
      case 'rgb':
        state.value.color.hex = tinycolor({ r:valArr[0], g:valArr[1], b:valArr[2] }).toHex();
        break;
      case 'hsl':
        state.value.color.hex = tinycolor({ h: valArr[0], s: valArr[1], l: valArr[2] }).toHex();
        break
      case 'hsb':
        state.value.color.hex = tinycolor({ h: valArr[0], s: valArr[1], v: valArr[2] }).toHex();
        break
      default:
        break;
    }
    if(valArr.length==4){
      state.value.color.alpha = valArr[3]
    }
  }else{
    setColor()
  }
}

/**
 * 检验输入的值是否符合规则
 * @param {string} value
 */
function checkColorValue (value:string) {
  let isPass = true,colorRegex
  let valArr = getAlpha(value)
  switch (rgbType.value) {
    case 'rgb':
      colorRegex = /^rgb\s*\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)$|^rgba\s*\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*((100|[1-9]?\d(?:\.\d{1,2})?)|0(?:\.\d{1,2})?)(%?)\s*\)$/
      isPass = colorRegex.test(value)
      if(isPass){
        valArr.map((item,index)=>{
          if(item>255) item = 255
        })
      }
      break;
    case 'hsl':
      colorRegex = /^hsl\s*\(\s*(\d{1,3}|[1-2]\d{2}|3[0-5]\d|360)\s*,\s*([0-9]{1,2}|100)%\s*,\s*([0-9]{1,2}|100)%\s*\)$|^hsla\s*\(\s*(\d{1,3}|[1-2]\d{2}|3[0-5]\d|360)\s*,\s*([0-9]{1,2}|100)(%?)\s*,\s*([0-9]{1,2}|100)(%?)\s*,\s*((100|[1-9]?\d(?:\.\d{1,2})?)|0(?:\.\d{1,2})?)(%?)\s*\)$/;
      isPass = colorRegex.test(value)
      if(isPass){
        valArr.map((item,index)=>{
          if(item>360 && index ==0) item = 360
          if(item>100 && index !=0 ) item = 100
        })
      }
      break
    case 'hsb':
      colorRegex = /^hsb\s*\(\s*(\d{1,3}|[1-2]\d{2}|3[0-5]\d|360)\s*,\s*([0-9]{1,2}|100)%\s*,\s*([0-9]{1,2}|100)%\s*\)$|^hsba\s*\(\s*(\d{1,3}|[1-2]\d{2}|3[0-5]\d|360)\s*,\s*([0-9]{1,2}|100)(%?)\s*,\s*([0-9]{1,2}|100)(%?)\s*,\s*((100|[1-9]?\d(?:\.\d{1,2})?)|0(?:\.\d{1,2})?)(%?)\s*\)$/;
      isPass = colorRegex.test(value)
      if(isPass){
        valArr.map((item,index)=>{
          if(item>360 && index ==0) item = 360
          if(item>100 && index !=0 ) item = 100
        })
      }
      break
    default:
      break;
  }
  return {
    isPass,valArr
  }
}

/**
 * 获取css模式下面的括号里的颜色值
 * @param {string} value
 */
function getAlpha (value) {
  // 取出括号中间的值
  const match = value.match(/\(([^)]+)\)/);
  if (match) {
    const valuesStr = match[1];
    const valuesArr = valuesStr.split(',').map((value,index) => {
      if(index==(valuesStr.split(',').length-1)){
        let valueTrim = value.trim().replace('%', '')
        return parseFloat(valueTrim) * (value.includes('%') ? 1: 100)
      }else{
        return value.trim()
      }
    })
    return valuesArr.map(Number)
  }
  return []
}

function openPopover (type:string) {
  clickButton.value = type
  if(type == 'colorType'){
    buttonRef.value = colorTypeRef.value
  }else{
    buttonRef.value = rgbTypeRef.value
  }
  popoverVisible.value = true
  emit('open',popoverVisible.value)
}

function onClickOutside () {
  popoverVisible.value = false
  buttonRef.value = null
  emit('open',popoverVisible.value)
}

function setValue (item: {value:string,label:string}) {
  if(clickButton.value=='colorType'){
    colorType.value = item.value
    emit('change','colorType',item.value)
  }else{
    rgbType.value = item.value
    setColor()
    emit('change','rgbType',item.value)
  }
  onClickOutside()
  // popoverVisible.value = false
  // buttonRef.value = null
}

function rgbBlur () {
  const reg = /^(?:0|(?:[1-9]\d*))$/
  if(reg.test(R.value) && reg.test(G.value) && reg.test(B.value)){
    if(rgbType.value == 'rgb'){
      if(R.value>255){
        R.value = 255
      }
      if(G.value>255){
        G.value = 255
      }
      if(B.value>255){
        B.value = 255
      }
      state.value.color.hex = tinycolor({ r:R.value, g:G.value, b:B.value }).toHex();
    }else{
      if(R.value>360){
        R.value = 360
      }
      if(G.value>100){
        G.value = 100
      }
      if(B.value>100){
        B.value = 100
      }
      state.value.color.hue =  R.value
      if(rgbType.value == 'hsl'){
        // state.value.color.hex = tinycolor({ h: R.value, s: G.value, l: B.value }).toHex();
        state.value.color.hslSaturation = G.value/100
        state.value.color.lightness = B.value/100
      }else{
        // state.value.color.hex = tinycolor({ h: R.value, s: G.value, v: B.value }).toHex();
        state.value.color.saturation = G.value/100
        state.value.color.brightness = B.value/100
      }
    }
    state.value.color.alpha = state.value.alpha
  }else{
    setColor()
  }
}

function rgbEnter (type:string) {
  rgbBlur()
}

function aBlur () {
  if(!A.value.endsWith('%')) {
    A.value = A.value.replaceAll('%','')+'%'
  }
  const newA = A.value.replaceAll('%','').replace(/[^0-9]+/g, '')
  if(newA.length==0) {
    state.value.alpha = 0
    state.value.color.alpha = 0
    newA.value = '0%'
  }else{
    if(newA!=state.value.alpha) {
      state.value.color.alpha = parseInt(newA)
      state.value.alpha = parseInt(newA)
    }
  }
}

watch(() => props.color, (value: Color) => {
  if (value) {
    state.value.color = value
    state.value.alpha = Math.round(state.value.color.alpha)
    state.value.hex = state.value.color.hex
    state.value.rgba = state.value.color.RGB
  }
},{ deep: true })

watch(()=>props.colorType, (newV) => {
  colorType.value = newV
})

watch(()=>props.rgbType, (newV) => {
  rgbType.value = newV
})


watch(() => state.value.color, () => {
  if (state.value.color) {
    state.value.previewBgColor = state.value.color.toRgbString();
    setColor()
  }
  }, { deep: true })

defineExpose({
  onClickOutside
})
</script>
<style lang="scss" scoped>
.display-color-box{
  margin-top: 18px;
  .hex-color-box{
    height: 36px;
    margin-bottom: 8px;
    display: grid;
    grid-template-columns: 192px 88px;
    grid-column-gap: 8px;
    .hex-color-input{
      // width: 192px;
      display: flex;
      align-items: center;
      height: 36px;
      background: #F5F6F7;
      border-radius: 4px 4px 4px 4px;
      ::v-deep(.el-input){
        .el-input__wrapper{
          background-color: transparent;
          box-shadow: none;
          padding: 0;
          .el-input__inner{
            text-align: center;
          }
        }
      }
      &:has(.is-focus){
        box-shadow: 0 0 0 1px #1E6FFF !important;
      }
    }
    .color-type-box{
      // width: 88px;
      height: 36px;
      background: #F5F6F7;
      border-radius: 4px 4px 4px 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      span{
        font-size: 14px;
        color: #363b4c;
      }
      .down-icon{
        width: 16px;
        min-width: 16px;
        height: 16px;
        margin-left: 16px;
      }
    }
  }
  .rgb-color-box{
    height: 36px;
    background: #F5F6F7;
    border-radius: 4px 4px 4px 4px;
    display: flex;
    align-items: center;
    padding: 2px 4px;
    box-sizing: border-box;
    &:has(.is-focus){
      box-shadow: 0 0 0 1px #1E6FFF !important;
    }
    .rgb-color-input{
      display: grid;
      width: 100%;
      grid-template-columns: repeat(4,1fr);
      ::v-deep(.el-input){
        .el-input__wrapper{
          background-color: transparent;
          box-shadow: none;
          padding: 0;
          .el-input__inner{
            text-align: center;
          }
        }
      }
    }
    .down-box{
      width: 32px;
      min-width: 32px;
      height: 32px;
      border-radius: 4px 4px 4px 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      .down-icon{
        width: 16px;
        min-width: 16px;
        height: 16px;
      }
      &:hover{
        background: #EEEFF0;
      }
    }
  }
  .rgb-tips-box{
    padding: 2px 4px;
    box-sizing: border-box;
    background: transparent;
    height: 24px;
    .down-box{
      width: 32px;
      min-width: 32px;
      cursor: default;
      &:hover{
        background: transparent;
      }
    }
    .rgb-color-input{
      .tips-item{
        color: #6f7588;
        font-size: 14px;
        padding-left: 11px;
      }
    }
  }
}
</style>
<style lang="scss">
.color-popover{
  min-width: 88px !important;
  width: 88px !important;
  padding: 8px !important;
  box-sizing: border-box;
  .popover-item{
    height: 32px;
    line-height: 32px;
    padding: 0px 8px;
    box-sizing: border-box;
    border-radius: 4px 4px 4px 4px;
    cursor: pointer;
    font-size: 14px;
    color: #363b4c;
    &:hover{
      background-color: #eeeff0;
    }
  }
  .active{
    background: #E4EDFF;
    color: #1E6FFF;
    &:hover{
      background-color: #E4EDFF;
    }
  }
  .popover-item+.popover-item{
    margin-top: 4px;
  }
}
.color-popover[data-popper-placement^=bottom]{
  transform:translateY(-12px);
}
.color-popover[data-popper-placement^=top]{
  transform:translateY(12px);
}
.color-popover[data-popper-placement^=left]{
  transform:translateX(12px);
}
.color-popover[data-popper-placement^=right]{
  transform:translateX(-12px);
}
</style>