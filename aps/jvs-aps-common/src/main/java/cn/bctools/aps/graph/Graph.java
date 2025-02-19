package cn.bctools.aps.graph;

import lombok.Data;

import javax.validation.Valid;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 图
 */
@Data
public class Graph<T> {
    @Valid
    private Set<GraphNode<T>> nodes = new HashSet<>();
    private Set<GraphEdge> edges = new HashSet<>();

    /**
     * 添加点
     *
     * @param node 点
     */
    public void addNode(GraphNode<T> node) {
        nodes.add(node);
    }

    /**
     * 添加线
     *
     * @param edge 线
     */
    public void addEdge(GraphEdge edge) {
        edges.add(edge);
    }

    /**
     * 删除节点
     *
     * @param node 节点
     */
    public void removeNode(GraphNode<T> node) {
        nodes.remove(node);
    }

    /**
     * 获取节点
     *
     * @param nodeId 节点id
     * @return 节点
     */
    public GraphNode<T> getNode(String nodeId) {
        return nodes.stream()
                .filter(node -> node.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取以指定节点id为结束节点的所有起始节点id
     *
     * @param nodeId 节点id
     * @return 起始节点id集合
     */
    public Set<String> getNodeFromIds(String nodeId) {
        return edges.stream()
                .filter(edge -> edge.getTarget().equals(nodeId))
                .map(GraphEdge::getSource)
                .collect(Collectors.toSet());
    }

    /**
     * 获取以指定节点id为开始节点的所有结束节点id
     *
     * @param nodeId 节点id
     * @return 结束节点id集合
     */
    public Set<String> getNodeToIds(String nodeId) {
        return edges.stream()
                .filter(edge -> edge.getSource().equals(nodeId))
                .map(GraphEdge::getTarget)
                .collect(Collectors.toSet());
    }
}
