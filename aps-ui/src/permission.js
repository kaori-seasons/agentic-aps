import router from '@/router'
import NProgress from 'nprogress' // progress bar
import 'nprogress/nprogress.css' // progress bar style
import { validatenull } from '@/util/validate'
import useTagsStore from '@/store/tags'
import jvsRouter from '@/router/jvs-router'

// 前一次路由 防止复制链接没有回退路径(针对移动端)
window.preVueRouter = ''

router.beforeEach((to, from, next) => {
  let tagsStore = useTagsStore()
  window.preVueRouter = from.path
  // 缓冲设置
  if(to.meta.keepAlive === true && tagsStore.tagList.some(ele => { return ele.value === to.fullPath })) {
    to.meta.$keepAlive = true
  }else{
    NProgress.start()
    if(to.meta.keepAlive === true && validatenull(to.meta.$keepAlive)) {
      to.meta.$keepAlive = true
    }else{
      to.meta.$keepAlive = false
    }
  }
  const meta = to.meta || {}
  routerAddTags(to,meta,next,from)
})

router.afterEach(() => {
  NProgress.done()
})

function routerAddTags (to, meta, next, from) {
  const value = to.query.src || to.fullPath
  const label = to.query.name || to.name
  let $jvsRouter = new jvsRouter()
  let tagsStore = useTagsStore()
  if(meta.isTab !== false && !validatenull(value) && !validatenull(label)) {
    tagsStore.ADD_TAG({
      label: label,
      value: value,
      params: to.params,
      query: to.query,
      hash: to.hash,
      group: $jvsRouter.group || []
    })
  }
  next()
}