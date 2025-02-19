export default [
	{
		path: "/matter",
		name: "物料",
		component: () => import(/* webpackChunkName: "views" */ "./dataBase/matter/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false
		}
	},
	{
		path: "/resource",
		name: "主资源",
		component: () => import(/* webpackChunkName: "views" */ "./dataBase/resource/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false
		}
	},
	{
		path: "/productionOrder",
		name: "生产订单",
		component: () => import(/* webpackChunkName: "views" */ "./dataBase/productionOrder/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false
		}
	},
	{
		path: "/materialPlan",
		name: "来料计划",
		component: () => import(/* webpackChunkName: "views" */ "./dataBase/materialPlan/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false
		}
	},
	{
		path: "/bom",
		name: "制造BOM",
		component: () => import(/* webpackChunkName: "views" */ "./dataBase/bom/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false
		}
	},
	{
		path: "/technology",
		name: "工艺",
		component: () => import(/* webpackChunkName: "views" */ "./productionEngineering/technology/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false
		}
	},
	{
		path: "/routing",
		name: "工艺路线",
		component: () => import(/* webpackChunkName: "views" */ "./productionEngineering/routing/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false
		}
	},
	{
		path: "/productionCalendar",
		name: "生产日历",
		component: () => import(/* webpackChunkName: "views" */ "./productionEngineering/productionCalendar/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false
		}
	},
	{
		path: "/productionSchedulingStrategy",
		name: "生产日历",
		component: () => import(/* webpackChunkName: "views" */ "./programOfProduction/productionSchedulingStrategy/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false
		}
	},
	{
		path: "/productionSchedulingPlan",
		name: "排产计划",
		component: () => import(/* webpackChunkName: "views" */ "./programOfProduction/productionSchedulingPlan/index.vue"),
		meta: {
			keepAlive: true,
			isTab: false,
			isAuth: false
		}
	}
]
