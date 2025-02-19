import { defineAsyncComponent } from 'vue'

export default {
  matter: defineAsyncComponent(() => import('@/views/aps/dataBase/matter/index.vue')),
  resource: defineAsyncComponent(() => import('@/views/aps/dataBase/resource/index.vue')),
  productionOrder: defineAsyncComponent(() => import('@/views/aps/dataBase/productionOrder/index.vue')),
  materialPlan: defineAsyncComponent(() => import('@/views/aps/dataBase/materialPlan/index.vue')),
  bom: defineAsyncComponent(() => import('@/views/aps/dataBase/bom/index.vue')),
  technology: defineAsyncComponent(() => import('@/views/aps/productionEngineering/technology/index.vue')),
  routing: defineAsyncComponent(() => import('@/views/aps/productionEngineering/routing/index.vue')),
  productionCalendar: defineAsyncComponent(() => import('@/views/aps/productionEngineering/productionCalendar/index.vue')),
  productionSchedulingStrategy: defineAsyncComponent(() => import('@/views/aps/programOfProduction/productionSchedulingStrategy/index.vue')),
  productionSchedulingPlan: defineAsyncComponent(() => import('@/views/aps/programOfProduction/productionSchedulingPlan/index.vue')),
}