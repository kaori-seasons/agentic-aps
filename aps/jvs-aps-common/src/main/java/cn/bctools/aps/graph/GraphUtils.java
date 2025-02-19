package cn.bctools.aps.graph;

import cn.bctools.common.utils.ObjectNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 图工具
 */
public class GraphUtils {

    private GraphUtils() {
    }

    /**
     * 是否为空图
     *
     * @return true-空，false-非空
     */
    public static <T> Boolean isEmpty(Graph<T> graph) {
        return graph == null || graph.getNodes().isEmpty();
    }

    /**
     * 检测是否有多个节点无出度
     *
     * @param graph 图
     * @return true-是，false-否
     */
    public static <T> boolean checkMultipleNodeNoOutDegree(Graph<T> graph) {
        return getNoOutDegreeNodeIds(graph).size() >= 2;
    }

    /**
     * 检测是否有环
     *
     * @param graph 图
     * @return true-有环，false-无环
     */
    public static <T> boolean checkHasCycle(Graph<T> graph) {
        int[] count = {0};
        Function<Topological, Integer> function = topological -> 1;
        Consumer<List<Integer>> consumer = integers -> count[0] = integers.size();
        topologicalSort(graph, function, consumer);
        return count[0] != graph.getNodes().size();
    }

    /**
     * 获取无出度的节点id
     *
     * @param routeDesign 工艺路线设计
     * @return true-是，false-否
     */
    public static <T> List<String> getNoOutDegreeNodeIds(Graph<T> routeDesign) {
        Map<String, Long> nodeFromCountMap = routeDesign.getEdges()
                .stream()
                .collect(Collectors.groupingBy(GraphEdge::getSource, Collectors.counting()));
        return routeDesign.getNodes()
                .stream()
                .map(node -> {
                    long count = nodeFromCountMap.getOrDefault(node.getId(), 0L);
                    return count == 0 ? node : null;
                })
                .filter(ObjectNull::isNotNull)
                .map(GraphNode::getId)
                .collect(Collectors.toList());
    }

    /**
     * 拓扑排序
     *
     * @param graph 图
     * @param function 节点函数
     * @param finalProcessor 最终结果函数
     * @param <T> 图
     * @param <NodeResult> 节点处理结果
     */
    public static <T, NodeResult> void topologicalSort(Graph<T> graph,
                                                       Function<Topological, NodeResult> function,
                                                       Consumer<List<NodeResult>> finalProcessor) {
        // 邻接表，存储每个节点指向的所有节点 Map<节点id, 指向的节点id集合>
        Map<String, List<String>> adjacencyMap = new HashMap<>();
        Map<String, Integer> inDegreeMap = new HashMap<>();
        graph.getNodes().forEach(node -> {
            adjacencyMap.put(node.getId(), new ArrayList<>());
            inDegreeMap.put(node.getId(), 0);
        });

        // 遍历线，填充邻接表和计算每个节点的入度数量
        graph.getEdges().forEach(edge -> {
            if (adjacencyMap.containsKey(edge.getSource())) {
                adjacencyMap.get(edge.getSource()).add(edge.getTarget());
            }
            if (inDegreeMap.containsKey(edge.getTarget())) {
                int inDegreeCount = inDegreeMap.get(edge.getTarget()) + 1;
                inDegreeMap.put(edge.getTarget(), inDegreeCount);
            }
        });

        // 没有入度的节点id集合
        Set<String> noInDegreeNodeIds = new HashSet<>();
        Queue<String> noInDegreeNodeQueue = new LinkedList<>();
        inDegreeMap.forEach((key, value) -> {
            if (value == 0) {
                noInDegreeNodeQueue.offer(key);
                noInDegreeNodeIds.add(key);
            }
        });

        List<NodeResult> results = new ArrayList<>();
        while (!noInDegreeNodeQueue.isEmpty()) {
            String nodeId = noInDegreeNodeQueue.poll();
            adjacencyMap.get(nodeId).forEach(nextNodeId -> {
                int nextNodeInDegreeCount = inDegreeMap.get(nextNodeId) - 1;
                inDegreeMap.put(nextNodeId, nextNodeInDegreeCount);
                if (nextNodeInDegreeCount == 0) {
                    noInDegreeNodeQueue.offer(nextNodeId);
                }
            });

            Topological topological = new Topological()
                    .setNodeId(nodeId)
                    .setNoInDegreeNodeIds(noInDegreeNodeIds);
            NodeResult result = function.apply(topological);
            results.add(result);
        }

        finalProcessor.accept(results);
    }
}
