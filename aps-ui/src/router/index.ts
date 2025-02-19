import { createRouter, createWebHashHistory } from 'vue-router'
import pageRouter from './page'
import apsRouter from '@/views/aps/router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    ...pageRouter,
    ...apsRouter,
  ]
})
export default router
