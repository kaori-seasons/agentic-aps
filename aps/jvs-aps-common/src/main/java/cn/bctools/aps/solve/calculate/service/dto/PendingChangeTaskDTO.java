package cn.bctools.aps.solve.calculate.service.dto;

import cn.bctools.aps.solve.model.MainProductionResource;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author jvs
 * 待变更任务
 */
@Data
@Accessors(chain = true)
public class PendingChangeTaskDTO {
    // 变更任务所在目标资源的下标
    private int fromIndex;
    // 变更任务所在目标主资源
    private MainProductionResource resource;
}
