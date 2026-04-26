import { hasPermission } from '@/util/permission'

function applyPermission(el: HTMLElement, value: string | string[]) {
  const visible = hasPermission(value)
  const originalDisplay = el.dataset.permissionDisplay ?? el.style.display ?? ''
  if (!el.dataset.permissionDisplay) {
    el.dataset.permissionDisplay = originalDisplay
  }
  el.style.display = visible ? originalDisplay : 'none'
  el.dataset.permissionVisible = visible ? 'true' : 'false'
}

export default {
  mounted(el: HTMLElement, binding: any) {
    applyPermission(el, binding.value)
  },
  updated(el: HTMLElement, binding: any) {
    applyPermission(el, binding.value)
  }
}
