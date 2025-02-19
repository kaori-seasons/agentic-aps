import request from '@/api/request'

// 获取下拉列表
export const getSelectData = (str, method, params, designId, headers) => {
  let temp = {
    url: str,
    method: method ? method : 'get'
  }
  if(params) {
    if(method && method == 'post') {
      temp.data = params
    }else{
      temp.params = params
    }
  }
  if (designId) {
    temp.headers = {
      'designId': designId
    }
  }
  if(headers) {
    if(!temp.headers) {
      temp.headers = {}
    }
    temp.headers = Object.assign(temp.headers, headers)
  }
  return request(temp)
}

// 自定义请求
export const sendMyRequire = (http, data) => {
  let obj = {
    url: http.url,
    method: http.httpMethod,
    headers: {
      'Content-Type': http.requestContentType
    }
  }
  if(data) {
    if(http.requestContentType == 'application/x-www-form-urlencoded') {
      obj.params = data
    }else{
      obj.data = data
    }
  }
  if(http.headers) {
    obj.headers = Object.assign(http.headers, http.headers)
  }
  return request(obj)
}