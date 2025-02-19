export const socketStatus = {
    'COMMAND_UNKNOW':0, // 未知状态
    "COMMAND_HANDSHAKE_REQ":1, // 握手请求，含http的websocket握手请求
    "COMMAND_HANDSHAKE_RESP":2, //握手响应，含http的websocket握手响应
    "COMMAND_AUTH_REQ": 3, //鉴权请求
    "COMMAND_AUTH_RESP": 4, //鉴权响应
    "COMMAND_LOGIN_REQ": 5, //登录请求
    "COMMAND_LOGIN_RESP": 6, //登录响应
    "COMMAND_JOIN_GROUP_REQ": 7, //申请进入群组
    "COMMAND_JOIN_GROUP_RESP": 8, //申请进入群组响应
    "COMMAND_JOIN_GROUP_NOTIFY_RESP": 9, //进入群组通知
    "COMMAND_EXIT_GROUP_NOTIFY_RESP": 10, //退出群组通知
    "COMMAND_CHAT_REQ": 11, //聊天请求
    "COMMAND_CHAT_RESP": 12, //聊天响应
    "COMMAND_HEARTBEAT_REQ": 13, //心跳请求
    "COMMAND_CLOSE_REQ": 14, //关闭请求
    "COMMAND_CANCEL_MSG_REQ": 15, //发出撤消消息指令(管理员可以撤消所有人的消息，自己可以撤消自己的消息)
    "COMMAND_CANCEL_MSG_RESP": 16, //收到撤消消息指令
    "COMMAND_GET_USER_REQ": 17, //获取用户信息
    "COMMAND_GET_USER_RESP": 18, //获取用户信息响应
    "COMMAND_GET_MESSAGE_REQ": 19, //获取聊天消息
    "COMMAND_GET_MESSAGE_RESP": 20, //获取聊天消息响应
    "COMMAND_NOTIFY_REQ": 21, //通知请求
    "COMMAND_NOTIFY_RESP": 22, //通知响应
    "COMMAND_DATA_PUSH_REQ": 23, //数据推送请求
    "COMMAND_DATA_PUSH_RESP": 24, //数据推送响应
    "COMMAND_BUSINESS_REQ": 25, //业务自定义消息请求
    "COMMAND_BUSINESS_RESP": 26, //业务自定义消息响应
    "COMMAND_GROUP_USER_UPDATE_REQ": 27, //组用户信息修改请求
    "COMMAND_GROUP_USER_UPDATE_RESP": 28, //组用户信息修改响应
    "COMMAND_QUIT_GROUP_REQ": 29, //申请退出群组请求
    "COMMAND_QUIT_GROUP_RESP": 30, //申请退出群组响应
    "COMMAND_GET_GROUP_USER_REQ": 31, //获取指定群组用户请求
    "COMMAND_GET_GROUP_USER_RESP": 32, //获取指定群组用户响应
}