<template>
  <el-button
    :size="size || defaultSet.size"
    :type="type || defaultSet.type"
    :text="text || defaultSet.text"
    :plain="plain || defaultSet.plain"
    :round="round || defaultSet.round"
    :circle="circle || defaultSet.circle"
    :loading="loading || defaultSet.loading"
    :disabled="disabled || defaultSet.disabled"
    :icon="icon || defaultSet.icon"
    :autofocus="autofocus || defaultSet.autofocus"
    :native-type="nativeType || defaultSet.nativeType"
    @click="clickHandle">
    <span v-if="permisionFlag">{{defaultText}}</span>
    <slot></slot>
    <!-- 其余插槽待拓展。。。。。。 -->
  </el-button>
</template>
<script lang="ts" setup name="jvs-button">
import {
  ref,
  reactive,
  watch ,
  onMounted,
  computed,
  getCurrentInstance,
} from 'vue'

const emit = defineEmits(['click'])

const props = defineProps({
  size: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    default: ''
  },
  text: {
    type: Boolean,
    default: false
  },
  plain: {
    type: Boolean,
    default: false
  },
  round: {
    type: Boolean,
    default: false
  },
  circle: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  },
  icon: {
    type: String,
    default: ''
  },
  autofocus: {
    type: Boolean,
    default: false
  },
  nativeType: {
    type: String,
    default: 'button'
  },
  permisionFlag: {
    type: String,
    default: ''
  },
  defaultText: {
    type: String,
    default: ''
  }
})

const defaultSet = ref({
  size: 'small', // 尺寸
  type: 'default', // 类型
  text: false,
  plain: false, // 	是否朴素按钮
  round: false, // 是否圆角按钮
  circle: false, // 是否圆形按钮
  loading: false, // 是否加载中状态
  disabled: false, // 是否禁用状态
  icon: '', // 图标类名
  autofocus: false, // 是否默认聚焦
  nativeType: 'button',
  permisionFlag: '', // 权限标识
})

function clickHandle () {
  emit('click', true)
}
</script>
