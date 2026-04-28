export default [
	{
		path: "/matter",
		name: "物料",
		component: () => import(/* webpackChunkName: "views" */ "./dataBase/matter/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false,
			permission: 'aps:material:view'
		}
	},
	{
		path: "/resource",
		name: "主资源",
		component: () => import(/* webpackChunkName: "views" */ "./dataBase/resource/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false,
			permission: 'aps:resource:view'
		}
	},
	{
		path: "/productionOrder",
		name: "生产订单",
		component: () => import(/* webpackChunkName: "views" */ "./dataBase/productionOrder/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false,
			permission: 'aps:production-order:view'
		}
	},
	{
		path: "/materialPlan",
		name: "来料计划",
		component: () => import(/* webpackChunkName: "views" */ "./dataBase/materialPlan/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false,
			permission: 'aps:incoming-material-order:view'
		}
	},
	{
		path: "/bom",
		name: "制造BOM",
		component: () => import(/* webpackChunkName: "views" */ "./dataBase/bom/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false,
			permission: 'aps:bom:view'
		}
	},
	{
		path: "/technology",
		name: "工序模板",
		component: () => import(/* webpackChunkName: "views" */ "./productionEngineering/technology/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false,
			permission: 'aps:process:view'
		}
	},
	{
		path: "/routing",
		name: "工艺路线",
		component: () => import(/* webpackChunkName: "views" */ "./productionEngineering/routing/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false,
			permission: 'aps:process-route:view'
		}
	},
	{
		path: "/productionCalendar",
		name: "生产日历",
		component: () => import(/* webpackChunkName: "views" */ "./productionEngineering/productionCalendar/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false,
			permission: 'aps:work-calendar:view'
		}
	},
	{
		path: "/productionSchedulingStrategy",
		name: "排产策略",
		component: () => import(/* webpackChunkName: "views" */ "./programOfProduction/productionSchedulingStrategy/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false,
			permission: 'aps:strategy:view'
		}
	},
	{
		path: "/productionSchedulingPlan",
		name: "排产计划",
		component: () => import(/* webpackChunkName: "views" */ "./programOfProduction/productionSchedulingPlan/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false,
			permission: 'aps:schedule:view'
		}
	},
	{
		path: "/operationAudit",
		name: "操作审计",
		component: () => import(/* webpackChunkName: "views" */ "./system/operationAudit/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false,
			permission: 'aps:audit:view'
		}
	}
]
