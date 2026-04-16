package cn.windwheel.aps.entity.handler;

import cn.windwheel.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.windwheel.aps.graph.Graph;
import cn.windwheel.aps.graph.GraphEdge;
import cn.windwheel.aps.graph.GraphNode;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

/**
 * @author jvs
 */
public class ProcessRouteDesignTypeHandler extends AbstractJsonTypeHandler<Graph<ProcessRouteNodePropertiesDTO>> {
    @Override
    protected Graph<ProcessRouteNodePropertiesDTO> parse(String json) {
        Graph<Object> rawGraph = JSON.parseObject(json, new TypeReference<Graph<Object>>() {});
        return normalize(rawGraph);
    }

    @Override
    protected String toJson(Graph<ProcessRouteNodePropertiesDTO> obj) {
        return JSON.toJSONString(normalize(obj),
                JSONWriter.Feature.WriteMapNullValue,
                JSONWriter.Feature.WriteNullListAsEmpty,
                JSONWriter.Feature.WriteNullStringAsEmpty);
    }

    private Graph<ProcessRouteNodePropertiesDTO> normalize(Graph<?> rawGraph) {
        if (rawGraph == null) {
            return null;
        }
        Graph<ProcessRouteNodePropertiesDTO> graph = new Graph<>();
        if (rawGraph.getNodes() != null) {
            rawGraph.getNodes().forEach(rawNode -> {
                GraphNode<ProcessRouteNodePropertiesDTO> node = new GraphNode<>();
                node.setId(rawNode.getId());
                node.setStyle(rawNode.getStyle());
                node.setData(parseNodeData(rawNode.getData()));
                graph.addNode(node);
            });
        }
        if (rawGraph.getEdges() != null) {
            rawGraph.getEdges().forEach(rawEdge -> {
                GraphEdge edge = new GraphEdge();
                edge.setSource(rawEdge.getSource());
                edge.setTarget(rawEdge.getTarget());
                graph.addEdge(edge);
            });
        }
        return graph;
    }

    private ProcessRouteNodePropertiesDTO parseNodeData(Object rawData) {
        if (rawData == null) {
            return null;
        }
        if (rawData instanceof ProcessRouteNodePropertiesDTO nodeProperties) {
            return nodeProperties;
        }
        if (rawData instanceof String text) {
            if (text.isBlank()) {
                return null;
            }
            return JSON.parseObject(text, ProcessRouteNodePropertiesDTO.class);
        }
        return JSON.parseObject(JSON.toJSONString(rawData), ProcessRouteNodePropertiesDTO.class);
    }
}
