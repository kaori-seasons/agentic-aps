/**
 * wyQAQ
 * 1396871452@qq.com
 */
import { validatenull } from '@/util/validate'
import website from '@/const/website'
import * as CryptoJS from 'crypto-js'

const keyName = website.key + '-'
/**
 * 存储localStorage
 */
export const setStore = (params = {}) => {
  let { name, content, type,reqEnc } = params
  name = keyName + name
  let obj = {
    dataType: typeof content,
    content: content,
    type: type,
    datetime: new Date().getTime(),
  }
  if (typeof content == 'string' || typeof content == 'object') {
    let bool = true
    if (typeof content == 'string' && !content) {
      bool = false
    }
    if (typeof content == 'object') {
      obj.content = JSON.stringify(obj.content)
    }
  }
  localStorage.setItem(name, JSON.stringify(obj))
}
/**
 * 获取localStorage
 */

export const getStore = (params = {}) => {
  let { name, debug, type,reqEnc } = params
  name = keyName + name
  let obj = {},
    content
  obj = localStorage.getItem(name)
  if (validatenull(obj)) {
    if (type) {
      return
    } else {
      obj = localStorage.getItem(name)
    }
  } else {
    try {
      obj = JSON.parse(obj)
    } catch {
      return obj
    }
    if (debug) {
      return obj
    }
    try {
      if ((obj.dataType == 'string' || obj.dataType == 'object')) {
        if (obj.dataType == 'object') {
          obj.content = JSON.parse(obj.content)
        }
      }
    } catch (error) {
      removeItemAll('lib', 'allType', 'iconList', 'isMobile')
    }
    if (obj.dataType == 'string') {
      content = obj.content
    } else if (obj.dataType == 'number') {
      content = Number(obj.content)
    } else if (obj.dataType == 'boolean') {
      content = eval(obj.content)
    } else if (obj.dataType == 'object') {
      content = obj.content
    }
    return content
  }
}
/**
 * 删除localStorage
 */
export const removeStore = (params = {}) => {
  let { name, type } = params
  name = keyName + name
  sessionStorage.removeItem(name)
  localStorage.removeItem(name)
}

/**
 * 获取全部localStorage
 */
export const getAllStore = (params = {}) => {
  let list = []
  let { type } = params
  for (let i = 0; i <= localStorage.length; i++) {
    list.push({
      name: localStorage.key(i),
      content: getStore({
        name: localStorage.key(i),
      }),
    })
  }
  return list
}

/**
 * 清空全部localStorage
 */
export const clearStore = (params = {}) => {
  let { type } = params
  sessionStorage.clear()
  localStorage.clear()
}

/**
 * 全部移除localStorage和sessionStorage
 */
export const removeItemAll = (data, callback) => {
  let localStorageObj = JSON.parse(JSON.stringify(localStorage))
  let sessionStorageObj = JSON.parse(JSON.stringify(sessionStorage))
  for (let key in localStorageObj) {
    if (data.indexOf(key.replace(keyName, '')) == -1) {
      localStorage.removeItem(key)
    }
  }
  for (let key in sessionStorageObj) {
    if (data.indexOf(key.replace(keyName, '')) == -1) {
      sessionStorage.removeItem(key)
    }
  }
  if (callback && typeof callback == 'function') {
    callback()
  }
}

/**
 * 存储sessionStorage
 */
export const setSessionStore = (params = {}) => {
  let { name, content, type } = params
  sessionStorage.setItem(name, JSON.stringify(content))
}
/**
 * 获取sessionStorage
 */
export const getSessionStore = (params = {}) => {
  let { name, content, type } = params
  return JSON.parse(sessionStorage.getItem(name))
}
/**
 * 删除sessionStorage
 */
export const delSessionStore = (params = {}) => {
  let { name, content, type } = params
  sessionStorage.removeItem(name)
}
