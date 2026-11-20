package cn.windwheel.aps.demand;

import static cn.windwheel.aps.demand.PlanningChecks.*;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/** Approved BOM slice and variant stocks for one published family demand run. */
public record ConfiguredAssembly(
        String parentRun,
        String version,
        Instant validFrom,
        Instant validUntil,
        Map<String, String> orderVariants,
        Map<String, BigDecimal> forecastMix,
        Map<String, BigDecimal> freeAssemblyStock,
        Map<String, List<Part>> bom) {
    public record Part(String material, BigDecimal per) {}

    private record Need(String material, BigDecimal quantity) {}

    void validate() {
        key(parentRun);
        key(version);
        require(
                validFrom != null && validUntil != null && validUntil.isAfter(validFrom),
                "配置 BOM 缺少有效期");
        require(
                orderVariants != null
                        && forecastMix != null
                        && !forecastMix.isEmpty()
                        && forecastMix.size() <= 64
                        && freeAssemblyStock != null
                        && bom != null
                        && bom.size() <= 256,
                "配置映射或 BOM 规模无效");
        BigDecimal sum = ZERO;
        for (var e : forecastMix.entrySet()) {
            key(e.getKey());
            quantity(e.getValue());
            sum = sum.add(e.getValue());
            require(bom.containsKey(e.getKey()), "预测配置缺少 BOM");
        }
        require(sum.compareTo(BigDecimal.ONE) == 0, "互斥配置的配比合计须为1");
        orderVariants.forEach(
                (line, variant) -> {
                    key(line);
                    require(bom.containsKey(variant), "订单配置缺少 BOM");
                });
        freeAssemblyStock.forEach(
                (id, stock) -> {
                    key(id);
                    quantity(stock);
                    require(bom.containsKey(id), "仅允许设置自制装配件可用库存");
                });
        var visited = new HashSet<String>();
        for (String item : bom.keySet()) validateTree(item, new HashSet<>(), visited);
    }

    private void validateTree(String item, Set<String> path, Set<String> visited) {
        key(item);
        if (!bom.containsKey(item) || visited.contains(item)) return;
        require(path.add(item) && path.size() <= 32, "BOM 有环或层级超过32");
        var children = bom.get(item);
        require(children != null && !children.isEmpty() && children.size() <= 128, "BOM 子件缺失或超限");
        var unique = new HashSet<String>();
        for (Part part : children) {
            require(part != null, "BOM 行为空");
            key(part.material());
            quantity(part.per());
            require(part.per().signum() > 0 && unique.add(part.material()), "BOM 子件重复或用量非正数");
            validateTree(part.material(), path, visited);
        }
        path.remove(item);
        visited.add(item);
    }

    List<PlanningInput.Requirement> requirements(DemandPlanner.Result parent, String target) {
        validate();
        require(!bom.containsKey(target), "目标须为外购叶件，自制中间件由配置 BOM 净算");
        var stock = new HashMap<>(freeAssemblyStock);
        List<PlanningInput.Requirement> output = new ArrayList<>();
        int[] visits = {0};
        for (var point : parent.projection()) {
            // At each instant allocate firm demand before the uncovered family forecast.
            var flows =
                    point.flows().stream()
                            .filter(f -> f.kind().equals("SALE") || f.kind().equals("FORECAST"))
                            .sorted(
                                    Comparator.comparing(
                                                    (DemandPlanner.Flow f) ->
                                                            !f.kind().equals("SALE"))
                                            .thenComparing(DemandPlanner.Flow::line))
                            .toList();
            for (var f : flows) {
                require(
                        !point.at().isBefore(validFrom) && point.at().isBefore(validUntil),
                        "需求时点超出已批准 BOM 有效期");
                Map<String, BigDecimal> mix;
                if (f.kind().equals("SALE")) {
                    String variant = orderVariants.get(f.line());
                    require(variant != null, "销售行缺少确定配置：" + f.line());
                    mix = Map.of(variant, BigDecimal.ONE);
                } else mix = new TreeMap<>(forecastMix);
                BigDecimal total = ZERO;
                for (var variant : mix.entrySet()) {
                    BigDecimal weight = f.change().negate().multiply(variant.getValue());
                    total = total.add(explode(variant.getKey(), weight, target, stock, visits));
                }
                if (total.signum() > 0)
                    output.add(
                            new PlanningInput.Requirement(
                                    parentRun + "/" + version + "/" + f.kind() + "/" + f.line(),
                                    point.at(),
                                    total));
            }
        }
        return output;
    }

    private BigDecimal explode(
            String variant,
            BigDecimal qty,
            String target,
            Map<String, BigDecimal> stock,
            int[] visits) {
        var queue = new ArrayDeque<Need>();
        queue.push(new Need(variant, qty));
        BigDecimal total = ZERO;
        while (!queue.isEmpty()) {
            require(++visits[0] <= 1_000_000, "配置展开超出单序列上限");
            var need = queue.pop();
            if (need.material().equals(target)) {
                total = total.add(need.quantity());
                continue;
            }
            var parts = bom.get(need.material());
            if (parts == null) continue;
            BigDecimal available = stock.getOrDefault(need.material(), ZERO);
            BigDecimal covered = available.min(need.quantity());
            stock.put(need.material(), available.subtract(covered));
            BigDecimal shortage = need.quantity().subtract(covered);
            if (shortage.signum() > 0)
                for (Part part : parts)
                    queue.push(
                            new Need(
                                    part.material(),
                                    shortage.multiply(part.per())
                                            .setScale(6, RoundingMode.HALF_UP)));
        }
        return total;
    }
}
