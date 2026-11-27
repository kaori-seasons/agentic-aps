package cn.windwheel.aps.solve;

import cn.windwheel.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.windwheel.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.windwheel.aps.entity.enums.MaterialSourceEnum;
import cn.windwheel.aps.entity.enums.ProcessRelationshipEnum;
import cn.windwheel.aps.graph.Graph;
import cn.windwheel.aps.graph.GraphNode;
import cn.windwheel.aps.solve.model.BasicData;
import cn.windwheel.aps.solve.model.BomMaterial;
import cn.windwheel.aps.solve.model.IncomingMaterialOrder;
import cn.windwheel.aps.solve.model.Material;
import cn.windwheel.aps.solve.model.MrpMaterial;
import cn.windwheel.aps.solve.model.ProductionOrder;
import cn.windwheel.aps.solve.util.MaterialShortageCollector;
import cn.windwheel.aps.solve.util.MrpUtils;
import cn.windwheel.aps.solve.util.ProductionTaskUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 欠料采集走的是真实的 MRP 展开路径。采集器只读过程值，这里同时守住两件事：
 * 采集到的缺口与 MRP 图上的缺量一致；带不带采集器，欠料图完全相同。
 */
class MaterialShortageCollectorTest {
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 11, 28, 8, 0);
    private static final Duration LEAD = Duration.ofDays(2);
    private static final Duration BUFFER = Duration.ofHours(4);

    private static Material material(String id, String code, MaterialSourceEnum source, int stock) {
        return new Material().setId(id).setCode(code).setName(code + "-name").setSource(source)
                .setQuantity(BigDecimal.valueOf(stock)).setUnit("件").setLeadTimeDuration(LEAD)
                .setBufferTimeDuration(BUFFER);
    }

    private static BomMaterial child(String id, String quantity) {
        var bom = new BomMaterial();
        bom.setMaterialId(id);
        bom.setQuantity(new BigDecimal(quantity));
        return bom;
    }

    private static IncomingMaterialOrder line(String code, String materialId, int quantity, LocalDateTime arrival) {
        var line = new IncomingMaterialOrder();
        line.setCode(code);
        line.setMaterialId(materialId);
        line.setMaterialCode(materialId);
        line.setQuantity(BigDecimal.valueOf(quantity));
        line.setDeliveryTime(arrival);
        return line;
    }

    private static Graph<ProcessRouteNodePropertiesDTO> route(String code) {
        var graph = new Graph<ProcessRouteNodePropertiesDTO>();
        graph.addNode(new GraphNode<ProcessRouteNodePropertiesDTO>().setId(code).setData(
                new ProcessRouteNodePropertiesDTO().setCode(code).setProcessRelationship(ProcessRelationshipEnum.ES)
                        .setUseMainResources(List.of(new ProcessUseMainResourcesDTO().setId("R").setThroughput("10pH")))));
        return graph;
    }

    private static BasicData data(List<Material> materials, Map<String, List<BomMaterial>> bom,
                                  List<IncomingMaterialOrder> incoming) {
        Map<String, Graph<ProcessRouteNodePropertiesDTO>> routes = new HashMap<>();
        materials.forEach(m -> routes.put(m.getId(), route("OP-" + m.getCode())));
        return new BasicData()
                .setMaterialMap(materials.stream().collect(Collectors.toMap(Material::getId, m -> m)))
                .setBomMap(bom)
                .setIncomingMaterialOrderMap(incoming.stream()
                        .collect(Collectors.groupingBy(IncomingMaterialOrder::getMaterialId)))
                .setProcessRouteMap(routes);
    }

    private static ProductionOrder order(String id, String code, String materialId, String quantity) {
        return new ProductionOrder().setId(id).setCode(code).setMaterialId(materialId).setMaterialCode(materialId)
                .setQuantity(new BigDecimal(quantity)).setDeliveryTime(DUE).setPriority(1).setSupplement(false);
    }

    private static void same(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " actual " + actual);
    }

    private static MaterialShortageCollector.Gap only(MaterialShortageCollector collector) {
        List<MaterialShortageCollector.Gap> gaps = collector.snapshot();
        assertEquals(1, gaps.size(), () -> "expected exactly one gap but got " + gaps.size());
        return gaps.get(0);
    }

    /** 物料依赖关系：子件物料 -> 父件物料，根节点的父件记为自身。 */
    private static Map<String, String> upstream(cn.windwheel.aps.graph.Graph<MrpMaterial> graph) {
        Map<String, MrpMaterial> data = graph.getNodes().stream()
                .collect(Collectors.toMap(GraphNode::getId, GraphNode::getData));
        Map<String, String> result = new HashMap<>();
        data.forEach((nodeId, material) -> result.put(material.getId(), graph.getEdges().stream()
                .filter(e -> e.getSource().equals(nodeId)).map(e -> data.get(e.getTarget()).getId())
                .findFirst().orElse(material.getId())));
        return result;
    }

    /** 一期验收：一张“有在途但到货晚于交期”的订单。 */
    @Test
    void lateInTransitIsCollectedWithLineDetailAndMatchesTheMrpGraph() {
        // 成品缺 5，每件用 2 个外购件：毛需求 10。按时到 2，晚到 20 被跳过，缺口 8。
        var data = data(
                List.of(material("FG", "FG-100", MaterialSourceEnum.PRODUCED, 0),
                        material("RM", "RM-200", MaterialSourceEnum.PURCHASED, 0)),
                Map.of("FG", List.of(child("RM", "2"))),
                List.of(line("PO-ONTIME", "RM", 2, DUE.minusDays(1)), line("PO-LATE", "RM", 20, DUE.plusHours(1))));
        var collector = new MaterialShortageCollector();
        var order = order("order-1", "WO-1", "FG", "5");
        var graph = MrpUtils.calculateMaterialAvailability(true, order, data, collector);

        MaterialShortageCollector.Gap gap = only(collector);
        assertEquals("RM-200", gap.getMaterialCode());
        assertEquals("WO-1", gap.getOrderCode());
        assertEquals("order-1", gap.getOrderId());
        assertEquals(DUE, gap.getDeliveryTime());
        // gapQuantity 与 MRP 图上的缺量一致。
        var node = graph.getNodes().stream().map(GraphNode::getData)
                .filter(m -> "RM".equals(m.getId())).findFirst().orElseThrow();
        same("8", node.getQuantity());
        same("8", gap.getGapQuantity());
        same("10", gap.getDemandQuantity());
        same("0", gap.getOnHandConsumed());
        same("2", gap.getInTransitConsumed());
        // inTransitSkipped 大于零，skippedLines 有明细。
        same("20", gap.getInTransitSkipped());
        assertEquals(List.of("PO-LATE"), gap.getSkippedLines().stream().map(l -> l.lineCode()).toList());
        same("20", gap.getSkippedLines().get(0).quantity());
        assertEquals(DUE.plusHours(1), gap.getSkippedLines().get(0).deliveryTime());
        // 可用时刻含缓冲期：到货 + 4 小时。
        assertEquals(DUE.plusHours(5), gap.getSkippedLines().get(0).availableAt());
        assertEquals(List.of("PO-ONTIME"), gap.getAllocatedLines().stream().map(l -> l.lineCode()).toList());
        same("2", gap.getAllocatedLines().get(0).usedQuantity());
        assertEquals(LEAD, gap.getLeadTime());
        assertEquals(BUFFER, gap.getBufferTime());
        assertEquals("件", gap.getUnit());
        assertEquals(0, gap.getSequence());
        assertEquals(1, gap.getOccurrences());
    }

    @Test
    void collectorDoesNotChangeTheMrpGraph() {
        var data = data(
                List.of(material("FG", "FG-100", MaterialSourceEnum.PRODUCED, 1),
                        material("RM", "RM-200", MaterialSourceEnum.PURCHASED, 3)),
                Map.of("FG", List.of(child("RM", "2"))),
                List.of(line("PO-LATE", "RM", 20, DUE.plusHours(1))));
        var plain = MrpUtils.calculateMaterialAvailability(true, order("order-1", "WO-1", "FG", "5"),
                data(List.of(material("FG", "FG-100", MaterialSourceEnum.PRODUCED, 1),
                        material("RM", "RM-200", MaterialSourceEnum.PURCHASED, 3)),
                        Map.of("FG", List.of(child("RM", "2"))),
                        List.of(line("PO-LATE", "RM", 20, DUE.plusHours(1)))));
        var observed = MrpUtils.calculateMaterialAvailability(true, order("order-1", "WO-1", "FG", "5"), data,
                new MaterialShortageCollector());
        Map<String, BigDecimal> plainQuantities = plain.getNodes().stream()
                .collect(Collectors.toMap(n -> n.getData().getId(), n -> n.getData().getQuantity()));
        Map<String, BigDecimal> observedQuantities = observed.getNodes().stream()
                .collect(Collectors.toMap(n -> n.getData().getId(), n -> n.getData().getQuantity()));
        assertEquals(plainQuantities, observedQuantities);
        // 节点 id 是每次展开临时生成的，不能直接比；比依赖关系的形状。
        assertEquals(plain.getEdges().size(), observed.getEdges().size());
        assertEquals(upstream(plain), upstream(observed));
    }

    /** 订单产品本身的缺口是生产需求，自制子件的缺口由补充订单消化，两者都不是催料对象。 */
    @Test
    void orderProductAndMadeInHouseGapsAreNotCollected() {
        var data = data(
                List.of(material("FG", "FG-100", MaterialSourceEnum.PRODUCED, 0),
                        material("SF", "SF-300", MaterialSourceEnum.PRODUCED, 0),
                        material("RM", "RM-200", MaterialSourceEnum.PURCHASED, 0)),
                Map.of("FG", List.of(child("SF", "1")), "SF", List.of(child("RM", "3"))),
                List.of());
        var collector = new MaterialShortageCollector();
        MrpUtils.calculateMaterialAvailability(true, order("order-1", "WO-1", "FG", "5"), data, collector);

        MaterialShortageCollector.Gap gap = only(collector);
        assertEquals("RM-200", gap.getMaterialCode());
        same("15", gap.getGapQuantity());

        // 订单产品自己就是外购件且库存不够：根节点的缺口是“这单要买的货”，不进欠料清单。
        var purchased = data(List.of(material("TRADER", "TRADER-1", MaterialSourceEnum.PURCHASED, 0)), Map.of(), List.of());
        var none = new MaterialShortageCollector();
        MrpUtils.calculateMaterialAvailability(true, order("order-2", "WO-2", "TRADER", "5"), purchased, none);
        assertTrue(none.snapshot().isEmpty());
    }

    /** 同一外购件在一单 BOM 里缺两处：按订单加物料合并成一条，出现次数留档。 */
    @Test
    void gapsOfTheSameMaterialInOneOrderAreAggregated() {
        var data = data(
                List.of(material("FG", "FG-100", MaterialSourceEnum.PRODUCED, 0),
                        material("SFA", "SFA-1", MaterialSourceEnum.PRODUCED, 0),
                        material("SFB", "SFB-1", MaterialSourceEnum.PRODUCED, 0),
                        material("RM", "RM-200", MaterialSourceEnum.PURCHASED, 0)),
                Map.of("FG", List.of(child("SFA", "1"), child("SFB", "1")),
                        "SFA", List.of(child("RM", "1")), "SFB", List.of(child("RM", "1"))),
                List.of());
        var collector = new MaterialShortageCollector();
        var graph = MrpUtils.calculateMaterialAvailability(true, order("order-1", "WO-1", "FG", "5"), data, collector);

        // 欠料图上是两个 RM 节点；欠料清单上只能有一条，否则键会撞。
        assertEquals(2, graph.getNodes().stream().filter(n -> "RM".equals(n.getData().getId())).count());
        MaterialShortageCollector.Gap gap = only(collector);
        assertEquals(2, gap.getOccurrences());
        same("10", gap.getGapQuantity());
        same("10", gap.getDemandQuantity());
    }

    /**
     * 存量缺陷的刻画测试，不是本次改造引入的：同一外购件在一单里缺两处时，任务生成会因重复键抛异常，
     * 整次求解失败。这次改造坚持“只加观察不改行为”，没有顺手修它。
     * 修掉它的人请同步改这里：到那时断言应当变成“正常生成任务”。
     */
    @Test
    void taskGenerationStillFailsOnDuplicateMaterialTodayButTheEvidenceIsKept() {
        var data = data(
                List.of(material("FG", "FG-100", MaterialSourceEnum.PRODUCED, 0),
                        material("SFA", "SFA-1", MaterialSourceEnum.PRODUCED, 0),
                        material("SFB", "SFB-1", MaterialSourceEnum.PRODUCED, 0),
                        material("RM", "RM-200", MaterialSourceEnum.PURCHASED, 0)),
                Map.of("FG", List.of(child("SFA", "1"), child("SFB", "1")),
                        "SFA", List.of(child("RM", "1")), "SFB", List.of(child("RM", "1"))),
                List.of());
        var collector = new MaterialShortageCollector();
        var order = order("order-1", "WO-1", "FG", "5");
        var graph = MrpUtils.calculateMaterialAvailability(true, order, data, collector);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ProductionTaskUtils.generateTask(order, data, graph, List.of()));
        assertTrue(failure.getMessage().contains("Duplicate key RM"), failure.getMessage());
        // 任务生成失败不影响已采集到的欠料证据：MRP 在任务生成之前就完成了。
        same("10", only(collector).getGapQuantity());
    }

    /** 缺口归属依赖订单处理顺序，这是现状。采集器把依据留档：期初多少、被谁先占了、本单排第几。 */
    @Test
    void attributionBasisIsRecordedBecauseItDependsOnProcessingOrder() {
        // 外购件库存 10，两张订单各要 10。先处理的那张拿走库存，后处理的那张背缺口。
        var first = data(
                List.of(material("FG", "FG-100", MaterialSourceEnum.PRODUCED, 0),
                        material("RM", "RM-200", MaterialSourceEnum.PURCHASED, 10)),
                Map.of("FG", List.of(child("RM", "2"))), List.of());
        var collector = new MaterialShortageCollector();
        MrpUtils.calculateMaterialAvailability(true, order("order-a", "WO-A", "FG", "5"), first, collector);
        MrpUtils.calculateMaterialAvailability(true, order("order-b", "WO-B", "FG", "5"), first, collector);

        MaterialShortageCollector.Gap gap = only(collector);
        assertEquals("WO-B", gap.getOrderCode());
        same("10", gap.getGapQuantity());
        same("10", gap.getOnHandStart());
        same("0", gap.getOnHandAvailable());
        same("0", gap.getOnHandConsumed());
        assertEquals(1, gap.getSequence());

        // 换个顺序就换个人背缺口：同样的数据，只是处理顺序不同。
        var reversed = data(
                List.of(material("FG", "FG-100", MaterialSourceEnum.PRODUCED, 0),
                        material("RM", "RM-200", MaterialSourceEnum.PURCHASED, 10)),
                Map.of("FG", List.of(child("RM", "2"))), List.of());
        var other = new MaterialShortageCollector();
        MrpUtils.calculateMaterialAvailability(true, order("order-b", "WO-B", "FG", "5"), reversed, other);
        MrpUtils.calculateMaterialAvailability(true, order("order-a", "WO-A", "FG", "5"), reversed, other);
        assertEquals("WO-A", only(other).getOrderCode());
    }

    @Test
    void unconstrainedSchedulingProducesNoShortageEvidence() {
        // 不约束物料时不做 MRP，只有根节点，不能把“没观测到”当成缺口。
        var data = data(
                List.of(material("FG", "FG-100", MaterialSourceEnum.PRODUCED, 0),
                        material("RM", "RM-200", MaterialSourceEnum.PURCHASED, 0)),
                Map.of("FG", List.of(child("RM", "2"))), List.of());
        var collector = new MaterialShortageCollector();
        var graph = MrpUtils.calculateMaterialAvailability(false, order("order-1", "WO-1", "FG", "5"), data, collector);
        assertEquals(1, graph.getNodes().size());
        assertTrue(collector.snapshot().isEmpty());
    }

    @Test
    void gapNodeQuantityIsTheShortageNotTheDemand() {
        // MrpMaterial.quantity 在扣减后被覆写成缺料数量，毛需求必须在覆写前留一份。
        var data = data(
                List.of(material("FG", "FG-100", MaterialSourceEnum.PRODUCED, 0),
                        material("RM", "RM-200", MaterialSourceEnum.PURCHASED, 4)),
                Map.of("FG", List.of(child("RM", "2"))), List.of());
        var collector = new MaterialShortageCollector();
        var graph = MrpUtils.calculateMaterialAvailability(true, order("order-1", "WO-1", "FG", "5"), data, collector);
        MrpMaterial node = graph.getNodes().stream().map(GraphNode::getData)
                .filter(m -> "RM".equals(m.getId())).findFirst().orElseThrow();
        MaterialShortageCollector.Gap gap = only(collector);
        same("6", node.getQuantity());
        same("6", gap.getGapQuantity());
        same("10", gap.getDemandQuantity());
        same("4", gap.getOnHandConsumed());
    }
}
