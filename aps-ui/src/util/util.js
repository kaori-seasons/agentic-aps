import SnowflakeId from 'snowflake-id'

// 表单序列化
export const serialize = data => {
  let list = []
  Object.keys(data).forEach(ele => {
    list.push(`${ele}=${data[ele]}`)
  })
  return list.join('&')
}

/**
 * 去除对象中的空值参数，null和undefined
 */
export const noEmptyOfObject = (obj) => {
  if(typeof obj == 'object') {
    if(obj instanceof Array) {
      return obj
    }else{
      let temp = {}
      for(let i in obj) {
        if(obj[i] || obj[i] === false || obj[i] === 0) {
          temp[i] = obj[i]
        }
      }
      return temp
    }
  }else{
    return obj
  }
}

// 生成uuid
export function generateUUID () {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0
    const v = c === 'x' ? r : (r & 0x3 | 0x8)
    return v.toString(16)
  })
}

/**
 * 雪花算法生成唯一ID
 */
export const guid = num => {
  const id= new SnowflakeId()
  return id.generate()
}

// 压缩加密 解密插件
import pako from 'pako'
/**
 * 使用pako压缩加密
 * @param {*} data
 * @returns
 */
export function pakoZipEncript(data){
  const u8ArrData = pako.deflateRaw(JSON.stringify(data),{to:'string'})
  let CHUNK_SIZE = 0x8000
  let index = 0
  let length = u8ArrData.length
  let result = ''
  let slice
  while(index<length){
    slice = u8ArrData.subarray(index,Math.min(index + CHUNK_SIZE,length))
    result += String.fromCharCode.apply(null,slice)
    index += CHUNK_SIZE
  }
  return btoa(result)
}

/**
 * 使用pako压缩解密
 * @param {*} data
 * @returns
 */
export function pakoZipDecrypt(data){
  let decodedStr = atob(data);
  let uint8Array = new Uint8Array(decodedStr.length);
  for (let i = 0; i < decodedStr.length; i++) {
    uint8Array[i] = decodedStr.charCodeAt(i);
  }
  return  pako.inflateRaw(uint8Array, { to: 'string' })
}