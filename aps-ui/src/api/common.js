import request from "./request"

// 导出  下载文件
export const downloadRequest = (url) => {
  return request({
    url: url,
    method: 'get',
    responseType: 'arraybuffer',
  })
}