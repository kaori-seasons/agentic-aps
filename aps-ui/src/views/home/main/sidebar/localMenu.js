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
          permisionFlag: "",
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
          permisionFlag: "",
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
          permisionFlag: "",
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
          permisionFlag: "",
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
          permisionFlag: "",
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
          permisionFlag: "",
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
          permisionFlag: "",
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
          permisionFlag: "",
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
          permisionFlag: "",
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
          permisionFlag: "",
          langKey: 'productionSchedulingPlan'
        },
      ]
    }
  ],
}
