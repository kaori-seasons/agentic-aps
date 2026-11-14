package cn.windwheel.aps.solve;

import cn.windwheel.aps.entity.SolveAuditPO;
import cn.windwheel.aps.mapper.SolveAuditMapper;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.utils.TenantContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SolveAuditService {
    private final SolveAuditMapper mapper;
    private final ObjectMapper objectMapper;
    private final cn.windwheel.aps.solve.presolve.SmallModelService smallModelService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void start(UUID planId, PlanningStrategy strategy, BasicData data) {
        String snapshot = json(Map.of("strategy",strategy,"basicData",data));
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(snapshot.getBytes(StandardCharsets.UTF_8)));
            mapper.insert(new SolveAuditPO().setId(planId.toString()).setTenantId(TenantContextHolder.getTenantId())
                    .setStatus("RUNNING").setInputHash(hash).setInputJson(snapshot).setCreatedAt(LocalDateTime.now()));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void finish(UUID planId, String status, Object report) {
        mapper.update(new SolveAuditPO().setStatus(status).setResultJson(json(report)).setFinishedAt(LocalDateTime.now()),
                Wrappers.<SolveAuditPO>lambdaUpdate().eq(SolveAuditPO::getId,planId.toString())
                        .eq(SolveAuditPO::getTenantId,TenantContextHolder.getTenantId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordProblem(UUID planId, SchedulingSolution solution) {
        SolveAuditPO audit = get(planId.toString());
        if (audit == null) { throw new BusinessException("求解审计不存在"); }
        try {
            var input = (com.fasterxml.jackson.databind.node.ObjectNode)objectMapper.readTree(audit.getInputJson());
            input.set("problem",SolveProblemSnapshot.mapper(objectMapper).valueToTree(SolveProblemSnapshot.from(solution)));
            String json = objectMapper.writeValueAsString(input);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
            mapper.update(new SolveAuditPO().setInputJson(json).setInputHash(hash),
                    Wrappers.<SolveAuditPO>lambdaUpdate().eq(SolveAuditPO::getId,planId.toString())
                            .eq(SolveAuditPO::getTenantId,TenantContextHolder.getTenantId()));
        } catch (Exception exception) {
            throw new BusinessException("求解问题快照保存失败");
        }
    }

    public SchedulingSolution restoreProblem(String planId) {
        SolveAuditPO audit = get(planId);
        if (audit == null) { throw new BusinessException("当前租户没有该求解审计"); }
        try {
            return SolveProblemSnapshot.mapper(objectMapper)
                    .treeToValue(objectMapper.readTree(audit.getInputJson()).path("problem"),SolveProblemSnapshot.class).restore();
        } catch (Exception exception) {
            throw new BusinessException("求解问题快照无法重建");
        }
    }

    public SolveAuditPO get(String planId) {
        return mapper.selectOne(Wrappers.<SolveAuditPO>lambdaQuery().eq(SolveAuditPO::getId,planId)
                .eq(SolveAuditPO::getTenantId,TenantContextHolder.getTenantId()));
    }

    public String signature(BasicData data) {
        Map<String,Object> source = new LinkedHashMap<>();
        source.put("materials",data.getMaterialMap());
        source.put("bom",data.getBomMap());
        source.put("routes",data.getProcessRouteMap());
        source.put("calendars",data.getWorkCalendarMap());
        source.put("resources",data.getProductionResources());
        source.put("orders",data.getProductionOrders());
        source.put("incoming",data.getIncomingMaterialOrderMap());
        source.put("baselineTasks",data.getBaselineTasks());
        source.put("pinnedTasks",data.getPinnedTasks());
        try {
            String canonical = normalize(objectMapper.valueToTree(source)).toString();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException("无法生成基础数据版本");
        }
    }

    private JsonNode normalize(JsonNode value) {
        if (value.isObject()) {
            var normalized = objectMapper.createObjectNode();
            var names = new ArrayList<String>();
            value.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            names.forEach(name -> normalized.set(name,normalize(value.get(name))));
            return normalized;
        }
        if (value.isArray()) {
            var values = new ArrayList<JsonNode>();
            value.forEach(item -> values.add(normalize(item)));
            values.sort(Comparator.comparing(JsonNode::toString));
            var normalized = objectMapper.createArrayNode();
            values.forEach(normalized::add);
            return normalized;
        }
        return value;
    }

    public SolveAuditPO latestFeasible() {
        return mapper.selectOne(Wrappers.<SolveAuditPO>lambdaQuery()
                .eq(SolveAuditPO::getTenantId,TenantContextHolder.getTenantId())
                .eq(SolveAuditPO::getStatus,"FEASIBLE").orderByDesc(SolveAuditPO::getCreatedAt).last("LIMIT 1"));
    }

    public void validateBeforeConfirm(BasicData current) {
        SolveAuditPO audit = latestFeasible();
        if (audit == null) { return; } // 兼容升级前已经生成的待确认计划。
        try {
            String expected = objectMapper.readTree(audit.getInputJson()).path("basicData").path("sourceSignature").asText();
            if (!expected.equals(signature(current))) {
                throw new BusinessException("订单、物料、日历或报工数据已变化，请放弃当前候选并重新求解");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new BusinessException("求解快照无法校验，禁止确认");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void markReviewed(String status) {
        SolveAuditPO audit = latestFeasible();
        if (audit != null) {
            mapper.update(new SolveAuditPO().setStatus(status),
                    Wrappers.<SolveAuditPO>lambdaUpdate().eq(SolveAuditPO::getId,audit.getId())
                            .eq(SolveAuditPO::getTenantId,TenantContextHolder.getTenantId()));
        }
    }

    public Map<String,Object> report(SchedulingSolution result, BasicData data) {
        Map<String,cn.windwheel.aps.entity.PlanTaskAdjustPO> baseline = new HashMap<>();
        data.getBaselineTasks().forEach(task -> baseline.put(task.getCode(),task));
        List<Map<String,Object>> tasks = new ArrayList<>();
        int changed = 0;
        for (ProductionTask task : result.getTasks()) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("taskCode",task.getCode());
            row.put("resourceId",task.getResource() == null ? null : task.getResource().getId());
            row.put("start",task.getStartTime());
            row.put("end",task.getEndTime());
            row.put("pinned",task.getPinned());
            var old = baseline.get(task.getCode());
            boolean moved = old != null && (!Objects.equals(old.getMainResourceId(),row.get("resourceId"))
                    || !Objects.equals(old.getStartTime(),task.getStartTime()) || !Objects.equals(old.getEndTime(),task.getEndTime()));
            row.put("changed",moved);
            if (moved) { changed++; }
            tasks.add(row);
        }
        Map<String,Object> report = new LinkedHashMap<>();
        report.put("score",String.valueOf(result.getScore()));
        report.put("solverEngine",result.getStrategy().getConfig().getSolverEngine());
        report.put("config",result.getStrategy().getConfig());
        report.put("tasks",tasks);
        report.put("changedTaskCount",changed);
        report.put("summary","解释性建议：硬约束校验已通过，共" + tasks.size() + "项任务，已有任务中" + changed + "项位置或时间发生变化。请在甘特图核对后确认。");
        report.put("modelExplanation",smallModelService.explain(report));
        return report;
    }

    private String json(Object value) {
        try {
            return objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true)
                    .writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException("求解审计快照序列化失败");
        }
    }
}
