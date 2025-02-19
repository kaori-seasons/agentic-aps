package cn.bctools.aps.solve.util;

import cn.bctools.aps.graph.Graph;
import cn.bctools.aps.graph.GraphEdge;
import cn.bctools.aps.graph.GraphNode;
import cn.bctools.aps.solve.model.*;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * @author jvs
 * 物料需求计划
 */
public class MrpUtils {
    private MrpUtils() {
    }

    /**
     * 物料齐套计算
     * 1. 从BOM顶层往下依次检查是否缺料。 直到没有欠料的层级
     * 2. 最终生成一个有向无环图结构的物料需求清单
     *    - 点：欠料物料
     *    - 线：物料依赖关系（入度：上层物料，出度：下层物料）
     * 3. 生成计划时，可根据此图结构，构造任务依赖
     *
     * @param materialConstrained true-约束物料, false-不约束物料
     * @param order 订单
     * @param basicData 基础数据
     * @return 物料齐套计算结果（若启用约束物料，则计算结果为欠料信息；否则为生产订单物料需求信息）
     */
    public static Graph<MrpMaterial> calculateMaterialAvailability(boolean materialConstrained, ProductionOrder order, BasicData basicData) {
        Graph<MrpMaterial> mrpGraph = new Graph<>();
        MaterialStack orderMaterial = buildMaterialStack(order.getMaterialId(), order.getQuantity(), null);

        // 不约束物料，不执行MRP计算
        if (!materialConstrained) {
            addMrpMaterial(mrpGraph, orderMaterial, null);
            return mrpGraph;
        }

        // 约束物料，执行MRP计算
        Map<String, Material> materialMap = basicData.getMaterialMap();
        Map<String, List<BomMaterial>> bomMap = basicData.getBomMap();
        Map<String, List<IncomingMaterialOrder>> incomingMaterialOrderMap = basicData.getIncomingMaterialOrderMap();

        ArrayDeque<MaterialStack> materialStack = new ArrayDeque<>();
        materialStack.push(orderMaterial);
        while (!materialStack.isEmpty()) {
            MaterialStack stack = materialStack.pop();
            MrpMaterial mrpMaterial = stack.getMrpMaterial();
            Material material = materialMap.get(mrpMaterial.getId());
            // 扣减库存，得到缺料数量
            BigDecimal lackQuantity = InventoryUtils.deductInventory(order, material, mrpMaterial.getQuantity(), incomingMaterialOrderMap);
            mrpMaterial.setQuantity(lackQuantity);
            // 库存不足
            if (lackQuantity.compareTo(BigDecimal.ZERO) > 0) {
                // 添加当前物料MRP计算结果
                addMrpMaterial(mrpGraph, stack, stack.getParentId());
                // 将下层物料入栈
                List<BomMaterial> bomMaterialList = Optional.ofNullable(bomMap.get(mrpMaterial.getId()))
                        .orElseGet(ArrayList::new);
                bomMaterialList.forEach(bomMaterial -> {
                    BigDecimal quantity = bomMaterial.getQuantity().multiply(lackQuantity).setScale(6, RoundingMode.HALF_UP);
                    MaterialStack childMaterial = buildMaterialStack(bomMaterial.getMaterialId(), quantity, stack.getId());
                    materialStack.push(childMaterial);
                });
            }
        }
        return mrpGraph;
    }

    /**
     * 添加MRP物料需求
     *
     * @param mrpGraph mrp物料需求计算结果
     * @param mrpMaterial mrp物料需求
     * @param parentId 上层物料节点id
     */
    private static void addMrpMaterial(Graph<MrpMaterial> mrpGraph, MaterialStack mrpMaterial, String parentId) {
        GraphNode<MrpMaterial> node = new GraphNode<>();
        node.setId(mrpMaterial.getId());
        node.setData(mrpMaterial.getMrpMaterial());
        mrpGraph.addNode(node);
        // 添加线
        if (ObjectNull.isNotNull(parentId)) {
            GraphEdge edge = new GraphEdge()
                    .setSource(node.getId())
                    .setTarget(parentId);
            mrpGraph.addEdge(edge);
        }
    }


    /**
     * 构造物料栈数据
     *
     * @param materialId 物料id
     * @param quantity 需求数量
     * @param parentId 上层物料
     * @return 物料信息
     */
    private static MaterialStack buildMaterialStack(String materialId, BigDecimal quantity, String parentId) {
        MrpMaterial mrpMaterial = new MrpMaterial()
                .setId(materialId)
                .setQuantity(quantity);
        return new MaterialStack()
                .setId(IdWorker.getIdStr())
                .setMrpMaterial(mrpMaterial)
                .setParentId(parentId);
    }

    @Data
    @Accessors(chain = true)
    private static class MaterialStack {
        /**
         * mrp物料需求
         */
        private MrpMaterial mrpMaterial;

        /**
         * 节点id
         */
        private String id;

        /**
         * 上层节点id
         */
        private String parentId;
    }


}
