package cn.windwheel.aps.digitaltwin;

import cn.windwheel.aps.entity.*;
import cn.windwheel.aps.mapper.SolveAuditMapper;
import cn.windwheel.aps.entity.dto.plan.PlanTaskInputMaterialDTO;
import cn.windwheel.aps.entity.enums.IncomingMaterialOrderStatusEnum;
import cn.windwheel.aps.entity.enums.OrderStatusEnum;
import cn.windwheel.aps.entity.enums.PlanTaskStatusEnum;
import cn.windwheel.aps.entity.enums.ResourceStatusEnum;
import cn.windwheel.aps.service.*;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.utils.TenantContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 数字孪生快照聚合：把当前租户的真实数据按孪生需要的形状一次拼好。
 * <p>
 * 孪生需要四样东西，各自来源：
 *   设备台账      ← jvs_production_resource
 *   订单          ← jvs_production_order（待排/已排，含补充订单）
 *   已确认计划    ← jvs_plan_task（现场正在执行的计划）
 *   候选计划      ← jvs_plan_task_pending（待确认）；没有待确认计划时退而取最近一次 FEASIBLE 求解审计，
 *                    并用 candidateSource 如实标注是哪一种
 *   来料计划      ← jvs_incoming_material_order（在途到货）
 * KPI 与决策收益在服务端算，孪生只负责呈现——数字口径与求解审计同源。
 */
@Service
@RequiredArgsConstructor
public class DigitalTwinSnapshotService {
    private final ProductionResourceService resourceService;
    private final ProductionOrderService orderService;
    private final IncomingMaterialOrderService incomingService;
    private final MaterialService materialService;
    private final PlanTaskService planTaskService;
    private final PlanTaskPendingService planTaskPendingService;
    private final SolveAuditMapper solveAuditMapper;
    private final ObjectMapper objectMapper;

    /** 工序消耗的物料：MRP 按工艺路线展开后写在任务上，是「到货 → 齐套 → 开工」联动的依据。 */
    public record Input(String materialCode, BigDecimal quantity) {}

    /** 任务的最小表示：一张订单的一道工序落在哪台设备、什么时间窗、消耗哪些物料。两版计划共用。 */
    public record Task(String id, String orderCode, String materialCode, String processCode, String processName,
                       String resourceId, String resourceName, BigDecimal quantity,
                       LocalDateTime start, LocalDateTime end, boolean frozen, String status, List<Input> inputs) {}

    /** 物料库存台账：只带来料或工序消耗涉及的物料。 */
    public record Material(String code, String name, String unit, BigDecimal stock, BigDecimal safetyStock, String leadTime) {}

    public record Order(String code, String materialCode, String materialName, String unit, BigDecimal quantity,
                       LocalDateTime deliveryTime, Integer priority, String color, boolean supplement,
                       String parentOrderCode, String schedulingStatus) {}

    public record Resource(String id, String code, String name, String group, BigDecimal capacity, String unit,
                          String throughput, String status) {}

    public record Supply(String code, String materialCode, String materialName, String unit, BigDecimal quantity,
                        LocalDateTime eta, String status) {}

    public record Kpi(int orders, int unscheduled, long tardyMinutes, int setups, long makespanMinutes) {}

    /** 三类决策的量化收益：齐套门禁 / 换型归并 / 负载均衡。口径见 decisions()。 */
    public record Decisions(int gateBlocked, int gateBlockedAfter, int setupsBefore, int setupsAfter,
                            double balanceBefore, double balanceAfter, int balanceDeltaPercent,
                            List<Map<String, Object>> evidence) {}

    public record Snapshot(LocalDateTime generatedAt, boolean live, String message,
                           List<Resource> resources, List<Order> orders, List<Material> materials, List<Supply> supplies,
                           List<Task> current, List<Task> candidate, boolean hasCandidate,
                           String candidateSource, String candidatePlanId, LocalDateTime candidateAt,
                           Kpi kpiCurrent, Kpi kpiCandidate, Decisions decisions) {}

    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        String tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant.isBlank()) throw new BusinessException("缺少租户上下文");

        // ---- 设备 ----
        List<ProductionResourcePO> resources = resourceService.list();
        Map<String, ProductionResourcePO> resourceById = resources.stream()
                .collect(Collectors.toMap(ProductionResourcePO::getId, Function.identity(), (a, b) -> a));
        List<Resource> resourceList = resources.stream().map(r -> new Resource(r.getId(), r.getCode(), r.getName(),
                r.getResourceGroup(), r.getCapacity(), r.getUnit(), r.getThroughput(),
                r.getResourceStatus() == null ? null : r.getResourceStatus().name())).toList();

        // ---- 物料 / 订单 ----
        Map<String, MaterialPO> materials = materialService.list().stream()
                .collect(Collectors.toMap(MaterialPO::getCode, Function.identity(), (a, b) -> a));
        Map<String, MaterialPO> materialById = materials.values().stream()
                .collect(Collectors.toMap(MaterialPO::getId, Function.identity(), (a, b) -> a));
        List<ProductionOrderPO> orderPOs = orderService.list(Wrappers.<ProductionOrderPO>lambdaQuery()
                .ne(ProductionOrderPO::getOrderStatus, OrderStatusEnum.COMPLETED)
                .ne(ProductionOrderPO::getOrderStatus, OrderStatusEnum.CANCELLED));
        Map<String, ProductionOrderPO> orderById = orderPOs.stream()
                .collect(Collectors.toMap(ProductionOrderPO::getId, Function.identity(), (a, b) -> a));
        List<Order> orders = orderPOs.stream().map(o -> new Order(o.getCode(), o.getMaterialCode(),
                materialName(materials, o.getMaterialCode()), materialUnit(materials, o.getMaterialCode()), o.getQuantity(),
                o.getDeliveryTime(), o.getPriority(), o.getColor(), Boolean.TRUE.equals(o.getSupplement()),
                o.getParentOrderCode(), o.getSchedulingStatus() == null ? null : o.getSchedulingStatus().name()))
                .toList();

        // ---- 已确认计划：jvs_plan_task，排除已废弃与合并子任务 ----
        List<Task> current = planTaskService.list().stream()
                .filter(t -> t.getStartTime() != null && t.getEndTime() != null)
                .filter(t -> orderById.containsKey(t.getProductionOrderId()))
                .map(t -> toTask(t, orderById, resourceById, materialById)).toList();

        // ---- 候选计划：待确认表优先，其次取最近一次 FEASIBLE 审计 ----
        List<Task> candidate = List.of();
        String candidatePlanId = null;
        String candidateSource = null;
        LocalDateTime candidateAt = null;
        boolean hasCandidate = false;
        List<PlanTaskPendingPO> pending = planTaskPendingService.list();
        if (pending != null && !pending.isEmpty()) {
            candidate = pending.stream()
                    .filter(t -> t.getStartTime() != null && t.getEndTime() != null && orderById.containsKey(t.getProductionOrderId()))
                    .map(t -> toTask(pendingToTask(t), orderById, resourceById, materialById)).toList();
            hasCandidate = !candidate.isEmpty();
            candidateSource = "PENDING";
        }
        if (!hasCandidate) {
            SolveAuditPO audit = solveAuditMapper.selectOne(Wrappers.<SolveAuditPO>lambdaQuery()
                    .eq(SolveAuditPO::getTenantId, tenant).eq(SolveAuditPO::getStatus, "FEASIBLE")
                    .orderByDesc(SolveAuditPO::getCreatedAt).last("LIMIT 1"));
            if (audit != null && audit.getResultJson() != null) {
                candidate = tasksFromAudit(audit.getResultJson(), orderById, resourceById, materialById);
                hasCandidate = !candidate.isEmpty();
                candidatePlanId = audit.getId();
                candidateSource = "AUDIT";
                candidateAt = audit.getCreatedAt();
            }
        }

        // ---- 来料计划 ----
        List<Supply> supplies = incomingService.list(Wrappers.<IncomingMaterialOrderPO>lambdaQuery()
                        .eq(IncomingMaterialOrderPO::getOrderStatus, IncomingMaterialOrderStatusEnum.PROCESSING)).stream()
                .map(s -> new Supply(s.getCode(), s.getMaterialCode(), materialName(materials, s.getMaterialCode()),
                        materialUnit(materials, s.getMaterialCode()), s.getQuantity(), s.getDeliveryTime(),
                        s.getOrderStatus() == null ? null : s.getOrderStatus().name())).toList();

        // ---- 物料台账：只带来料或两版计划的工序消耗涉及的物料 ----
        Set<String> usedMaterials = new HashSet<>();
        supplies.forEach(x -> usedMaterials.add(x.materialCode()));
        Stream.concat(current.stream(), candidate.stream()).forEach(t -> t.inputs().forEach(in -> usedMaterials.add(in.materialCode())));
        List<Material> materialList = usedMaterials.stream().sorted().map(code -> {
            MaterialPO m = materials.get(code);
            return m == null ? new Material(code, null, null, null, null, null)
                    : new Material(code, m.getName(), m.getUnit(), m.getQuantity(), m.getSafetyStock(), m.getLeadTime());
        }).toList();

        // ---- KPI 与决策 ----
        Set<String> scheduledNow = current.stream().map(Task::orderCode).collect(Collectors.toSet());
        Set<String> scheduledCand = candidate.stream().map(Task::orderCode).collect(Collectors.toSet());
        // 待排池 = 可排、未完成、非补充订单；补充订单由主订单的 MRP 生成，不单独计
        List<ProductionOrderPO> pool = orderPOs.stream().filter(o -> !Boolean.TRUE.equals(o.getSupplement())).toList();
        // 排入/未排只按主订单计：补充订单由主订单的 MRP 生成，不是独立的交付承诺
        Set<String> rootScheduledNow = rootOrdersOf(current, orderById);
        Set<String> rootScheduledCand = hasCandidate ? rootOrdersOf(candidate, orderById) : rootScheduledNow;
        Kpi kc = kpi(current, pool, rootScheduledNow);
        Kpi kd = hasCandidate ? kpi(candidate, pool, rootScheduledCand) : kc;

        boolean live = !current.isEmpty() || hasCandidate;
        String message = !live ? "当前租户尚无已确认计划，也没有待确认的候选计划。先在「排产计划」生成一次排产，数字孪生才有内容可看。"
                : !hasCandidate ? "当前展示的是已确认计划。生成一次新的排产但暂不提交，即可在这里对比优化候选。" : null;
        return new Snapshot(LocalDateTime.now(), live, message, resourceList, orders, materialList, supplies, current, candidate,
                hasCandidate, candidateSource, candidatePlanId, candidateAt, kc, kd, decisions(pool, current, candidate, hasCandidate, scheduledNow, scheduledCand, resourceById, orderById, materials));
    }

    private Task toTask(PlanTaskPO t, Map<String, ProductionOrderPO> orderById, Map<String, ProductionResourcePO> resourceById,
                        Map<String, MaterialPO> materialById) {
        ProductionOrderPO order = orderById.get(t.getProductionOrderId());
        ProductionResourcePO res = resourceById.get(t.getMainResourceId());
        return new Task(t.getCode(), order == null ? null : order.getCode(), order == null ? null : order.getMaterialCode(),
                t.getProcessInfo() == null ? null : t.getProcessInfo().getCode(),
                t.getProcessInfo() == null ? null : t.getProcessInfo().getName(),
                t.getMainResourceId(), res == null ? null : res.getName(), t.getScheduledQuantity(),
                t.getStartTime(), t.getEndTime(), Boolean.TRUE.equals(t.getPinned()),
                t.getTaskStatus() == null ? null : t.getTaskStatus().name(), inputsOf(t.getInputMaterials(), materialById));
    }

    /** 任务消耗物料：存的是物料 id，这里折成编码，孪生按编码与来料、库存对账。 */
    private List<Input> inputsOf(List<PlanTaskInputMaterialDTO> inputs, Map<String, MaterialPO> materialById) {
        if (inputs == null) return List.of();
        List<Input> out = new ArrayList<>();
        for (PlanTaskInputMaterialDTO in : inputs) {
            MaterialPO m = in.getId() == null ? null : materialById.get(in.getId());
            if (m != null && in.getQuantity() != null) out.add(new Input(m.getCode(), in.getQuantity()));
        }
        return out;
    }

    /** 待确认任务与已确认任务字段同构，直接映射为 PlanTaskPO 复用一条转换。 */
    private PlanTaskPO pendingToTask(PlanTaskPendingPO p) {
        return new PlanTaskPO().setCode(p.getCode()).setProductionOrderId(p.getProductionOrderId())
                .setProcessInfo(p.getProcessInfo()).setMainResourceId(p.getMainResourceId())
                .setScheduledQuantity(p.getScheduledQuantity()).setStartTime(p.getStartTime()).setEndTime(p.getEndTime())
                .setPinned(p.getPinned()).setInputMaterials(p.getInputMaterials());
    }

    /** 审计里的任务行：{taskCode, resourceId, start, end, changed, pinned}，需回查订单与工序。 */
    private List<Task> tasksFromAudit(String resultJson, Map<String, ProductionOrderPO> orderById,
                                      Map<String, ProductionResourcePO> resourceById, Map<String, MaterialPO> materialById) {
        try {
            JsonNode tasks = objectMapper.readTree(resultJson).path("tasks");
            // 审计只存编码与时间；订单、工序、数量从任务编码 + 待确认/已确认表回查
            Map<String, PlanTaskPO> known = planTaskService.list().stream()
                    .collect(Collectors.toMap(PlanTaskPO::getCode, Function.identity(), (a, b) -> a));
            List<Task> list = new ArrayList<>();
            for (JsonNode row : tasks) {
                String code = row.path("taskCode").asText(null);
                PlanTaskPO base = known.get(code);
                ProductionOrderPO order = base == null ? null : orderById.get(base.getProductionOrderId());
                String resourceId = row.path("resourceId").asText(base == null ? null : base.getMainResourceId());
                ProductionResourcePO res = resourceById.get(resourceId);
                if (order == null) continue;
                list.add(new Task(code, order.getCode(), order.getMaterialCode(),
                        base == null ? null : base.getProcessCode(), base == null ? null : processName(base),
                        resourceId, res == null ? null : res.getName(), base == null ? null : base.getScheduledQuantity(),
                        parse(row.path("start").asText(null)), parse(row.path("end").asText(null)),
                        row.path("pinned").asBoolean(false), null, base == null ? List.of() : inputsOf(base.getInputMaterials(), materialById)));
            }
            return list;
        } catch (Exception e) {
            throw new BusinessException("求解审计结果无法解析");
        }
    }

    private String processName(PlanTaskPO base) {
        return base.getProcessInfo() == null ? null : base.getProcessInfo().getName();
    }

    private LocalDateTime parse(String text) {
        if (text == null) return null;
        try { return LocalDateTime.parse(text); } catch (Exception e) { return null; }
    }

    private String materialName(Map<String, MaterialPO> materials, String id) {
        MaterialPO m = materials.get(id);
        return m == null ? null : m.getName();
    }

    private String materialUnit(Map<String, MaterialPO> materials, String id) {
        MaterialPO m = materials.get(id);
        return m == null ? null : m.getUnit();
    }

    /** 与孪生 sim.kpi 同口径：排入订单数、未排订单数、逾期分钟、换型次数、完工跨度。 */
    Kpi kpi(List<Task> plan, List<ProductionOrderPO> pool, Set<String> scheduledOrderCodes) {
        Map<String, ProductionOrderPO> byCode = pool.stream().collect(Collectors.toMap(ProductionOrderPO::getCode, Function.identity(), (a, b) -> a));
        long tardy = 0;
        LocalDateTime maxEnd = null;
        for (Task t : plan) {
            ProductionOrderPO o = byCode.get(t.orderCode());
            if (o == null) continue;
            // 逾期只按主订单算：补充订单的交期继承自主订单，按它再算一遍会把同一张单的逾期放大若干倍
            if (Boolean.TRUE.equals(o.getSupplement())) continue;
            // 逾期：订单最后工序完成晚于交期（按订单聚合，避免多工序重复计）
            if (o != null && o.getDeliveryTime() != null && t.end() != null) {
                LocalDateTime due = o.getDeliveryTime();
                boolean lastOp = plan.stream().noneMatch(x -> x.orderCode().equals(t.orderCode()) && x.end() != null && x.end().isAfter(t.end()));
                if (lastOp) tardy += Math.max(0, DurationMinutes.between(due, t.end()));
            }
            if (t.end() != null && (maxEnd == null || t.end().isAfter(maxEnd))) maxEnd = t.end();
        }
        int setups = 0;
        Map<String, List<Task>> byRes = plan.stream().filter(t -> t.start() != null).collect(Collectors.groupingBy(Task::resourceId));
        for (List<Task> q : byRes.values()) {
            q.sort(Comparator.comparing(Task::start));
            for (int i = 1; i < q.size(); i++) {
                String a = q.get(i - 1).materialCode(), b = q.get(i).materialCode();
                if (a != null && b != null && !a.equals(b)) setups++;
            }
        }
        long makespan = 0;
        if (maxEnd != null) {
            LocalDateTime minStart = plan.stream().map(Task::start).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            if (minStart != null) makespan = DurationMinutes.between(minStart, maxEnd);
        }
        int unscheduled = (int) byCode.values().stream()
                .filter(o -> !scheduledOrderCodes.contains(o.getCode()))
                .filter(o -> !PlanTaskStatusEnum.COMPLETED.name().equals(o.getSchedulingStatus() == null ? null : o.getSchedulingStatus().name()))
                .count();
        return new Kpi(scheduledOrderCodes.size(), unscheduled, tardy, setups, makespan);
    }

    /** 分钟差工具，避免 java.time.Duration 与本类 Duration 命名混淆。 */
    private static final class DurationMinutes {
        static long between(LocalDateTime a, LocalDateTime b) { return java.time.Duration.between(a, b).toMinutes(); }
    }

    Decisions decisions(List<ProductionOrderPO> pool, List<Task> current, List<Task> candidate, boolean hasCandidate,
                        Set<String> scheduledNow, Set<String> scheduledCand, Map<String, ProductionResourcePO> resourceById,
                        Map<String, ProductionOrderPO> orderById, Map<String, MaterialPO> materials) {
        Set<String> poolCodes = pool.stream().map(ProductionOrderPO::getCode).collect(Collectors.toSet());
        // 未排/已排只按主订单计（补充订单归并到父）
        Set<String> rootNow = rootOrdersOf(current, orderById);
        Set<String> rootAfter = hasCandidate ? rootOrdersOf(candidate, orderById) : rootNow;
        List<String> blockedNow = poolCodes.stream().filter(c -> !rootNow.contains(c)).sorted().toList();
        List<String> blockedAfter = hasCandidate ? poolCodes.stream().filter(c -> !rootAfter.contains(c)).sorted().toList() : blockedNow;
        int setupsNow = kpiSetups(current), setupsAfter = hasCandidate ? kpiSetups(candidate) : setupsNow;
        double balNow = balance(current, resourceById), balAfter = hasCandidate ? balance(candidate, resourceById) : balNow;
        int deltaPercent = balNow == 0 ? 0 : (int) Math.round((balNow - balAfter) / balNow * 100);

        List<Map<String, Object>> evidence = new ArrayList<>();
        // 证据1：门禁——哪些订单未排入（真实缺口原因由缺料预警提供；此处给订单与交期）
        for (String code : blockedNow) {
            ProductionOrderPO o = orderById.values().stream().filter(x -> x.getCode().equals(code)).findFirst().orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("decision", "gate"); row.put("order", code);
            row.put("material", o == null ? null : o.getMaterialCode());
            row.put("delivery", o == null || o.getDeliveryTime() == null ? null : o.getDeliveryTime().toString());
            row.put("scheduledAfter", hasCandidate && rootAfter.contains(code));
            evidence.add(row);
        }
        // 证据2：换型——每台设备上相邻任务是否换系
        Map<String, List<Task>> curByRes = current.stream().filter(t -> t.start() != null).collect(Collectors.groupingBy(Task::resourceId));
        for (Map.Entry<String, List<Task>> e : curByRes.entrySet()) {
            List<Task> q = new ArrayList<>(e.getValue()); q.sort(Comparator.comparing(Task::start));
            long switches = 0; for (int i = 1; i < q.size(); i++) if (!Objects.equals(q.get(i - 1).materialCode(), q.get(i).materialCode())) switches++;
            if (switches == 0) continue;
            ProductionResourcePO r = resourceById.get(e.getKey());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("decision", "setup"); row.put("resourceId", e.getKey()); row.put("resource", r == null ? e.getKey() : r.getName());
            row.put("before", switches);
            row.put("after", hasCandidate ? switchesOn(candidate, e.getKey()) : switches);
            evidence.add(row);
        }
        // 证据3：负载——每台设备的占用分钟
        Map<String, Long> loadNow = loadMinutes(current), loadAfter = hasCandidate ? loadMinutes(candidate) : loadNow;
        for (String rid : resourceById.keySet()) {
            if (!loadNow.containsKey(rid) && !loadAfter.containsKey(rid)) continue;
            ProductionResourcePO r = resourceById.get(rid);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("decision", "balance"); row.put("resourceId", rid); row.put("resource", r == null ? rid : r.getName());
            row.put("before", loadNow.getOrDefault(rid, 0L)); row.put("after", loadAfter.getOrDefault(rid, 0L));
            evidence.add(row);
        }
        return new Decisions(blockedNow.size(), blockedAfter.size(), setupsNow, setupsAfter,
                round2(balNow), round2(balAfter), deltaPercent, evidence);
    }

    /** 任务所属订单归并到主订单：补充订单带 parentOrderCode，其父才是交付主体。 */
    private Set<String> rootOrdersOf(List<Task> plan, Map<String, ProductionOrderPO> orderById) {
        Set<String> roots = new HashSet<>();
        for (Task t : plan) {
            ProductionOrderPO o = orderById.values().stream().filter(x -> x.getCode().equals(t.orderCode())).findFirst().orElse(null);
            if (o == null) continue;
            roots.add(Boolean.TRUE.equals(o.getSupplement()) && o.getParentOrderCode() != null ? o.getParentOrderCode() : o.getCode());
        }
        return roots;
    }

    private int kpiSetups(List<Task> plan) {
        int setups = 0;
        Map<String, List<Task>> byRes = plan.stream().filter(t -> t.start() != null).collect(Collectors.groupingBy(Task::resourceId));
        for (List<Task> q : byRes.values()) {
            q.sort(Comparator.comparing(Task::start));
            for (int i = 1; i < q.size(); i++) if (!Objects.equals(q.get(i - 1).materialCode(), q.get(i).materialCode())) setups++;
        }
        return setups;
    }

    private long switchesOn(List<Task> plan, String resourceId) {
        List<Task> q = new ArrayList<>(plan.stream().filter(t -> resourceId.equals(t.resourceId()) && t.start() != null).toList());
        q.sort(Comparator.comparing(Task::start));
        long n = 0; for (int i = 1; i < q.size(); i++) if (!Objects.equals(q.get(i - 1).materialCode(), q.get(i).materialCode())) n++;
        return n;
    }

    private Map<String, Long> loadMinutes(List<Task> plan) {
        Map<String, Long> loads = new LinkedHashMap<>();
        for (Task t : plan) {
            if (t.start() == null || t.end() == null || t.resourceId() == null) continue;
            loads.merge(t.resourceId(), java.time.Duration.between(t.start(), t.end()).toMinutes(), Long::sum);
        }
        return loads;
    }

    /** 负载离散度：各设备占用分钟的样本标准差。越小越均衡。 */
    double balance(List<Task> plan, Map<String, ProductionResourcePO> resourceById) {
        List<Long> loads = new ArrayList<>();
        for (String id : resourceById.keySet()) loads.add(plan.stream()
                .filter(t -> id.equals(t.resourceId()) && t.start() != null && t.end() != null)
                .mapToLong(t -> java.time.Duration.between(t.start(), t.end()).toMinutes()).sum());
        if (loads.isEmpty()) return 0;
        double avg = loads.stream().mapToLong(Long::longValue).average().orElse(0);
        return Math.sqrt(loads.stream().mapToDouble(l -> (l - avg) * (l - avg)).average().orElse(0));
    }

    private double round2(double v) { return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue(); }
}
