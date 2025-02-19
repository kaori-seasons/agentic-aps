package cn.bctools.aps.service.facade.param;

import cn.bctools.aps.entity.PlanTaskOrderPO;
import cn.bctools.aps.entity.PlanTaskPO;
import cn.bctools.aps.entity.ProductionResourcePO;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author jvs
 * 生成任务的派工计划方法入参
 */
@Data
@Accessors(chain = true)
public class GenerateTaskAssignParam {

    /**
     * 批量产能
     */
    Boolean batchThroughput;

    /**
     * 待派工任务
     */
    PlanTaskPO task;

    /**
     * 派工计划开始时间
     */
    LocalDateTime planStartTime;

    /**
     * 派工计划结束时间
     */
    LocalDateTime planEndTime;

    /**
     * 派工计划数量
     */
    BigDecimal planQuantity;

    /**
     * 待派工任务所属资源
     */
    ProductionResourcePO resource;

    /**
     * 待派工任务所属资源下所有任务
     */
    List<PlanTaskPO> resourceTasks;

    /**
     * 所有订单 Map<订单id, 订单信息>
     */
    Map<String, PlanTaskOrderPO> planTaskOrderMap;
}
