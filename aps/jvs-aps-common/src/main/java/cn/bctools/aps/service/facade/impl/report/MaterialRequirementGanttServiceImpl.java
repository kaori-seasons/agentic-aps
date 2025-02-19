package cn.bctools.aps.service.facade.impl.report;

import cn.bctools.aps.annotation.Report;
import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.dto.schedule.report.PlanMaterialRequirementGanttDTO;
import cn.bctools.aps.entity.MaterialPO;
import cn.bctools.aps.entity.enums.ReportTypeEnum;
import cn.bctools.aps.service.MaterialService;
import cn.bctools.aps.service.facade.ReportFacadeService;
import cn.bctools.aps.util.BigDecimalUtils;
import cn.bctools.aps.vo.schedule.report.GanttMaterialRequirementVO;
import cn.bctools.aps.vo.schedule.report.GanttMaterialVO;
import cn.bctools.aps.vo.schedule.report.PlanMaterialRequirementGanttVO;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 物料需求甘特图
 * <p>
 * 根据任务安排，展示每日所需物料
 */

@Report(type = ReportTypeEnum.PLAN_MATERIAL_REQUIREMENT_GANTT)
@Service
@AllArgsConstructor
public class MaterialRequirementGanttServiceImpl extends AbstractPlanReport implements ReportFacadeService<PlanMaterialRequirementGanttVO, PlanMaterialRequirementGanttDTO> {

    private final MaterialService materialService;

    /**
     * 甘特图填充天数
     */
    private static final Integer FILL_DAY = 120;

    @Override
    public PlanMaterialRequirementGanttVO getPreviewReport() {
        List<TaskDTO> tasks = listPreviewReportTask();
        PlanMaterialRequirementGanttDTO query = new PlanMaterialRequirementGanttDTO();
        query.setDateRange(parseReportDateRange(tasks, FILL_DAY));
        return generateReport(query, tasks);
    }

    @Override
    public PlanMaterialRequirementGanttVO getReport(PlanMaterialRequirementGanttDTO query) {
        List<TaskDTO> tasks = listReportTask(query, FILL_DAY);
        return generateReport(query, tasks);
    }

    /**
     * 生成报告
     *
     * @param query 查询条件
     * @param tasks 任务集合
     * @return 任务报告集合
     */
    private PlanMaterialRequirementGanttVO generateReport(PlanMaterialRequirementGanttDTO query, List<TaskDTO> tasks) {
        // 物料信息
        List<MaterialPO> materials = materialService.list(Wrappers.<MaterialPO>lambdaQuery()
                .select(MaterialPO::getId, MaterialPO::getCode, MaterialPO::getName)
                .eq(ObjectNull.isNotNull(query.getId()), MaterialPO::getId, query.getId())
                .orderByAsc(MaterialPO::getCreateTime));
        if (ObjectNull.isNull(materials)) {
            return new PlanMaterialRequirementGanttVO();
        }
        List<GanttMaterialVO> ganttMaterials = BeanCopyUtil.copys(materials, GanttMaterialVO.class);

        // 物料需求
        List<GanttMaterialRequirementVO> ganttMaterialRequirements = null;
        if (ObjectNull.isNotNull(tasks)) {
            // 任务输入物料汇总
            Map<String, List<PlanTaskMaterial>> taskMaterialMap = tasks.stream()
                    .filter(task -> !task.getMergeTask())
                    .filter(task -> ObjectNull.isNotNull(task.getInputMaterials()))
                    .flatMap(task ->
                            task.getInputMaterials().stream()
                                    .map(input -> new PlanTaskMaterial()
                                            .setId(input.getId())
                                            .setQuantity(input.getQuantity())
                                            .setDate(task.getStartTime().toLocalDate())
                                    )
                    )
                    .collect(Collectors.groupingBy(PlanTaskMaterial::getId));

            // 计算物料需求
            ganttMaterialRequirements = taskMaterialMap.entrySet().stream()
                    .filter(e -> {
                        if (ObjectNull.isNotNull(query.getId())) {
                            return e.getKey().equals(query.getId());
                        }
                        return true;
                    })
                    .map(e -> {
                        Map<LocalDate, BigDecimal> dailyDemand = e.getValue().stream()
                                .collect(Collectors.toMap(PlanTaskMaterial::getDate, PlanTaskMaterial::getQuantity, BigDecimal::add, TreeMap::new));

                        // 当前物料总需求量
                        BigDecimal sum = dailyDemand.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                        BigDecimal totalQuantity = BigDecimalUtils.stripTrailingZeros(sum.setScale(6, RoundingMode.HALF_UP));
                        return new GanttMaterialRequirementVO()
                                .setId(e.getKey())
                                .setTotalQuantity(totalQuantity)
                                .setDailyDemand(dailyDemand);
                    })
                    .toList();
        }

        // 填充甘特
        PlanMaterialRequirementGanttVO vo = new PlanMaterialRequirementGanttVO()
                .setMaterials(ganttMaterials)
                .setRequirements(ganttMaterialRequirements);
        vo.setDateRange(query.getDateRange());
        return vo;
    }

    @Data
    @Accessors(chain = true)
    private static class PlanTaskMaterial {
        /**
         * 物料id
         */
        private String id;

        /**
         * 任务所需当前物料总数
         */
        private BigDecimal quantity;

        /**
         * 预计使用时间
         */
        private LocalDate date;
    }
}
