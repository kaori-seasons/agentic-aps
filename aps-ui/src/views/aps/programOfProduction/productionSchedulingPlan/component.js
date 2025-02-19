import { defineAsyncComponent } from 'vue'
export default {
  orders: defineAsyncComponent(() => import('@/views/aps/programOfProduction/productionSchedulingPlan/orders/index.vue')),
  task: defineAsyncComponent(() => import('@/views/aps/programOfProduction/productionSchedulingPlan/task/index.vue')),
  resourceGantt: defineAsyncComponent(() => import('@/views/aps/programOfProduction/productionSchedulingPlan/resourceGantt/index.vue')),
  orderGantt: defineAsyncComponent(() => import('@/views/aps/programOfProduction/productionSchedulingPlan/orderGantt/index.vue')),
  materialGantt: defineAsyncComponent(() => import('@/views/aps/programOfProduction/productionSchedulingPlan/materialGantt/index.vue')),
}