import { reactive, ref } from 'vue'
 
export function useIsMobile() {
  const mobileFlag = ref(false)
  const isMobile = () => {
    mobileFlag.value = navigator.userAgent.match(/(phone|pad|pod|iPhone|iPod|ios|iPad|Android|Mobile|BlackBerry|IEMobile|MQQBrowser|JUC|Fennec|wOSBrowser|BrowserNG|WebOS|Symbian|Windows Phone)/i)
  }
  return {
    mobileFlag,isMobile
  }
}