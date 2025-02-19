import { defineStore } from 'pinia'
import { getStore, setStore } from '@/util/store.js'
import Socket from '@/util/socket'
let wsUrl = ''
const env = process.env
let messageUrl = ''
let WS = window.location.protocol == 'http:' ? 'ws' : "wss"
messageUrl = `${WS}://` + location.host
wsUrl = `${WS}://` + location.host + '/im/'
const useSocketStore = defineStore("socket", {
  state: () => ({
    $socket: null,
    socketMsg: {
      timeStamp: 0,
      type: '',
      data: {},
      command: null
    },
    $messageSocket:null,
    messageSocketMsg:getStore({name:'remainingCount'}) || {
      remainingCount:0
    }
  }),
  getters: {},
  actions:{
    INIT (docId,callBack) {
      if(this.$socket) {
        if(!this.$socket._websocket || this.$socket._websocket.readyState === 3) {
          this.$socket = null
        }
      }
      if(this.$socket !== null) return
      const token = getStore({name: 'access_token'})
      if(!token) return
      let tempLinkId = docId
      const socketToken = 'Bearer ' + token
      this.$socket = new Socket(`${wsUrl}?logType=${encodeURI('default')}&value=${socketToken}&tempLinkId=${tempLinkId}`,token,true)
      this.$socket.init()
      this.$socket.addMethods([(e) => {
        if (e.type === 'close') {
          this.socketMsg = {
            timeStamp: e.timeStamp,
            type: e.type,
            data: 'socket已断开连接'
          }
          this.$socket = null
        }
        try {
          const parseData = e.data ? JSON.parse(e.data) : JSON.parse(e)
          this.socketMsg = {
            timeStamp: e.timeStamp,
            type: parseData.type || e.type,
            data: parseData.data,
            command: parseData.command
          }
          if(parseData.command==='COMMAND_LOGIN_RESP'){
            callBack && callBack()
          }
        }catch (error) {
          console.error(error)
        }
      }])
    },
    // 发送消息
    SEND_MESSAGE(data) {
      if (this.$socket?._websocket && this.$socket?._websocket?.readyState == 1) {
        this.$socket.send(data)
      }
    },
    // 关闭消息
    CLOSE(data){
      if(this.$socket){
        this.$socket.wsClose()
        this.$socket = null
      }
    },
    // 关闭消息
    MESSAGE_CLOSE(data){
      if(this.$messageSocket){
        this.$messageSocket.wsClose()
        this.$messageSocket = null
      }
    },
    setMessageData(data){
      this.messageSocketMsg = data
      setStore({name:'remainingCount',content:data})
    }
  }
})
export default useSocketStore