export default {
  title: '智能APS',
  logo: '菜单',
  key: 'jvs',   //配置主键,目前用于存储
  indexTitle: '智能APS',
  whiteList: ['/404', '/401', '/lock'], // 配置无权限可以访问的页面
  whiteTagList: ['/404', '/401', '/lock' ], // 配置不添加tags页面 （'/*'——*为通配符）
  lockPage: '/lock',
  tokenTime: 6000,
  infoTitle: '智能APS',
  statusWhiteList: [428],
  // 配置首页不可关闭
  isFirstPage: false,
  fistPage: {
    label: '首页',
    hash:"",
    value: '/wel/index',
    params: {},
    query: {},
    group: [],
    close: false
  },
  // 配置菜单的属性
  menu: {
    props: {
      label: 'name',
      path: 'url',
      icon: 'icon',
    }
  }
}