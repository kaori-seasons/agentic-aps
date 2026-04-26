export const localMenuData =  {
  defaultMenu: [
    {
      name: '基础数据',
      icon: 'jvs-aps-icon-jichushuju',
      langKey: 'dataBase',
      children: [
        {
          extend: {
            newWindow: false,
            icon: "",
            url: "/jvs-aps-ui/matter",
            name: "物料",
          },
          name: "物料",
          permisionFlag: "aps:material:view",
          langKey: 'matter'
        },
        {
          extend: {
            newWindow: false,
            icon: "",
            url: "/jvs-aps-ui/resource",
            name: "主资源",
          },
          name: "主资源",
          permisionFlag: "aps:resource:view",
          langKey: 'resource'
        },
        {
          extend: {
            newWindow: false,
            icon: "",
            url: "/jvs-aps-ui/productionOrder",
            name: "生产订单",
          },
          name: "生产订单",
          permisionFlag: "aps:production-order:view",
          langKey: 'productionOrder'
        },
        {
          extend: {
            newWindow: false,
            icon: "",
            url: "/jvs-aps-ui/materialPlan",
            name: "来料计划",
          },
          name: "来料计划",
          permisionFlag: "aps:incoming-material-order:view",
          langKey: 'materialPlan'
        },
        {
          extend: {
            newWindow: false,
            icon: "",
            url: "/jvs-aps-ui/bom",
            name: "制造BOM",
          },
          name: "制造BOM",
          permisionFlag: "aps:bom:view",
          langKey: 'bom'
        },
      ]
    },
    {
      name: '生产工艺',
      icon: 'jvs-aps-icon-shengchangongyi',
      langKey: 'productionEngineering',
      children: [
        {
          extend: {
            newWindow: false,
            icon: "",
            url: "/jvs-aps-ui/technology",
            name: "工序模板",
          },
          name: "工序模板",
          permisionFlag: "aps:process:view",
          langKey: 'technology'
        },
        {
          extend: {
            newWindow: false,
            icon: "",
            url: "/jvs-aps-ui/routing",
            name: "工艺路线",
          },
          name: "工艺路线",
          permisionFlag: "aps:process-route:view",
          langKey: 'routing'
        },
        {
          extend: {
            newWindow: false,
            icon: "",
            url: "/jvs-aps-ui/productionCalendar",
            name: "生产日历",
          },
          name: "生产日历",
          permisionFlag: "aps:work-calendar:view",
          langKey: 'productionCalendar'
        },
      ]
    },
    {
      name: '生产计划',
      icon: 'jvs-aps-icon-shengchanjihua',
      langKey: 'programOfProduction',
      children: [
        {
          extend: {
            newWindow: false,
            icon: "",
            url: "/jvs-aps-ui/productionSchedulingStrategy",
            name: "排产策略",
          },
          name: "排产策略",
          permisionFlag: "aps:strategy:view",
          langKey: 'productionSchedulingStrategy'
        },
        {
          extend: {
            newWindow: false,
            icon: "",
            url: "/jvs-aps-ui/productionSchedulingPlan",
            name: "排产计划",
          },
          name: "排产计划",
          permisionFlag: "aps:schedule:view",
          langKey: 'productionSchedulingPlan'
        },
      ]
    },
    {
      name: '系统治理',
      icon: 'jvs-aps-icon-jichushuju',
      langKey: 'systemGovernance',
      children: [
        {
          extend: {
            newWindow: false,
            icon: "",
            url: "/jvs-aps-ui/operationAudit",
            name: "操作审计",
          },
          name: "操作审计",
          permisionFlag: "aps:audit:view",
          langKey: 'operationAudit'
        },
      ]
    }
  ],
}
