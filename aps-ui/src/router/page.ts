import Layout from '@/views/home/main/index.vue'

export default [
  {
    path: '/login',
    component: () => import('@/views/auth/login.vue'),
    name: '登录',
    meta: {
      keepAlive: false,
      isTab: false,
      isAuth: false
    }
  },
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
    path: '/403',
    component: () => import ( /* webpackChunkName: "page" */ '@/components/error-page/403.vue'),
    name: '403',
    meta: {
      keepAlive: true,
      isTab: false,
      isAuth: false
    }
  },
  {
    path: '/500',
    component: () => import('@/components/error-page/500.vue'),
    name: '500',
    meta: {
      keepAlive: false,
      isTab: false,
      isAuth: false
    }
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
  {
    path: '/account',
    component: Layout,
    children: [{
      path: 'settings',
      name: '个人设置',
      component: () => import('@/views/account/settings.vue'),
      meta: {
        keepAlive: false,
        isTab: false,
        isAuth: false
      }
    }]
  },
]
