// src/directives/loadMoreDirective.ts
import { DirectiveBinding, onMounted, onUnmounted } from 'vue'

export default {
  mounted(el: HTMLElement, binding: DirectiveBinding<(() => void) | null>) {
    const selectWrapper = document.querySelector('.el-select-dropdown .el-select-dropdown__wrap')
    function loadMores() {
      const isBase =  el.selectDomInfo.scrollHeight -  el.selectDomInfo.scrollTop <=  el.selectDomInfo.clientHeight + 1
      if (isBase) {
        binding.value && binding.value()
      }
    }
    el.selectDomInfo = selectWrapper
    el.userLoadMore = loadMores
    selectWrapper?.addEventListener('scroll', loadMores)
  },
  unmounted(el: any) {
    if (el.userLoadMore) {
      el.selectDomInfo.removeEventListener('scroll', el.userLoadMore)
      delete el.selectDomInfo
      delete el.userLoadMore
    }
  },
}