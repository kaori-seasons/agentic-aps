package cn.windwheel.aps.shortage;

import cn.windwheel.aps.solve.model.ProductionTask;
import cn.windwheel.aps.solve.model.SchedulingSolution;
import cn.windwheel.aps.solve.util.InventoryUtils.LineUse;
import cn.windwheel.aps.solve.util.MaterialShortageCollector.Gap;
import cn.windwheel.aps.shortage.ShortageModel.AlertState;
import cn.windwheel.aps.shortage.ShortageModel.Dimension;
import cn.windwheel.aps.shortage.ShortageModel.ItemState;
import cn.windwheel.aps.shortage.ShortageModel.Receipt;
import cn.windwheel.aps.shortage.ShortageModel.Source;
import cn.windwheel.aps.shortage.ShortageModel.SupplyMode;
import cn.windwheel.aps.demand.DemandPlanner;
import cn.windwheel.common.utils.TenantContextHolder;
import cn.windwheel.aps.entity.enums.PlanningDirectionEnum;
import cn.windwheel.aps.shortage.ShortageRule.Effective;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 欠料清单的两个入口。
 * <p>
 * 求解侧：MRP 展开完成即落库，不等求解结束。求解可能跑几十秒，缺料信息不必等；
 * CP-SAT 更是只要有外购件缺口就拒绝求解，这时欠料清单是唯一能解释失败的东西。
 * 需求侧：补货运行提交后把 Finding 映射成条目。
 * <p>
 * 两条入口都不新算一遍 MRP 或补货，只是把现有计算的过程值收集落库，算法不变。
 * 任何一步失败都只记日志，绝不反过来打断求解或补货。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortageItemService {
    private static final String MRP_CATEGORY = "MRP_GAP";

    private final ShortageItemStore items;
    private final ShortageAlertStore alerts;
    private final ShortageNotifyStore notifies;
    private final ShortageAlertService alertService;
    private final ShortageRuleEngine engine;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final ShortageProperties props;
    private final PlatformTransactionManager transactions;

    /**
     * 求解侧落库。
     *
     * @param complete false 表示 MRP 循环中途异常退出，只记已观测到的缺口，不关闭上一轮未被观测到的条目
     */
    public void recordSolveGaps(UUID runId, List<Gap> gaps, boolean complete, LocalDateTime dataCutoff, String sortRule) {
        String tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant.isBlank()) {
            log.warn("求解线程缺少租户上下文，欠料清单未落库 planId={}", runId);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<String> keys = new ArrayList<>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Map<String, Proposal> proposals = proposals(tenant, gaps);
                Map<ShortageRule, Effective> rules = alertService.rules(tenant);
                for (Gap gap : gaps) {
                    ShortageItem item = mrpItem(tenant, runId.toString(), gap, dataCutoff, now, sortRule, proposals);
                    keys.add(item.getShortageKey());
                    ShortageRuleEngine.Evaluation evaluation = engine.evaluate(item, gap.getLeadTime(),
                            gap.getBufferTime(), rules::get, now);
                    item.setSeverity(evaluation.severity()).setReasonCode(evaluation.reasonCode());
                    items.upsert(item);
                }
                if (complete) {
                    List<String> gone = items.resolveUnobserved(tenant, Dimension.ORDER, runId.toString(), null, null, now);
                    closeAlerts(tenant, gone, now, "求解 " + runId + " 未再观测到缺口");
                }
            });
        } catch (Exception e) {
            log.error("欠料清单落库失败，不影响本次求解 planId={}", runId, e);
            return;
        }
        if (!keys.isEmpty()) {
            alertService.evaluate(tenant, keys, now);
        }
    }

    /**
     * 求解结束后回填排程关联：受影响任务的实际计划时间、最早可开工与推迟时长。
     * 这一步只补信息，等级不变。
     */
    public void attachSchedule(UUID runId, SchedulingSolution solution) {
        String tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant.isBlank()) {
            return;
        }
        try {
            List<ShortageItem> recorded = items.findByRun(tenant, runId.toString());
            // 同一物料可能在多个订单里缺，必须按订单加物料定位，不能只按物料。
            Map<String, String> byOrderMaterial = new HashMap<>();
            for (ShortageItem item : recorded) {
                byOrderMaterial.putIfAbsent(item.getMaterialId() + "\u0000" + tree(item.getDetail()).path("orderId").asText(),
                        item.getShortageKey());
            }
            Map<String, List<ProductionTask>> affected = new LinkedHashMap<>();
            for (ProductionTask task : solution.getTasks()) {
                if (task.getMaterialDelayMap() == null || task.getStartTime() == null || task.getMainOrderId() == null) {
                    continue;
                }
                for (String materialId : task.getMaterialDelayMap().keySet()) {
                    String key = byOrderMaterial.get(materialId + "\u0000" + task.getMainOrderId());
                    if (key != null) {
                        affected.computeIfAbsent(key, k -> new ArrayList<>()).add(task);
                    }
                }
            }
            if (affected.isEmpty()) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            boolean forward = solution.getStrategy().getConfig().getDirection() != PlanningDirectionEnum.BACKWARD;
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                for (Map.Entry<String, List<ProductionTask>> entry : affected.entrySet()) {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    LocalDateTime earliest = null;
                    for (ProductionTask task : entry.getValue()) {
                        if (task.getStartTime() == null) {
                            continue;
                        }
                        earliest = earliest == null || task.getStartTime().isBefore(earliest)
                                ? task.getStartTime() : earliest;
                        rows.add(taskRow(task));
                    }
                    if (earliest == null) {
                        continue;
                    }
                    // 正排的推迟是相对计划起点；倒排从交期往回排，“推迟”没有可比的基准，不硬给。
                    Duration delay = null;
                    if (forward && solution.getPlanTime() != null) {
                        delay = Duration.ofMinutes(Math.max(0, Duration.between(solution.getPlanTime(), earliest).toMinutes()));
                    }
                    items.attachSchedule(tenant, entry.getKey(), earliest, delay == null ? null : delay.toString(),
                            write(rows), now);
                }
            });
        } catch (Exception e) {
            log.error("欠料清单回填排程失败 planId={}", runId, e);
        }
    }

    /**
     * 需求侧落库，在补货运行的事务提交之后、以独立事务执行。
     *
     * @return 本次观测到的条目键
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public List<String> recordDemandRun(String tenant, String runId, String location, String material, String unit,
                                        DemandPlanner.Result result, Instant asOf, String zoneId) {
        LocalDateTime now = LocalDateTime.now();
        ZoneId zone = ZoneId.of(zoneId);
        String name = materialName(tenant, material);
        List<String> keys = new ArrayList<>();
        Map<ShortageRule, Effective> rules = alertService.rules(tenant);
        for (DemandPlanner.Finding finding : result.exceptions()) {
            ShortageRule rule = ShortageRuleEngine.demandRule(finding.reason());
            if (rule == null) {
                continue;
            }
            ShortageItem item = demandItem(tenant, runId, location, material, name, unit, finding, rule, result, asOf,
                    zone, now);
            ShortageRuleEngine.Evaluation evaluation = engine.evaluate(item, Duration.ZERO, Duration.ZERO,
                    rules::get, now);
            item.setSeverity(evaluation.severity()).setReasonCode(evaluation.reasonCode());
            items.upsert(item);
            keys.add(item.getShortageKey());
        }
        // 同一地点物料是一个完整的计算范围：本轮没再出现的问题即已消失。
        List<String> gone = items.resolveUnobserved(tenant, Dimension.WEEK, runId, location, material, now);
        closeAlerts(tenant, gone, now, "补货运行 " + runId + " 未再观测到");
        return keys;
    }

    public void evaluate(String tenant, List<String> keys, LocalDateTime now) {
        if (!keys.isEmpty()) {
            alertService.evaluate(tenant, keys, now);
        }
    }

    private void closeAlerts(String tenant, List<String> keys, LocalDateTime now, String note) {
        if (keys.isEmpty()) {
            return;
        }
        List<String> ids = alerts.nonTerminalForItems(tenant, keys).stream()
                .map(ShortageAlert::getAlertId).toList();
        alertService.retire(tenant, ids, AlertState.RESOLVED, ShortageModel.SYSTEM_ACTOR, now, note);
        notifies.receipt(tenant, ids, Receipt.RESOLVED, note, ShortageModel.SYSTEM_ACTOR, now);
    }

    private ShortageItem mrpItem(String tenant, String runId, Gap gap, LocalDateTime dataCutoff, LocalDateTime now,
                                 String sortRule, Map<String, Proposal> proposals) {
        BigDecimal skipped = gap.getInTransitSkipped();
        SupplyMode mode;
        LocalDateTime eta;
        String supplier = null;
        if (!gap.getAllocatedLines().isEmpty()) {
            mode = SupplyMode.IN_TRANSIT;
            eta = gap.getAllocatedLines().stream().map(LineUse::availableAt).filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo).orElse(null);
        } else if (!gap.getSkippedLines().isEmpty()) {
            // 有在途但全部到得太晚：这仍是已知的最早到货，不能报成“无到货计划”。
            mode = SupplyMode.IN_TRANSIT_LATE;
            eta = gap.getSkippedLines().stream().map(LineUse::availableAt).filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo).orElse(null);
        } else {
            Proposal proposal = proposals.get(gap.getMaterialId()) != null ? proposals.get(gap.getMaterialId())
                    : proposals.get(gap.getMaterialCode());
            if (proposal == null) {
                mode = SupplyMode.NONE;
                eta = null;
            } else {
                mode = SupplyMode.PURCHASE_PROPOSAL;
                eta = proposal.availableAt();
                supplier = proposal.supplier();
            }
        }
        BigDecimal reservedEarlier = gap.getOnHandStart() == null || gap.getOnHandAvailable() == null ? null
                : gap.getOnHandStart().subtract(gap.getOnHandAvailable()).max(BigDecimal.ZERO);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("orderId", gap.getOrderId());
        detail.put("orderSequence", gap.getSequence());
        detail.put("occurrences", gap.getOccurrences());
        detail.put("onHandStart", ShortageText.decimal(gap.getOnHandStart()));
        detail.put("onHandReservedEarlier", ShortageText.decimal(reservedEarlier));
        detail.put("leadTime", ShortageText.iso(gap.getLeadTime()));
        detail.put("bufferTime", ShortageText.iso(gap.getBufferTime()));
        detail.put("sortRule", sortRule);
        return new ShortageItem()
                .setTenantId(tenant)
                .setShortageKey(orderKey(gap.getOrderCode(), gap.getMaterialCode()))
                .setDimension(Dimension.ORDER)
                .setSource(Source.MRP)
                .setRunId(runId)
                .setDetectedAt(now)
                .setFirstDetectedAt(now)
                .setDataCutoff(dataCutoff)
                // 落库时先给一个观测类别，复评会换成命中的规则码。
                .setReasonCode(MRP_CATEGORY)
                .setMaterialId(gap.getMaterialId())
                .setMaterialCode(gap.getMaterialCode())
                .setMaterialName(gap.getMaterialName())
                .setUnit(gap.getUnit())
                .setGapQuantity(scale(gap.getGapQuantity()))
                .setRequiredQuantity(scale(gap.getDemandQuantity()))
                .setRequiredAt(gap.getDeliveryTime())
                .setOrderCode(gap.getOrderCode())
                .setOnHandAvailable(gap.getOnHandAvailable())
                .setOnHandConsumed(scale(gap.getOnHandConsumed()))
                .setInTransitConsumed(scale(gap.getInTransitConsumed()))
                .setInTransitSkipped(scale(skipped))
                .setSkippedLines(write(lines(gap.getSkippedLines())))
                .setAvailableInTransit(write(lines(gap.getAllocatedLines())))
                .setSupplyMode(mode)
                .setExpectedEta(eta)
                .setSupplierCode(supplier)
                .setDetail(write(detail))
                .setState(ItemState.OPEN)
                .setObservedCount(1)
                .setLastEvaluatedAt(now)
                .setCreatedAt(now)
                .setUpdatedAt(now);
    }

    private ShortageItem demandItem(String tenant, String runId, String location, String material, String name,
                                    String unit, DemandPlanner.Finding finding, ShortageRule rule,
                                    DemandPlanner.Result result, Instant asOf, ZoneId zone, LocalDateTime now) {
        boolean weekly = rule == ShortageRule.DEMAND_SHORTAGE || rule == ShortageRule.DEMAND_BELOW_SAFETY
                || rule == ShortageRule.DEMAND_PROPOSAL_BLOCKED;
        Instant at = finding.at() == null ? asOf : finding.at();
        LocalDate week = week(at, zone);
        String reference = finding.reference() == null ? "-" : finding.reference();
        String key = naturalKey("WEEK", location, material, rule.name(), reference, weekly ? week.toString() : null);
        BigDecimal scenario = result.projection().stream().filter(p -> Objects.equals(p.at(), finding.at()))
                .map(DemandPlanner.Point::scenario).findFirst().orElse(null);
        return new ShortageItem()
                .setTenantId(tenant)
                .setShortageKey(key)
                .setDimension(Dimension.WEEK)
                .setSource(Source.DEMAND)
                .setRunId(runId)
                .setDetectedAt(now)
                .setFirstDetectedAt(now)
                .setDataCutoff(ShortageText.local(asOf, zone))
                .setReasonCode(rule.name())
                .setMaterialCode(material)
                .setMaterialName(name)
                .setUnit(unit)
                .setGapQuantity(scale(finding.quantity()))
                .setRequiredAt(ShortageText.local(finding.at(), zone))
                .setWeekStart(week)
                .setLocationCode(location)
                .setReference(reference)
                .setSourceReason(finding.reason())
                .setOnHandAvailable(BigDecimal.ZERO)
                .setSafetyStock(result.policy() == null ? null : result.policy().safety())
                .setScenarioBalance(scenario)
                .setState(ItemState.OPEN)
                .setObservedCount(1)
                .setLastEvaluatedAt(now)
                .setCreatedAt(now)
                .setUpdatedAt(now);
    }

    static String orderKey(String orderCode, String materialCode) {
        return naturalKey("ORDER", orderCode == null ? "" : orderCode, materialCode == null ? "" : materialCode,
                null, null, null);
    }

    /**
     * 业务自然键：冒号拼接。超过列宽时改用内容摘要收尾，键的唯一性不受影响。
     */
    static String naturalKey(String... parts) {
        StringBuilder key = new StringBuilder();
        for (String part : parts) {
            if (part != null) {
                if (key.length() > 0) {
                    key.append(':');
                }
                key.append(part);
            }
        }
        if (key.length() <= 400) {
            return key.toString();
        }
        return key.substring(0, 350) + "#" + cn.hutool.crypto.digest.DigestUtil.sha256Hex(key.toString()).substring(0, 45);
    }

    private static LocalDate week(Instant at, ZoneId zone) {
        return at.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private record Proposal(LocalDateTime availableAt, String supplier) {}

    /**
     * 物料地点上仍有效的采购建议。需求模块的物料键由接入方约定，可能等于内部 ID 也可能等于编码，两个都查。
     * 表不存在或查询失败都按没有建议处理，不能让补货模块的表结构问题打断求解。
     */
    private Map<String, Proposal> proposals(String tenant, List<Gap> gaps) {
        List<String> keys = gaps.stream().flatMap(g -> java.util.stream.Stream.of(g.getMaterialId(), g.getMaterialCode()))
                .filter(Objects::nonNull).distinct().toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        List<Object> args = new ArrayList<>();
        args.add(tenant);
        args.addAll(keys);
        Map<String, Proposal> result = new HashMap<>();
        try {
            db.query("select material_id,min(available_at),min(supplier) from jvs_demand_purchase where tenant_id=?"
                            + " and state in ('READY','ACKNOWLEDGED') and material_id in ("
                            + ShortageItemStore.placeholders(keys.size()) + ") group by material_id",
                    r -> {
                        try {
                            result.put(r.getString(1), new Proposal(
                                    ShortageText.local(Instant.parse(r.getString(2)), props.zone()), r.getString(3)));
                        } catch (Exception ignored) {
                            // 时间格式不合 ISO 的建议行不作为到货依据。
                        }
                    }, args.toArray());
        } catch (DataAccessException e) {
            log.warn("读取采购建议失败，欠料清单按无采购建议处理：{}", e.getMostSpecificCause().getMessage());
        }
        return result;
    }

    private String materialName(String tenant, String material) {
        try {
            List<String> names = db.queryForList("select name from jvs_material where tenant_id=? and del_flag=0"
                    + " and (id=? or code=?) limit 1", String.class, tenant, material, material);
            return names.isEmpty() ? null : names.get(0);
        } catch (DataAccessException e) {
            return null;
        }
    }

    private List<Map<String, Object>> lines(List<LineUse> lines) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LineUse line : lines) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("lineCode", line.lineCode());
            row.put("quantity", ShortageText.decimal(line.quantity()));
            row.put("usedQuantity", ShortageText.decimal(line.usedQuantity()));
            row.put("deliveryTime", line.deliveryTime() == null ? null : line.deliveryTime().toString());
            row.put("availableAt", line.availableAt() == null ? null : line.availableAt().toString());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> taskRow(ProductionTask task) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskCode", task.getCode());
        row.put("orderCode", task.getOrder() == null ? null : task.getOrder().getCode());
        row.put("processCode", task.getProcess() == null ? null : task.getProcess().getCode());
        // 求解模型里的主资源只有内部 ID 与名称，没有业务编码。
        row.put("resourceId", task.getResource() == null ? null : task.getResource().getId());
        row.put("resourceName", task.getResource() == null ? null : task.getResource().getName());
        row.put("plannedStartTime", task.getStartTime() == null ? null : task.getStartTime().toString());
        row.put("plannedEndTime", task.getEndTime() == null ? null : task.getEndTime().toString());
        return row;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
    }

    private JsonNode tree(String text) {
        try {
            return text == null || text.isBlank() ? json.createObjectNode() : json.readTree(text);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("欠料明细无法序列化", e);
        }
    }
}
