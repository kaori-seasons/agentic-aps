import { defineStore } from 'pinia'
import { getStore, removeStore, setStore } from '@/util/store'
import website from '@/const/website'
import { systemRouter } from '@/const/const'

const isFirstPage = website.isFirstPage
const tagWel = website.fistPage
const tagObj = {
  label: '', // 标题名称+
  value: '', // 标题的路径
  params: '', // 标题的路径参数
  query: '', // 标题的参数
  group: [] // 分组
}

// 处理首个标签
function setFistTag (list) {
  if (list.length == 1) {
    list[0].close = false
  } else {
    list.forEach(ele => {
      if (ele.value === tagWel.value && isFirstPage === false) {
        ele.close = false
      } else {
        ele.close = true
      }
    })
  }
}

const useTagsStore = defineStore('tags', {
  state: () => ({
    tagList: getStore({ name: 'tagList' }) || [],
    tag: getStore({ name: 'tag' }) || tagObj,
    tagWel: tagWel,
    menuAll: getStore({ name: 'menuAll' }) || []
  }),
  actions: {
    ADD_TAG (action) {
      this.tag = action
      setStore({ name: 'tag', content: this.tag, type: 'session' })
      let bool = true
      for(let i in this.tagList) {
        if(this.tagList[i].value == action.value && this.tagList[i].hash == action.hash) {
          bool = false
        }
      }
      if(!action.hash && systemRouter.indexOf(action.value) == -1 && action.value.startsWith('/') && !action.value.includes('#')) {
        bool = false
      }
      if(bool) {
        this.tagList.push(action)
        setFistTag(this.tagList)
        setStore({ name: 'tagList', content: this.tagList, type: 'session' })
      }
    },
    DEL_TAG (action) {
      this.tagList = this.tagList.filter(item => {
        return !(item.value == action.value && item.hash == action.hash)
      })
      setFistTag(this.tagList)
      setStore({ name: 'tagList', content: this.tagList, type: 'session' })
    },
    DEL_ALL_TAG () {
      this.tagList = [this.tagWel]
      setStore({ name: 'tagList', content: this.tagList, type: 'session' })
    },
    DEL_TAG_OTHER () {
      this.tagList = this.tagList.filter(item => {
        if(item.hash) {
          if(item.hash === this.tag.hash) {
            return true
          }
        }else{
          if (item.value === this.tag.value) {
            return true;
          } else if (!website.isFirstPage && item.value === website.fistPage.value) {
            return true;
          }
        }
      })
      setFistTag(this.tagList)
      setStore({ name: 'tagList', content: this.tagList, type: 'session' })
    },
    DEL_APP_TAG (action) {
      if(action && action.id) {
        this.tagList = this.tagList.filter(item => {
          let hash = item.hash
          let appid = ''
          if(hash && hash.includes('?') && hash.includes('jvsAppId')) {
            let tp = hash.split('?')[1]
            let tl = tp.split('&')
            tl.filter(it => {
              if(it.startsWith('jvsAppId')) {
                let itkv = it.split('=')
                appid = itkv[1]
              }
            })
          }
          return appid != action.id
        })
      }
    },
    SET_MENU_ALL (menuAll) {
      this.menuAll = menuAll
    },
  },
})

export default useTagsStore