import type { PiniaPluginContext } from 'pinia'

// 处理electron多窗口，pinia共享问题
export function shareStorePlugin({ store }: PiniaPluginContext) {
  // 初始化本地缓存版本
  const storeName: string = store.$id
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  let storeShareFlag : boolean = false
  // 监听数据变化
  store.$subscribe(() => {
    if(typeof BroadcastChannel !== 'undefined') {
      const channelBroad = new BroadcastChannel('store-share')
      if(!storeShareFlag){
        channelBroad.postMessage({
          id: window.id,
          targetStoreName: storeName,
          data: JSON.stringify(store.$state)
        })
      }
    }
  })
  if(typeof BroadcastChannel !== 'undefined') {
    const channelBroad = new BroadcastChannel('store-share')
    channelBroad.addEventListener('message', e => {
      storeShareFlag = true
      if(e.data.targetStoreName == storeName && e.data.id != window.id) {
        const obj = JSON.parse(e.data.data)
        const keys = Object.keys(obj)
        const values = Object.values(obj)
        for(let i = 0; i < keys.length; i++) {
          store.$state[keys[i]] = values[i]
        }
      }
      setTimeout(() => {
        storeShareFlag = false
      }, 200)
    }, true)
  }
}