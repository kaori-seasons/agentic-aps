import useCommonStore from '@/store/common'
import { systemRouter } from '@/const/const'

class jvsRouter {
  // 全局配置
  $website = useCommonStore().website
  $defaultTitle = "业务系统"
  routerList = []
  group = ''
  setTitle (title) {
    //TODO 设置标题
    console.log('setTitle...', title)
  }
  closeTag (value) {
    let commonStore = useCommonStore()
    const tag = value || commonStore.getters.tag
    commonStore.commit("DEL_TAG", tag)
  }
  // 处理路由
  getPath (params) {
    let { src } = params
    let result = src || '/'
    if(src.includes('http') || src.includes('https')) {
      result = `/myiframe/urlPath`
    }else{
      // 无http/https的路由加当前地址前缀
      // 系统自带路由不处理
      let sysList = systemRouter
      let bool = true
      for(let i in sysList) {
        if (src.includes(sysList[i])) {
          bool = false
        }
      }
      if(bool) {
        let tp = (src && src.split('/')) || []
        let ts = '' // location.origin
        tp = tp.splice(1, tp.length - 1)
        if (tp.length > 0) {
          if (tp.length == 1) {
            ts += ('/' + tp[0])
          } else {
            ts += ('/' + tp[0])
            if (tp.indexOf('#') == -1) {
              ts += '/#'
            }
            for(let i = 1; i < tp.length; i++) {
              ts += ('/' + tp[i])
            }
          }
        }
        params.src = ts
        result = `/myiframe/urlPath`
      }
    }
    return {path: result, query: params}
  }
  // 正则处理路由
  vaildPath (list, path) {
    let result = false
    list.forEach(ele => {
      if (new RegExp("^" + ele + ".*", "g").test(path)) {
        result = true
      }
    })
    return result;
  }
  // 设置路由值
  getValue (route) {
    let value = "";
    if (route.query.src) {
      value = route.query.src;
    }
    return value;
  }
  // 动态路由
  formatRoutes (aMenu = [], first, router) {
    const aRouter = []
    const propsConfig = this.$website.menu.props;
    const propsDefault = {
      label: propsConfig.label || "label",
      path: propsConfig.path || "path",
      icon: propsConfig.icon || "icon"
    };
    if (aMenu.length === 0) return;
    this.routerList = [];
    for (let i = 0; i < aMenu.length; i++) {
      const oMenu = aMenu[i];
      if (this.routerList.includes(oMenu[propsDefault.path])) return;
      const path = (() => {
        if (first) {
          if (propsDefault.path) {
            if (oMenu[propsDefault.path]) {
              return oMenu[propsDefault.path].replace("/index", "");
            } else {
              return oMenu[propsDefault.path];
            }
          } else {
            return oMenu[propsDefault.path];
          }
        } else {
          return oMenu[propsDefault.path];
        }
      })();
      const name = oMenu[propsDefault.label];
      const icon = oMenu[propsDefault.icon];
      if (path) {
        const oRouter = {
          path: path,
          components: () => import("./page"),
          name: name,
          icon: icon,
          redirect: (() => {
            // !isChild &&
            if (first) return `${path}`;
            else return "";
          })()
        };
        aRouter.push(oRouter);
      }
    }
    if (first) {
      if (!this.routerList.includes(aRouter[0][propsDefault.path])) {
        router.addRoutes(aRouter);
        this.routerList.push(aRouter[0][propsDefault.path]);
      }
    } else {
      return aRouter;
    }
  }
  // 格式路由为菜单格式
  formatMenuPath (route) {
    let value = "";
    if (route.query.src) {
      value = route.query.src;
    }
    if (route.hash) {
      value += route.hash
    }
    value = value.replace(/#\/*/g, '')
    return value;
  }
}

function objToform(obj) {
  let result = []
  Object.keys(obj).forEach(ele => {
    result.push(ele + '=' + obj[ele])
  })
  return result.join("&")
}

export default jvsRouter