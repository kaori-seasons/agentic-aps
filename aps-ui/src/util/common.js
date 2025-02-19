import moment from 'moment'
import {downloadRequest} from '@/api/common'

export function openUrl (url, type,callBack) {
  let typeTemp='_blank'
  if (type) {
    typeTemp=type
  }
  if (url) {
    let link=document.createElement('a')
    link.style.display='none'
    // 文件流下载 导出
    if(url.startsWith('/mgr') && !url.includes('print')) {
      downloadRequest(url).then(res => {
        let name = res.headers["content-disposition"].split(";")[1]
        name = name.split("=")[1]
        name = decodeURI(name)
        if(name) {
          link.download = name
        }
        let isExcel = false
        if(name && name.includes('.') && ['xlsx', 'xls'].indexOf(name.split('.')[1]) > -1) {
          isExcel = true
        }
        var blob = new Blob([res.data], isExcel ? {type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8'} : null)
        link.href = URL.createObjectURL(blob)
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        callBack && callBack()
      })
    }else{
      link.target=typeTemp
      link.href=url
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
    }
  }
}

/**
 * 扩展组件、日期组件默认值
 */
 export function getDefaultData (obj, arr, userInfo) {
  arr.forEach(item => {
    if(item.isDefault) {
      switch (item.type) {
        case 'user':
          obj[item.prop] = obj[item.prop] ? obj[item.prop] : userInfo.id;
          obj[item.prop + '_1'] = obj[item.prop + '_1'] ? obj[item.prop + '_1'] : userInfo.realName;
          break;
        case 'department':
          obj[item.prop] = obj[item.prop] ? obj[item.prop] : userInfo.deptId;
          obj[item.prop + '_1'] = obj[item.prop + '_1'] ? obj[item.prop + '_1'] : userInfo.deptName;
          break;
        default: break;
      }
    }
    // 日期组件默认值
    if(item.defaultDate && (!item.formula)) {
      switch(item.datetype) {
        case 'date':
          obj[item.prop] = obj[item.prop] ? obj[item.prop] : moment().format('YYYY-MM-DD')
          break;
        case 'week':
          obj[item.prop] = obj[item.prop] ? obj[item.prop] : moment().format('YYYY-MM-DD')
          break;
        case 'month':
          obj[item.prop] = obj[item.prop] ? obj[item.prop] : moment().format('YYYY-MM')
          break;
        case 'year':
          obj[item.prop] = obj[item.prop] ? obj[item.prop] : moment().format('YYYY')
          break;
        case 'dates':
          obj[item.prop] = obj[item.prop] ? obj[item.prop] : moment().format('YYYY-MM-DD')
          break;
        case 'datetime':
          obj[item.prop] = obj[item.prop] ? obj[item.prop] : moment().format('YYYY-MM-DD HH:mm:ss')
          break;
        case 'datetimerange':
          obj[item.prop] = obj[item.prop] ? obj[item.prop] : [moment().format('YYYY-MM-DD 00:00:00'), moment().format('YYYY-MM-DD 23:59:59')]
          break;
        case 'daterange':
          obj[item.prop] = obj[item.prop] ? obj[item.prop] : [moment().format('YYYY-MM-DD'), moment().format('YYYY-MM-DD')]
          break;
        case 'monthrange':
          obj[item.prop] = obj[item.prop] ? obj[item.prop] : [moment().format('YYYY-MM'), moment().format('YYYY-MM')]
          break;
        default: break;
      }
    }
  })
  return obj
}
