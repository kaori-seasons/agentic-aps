import { defineStore } from 'pinia'
import { getStore, removeStore, setStore } from '@/util/store'
import website from '@/const/website'

const params = {
  themeColor: '', // 主题颜色
  activeColor: '', // 激活状态颜色
  // 字体
  font: {
    size: '', // 大小
    color: '', // 颜色
  },
  // logo设置
  logo: {
    width: '240px', // 宽
    height: '64px', // 高
    fit: 'contain', // 图片显示填充方式
    color: '#1890ff', // 字体颜色
    fontSize: '16px', // 字体大小
    fontWeight: 400, // 字体粗细
    backgroundColor: '#fff', // 背景颜色
  },
  // 表单设置
  form: {
    size: 'small', // 表单内组件的尺寸  'large' | 'default' | 'small'
  },
  btn: {
    size: 'small', // 表单内组件的尺寸  'large' | 'default' | 'small'
  },
  table: {
    size: 'small', // 表单内组件的尺寸  'large' | 'default' | 'small'
  }
}

const useCommonStore = defineStore('common', {
  state: () => ({
    isCollapse: getStore({ name: 'isCollapse' }) || false,
    isFullScreen: getStore({ name: 'isFullScreen' }) || false,
    isShade: getStore({ name: 'isShade' }) || false,
    screen: getStore({ name: 'screen' }) || -1,
    isLock: getStore({ name: 'isLock' }) || false,
    showTag: getStore({ name: 'showTag' }) || true,
    showCollapse: getStore({ name: 'showCollapse' }) || true,
    showFullScren: getStore({ name: 'showFullScren' }) || true,
    website: getStore({ name: 'website' }) || website,
    tenantId: getStore({ name: 'tenantId' }) || '',
    direction: getStore({ name: 'direction' }) || 'forward',
    theme: getStore({ name: 'theme' }) || Object.assign({}, params),
    themeName: getStore({ name: 'themeName' }) || '',
    system: getStore({ name: 'system' }),
    template: getStore({ name: 'template' }) || '',
    tenantInfo: getStore({ name: 'tenantInfo' }),
    MobileWidth: getStore({ name: 'MobileWidth' }),
    systemHelpDict: getStore({ name: 'systemHelpDict' }) || [],
    autoOpenedDict: getStore({ name: 'autoOpenedDict' }) || [],
    appSettingOpen: getStore({ name: 'appSettingOpen' }) || false,
    menuType: getStore({ name: 'menuType' }) || '',
    functionsObj: getStore({ name: 'functionsObj' }) || {},
    kkfileUrl: state => getStore({name: 'kkfileUrl'}) || '',
    lockPasswd: getStore({name: 'lockPasswd'}) || '',
    labelValue: getStore({ name: 'labelValue' }) || null,
    lib: getStore({ name: 'lib' }) || [], // 图标库
    allType: getStore({ name: 'allType' }) || [], // 所有分类
    iconList: getStore({ name: 'iconList' }) || [], // 所有图标
  }),
  actions: {
    SET_LabelValue (labelValue) {
      this.labelValue = labelValue
      setStore({
        name: "labelValue",
        content: this.labelValue
      })
    },
    SET_SHADE (active) {
      this.isShade = active
    },
    SET_COLLAPSE () {
      this.isCollapse = !this.isCollapse
    },
    SET_FULLSCREN () {
      this.isFullScreen = !this.isFullScreen
    },
    SET_SHOWCOLLAPSE (active) {
      this.showCollapse = active
      setStore({
        name: "showCollapse",
        content: this.showCollapse
      });
    },
    SET_SHOWTAG (active) {
      this.showTag = active
      setStore({
        name: "showTag",
        content: this.showTag
      });
    },
    SET_SHOWMENU (active) {
      this.showMenu = active
      setStore({
        name: "showMenu",
        content: this.showMenu
      });
    },
    SET_SHOWLOCK (active) {
      this.showLock = active
      setStore({
        name: "showLock",
        content: this.showLock
      });
    },
    SET_SHOWSEARCH (active) {
      this.showSearch = active
      setStore({
        name: "showSearch",
        content: this.showSearch
      });
    },
    SET_SHOWFULLSCREN (active) {
      this.showFullScren = active
      setStore({
        name: "showFullScren",
        content: this.showFullScren
      });
    },
    SET_SHOWDEBUG (active) {
      this.showDebug = active
      setStore({
        name: "showDebug",
        content: this.showDebug
      });
    },
    SET_SHOWTHEME (active) {
      this.showTheme = active
      setStore({
        name: "showTheme",
        content: this.showTheme
      });
    },
    SET_SHOWCOLOR (active) {
      this.showColor = active
      setStore({
        name: "showColor",
        content: this.showColor
      });
    },
    SET_LOCK () {
      this.isLock = true
      setStore({
        name: "isLock",
        content: this.isLock,
        type: "session"
      });
    },
    SET_SCREEN (screen) {
      this.screen = screen
    },
    SET_THEME (theme) {
      this.theme = theme;
      setStore({
        name: "theme",
        content: this.theme,
        type: "session"
      });
    },
    SET_THEME_NAME (themeName) {
      this.themeName = themeName
      setStore({
        name: "themeName",
        content: this.themeName,
        type: "session"
      });
    },
    SET_LOCK_PASSWD (lockPasswd) {
      this.lockPasswd = lockPasswd
      setStore({
        name: "lockPasswd",
        content: this.lockPasswd,
        type: "session"
      });
    },
    SET_TENANTId (tenantId) {
      this.tenantId = tenantId
      setStore({
        name: "tenantId",
        content: this.tenantId,
        type: "session"
      });
      setStore({
        name: "tenantId",
        content: this.tenantId,
        type: ""
      });
    },
    CLEAR_LOCK () {
      this.isLock = false
      this.lockPasswd = ""
      removeStore({
        name: "lockPasswd"
      });
      removeStore({
        name: "isLock"
      });
    },
    SET_SYSTEM (system) {
      this.system = system
      setStore({
        name: "system",
        content: this.system,
        type: "session"
      });
    },
    SET_TEMPLATE (template) {
      this.template = template
      setStore({
        name: "template",
        content: this.template,
        type: "session"
      });
    },
    SET_TENANTINFO (info) {
      this.tenantInfo = info
      setStore({
        name: "tenantInfo",
        content: this.tenantInfo,
        type: "session"
      });
      if (info.systemName) {
        document.title = info.systemName
      }
    },
    SET_SYSTEM_HELP_DICT (info) {
      this.systemHelpDict = info
      setStore({
        name: "systemHelpDict",
        content: this.systemHelpDict,
        type: "session"
      });
    },
    SET_AUTO_OPENED_DICT (info) {
      this.autoOpenedDict = info
      setStore({
        name: "autoOpenedDict",
        content: this.autoOpenedDict,
        type: "session"
      });
    },
    SET_APP_SETTING_OPEN (info) {
      this.appSettingOpen = info
      setStore({
        name: "appSettingOpen",
        content: this.appSettingOpen,
        type: "session"
      });
    },
    SET_MENU_TYPE (info) {
      this.menuType = info
      setStore({
        name: "menuType",
        content: this.menuType,
        type: "session"
      });
    },
    set_MobileWidth (data) {
      this.MobileWidth = data
      setStore({
        name: 'MobileWidth',
        content: this.MobileWidth,
        type: 'session'
      })
    },
    SET_FUNCTIONS_OBJ (data) {
      this.functionsObj = data
      setStore({
        name: 'functionsObj',
        content: this.functionsObj,
        type: 'session'
      })
    },
    SET_KKFILE_URL (kkfileUrl) {
      this.kkfileUrl = kkfileUrl
      setStore({
        name: 'kkfileUrl',
        content: this.kkfileUrl,
        type: 'session'
      })
    },
    LogOut () {
      this.CLEAR_LOCK()
      this.SET_SYSTEM("")
      this.SET_TENANTId("")
      this.SET_MENU_TYPE("")
      this.SET_APP_SETTING_OPEN(false)
      this.SET_FUNCTIONS_OBJ({})
    },
    setLib (val) {
      this.lib = val
    },
    setAllType (val) {
      this.allType = val
    },
    setIconList (val) {
      this.iconList = val
    }
  }
})

export default useCommonStore