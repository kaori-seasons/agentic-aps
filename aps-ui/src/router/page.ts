import Layout from '@/views/home/main/index.vue'

export default [
  {
    path: '/wel',
    component: Layout,
    redirect: '/wel/index',
    children: [{
      path: 'index',
      name: '首页',
      closable:true,
      component: () => import ( /* webpackChunkName: "views" */ '@/views/home/index.vue')
    }],
  },
  {
    path: '/index',
    redirect: '/wel'
  },
  {
    path: '/',
    name: '默认首页',
    redirect: '/wel'
  },
  {
    path: '/404',
    component: () => import ( /* webpackChunkName: "page" */ '@/components/error-page/404.vue'),
    name: '404',
    meta: {
      keepAlive: true,
      isTab: false,
      isAuth: false
    }
  },
  {
    path: "/:catchAll(.*)",
    redirect: '/404',
  },
  {
    path: '/myiframe',
    component: Layout,
    redirect: '/myiframe',
    children: [{
      path: ":routerPath",
      name: 'iframe',
      component: () => import ( /* webpackChunkName: "views" */ '@/views/layoutPage/index.vue'),
      props: true
    }]
  },
]
