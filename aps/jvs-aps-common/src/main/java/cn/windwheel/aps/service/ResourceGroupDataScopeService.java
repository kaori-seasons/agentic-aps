package cn.windwheel.aps.service;

import cn.windwheel.aps.dto.schedule.TaskDTO;
import cn.windwheel.aps.entity.PlanTaskAdjustPO;
import cn.windwheel.aps.entity.PlanTaskPO;
import cn.windwheel.aps.entity.ProductionResourcePO;

import java.util.Collection;
import java.util.List;

/**
 * 资源组数据范围控制
 */
public interface ResourceGroupDataScopeService {

    boolean hasFullAccess();

    List<String> listAuthorizedResourceGroups();

    List<String> listAuthorizedResourceIds();

    void assertCanAccessResourceGroup(String resourceGroup);

    void assertCanAccessResourceId(String resourceId);

    void assertCanAccessResourceIds(Collection<String> resourceIds);

    void assertCanAccessResourceCodes(Collection<String> resourceCodes);

    void assertCanAccessTaskCodes(Collection<String> taskCodes);

    List<TaskDTO> filterTaskDTOs(Collection<TaskDTO> tasks);

    List<PlanTaskPO> filterPlanTasks(Collection<PlanTaskPO> tasks);

    List<PlanTaskAdjustPO> filterPlanTaskAdjusts(Collection<PlanTaskAdjustPO> tasks);

    List<ProductionResourcePO> filterResources(Collection<ProductionResourcePO> resources);
}
