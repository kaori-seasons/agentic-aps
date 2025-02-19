import registerFocus from './toolTipAutoShow'; // 获取焦点
import { Resize } from "./resizeObserver"
import loadMore from "./loadMore"
import scrollLoad from "./scrollLoad"
import lazyload from "./lazyload"

// 自定义指令集合
const directives = {
  Resize,
  loadMore,
  scrollLoad,
  lazyload
}

const registerDirectives = (app: any)=>{
  registerFocus(app)
}

export {
  registerDirectives,
  directives
}