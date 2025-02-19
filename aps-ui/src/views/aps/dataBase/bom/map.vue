<template>
  <div class="relation-map">
    <div id="bomContainer"></div>
  </div>
</template>
<script setup name="relationMap">
  import { ref, onMounted, watch } from 'vue'
  import { Badge, BaseBehavior, ExtensionCategory, Graph, GraphEvent, Label, Rect, register, NodeEvent } from '@antv/g6'

  const emit = defineEmits(['clickNode', 'deleteNode'])
  const props = defineProps({
    data: {
      type: Object
    }
  })

  let graph = null
  const nodePos = ref(null)
  
  const DEFAULT_LEVEL = 'detailed'
  class bomChartNode extends Rect {
    get data () {
      return this.context.model.getElementDataById(this.id).data
    }
    get level() {
      return this.data.level || DEFAULT_LEVEL
    }

    getLabelStyle () {
      const text = this.data.materialName
      const labelStyle = this.level === 'overview' ? {
        fill: '#fff',
        fontSize: 20,
        fontWeight: 500,
        textAlign: 'center',
        transform: [['translate', 0, 0]],
      } : {
        fill: '#363B4C',
        fontSize: 14,
        fontWeight: 700,
        textAlign: 'left',
        transform: [['translate', -76, -16]],
      }
      return { text, ...labelStyle }
    }

    getKeyStyle(attributes) {
      return {
        ...super.getKeyStyle(attributes),
        fill: this.level === 'overview' ? '#1E6FFF' : '#fff',
      }
    }

    getBadgeStyle(attributes) {
      if (this.level === 'overview') return false
      let text = this.data.quantity + (this.data.unit || '')
      let labelW = this.data.materialName.length * 14
      let badgeW = text.length * 12 + 16
      let x = -76 + labelW + badgeW
      return {
        text: text,
        fontSize: 12,
        fontWeight: 400,
        fill: '#fff',
        textAlign: 'left',
        backgroundFill: '#1E6FFF',
        transform: [['translate', (x > 76) ? (100 - badgeW) : (-76 + labelW + 8), -16]],
        padding: [2, 8, 0, 8],
        backgroundRadius: 12,
      }
    }

    drawBadgeShape(attributes, container) {
      const badgeStyle = this.getBadgeStyle(attributes)
      this.upsert('quantity', Label, badgeStyle, container)
    }

    getDescStyle(attributes) {
      if (this.level === 'overview') return false
      return {
        text: '物料编码：' + this.data.materialCode,
        fontSize: 14,
        fontWeight: 400,
        textTransform: 'uppercase',
        fill: '#6F7588',
        textAlign: 'left',
        transform: [['translate', -76, 12]],
      }
    }

    drawDescShape(attributes, container) {
      const positionStyle = this.getDescStyle(attributes)
      this.upsert('materialCode', Label, positionStyle, container)
    }

    render(attributes = this.parsedAttributes, container = this) {
      super.render(attributes, container)
      this.drawBadgeShape(attributes, container)
      this.drawDescShape(attributes, container)
    }
  }
  class bomLevelOfDetail extends BaseBehavior {
    prevLevel = DEFAULT_LEVEL
    levels = {
      ['overview']: [0, 0.6],
      ['detailed']: [0.6, 1],
    }

    constructor(context, options) {
      super(context, options)
      this.bindEvents()
    }

    update(options) {
      this.unbindEvents()
      super.update(options)
      this.bindEvents()
    }

    updateZoomLevel = async (e) => {
      if ('scale' in e.data) {
        const scale = e.data.scale
        const level = Object.entries(this.levels).find(([key, [min, max]]) => scale > min && scale <= max)?.[0]
        if (level && this.prevLevel !== level) {
          const { graph } = this.context
          graph.updateNodeData((prev) => prev.map((node) => ({ ...node, data: { ...node.data, level } })))
          await graph.draw()
          this.prevLevel = level
        }
      }
    }

    bindEvents() {
      const { graph } = this.context
      graph.on(GraphEvent.AFTER_TRANSFORM, this.updateZoomLevel)
      
    }

    unbindEvents() {
      const { graph } = this.context
      graph.off(GraphEvent.AFTER_TRANSFORM, this.updateZoomLevel)
    }

    destroy() {
      this.unbindEvents()
      super.destroy()
    }
  }

  register(ExtensionCategory.NODE, 'bom-chart-node', bomChartNode)
  register(ExtensionCategory.BEHAVIOR, 'bom-level-of-detail', bomLevelOfDetail)

  

  onMounted(() => {
    if(props.data) {
      mapInit(props.data)
    }
  })

  function mapInit (data) {
    if(graph) {
      graph.destroy()
    }
    graph = new Graph({
      container: 'bomContainer',
      zoomRange: [0.5, 1],
      cursor: 'default',
      layout: {
        type: 'dagre',
        rankdir: 'TB',
        ranksep: 80,
      },
      autoFit: 'view',
      padding: 10,
      animation: false,
      data: (data.nodes && data.nodes.length > 0) ? data : {},
      node: {
        type: 'bom-chart-node',
        style: {
          labelPlacement: 'center',
          ports: [{ placement: 'top' }, { placement: 'bottom' }],
          radius: 6,
          size: [200, 80],
          lineWidth: 0,
          stroke: '#fff',
          cursor: 'pointer'
        },
      },
      edge: {
        type: 'polyline',
        style: {
          router: {
            type: 'orth',
          },
          lineWidth: 6,
          stroke: '#D7D8DB',
          radius: 100,
        },
      },
      behaviors: ['bom-level-of-detail', 'zoom-canvas', 'drag-canvas'],
      plugins: [
        {
          type: 'contextmenu',
          getItems: () => {
            return [
              {name: '删除', value: 'del'}
            ]
          },
          onClick: (e, target, current) => {
            if(e == 'del') {
              let tp = JSON.parse(JSON.stringify(current.data))
              delete tp.levelValue
              emitHandle('deleteNode', tp)
            }
          },
          enable: e => (e.target && e.target.data && e.target.data.levelValue === 1)
        }
      ],
    })
    graph.on(NodeEvent.CLICK, nodeClick)
    graph.render()
  }

  function nodeClick (e) {
    let tp = JSON.parse(JSON.stringify(e.target.data))
    delete tp.levelValue
    emitHandle('clickNode', tp.id ? tp : {node: tp})
  }

  function emitHandle (type, data) {
    emit(type, data)
  }

  watch(() => props.data, (newVal, oldVal) => {
    if(newVal && newVal.nodes && newVal.nodes.length > 0) {
      mapInit(newVal)
    }else{
      if(graph) {
        graph.destroy()
        graph = null
      }
    }
  })
</script>
<style lang="scss" scoped>
.relation-map{
  width: 100%;
  height: 100%;
  position: relative;
  #bomContainer{
    width: 100%;
    height: 100%;
    ::v-deep(.g6-contextmenu){
      .g6-contextmenu-ul{
        min-width: unset;
      }
    }
  }
  .tool-box{
    position: absolute;
    width: 200px;
    height: 80px;
    background: rgba(0, 0, 0, .3);
    border-radius: 6px;
  }
}
</style>