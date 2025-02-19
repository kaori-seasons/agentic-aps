import { ElNotification,ElMessage, type MessageParams  } from 'element-plus'
import type { NotificationParams } from 'element-plus/lib'

class onePrompt {
  messageEntity: any
  messageObj: any
  constructor() {
    this.messageObj={}
    this.messageEntity = null
	}

  $oneNotify (options: NotificationParams | undefined) {
    const key = JSON.stringify(options)
    const that = this
    if(!this.messageObj[key]){
      options.onClose = function(){
        that.$deleteOpPrompt(key,'close')
      }
      this.messageEntity =  ElNotification(options)
      this.messageObj[key] = this.messageEntity
    }
  }

  $oneMessage (options: MessageParams) {
    const key = JSON.stringify(options)
    const that = this
    if(!this.messageObj[key]){
      options.onClose = function(){
        that.$deleteOpPrompt(key,'close')
      }
      this.messageEntity =  ElMessage(options)
      this.messageObj[key] = this.messageEntity
    }
  }

  $deleteOpPrompt (key: string,type: string) {
    if(type=='close'){
      delete this.messageObj[key]
    }
    if(type=='delete'){
      this.messageObj[key].close()
    }
  }

  $deleteAllPrompt(){
    for(let key in this.messageObj){
      this.$deleteOpPrompt(key,'delete')
    }
  }
}
export default new onePrompt;