import axios from 'axios';

let pendings = {};
// 添加请求
const addPending = config => {
  const { url, method, data, headers, params } = config;
  let id = ''
  if (headers['is-cancel-token']) {
    id = headers['is-cancel-token']
  } else {
    if (method == 'post' && method == 'POST') {
      id = [url, method, JSON.stringify(data), JSON.stringify(headers)].join('&');
    } else {
      if (headers['no-add-header']) {
        id = [url, method, JSON.stringify(params)].join('&');
      } else {
        id = [url, method, JSON.stringify(params), JSON.stringify(headers)].join('&');
      }
    }
  }
  const cancel = pendings[id];
  config.cancelToken = config.cancelToken || new axios.CancelToken(c => {
    if (!cancel) {
      // 不存在，就存进去
      pendings[id] = c;
    }
  })
  return config;
}
// 删除请求
const removePending = config => {
  const { url, method, data, headers, params } = config;
  let id = ''
  if (headers['is-cancel-token']) {
    id = headers['is-cancel-token']
  } else {
    if (method == 'post' && method == 'POST') {
      id = [url, method, JSON.stringify(data), JSON.stringify(headers)].join('&');
    } else {
      id = [url, method, JSON.stringify(params), JSON.stringify(headers)].join('&');
    }
  }
  const cancel = pendings[id];
  if (cancel && typeof cancel == 'function') {
    // 存在这个请求，删除
    cancel();
    delete pendings[id];
  }
}
// 删除指定Id的请求
const removeIdPending = id => {
  const cancel = pendings[id];
  if (cancel && typeof cancel == 'function') {
    // 存在这个请求，删除
    cancel();
    delete pendings[id];
  }
}
// 清除所有请求
const clearPendings = () => {
  Object.keys(pendings).forEach(i = pendings[i]())
}
export default { addPending, removePending, removeIdPending, clearPendings }
