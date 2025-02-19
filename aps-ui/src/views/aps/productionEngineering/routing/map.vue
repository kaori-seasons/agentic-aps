<template>
  <div class="relation-map">
    <div class="top-bar">
      <div class="left">
        <el-popover
          :visible="showSearchList"
          placement="bottom"
          :width="224"
          trigger="click"
          popper-style="padding: 0;"
        >
          <el-scrollbar  class="search-list">
            <div v-if="!(searchList && searchList.length > 0)" class="empty-box">暂无数据</div>
            <div v-for="item in searchList" :key="'search-item-'+item.id" class="select-item" @click="viewToNode(item)">
              <span class="name">{{item.data.name}}</span>
              <span class="code">{{item.data.code}}</span>
            </div>
          </el-scrollbar>
          <template #reference>
            <div class="search-input-box" @click.stop="oepnSearch(true)" v-click-outside="oepnSearch">
              <svg class="icon" aria-hidden="true">
                <use xlink:href="#icon-jvs-sousuo"></use>
              </svg>
              <div class="h-line"></div>
              <el-input v-model="searchKey" class="text" placeholder="搜索" @input="oepnSearch(true)"></el-input>
            </div>
          </template>
        </el-popover>
        <div class="tool-list">
          <div class="tool-list-item" @click="addNodeHandle">
            <el-icon><CirclePlus /></el-icon>
            <span>新增工序</span>
          </div>
          <div :class="{'tool-list-item': true, 'active': currentTool == 'create-edge'}" @click="tooClick('create-edge')">
            <el-icon><Right /></el-icon>
            <span>连线</span>
          </div>
        </div>
      </div>
      <div class="right">
        <el-button type="primary" :loading="saveLoading" @click="saveHandle">保存</el-button>
        <slot name="right"></slot>
      </div>
    </div>
    <div id="routingContainer"></div>

    <!-- 新增 修改 -->
    <technologyForm
      :static="true"
      :visible="dialogVisible"
      :dialogTitle="dialogTitle"
      :row="rowData"
      @submit="submitHandle"
      @close="closeHandle"
    ></technologyForm>
  </div>
</template>
<script lang="ts" setup name="relationMap">
  import { ref, onMounted, watch, computed } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElNotification } from 'element-plus'
  import { Badge, BaseBehavior, ExtensionCategory, Graph, GraphEvent, Label, Rect, Circle, register, NodeEvent, EdgeEvent } from '@antv/g6'
  import { CirclePlus, Right } from '@element-plus/icons-vue'

  import { generateUUID } from '@/util/util'
  
  import technologyForm from '../component/technology.vue'

  const { t } = useI18n()
  const emit = defineEmits(['clickNode', 'deleteNode'])
  const props = defineProps({
    data: {
      type: Object
    },
    saveLoading: {
      type: Boolean
    }
  })

  const searchKey = ref('')
  const showSearchList = ref(false)
  const currentTool = ref('')
  const mapData = ref({
    nodes: [],
    edges: []
  })
  const dialogVisible = ref(false)
  const dialogTitle = ref('')
  const rowData = ref(null)

  const searchList = computed(() => {
    let temp = []
    if(graph) {
      let nodes = graph.getNodeData()
      nodes.filter(node => {
        if(searchKey.value){
          if(node.data && (node.data.name.includes(searchKey.value) || node.data.code.includes(searchKey.value))) {
            temp.push(node)
          }
        }else{
          temp.push(node)
        }
      })
    }else{
      if(props.data.nodes && props.data.nodes.length > 0) {
        props.data.nodes.filter(node => {
          if(searchKey.value){
            if(node.data && (node.data.name.includes(searchKey.value) || node.data.code.includes(searchKey.value))) {
              temp.push(node)
            }
          }else{
            temp.push(node)
          }
        })
      }
    }
    return temp
  })
  
  let sourceId = ''
  let targetId = ''


  let graph = null
  const DEFAULT_LEVEL = 'detailed'
  class ChartNode extends Rect {
    get data () {
      return this.context.model.getElementDataById(this.id).data
    }
    get level() {
      return this.data.level || DEFAULT_LEVEL
    }
    get createEdgeBool() {
      let createEdge = false
      this.context.behavior['extensions'].filter(eit => {
        if(eit.key == 'create-edge' && eit.enable) {
          createEdge = true
        }
      })
      return createEdge
    }

    getLabelStyle () {
      const text = this.data.name
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
        cursor: this.createEdgeBool ? 'crosshair' : 'pointer',
      }
    }

    getDescStyle(attributes) {
      if (this.level === 'overview') return false
      return {
        text: '工艺编号：' + this.data.code,
        fontSize: 14,
        fontWeight: 400,
        fill: '#6F7588',
        textAlign: 'left',
        transform: [['translate', -76, 12]],
      }
    }

    drawDescShape(attributes, container) {
      const positionStyle = this.getDescStyle(attributes)
      this.upsert('code', Label, positionStyle, container)
    }

    render(attributes = this.parsedAttributes, container = this) {
      super.render(attributes, container)
      this.drawDescShape(attributes, container)
    }
  }
  class LevelOfDetail extends BaseBehavior {
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
          graph.updateNodeData((prev) => prev.map((node) => ({ ...node, data: { ...node.data, level }, style: {...node.style, cursor: this.createEdgeBool ? 'crosshair' : 'pointer'} })))
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
  

  register(ExtensionCategory.NODE, 'chart-node', ChartNode)
  register(ExtensionCategory.BEHAVIOR, 'level-of-detail', LevelOfDetail)

  

  onMounted(() => {
    if(props.data) {
      mapData.value = props.data
      mapInit()
    }
  })

  function mapInit () {
    if(graph) {
      graph.destroy()
    }
    let option = {
      container: 'routingContainer',
      zoomRange: [0.3, 1],
      cursor: 'default',
      autoFit: 'view',
      padding: 10,
      animation: false,
      data: mapData.value,
      node: {
        type: 'chart-node',
        style: {
          labelPlacement: 'center',
          radius: 6,
          size: [200, 80],
          lineWidth: 0,
          stroke: '#fff',
          cursor: 'pointer',
        },
      },
      edge: {
        type: 'line',
        style: {
          lineWidth: 6,
          stroke: '#D7D8DB',
          endArrow: true,
          loop: false,
          cursor: 'pointer',
          zIndex: 0
        },
      },
      behaviors: ['level-of-detail', 'zoom-canvas', 'drag-canvas',
        {
          key: 'drag-element',
          type: 'drag-element',
          enable: true
        },
        {
          key: 'create-edge',
          type: 'create-edge',
          trigger: 'drag',
          enable: false,
          onCreate: edge => {
            let source = edge.source
            let nodeEdges = graph.getRelatedEdgesData(source)
            let count = 0
            nodeEdges.filter(eit => {
              if(eit.source == source) {
                count += 1
              }
            })
            if(count < 2) {
              return edge
            }else{
              ElNotification.closeAll()
              ElNotification({
                title: t(`common.tip`),
                message: '每个工序只能存在一个后工序',
                position: 'bottom-right',
                type: 'error',
                duration: 2000
              })
            }
          },
        }
      ],
      plugins: [
        {
          type: 'contextmenu',
          getItems: (e) => {
            if(e.targetType == 'canvas') {
              return []
            }
            return [
              {name: '删除', value: 'del'}
            ]
          },
          onClick: (e, target, current) => {
            if(e == 'del') {
              if(current.type == 'edge') {
                deleteEdgeHandle(current)
              }else{
                deleteNodeHandle(current)
              }
            }
          },
          enable: true
        }
      ],
    }
    if(!(mapData.value && mapData.value.nodes && mapData.value.nodes.length > 0 && mapData.value.nodes[0].style)) {
      option.layout = {
        type: 'antv-dagre',
        rankdir: 'LR',
        align: 'UL',
        nodesep: 50,
        ranksep: 70,
        controlPoints: true,
        nodeSize: [200, 80],
      }
    }
    graph = new Graph(option)
    graph.on(NodeEvent.CLICK, nodeClick)
    graph.render()
  }

  function addNodeHandle () {
    currentTool.value = ''
    createEdgeHandle(false)
    rowData.value = null
    dialogTitle.value = t(`aps.productionEngineering.technology.add`)
    dialogVisible.value = true
  }

  function nodeClick (e) {
    if(currentTool.value != 'create-edge' && e.target && e.target.data) {
      rowData.value = JSON.parse(JSON.stringify(e.target.data))
      dialogTitle.value = t(`aps.productionEngineering.technology.edit`)
      dialogVisible.value = true
    }
  }

  function deleteNodeHandle (node) {
    graph.removeNodeData([node.id])
    graph.draw()
  }

  function createEdgeHandle (bool) {
    graph.updateBehavior({key: 'create-edge', enable: bool})
    graph.updateBehavior({key: 'drag-element', enable: !bool})
    graph.draw()
  }

  function deleteEdgeHandle (edge) {
    graph.removeEdgeData([edge.id])
    graph.draw()
  }

  function emitHandle (type, data) {
    emit(type, data)
  }

  function oepnSearch (bool) {
    showSearchList.value = (bool === true ? true : false)
  }

  function viewToNode (node) {
    graph.focusElement(node.id)
    showSearchList.value = false
    searchKey.value = ''
  }

  function tooClick (type) {
    if(currentTool.value == type) {
      currentTool.value = ''
    }else{
      currentTool.value = type
    }
    if(type == 'create-edge') {
      createEdgeHandle(currentTool.value == type)
    }
  }

  function submitHandle (type, form) {
    let norepeatBool =  true
    let allNodes = graph.getNodeData()
    for(let i in allNodes) {
      if(allNodes[i].id != form.nodeId && allNodes[i].data.code == form.code) {
        norepeatBool = false
      }
    }
    if(norepeatBool) {
      if(type == 'edit') {
        graph.updateNodeData([{id: form.nodeId, data: form}])
        graph.draw()
      }else{
        let id = generateUUID()
        graph.addNodeData([{id: id, data: {...form, nodeId: id, level: (graph.getZoom() > 0.6 ? 'detailed' : 'overview')}, style: {x: 0, y: 40}}])
        graph.draw().then(() => {
          viewToNode({id: id})
        })
      }
      closeHandle()
    }else{
      ElNotification.closeAll()
      ElNotification({
        title: t(`common.tip`),
        message: '工序编码存在重复',
        position: 'bottom-right',
        type: 'error',
      })
    }
  }

  function closeHandle () {
    rowData.value = null
    dialogTitle.value = ''
    dialogVisible.value = false
  }

  function saveHandle () {
    emit('save', graph.getData())
  }

  watch(() => props.data, (newVal, oldVal) => {
    if(newVal && newVal.nodes && newVal.nodes.length > 0) {
      mapData.value = newVal
      mapInit()
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
  .top-bar{
    margin: 0 0 16px 0;
    padding: 0 16px;
    box-sizing: border-box;
    display: flex;
    justify-content: space-between;
    align-items: center;
    .left{
      display: flex;
      align-items: center;
      .tool-list{
        display: flex;
        align-items: center;
        background: #fff;
        border-radius: 0 4px 4px 0;
        overflow: hidden;
        border-left: 1px solid #F5F6F7;
        .tool-list-item{
          min-width: 80px;
          display: flex;
          align-items: center;
          justify-content: center;
          height: 36px;
          line-height: 36px;
          padding: 0 10px;
          box-sizing: border-box;
          cursor: pointer;
          .el-icon{
            margin-right: 6px;
          }
          &:hover{
            background: #DDEAFF;
            color: #1E6FFF;
          }
          &.active{
            background: #1E6FFF;
            color: #fff;
          }
        }
      }
    }
    .right{
      display: flex;
      align-items: center;
    }
    .search-input-box {
      background-color: #fff;
      border-radius: 4px 0 0 4px;
      height: 36px;
      width: 224px;
      display: flex;
      align-items: center;
      cursor: pointer;
      outline: none;
      .icon {
        width: 14px;
        height: 14px;
        margin: 0px 14px;
      }
      .h-line {
        height: 14px;
        background-color: #d7d8db;
        width: 1px;
        margin-left: 0px;
        margin-right: 0px;
      }
      .text {
        margin-left: 14px;
        font-size: 14px;
        color: #6F7588;
        height: 100%;
        @include SourceHanSansCN-Regular;
        ::v-deep(.el-input__wrapper){
          padding: 0;
          box-shadow: none;
        }
      }
    }
    .el-button.el-button--primary:not(.is-plain){
      background: #1E6FFF;
      width: 104px;
      height: 36px;
    }
  }
  #routingContainer{
    width: 100%;
    height: calc(100% - 52px);
    ::v-deep(.g6-contextmenu){
      .g6-contextmenu-ul{
        min-width: unset;
      }
    }
    ::v-deep(.g6-toolbar){
      .g6-toolbar-item{
        svg{
          fill: #6F7588;
        }
      }
    }
  }
}
::v-deep(.el-popover.el-popper){
  padding: 0;
}
.search-list{
  width: 100%;
  padding: 10px;
  height: 300px;
  box-sizing: border-box;
  position: relative;
  .empty-box{
    margin-top: 10px;
    color: #6F7588;
    text-align: center;
  }
}
.select-item{
  width: 100%;
  padding: 0 10px;
  height: 30px;
  line-height: 30px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: pre;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  box-sizing: border-box;
  &:hover{
    background-color: #DDEAFF;
    .name, .code{
      color: #1E6FFF;
    }
  }
  .name{
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: pre;
    color: #363B4C;
  }
  .code{
    margin-left: 10px;
    max-width: 30%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: pre;
    color: #6F7588;
    font-size: 12px;
  }
}
</style>