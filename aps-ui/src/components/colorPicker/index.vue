<!-- 使用这个函数后需要配套使用 tranRenderColor 进行颜色选择 -->
<template>
  <div class="jvs-color-picker-show-box" ref="colorButtonRef" @click="openPickerColor" :class="[theme=='light'&&'light-color-picker-show-box', theme=='dark'&&'dark-color-picker-show-box', size=='mini'&&'mini-color-picker']" :style="{width:width}">
    <div class="show-color-box-wrap">
      <div class="show-color-box-bg"></div>
      <div class="show-color-box" :style="{background:getShowColorBg}">
      </div>
      <div class="show-color-hex" v-if="size!='mini'">{{getShowHex}}</div>
    </div>
    <div class="close-box" v-if="getShowHex!=resetColor && size!='mini'" @click.stop="resetColorFun">
      <svg class="close-icon">
        <use xlink:href="#jvs-public-danchuangguanbi"></use>
      </svg>
    </div>
  </div>
  <el-popover
    ref="popoverRef"
    :virtual-ref="colorButtonRef"
    virtual-triggering
    :visible="colorPickerVisible"
    :show-arrow="false"
    trigger="click"
    width="338"
    :placement="popoverPos"
    @show="colorPickerShow"
    @hide="colorPickerHide"
    popper-class="color-picker-popover-box"
  >
    <div class="color-picker-popover" v-click-outside="onClickOutside">
      <div class="jvs-color-picker">
        <div class="color-type-box" v-if="openGradual">
          <div class="color-type-mark" :style="getMarkStyle"></div>
          <div class="color-type-item" ref="typeButtenRef" :class="[colorType=='pure' && 'color-type-active']" @click="changeColorType('pure')">纯色</div>
          <div class="color-type-item" :class="[colorType=='gradual' && 'color-type-active']" @click="changeColorType('gradual')">渐变</div>
        </div>
        <!-- 渐变色才有的配置项 -->
        <div class="gradual-color-box" v-if="colorType=='gradual'">
          <div class="gradual-silder-box">
            <div class="gradual-silder-bar" ref="graSilgerBarRef" :style="{background:getGtadualStyle}" @click.stop="addGradualPoint">
              <div class="gradual-color-node" v-for="(item,index) in gradualRenderColorList"
                :class="[item.id==currentGradualColor.id && 'color-active']" :key="index" :id="`gradual-node-${index}`"
                :style="{left:item.left==100?`calc(${item.left}% - 14px)`:`${item.left}%`}" @click.stop="setCurrentItem(item)"
                @mousedown.stop="topResize($event,item,index)">
              </div>
            </div>
          </div>
          <div class="gradual-icon-box" @click="reverseArr">
            <svg class="gradual-icon">
              <use xlink:href="#bi-shuipingfanzhuan"></use>
            </svg>
          </div>
          <div class="gradual-icon-box" @click="addRatio">
              <svg class="gradual-icon">
                <use xlink:href="#bi-xuanzhuan"></use>
              </svg>
          </div>
        </div>
        <board ref="colorBoardRef" :color="(state.color as Color)" :show="colorPickerVisibleShow" @change="onBoardChange"></board>
        <div class="color-silder-box">
          <div class="color-zilla" v-if="isSupported" @click="open">
              <svg class="color-zilla-icon">
                <use xlink:href="#bi-xiguan"></use>
              </svg>
          </div>
          <div class="siler-box">
            <hue :color="(state.color as Color)" :show="colorPickerVisibleShow"></hue>
            <alpha :color="(state.color as Color)" :show="colorPickerVisibleShow" @change="alphaChange"></alpha>
          </div>
          <div class="silder-color-box">
            <div class="color-box-bg"></div>
            <div class="color-box" :style="getBgColorStyle"></div>
          </div>
        </div>
        <display ref="displayRef" :color="(state.color as Color)" :colorType="showColorType" @open="openPopover" :rgbType="rgbType" @change="typeChange"></display>
      </div>
      <div class="common-use-box">
        <div class="common-title">通用</div>
        <div class="color-list-box">
          <div class="color-item" v-for="(item,index) in getCurrentColorList" :key="index" @click="setColor(item)" :style="setColorBackGround(item)"></div>
          <template v-if="colorType=='pure'">
            <div class="color-item back-trans" @click="setAlpha"></div>
            <div class="color-item" @click="resetColorFun">
              <svg class="icon">
                <use xlink:href="#bi-huifu"></use>
              </svg>
            </div>
          </template>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import {
  ref,
  watch ,
  onMounted,
  PropType,
  computed,
  onUnmounted,
} from 'vue'

import board from './board.vue'
import hue from './hue.vue'
import alpha from './alpha.vue'
import display from './display.vue'
import { Color, HistoryColorKey, MAX_STORAGE_LENGTH } from './utils/color'
import tinycolor, { ColorInputWithoutInstance } from 'tinycolor2'
import { colorList, gradualColorList } from './utils/commonColor'
import { useEyeDropper } from '@vueuse/core'
import { cloneDeep, values } from 'lodash'
import { generateUUID, transColor, getAlpha, getHexColorAlpha, decToHex, analysGradual, byColorTypeAndTypeTransColor } from './utils/common.js'
import { ClickOutside as vClickOutside } from 'element-plus'
const { isSupported, open, sRGBHex } = useEyeDropper()
const emit = defineEmits(['update:modelValue', 'popShow', 'popHidden'])
const props = defineProps({
  modelValue: {
    type: [String, Object],
    // default: "#000000",
  },
  prop: String,
  resetColor:{
    type:String,
    default:"#000000"
  },
  theme: {
    type: String,
    default: "",
  },
  size: {
    type: String,
    default: "default",
  },
  width: {
    type: String,
    default: "130px",
  },
  openGradual: {
    type: Boolean,
    default: true
  },
  form: Object,
  allowEmpty: {
    type: Boolean,
    default: false
  }
})

const colorButtonRef = ref()
const colorPickerVisible = ref(false)
const colorPickerVisibleShow = ref(false)
const isOpenChildrenPop = ref(false)
const state = ref({
  color:null,
  hex:null,
  rgb:null
})
const colorType = ref('pure')
const typeButtenRef = ref()
const markStyle = ref({
  width:'132px',
  height:'28px',
})
const colorInstance = ref()
const oldColor = ref()
const showColorType = ref('hex')
const rgbType = ref('rgb')
const oldAlpha = ref(100)
const isCLickSet = ref(false)
const currentGradualColor = ref({})
const gradualRenderColorList = ref([])
const gradualRatio = ref(0)
const graSilgerBarRef = ref()
const gradualDrag = ref(false)
const colorBoardRef = ref()
const popoverPos = ref('bottom-end')
const displayRef = ref()
onMounted(()=>{
  if(props.modelValue) {
    oldColor.value = props.modelValue + ''
  }else{
    oldColor.value = '#000000'
  }
  let copyModelVal = oldColor.value + ''
  gradualRenderColorList.value = []
  if(!copyModelVal.startsWith('linear-gradient(')) {
    colorType.value = 'pure'
    const colorCssArr = getAlpha(oldColor.value)
    if(oldColor.value.startsWith('#')) {
      const getHexObj = getHexColorAlpha(oldColor.value)
      copyModelVal = getHexObj.color
      oldAlpha.value = getHexObj.alpha
      showColorType.value = 'hex'
      rgbType.value = 'rgb'
    }else{
      showColorType.value = 'css'
      rgbType.value = oldColor.value.substr(0,3)
    }
    if(rgbType.value=='hsb' &&  colorCssArr.length!=0) {
      copyModelVal = tinycolor({ h:colorCssArr[0], s: colorCssArr[1], v:colorCssArr[2] }).toHexString()
    }
    colorInstance.value = new Color(copyModelVal)
    state.value = {
      color: colorInstance.value,
      hex: colorInstance.value.toHexString(),
      rgb: colorInstance.value.toRgbString(),
    }
    if(showColorType.value=='css') {
      oldAlpha.value = colorCssArr[3] || 100
    }
    state.value.color.alpha =  oldAlpha.value
  }else{
    colorType.value = 'gradual'
    const directionRegex = /linear-gradient\(([^,]+)/
    const directionMatch = directionRegex.exec(copyModelVal)
    const direction = parseInt(directionMatch ? directionMatch[1].trim() : '0')
    gradualRatio.value = direction
    gradualRenderColorList.value = analysGradual(copyModelVal)
    currentGradualColor.value = gradualRenderColorList.value[0]
    if(currentGradualColor.value.color.startsWith('#')){
      showColorType.value = 'hex'
      rgbType.value = 'rgb'
    }else{
      showColorType.value = 'css'
      rgbType.value = currentGradualColor.value.color.substr(0,3)
    }
    resetStateColor(currentGradualColor.value)
  }
  window.addEventListener('keydown',handleEvent)
  window.addEventListener('scroll',handleScroll,true)
})

onUnmounted(() => {
  window.removeEventListener('keydown',handleEvent)
  window.removeEventListener('scroll',handleScroll,true)
})

const getShowHex = computed(() => {
  let str = ''
  if(colorType.value != 'pure') {
    str = '渐变色'
  }else{
    if(props.modelValue) {
      str = tinycolor(transColor(props.modelValue,100).renderColor).toHexString()
    }else{
      str = '#000000'
    }
  }
  if(props.allowEmpty && !props.modelValue) {
    str = ''
  }
  return str
})

const getMarkStyle = computed(() => {
  let obj = {
    top:'4px',
    left:'8px'
  }
  if(colorType.value!='pure') {
    obj.left = parseInt(markStyle.value.width) + 16 + 'px'
  }
  return {
    ...markStyle.value,
    ...obj
  }
})

const getShowColorBg = computed(() => {
  let str = ''
  if(colorType.value=='pure' && props.modelValue.startsWith('#')) {
    const getHexObj = getHexColorAlpha(props.modelValue)
    str = transColor(getHexObj.color,getHexObj.alpha).renderColor
  }else if(props.modelValue.startsWith('rgb')){
    str = props.modelValue
  }else if(props.modelValue) {
    const arr = analysGradual(props.modelValue)
    str = getGtadualColorStr(arr)
  }
  if(props.allowEmpty && !props.modelValue) {
    str = ''
  }
  return str
})

const getBgColorStyle = computed(() => {
  return {
    background: state.value.rgb,
  }
})

const getCurrentColorList = computed(()=>{
  if(colorType.value=='pure'){
    return colorList
  }else{
    return gradualColorList
  }
})

const getGtadualColor = computed(() => {
  return getGtadualColorStr(gradualRenderColorList.value)
})

const getGtadualStyle = computed(() => {
  let strColor = 'linear-gradient(to right, '
  gradualRenderColorList.value.sort((a,b)=>{
    return a.left - b.left
  }).map((item,index)=>{
    if(index!=0){
      strColor += ','
    }
    strColor += item.renderColor+' '+ item.left+'%'
  })
  strColor += ')'
  return strColor
})

function handleScroll() {
  isOpenChildrenPop.value = false
  colorPickerVisible.value = false
  displayRef.value.onClickOutside()
}

function openPickerColor () {
  const colorButtonPos = colorButtonRef.value.getBoundingClientRect()
  const bodyInfo = document.body.getBoundingClientRect()
  if((bodyInfo.height-colorButtonPos.height-colorButtonPos.y)>=602) { //算出距离底部的高度
    if(colorButtonPos.x>=250){
      popoverPos.value = 'bottom-end'
    }else if((bodyInfo.width-colorButtonPos.width-colorButtonPos.x)>=250) { //计算左侧是否可以展示
      popoverPos.value = 'bottom-start'
    }else{
      popoverPos.value = 'bottom'
    }
  }else if(colorButtonPos.top>=602) {
    if(colorButtonPos.x>=250) {
      popoverPos.value = 'top-end'
    }else if((bodyInfo.width-colorButtonPos.width-colorButtonPos.x)>=250) {
      popoverPos.value = 'top-start'
    }else{
      popoverPos.value = 'top'
    }
  }else if(colorButtonPos.x>=360) {
    if((bodyInfo.height-colorButtonPos.y)>=602) {
      popoverPos.value = 'left-start'
    }else if(colorButtonPos.y>=602) {
      popoverPos.value = 'left-bottom'
    }else{
      popoverPos.value = 'left'
    }
  }else{
    if((bodyInfo.height-colorButtonPos.y)>=602) {
      popoverPos.value = 'right-start'
    }else if(colorButtonPos.y>=602) {
      popoverPos.value = 'right-bottom'
    }else{
      popoverPos.value = 'right'
    }
  }
  colorPickerVisible.value = true
}

function onClickOutside () {
  if(!isOpenChildrenPop.value) {
    colorPickerVisible.value = false
  }
}

function colorPickerShow () {
  if(props.openGradual){
    markStyle.value.width = typeButtenRef.value.clientWidth+'px'
    markStyle.value.height = typeButtenRef.value.clientHeight+'px'
  }
  colorPickerVisibleShow.value = true
  emit('popShow')
}

function colorPickerHide () {
  colorPickerVisibleShow.value = false
  emit('popHidden',false)
}

function openPopover (value) {
  isOpenChildrenPop.value = value
}

function getGtadualColorStr (arr) {
  let strColor = `linear-gradient(${gradualRatio.value}deg, `;
  arr.sort((a,b)=>{
    return a.left - b.left
  }).map((item,index)=>{
    if(index!=0){
      strColor += ','
    }
    if(item.color.startsWith('#')) {
      if(item.alpha==100){
        strColor += item.color
      }else{
        strColor += tinycolor(item.color).toHexString()+decToHex(item.alpha)
      }
    }else{
      strColor += item.color
    }
    strColor += ' '+ item.left+'%'
  })
  strColor += ')'
  return strColor
}

function handleEvent (event:any) {
  if(event.keyCode == 46){
    if(gradualRenderColorList.value.length>2){
      const index =  gradualRenderColorList.value.findIndex(item => item.id==currentGradualColor.value.id)
      if(index==(gradualRenderColorList.value.length-1)){
        currentGradualColor.value = gradualRenderColorList.value[index-1]
      }else{
        currentGradualColor.value = gradualRenderColorList.value[index+1]
      }
      gradualRenderColorList.value.splice(index,1)
    }
  }
}

function setColorBackGround (item) {
  if(item.type=='base'){
    return 'background:'+item.color
  }else{
      return `background:linear-gradient(180deg,${item.color[0]}, ${item.color[1]})`
  }
}

function changeColorType (val) {
  window.tinycolor = tinycolor
  if(colorType.value != val){
    colorType.value = val
    if(val=='gradual'){
      let color = props.modelValue
      const  {renderColor,renderColor1} = transColor(props.modelValue,oldAlpha.value)
      const firstId = generateUUID()
      const secondColor = transColor(color,0).renderColor1
      gradualRenderColorList.value = [{
        id:firstId,
        color:color,
        renderColor:renderColor,
        left:0,
        alpha:oldAlpha.value
      },{
        id:generateUUID(),
        color:secondColor,
        renderColor:renderColor1,
        left:100,
        alpha:0
      }]
      currentGradualColor.value = {
        id:firstId,
        color:color,
        renderColor:renderColor,
        left:0,
        alpha:oldAlpha.value
      }
      resetStateColor(currentGradualColor.value)
      // 这里也需要更新颜色值
      emit('update:modelValue', getGtadualColor.value, props.prop, props.form)
    }else{
      let updateColorStr = gradualRenderColorList.value[0].color
      oldAlpha.value = gradualRenderColorList.value[0].alpha
      state.value.color.alpha =  oldAlpha.value;
      emit('update:modelValue', updateColorStr, props.prop, props.form)
    }
  }
}

function reverseArr () {
  let copyRnederList = cloneDeep(gradualRenderColorList.value)
  copyRnederList = copyRnederList.reverse()
  copyRnederList.map((item,index)=>{
    item.left = gradualRenderColorList.value[index].left
  })
  gradualRenderColorList.value = cloneDeep(copyRnederList)
}

function onBoardChange(saturation: number, brightness: number) {
  state.value.color.saturation = saturation
  state.value.color.brightness = brightness
}

function setColor(item) {
  isCLickSet.value = true
  if(item.type=='base') {
    // props.modelValue = item.color
    const colorStr = colorTranRGB(item.color, oldAlpha.value)
    emit('update:modelValue', colorStr, props.prop, props.form)
  }else{
    gradualRenderColorList.value = []
    item.color.map((cItem,index)=>{
      const colorStr = colorTranRGB(cItem,100)
      const { renderColor } = transColor(colorStr,100)
      gradualRenderColorList.value.push({
        id:generateUUID(),
        color:cItem,
        renderColor:renderColor,
        left:index!=0?100:0,
        alpha:100
      })
    })
    currentGradualColor.value = gradualRenderColorList.value[0]
    resetStateColor(currentGradualColor.value)
  }
}
/**
 * 对颜色进行rgb hsb hsl 的值进行对应转换
 * @param value 需要转换的值
 * @param alpha 透明度
 */
function colorTranRGB (value,alpha) {
  let colorStr = value,obj
  if(showColorType.value!='hex'){
    switch(rgbType.value){
      case "hsl":
        obj = tinycolor(value).toHsl()
        colorStr = `hsla(${obj.h}, ${parseInt(obj.s*100)}, ${parseInt(obj.l*100)}, ${alpha}%)`
        break
      case "hsb":
        obj = tinycolor(value).toHsv()
        colorStr = `hsba(${obj.h}, ${parseInt(obj.s*100)}, ${parseInt(obj.v*100)}, ${alpha}%)`
        break
      default:
        obj = tinycolor(value).toRgb()
        colorStr = `rgba(${obj.r}, ${obj.g}, ${obj.b}, ${alpha}%)`
        break
    }
  }
  return colorStr
}

function alphaChange (val) {
  oldAlpha.value = val
}

function updateColor () {
  if(colorType.value == 'pure') {
    const str = getRgbTypeStr()
    if(str.startsWith('#')) {
      let newAlpha = oldAlpha.value
      if(newAlpha!=state.value.color.alpha) {
        newAlpha = state.value.color.alpha
      }
      emit('update:modelValue',str + (newAlpha == 100 ? '' : decToHex(newAlpha)), props.prop, props.form)
    }else{
      emit('update:modelValue',str, props.prop, props.form)
    }
  }else{
    const str = getRgbTypeStr()
    currentGradualColor.value.alpha = state.value.color.alpha
    const  {renderColor} = transColor(state.value.color.toHexString(),currentGradualColor.value.alpha)
    const index =  gradualRenderColorList.value.findIndex(item => item.id==currentGradualColor.value.id)
    gradualRenderColorList.value[index].color = str
    gradualRenderColorList.value[index].renderColor = renderColor
    gradualRenderColorList.value[index].alpha = state.value.color.alpha
    currentGradualColor.value.renderColor = renderColor
    emit('update:modelValue',str, props.prop, props.form)
  }
}

/**
 * 获取当前type下对应的颜色展示值
 */
function getRgbTypeStr () {
  let str = state.value.color.toHexString()
  if(showColorType.value!='hex') {
    str = `${rgbType.value}a(`
    state.value.color[rgbType.value.toLocaleUpperCase()].map((item,index) => {
      if(index>0) str+=', '
      if(index==3){
          str += parseInt(item*100)+'%'
      }else if(index>0&&index<3) {
          if(rgbType.value!='rgb') {
            str+=parseInt(item*100)
          }else{
            str += item
          }
      }else{
        str += item
      }
    })
    str += ')'
  }
  return str
}


function typeChange (key:string,val:string) {
  isCLickSet.value = true
  if(key=='colorType') {
    showColorType.value = val
  }else{
    rgbType.value = val
  }
  updateColor()
}

function setCurrentItem (item) {
  console.log(item)
  if(gradualDrag.value) return
  currentGradualColor.value = item
  resetStateColor(currentGradualColor.value)
}
/**
 * 对渐变色选中后的颜色重新渲染
 */
function resetStateColor (item) {
  const colorCssArr = getAlpha(item.color)
  let copyModelVal = item.color
  if(rgbType.value=='hsb' &&  colorCssArr.length!=0) {
    copyModelVal = tinycolor({ h:colorCssArr[0], s: colorCssArr[1], v:colorCssArr[2] }).toHexString()
  }
  colorInstance.value = new Color(copyModelVal)
  state.value = {
    color: colorInstance.value,
    hex: colorInstance.value.toHexString(),
    rgb: colorInstance.value.toRgbString(),
  }
  oldAlpha.value = item.alpha
  state.value.color.alpha =  item.alpha
}

function addGradualPoint (event) {
  if(gradualDrag.value) return
  const left =parseInt(event.offsetX/graSilgerBarRef.value.clientWidth * 100)
  let renderColor,alpha,colorList
  if(showColorType.value!='hex'){
    colorList = getAlpha(gradualRenderColorList.value[0].color)
  }else{
    colorList = getAlpha(tinycolor(gradualRenderColorList.value[0].color).toRgbString())
  }
  if(left< gradualRenderColorList.value[0].left) {
    alpha = gradualRenderColorList.value[0].alpha
  }else{
    alpha = Math.abs(left - 100)
  }
  switch(rgbType.value){
    case "hsl":
      renderColor = `hsl(${colorList[0]}deg ${colorList[1]}% ${colorList[2]}% / ${alpha}%)`
      break
    case "hsb":
      renderColor = `hwb(${colorList[0]}deg ${colorList[1]}% ${colorList[2]}% / ${alpha}%)`
      break
    default:
      renderColor = `rgba(${colorList[0]}, ${colorList[1]}, ${colorList[2]}, ${alpha}%)`
      break
  }
  gradualRenderColorList.value.push({
    id:generateUUID(),
    color:gradualRenderColorList.value[0].color,
    renderColor:renderColor,
    left:left,
    alpha:alpha
  })
}


function addRatio () {
  gradualRatio.value = (gradualRatio.value+45) % 360
}

function topResize (e:MouseEvent,item,index) {
  // 处理拖动的时候不能选择文字
  document.onselectstart = function(){
    return false
  }
  const startY = e.clientX;
  const startWidth = (graSilgerBarRef.value.clientWidth-14) * (item.left/100)
  const mouseMove = (documentE: MouseEvent) => {
    gradualDrag.value = true
    let moveLeft = startWidth + (documentE.clientX - startY) * 1
    if(moveLeft>graSilgerBarRef.value.clientWidth-14){
      moveLeft = graSilgerBarRef.value.clientWidth-14
    }
    if(parseInt(moveLeft/(graSilgerBarRef.value.clientWidth-14) * 100)>parseInt((graSilgerBarRef.value.clientWidth-14)/(graSilgerBarRef.value.clientWidth)  * 100)) {
      item.left = 100
    }else if(parseInt(moveLeft/(graSilgerBarRef.value.clientWidth-14) * 100)<0){
      item.left = 0
    }else{
      item.left = parseInt(moveLeft/(graSilgerBarRef.value.clientWidth-14) * 100)
    }
  }
  const mouseUp = () => {
    document.removeEventListener('mousemove', mouseMove)
    document.removeEventListener('mouseup', mouseUp)
    // 拖拽完记得重新设置可以选中
    document.onselectstart = function () {
      return true;
    }
    setTimeout(()=>{
      gradualDrag.value = false
    }, 0)
  }
  document.addEventListener('mousemove', mouseMove)
  document.addEventListener('mouseup', mouseUp)
}

function resetColorFun () {
  changeColorType('pure')
  isCLickSet.value = true
  let colorStr = colorTranRGB(props.resetColor, 100)
  if(props.allowEmpty) {
    isCLickSet.value = false
    colorStr = ''
  }
  emit('update:modelValue', colorStr, props.prop, props.form)
}

function setAlpha () {
  oldAlpha.value =0
  state.value.color.alpha = 0
}

watch(sRGBHex, (newV,oldV) => {
  isCLickSet.value = true
  const colorStr = byColorTypeAndTypeTransColor(newV,oldAlpha.value,showColorType.value,rgbType.value)
  if(colorType.value=='pure'){
    emit('update:modelValue', colorStr, props.prop, props.form)
  }else{
    const {renderColor} = transColor(newV,currentGradualColor.value.alpha)
    currentGradualColor.value.color = colorStr
    currentGradualColor.value.renderColor = renderColor
    gradualRenderColorList.value.map((item)=>{
      if(item.id==currentGradualColor.value.id){
        item.color =  currentGradualColor.value.color
        item.renderColor =  currentGradualColor.value.renderColor
      }
    })
    resetStateColor(currentGradualColor.value)
  }
})

watch(()=>gradualRenderColorList.value, () => {
  if(colorType.value != 'pure') {
    emit('update:modelValue', getGtadualColor.value, props.prop, props.form)
  }
},{deep:true})

watch(()=>gradualRatio.value, () => {
  if(colorType.value != 'pure') {
    emit('update:modelValue', getGtadualColor.value, props.prop, props.form)
  }
},{deep:true})

watch(() => props.modelValue, (value: Color) => {
  isCLickSet.value = true
  let equal,newV = value+''
  if(newV.startsWith('linear-gradient')) {
    if(!currentGradualColor.value.color) {
      if(gradualRenderColorList.value.length==0) {
        const directionRegex = /linear-gradient\(([^,]+)/
        const directionMatch = directionRegex.exec(newV)
        const direction = parseInt(directionMatch ? directionMatch[1].trim() : '0')
        gradualRatio.value = direction
        gradualRenderColorList.value = analysGradual(newV)
      }
      currentGradualColor.value = gradualRenderColorList.value[0]
    }
    if(!currentGradualColor.value.color) {
      const directionRegex = /linear-gradient\(([^,]+)/;
      const directionMatch = directionRegex.exec(newV);
      const direction = parseInt(directionMatch ? directionMatch[1].trim() : '0')
      gradualRatio.value = direction
      gradualRenderColorList.value = analysGradual(newV)
      currentGradualColor.value = gradualRenderColorList.value[0]
    }
    newV = currentGradualColor.value.color+''
  }
  let copyValue = newV + ''
  if(newV.startsWith('#') && !(currentGradualColor.value.color || '').startsWith('linear-gradient')) {
    const getHexObj = getHexColorAlpha(copyValue)
    copyValue = getHexObj.color
    if(value.startsWith('linear-gradient')) {
      oldAlpha.value = currentGradualColor.value.alpha
    }else{
      oldAlpha.value = getHexObj.alpha
    }
    if(!isCLickSet.value) showColorType.value = 'hex'
    equal = tinycolor.equals(newV, oldColor.value)
  }else{
    showColorType.value = 'css'
    rgbType.value = newV.substr(0,3)
    const colorCssArr = getAlpha(newV),oldColorArr = getAlpha(oldColor.value)
    let obj = {},oldObj = {}
    switch(rgbType.value){
      case "hsl":
        obj = { h: colorCssArr[0], s: colorCssArr[1], l: colorCssArr[2] }
        oldObj = { h: oldColorArr[0], s: oldColorArr[1], l: oldColorArr[2] }
        break
      case "hsb":
        obj = { h: colorCssArr[0], s: colorCssArr[1], v: colorCssArr[2] }
        oldObj = { h: oldColorArr[0], s: oldColorArr[1], v: oldColorArr[2] }
        copyValue = tinycolor({ h:colorCssArr[0], s: colorCssArr[1], v:colorCssArr[2] }).toHexString()
        break
      default:
        obj = { r:colorCssArr[0], g:colorCssArr[1], b:colorCssArr[2] }
        oldObj = { r:oldColorArr[0], g:oldColorArr[1], b:oldColorArr[2] }
        break
    }
    if(showColorType.value=='css' && colorCssArr.length==4) {
      oldAlpha.value = colorCssArr[3]
    }
    equal = tinycolor.equals(tinycolor(obj).toHex(), tinycolor(oldObj).toHex())
  }
  if (!equal) {
    oldColor.value = newV+''
    if(isCLickSet.value){
      colorInstance.value = new Color(copyValue)
      state.value.color = colorInstance.value
    }
    state.value.color.alpha =  oldAlpha.value
  }
  isCLickSet.value = false
}, { deep: true })

watch(() => state.value.color, () => {
  state.value.hex = state.value.color.hex;
  state.value.rgb = state.value.color.toRgbString();
  if(!isCLickSet.value) updateColor()
  }, { deep: true })
</script>
<style lang="scss" scoped>
.jvs-color-picker-show-box{
  width: 130px;
  height: 32px;
  padding: 8px;
  box-sizing: border-box;
  background: #F5F6F7;
  border-radius: 4px 4px 4px 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  .show-color-box-wrap{
    width: 100%;
    display: flex;
    align-items: center;
    cursor: pointer;
    position: relative;
    user-select: none;
    .show-color-box-bg{
      position: absolute;
      width: 16px;
      min-width: 16px;
      height: 16px;
      border-radius: 4px 4px 4px 4px;
      border: 1px solid #C2C5CF;
      background-image: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAIAAADZF8uwAAAAGUlEQVQYV2M4gwH+YwCGIasIUwhT25BVBADtzYNYrHvv4gAAAABJRU5ErkJggg==);
      background-repeat: repeat;
      z-index: 0;
    }
    .show-color-box{
      z-index: 1;
      width: 16px;
      min-width: 16px;
      height: 16px;
      border-radius: 4px 4px 4px 4px;
      border: 1px solid #C2C5CF;
    }
    .show-color-hex{
      font-size: 14px;
      color: #3d3d3d;
      // margin-left: 4px;
      padding-left: 4px;
      box-sizing: border-box;
      width: max-content;
      user-select: text;
    }
  }
  .close-box{
    position: absolute;
    right: 4px;
    border-radius: 50%;
    background-color: #eeeff0;
    display: flex;
    align-items: center;
    cursor: pointer;
    visibility: hidden;
    .close-icon{
      width: 16px;
      height: 16px;
    }
  }
  &:hover{
    .close-box{
      visibility: visible;
    }
  }
}
.mini-color-picker{
  padding: 0px;
  height: 18px;
  width: 18px !important;
  .show-color-box-bg,.show-color-box{
    border: 1px solid #FFFFFF !important;
  }
}
.light-color-picker-show-box{
  background-color: #fff;
}
.dark-color-picker-show-box{
    background-color: #242732;
  .show-color-box-wrap{
    .show-color-hex{
      color: #f5f6f7;
    }
  }
  .close-box{
    background-color: #242732;
  }
}
.jvs-color-picker{
  width: 336px;
  padding: 12px 24px;
  box-sizing: border-box;
  background-color: #fff;
  .color-type-box{
    user-select: none;
    height: 36px;
    background: #F5F6F7;
    border-radius: 4px 4px 4px 4px;
    display: grid;
    grid-template-columns: repeat(2,1fr);
    grid-column-gap: 8px;
    // display: flex;
    // align-items: center;
    padding: 4px 8px;
    box-sizing: border-box;
    position: relative;
    width: 100%;
    .color-type-mark{
      z-index: 0;
      position: absolute;
      background-color: #fff;
      border-radius: 4px 4px 4px 4px;
      transition: all 0.2s;
    }
    .color-type-item{
      position: relative;
      z-index: 1;
      cursor: pointer;
      font-size: 14px;
      color: #363b4c;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 4px 4px 4px 4px;
      &:hover{
        background-color: #E4E7EA;
      }
    }
    .color-type-active{
      color: #1E6FFF;
      &:hover{
        background-color: transparent !important;
      }
    }
    // .color-type-item + .color-type-item{
    //     margin-left: 8px;
    // }
  }
  .gradual-color-box{
    display: flex;
    align-items: center;
    margin-top: 8px;
    .gradual-silder-box{
      width: 200px;
      height: 12px;
      margin-right: 16px;
      border-radius: 20px;
      background-image: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAIAAADZF8uwAAAAGUlEQVQYV2M4gwH+YwCGIasIUwhT25BVBADtzYNYrHvv4gAAAABJRU5ErkJggg==);
      background-repeat: repeat;
      .gradual-silder-bar{
          width: 100%;
          height: 100%;
          position: relative;
          border-radius: 20px;
          .gradual-color-node{
            position: absolute;
            cursor: pointer;
            width: 14px;
            height: 14px;
            box-shadow: 0px 0px 2px 0px rgba(0,0,0,0.3);
            border: 2px solid #FFFFFF;
            box-sizing: border-box;
            border-radius: 50%;
            transform: translate(0px, -1px);
          }
          .color-active{
            // border: 2px solid #1e6fff;
            box-shadow: 0px 0px 2px 0px rgba(0,0,0,0);
            &::after{
              position: absolute;
              left:0px;
              top:0px;
              content: "";
              width: 14px;
              height: 14px;
              border-radius: 50%;
              border: 2px solid #1e6fff;
              transform: translate(-4px, -4px);
              box-shadow: 0px 0px 2px 0px rgba(0,0,0,0.3);
            }
          }
        }
      }
      .gradual-icon-box{
        width: 32px;
        min-width: 32px;
        height: 32px;
        border-radius: 4px 4px 4px 4px;
        cursor: pointer;
        display: flex;
        justify-content: center;
        align-items: center;
        // background: #F5F6F7;
        .gradual-icon{
          width: 16px;
          height: 16px;
        }
        &:hover{
          background: #F5F6F7;
        }
      }
      .gradual-icon-box+.gradual-icon-box{
        margin-left: 8px;
      }
  }
  .color-silder-box{
    display: flex;
    align-items: center;
    height: 40px;
    width: 100%;
    .color-zilla{
      height: 32px;
      width: 32px;
      min-width: 32px;
      margin-right: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 4px 4px 4px 4px;
      &:hover{
        background: #F5F6F7;
      }
      .color-zilla-icon{
        width: 20px;
        height: 20px;
        cursor: pointer;
      }
    }
    .siler-box{
      width: 100%;
      height: 40px;
    }
    .silder-color-box{
      width: 40px;
      height: 40px;
      min-width: 40px;
      margin-left: 8px;
      border-radius: 4px 4px 4px 4px;
      border: 1px solid #EEEFF0;
      position: relative;
      overflow: hidden;
      .color-box{
        width: 100%;
        height: 100%;
        z-index: 1;
        position: relative;
      }
      .color-box-bg{
        position: absolute;
        width: 100%;
        height: 100%;
        background-image: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAIAAADZF8uwAAAAGUlEQVQYV2M4gwH+YwCGIasIUwhT25BVBADtzYNYrHvv4gAAAABJRU5ErkJggg==);
        background-repeat: repeat;
        z-index: 0;
      }
    }
  }
}
.common-use-box{
  width: 336px;
  border-top: 1px solid #eeeff0;
  padding: 16px 24px;
  box-sizing: border-box;
  background-color: #fff;
  .common-title{
    font-size: 14px;
    color: #363b4c;
    margin-bottom: 11px;
  }
  .color-list-box{
    display: grid;
    grid-column-gap: 9px;
    grid-row-gap: 8px;
    grid-template-columns: repeat(9,1fr);
    .color-item{
      height: 24px;
      border-radius: 4px;
      border: 1px solid #EEEFF0;
      box-sizing: border-box;
      cursor: pointer;
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      &:hover{
        &::after{
          width: 26px;
          height: 26px;
          content: '';
          position: absolute;
          border-radius: 4px;
          left: -2px;
          top: -2px;
          box-shadow: 0 0 0 2px #B7D1FF !important;
        }
      }
      .icon{
        width: 16px;
        height: 16px;
      }
    }
    .back-trans{
      background-image: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAIAAADZF8uwAAAAGUlEQVQYV2M4gwH+YwCGIasIUwhT25BVBADtzYNYrHvv4gAAAABJRU5ErkJggg==);
    }
  }
}
</style>
<style lang="scss">
.color-picker-popover-box{
  padding: 0px !important;
  border-radius: 4px !important;
  overflow: hidden;
  .color-picker-popover{
    border-radius: 4px;
  }
}
.color-picker-popover-box[data-popper-placement^=bottom]{
  transform:translateY(-12px);
}
.color-picker-popover-box[data-popper-placement^=top]{
  transform:translateY(12px);
}
.color-picker-popover-box[data-popper-placement^=left]{
  transform:translateX(12px);
}
.color-picker-popover-box[data-popper-placement^=right]{
  transform:translateX(-12px);
}
.test-box{
  width: 100%;
  height: 50px;
}
</style>
