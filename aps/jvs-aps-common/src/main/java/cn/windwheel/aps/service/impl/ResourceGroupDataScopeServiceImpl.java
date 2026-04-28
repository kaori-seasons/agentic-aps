package cn.windwheel.aps.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.windwheel.aps.dto.schedule.TaskDTO;
import cn.windwheel.aps.entity.PlanTaskAdjustPO;
import cn.windwheel.aps.entity.PlanTaskPO;
import cn.windwheel.aps.entity.ProductionResourcePO;
import cn.windwheel.aps.mapper.ProductionResourceMapper;
import cn.windwheel.aps.service.PlanTaskAdjustService;
import cn.windwheel.aps.service.PlanTaskService;
import cn.windwheel.aps.service.ProductionResourceService;
import cn.windwheel.aps.service.ResourceGroupDataScopeService;
import cn.windwheel.common.auth.PermissionService;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.utils.AuthContextHolder;
import cn.windwheel.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基于资源组的真实数据范围控制
 */
@Service
@AllArgsConstructor
public class ResourceGroupDataScopeServiceImpl implements ResourceGroupDataScopeService {

    private final ProductionResourceMapper productionResourceMapper;
    private final PlanTaskService planTaskService;
    private final PlanTaskAdjustService planTaskAdjustService;
    private final PermissionService permissionService;

    @Override
    public boolean hasFullAccess() {
        return permissionService.hasRole("APS_ADMIN");
    }

    @Override
    public List<String> listAuthorizedResourceGroups() {
        if (hasFullAccess()) {
            return listAllResourceGroups();
        }
        return AuthContextHolder.getResourceGroupIds().stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
    }

    @Override
    public List<String> listAuthorizedResourceIds() {
        if (hasFullAccess()) {
            return listResources(null).stream()
                    .map(ProductionResourcePO::getId)
                    .toList();
        }
        List<String> resourceGroups = listAuthorizedResourceGroups();
        if (ObjectNull.isNull(resourceGroups)) {
            return Collections.emptyList();
        }
        return listResources(resourceGroups).stream()
                .map(ProductionResourcePO::getId)
                .toList();
    }

    @Override
    public void assertCanAccessResourceGroup(String resourceGroup) {
        if (hasFullAccess()) {
            return;
        }
        if (StrUtil.isBlank(resourceGroup) || !listAuthorizedResourceGroups().contains(resourceGroup)) {
            throw new BusinessException("无权访问该资源组", 403);
        }
    }

    @Override
    public void assertCanAccessResourceId(String resourceId) {
        if (hasFullAccess()) {
            return;
        }
        ProductionResourcePO resource = productionResourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException("资源不存在");
        }
        assertCanAccessResourceGroup(resource.getResourceGroup());
    }

    @Override
    public void assertCanAccessResourceIds(Collection<String> resourceIds) {
        if (hasFullAccess() || ObjectNull.isNull(resourceIds)) {
            return;
        }
        Map<String, ProductionResourcePO> resourceMap = productionResourceMapper.selectBatchIds(resourceIds)
                .stream()
                .collect(Collectors.toMap(ProductionResourcePO::getId, Function.identity()));
        for (String resourceId : resourceIds) {
            ProductionResourcePO resource = resourceMap.get(resourceId);
            if (resource == null) {
                throw new BusinessException("资源不存在");
            }
            assertCanAccessResourceGroup(resource.getResourceGroup());
        }
    }

    @Override
    public void assertCanAccessResourceCodes(Collection<String> resourceCodes) {
        if (hasFullAccess() || ObjectNull.isNull(resourceCodes)) {
            return;
        }
        Map<String, ProductionResourcePO> resourceMap = productionResourceMapper.selectList(Wrappers.<ProductionResourcePO>lambdaQuery()
                        .in(ProductionResourcePO::getCode, resourceCodes))
                .stream()
                .collect(Collectors.toMap(ProductionResourcePO::getCode, Function.identity()));
        for (String resourceCode : resourceCodes) {
            if (StrUtil.isBlank(resourceCode)) {
                continue;
            }
            ProductionResourcePO resource = resourceMap.get(resourceCode);
            if (resource == null) {
                throw new BusinessException("资源编码不存在");
            }
            assertCanAccessResourceGroup(resource.getResourceGroup());
        }
    }

    @Override
    public void assertCanAccessTaskCodes(Collection<String> taskCodes) {
        if (hasFullAccess() || ObjectNull.isNull(taskCodes)) {
            return;
        }
        List<PlanTaskAdjustPO> adjustTasks = planTaskAdjustService.list(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                .in(PlanTaskAdjustPO::getCode, taskCodes));
        Set<String> existsCodes = adjustTasks.stream().map(PlanTaskAdjustPO::getCode).collect(Collectors.toSet());
        List<PlanTaskPO> planTasks = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                .in(PlanTaskPO::getCode, taskCodes));
        existsCodes.addAll(planTasks.stream().map(PlanTaskPO::getCode).collect(Collectors.toSet()));
        if (existsCodes.isEmpty()) {
            throw new BusinessException("任务不存在");
        }
        assertCanAccessResourceIds(adjustTasks.stream().map(PlanTaskAdjustPO::getMainResourceId).toList());
        assertCanAccessResourceIds(planTasks.stream().map(PlanTaskPO::getMainResourceId).toList());
    }

    @Override
    public List<TaskDTO> filterTaskDTOs(Collection<TaskDTO> tasks) {
        if (ObjectNull.isNull(tasks)) {
            return Collections.emptyList();
        }
        if (hasFullAccess()) {
            return new ArrayList<>(tasks);
        }
        Set<String> allowedResourceIds = new HashSet<>(listAuthorizedResourceIds());
        if (allowedResourceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return tasks.stream()
                .filter(task -> allowedResourceIds.contains(task.getMainResourceId()))
                .toList();
    }

    @Override
    public List<PlanTaskPO> filterPlanTasks(Collection<PlanTaskPO> tasks) {
        if (ObjectNull.isNull(tasks)) {
            return Collections.emptyList();
        }
        if (hasFullAccess()) {
            return new ArrayList<>(tasks);
        }
        Set<String> allowedResourceIds = new HashSet<>(listAuthorizedResourceIds());
        if (allowedResourceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return tasks.stream()
                .filter(task -> allowedResourceIds.contains(task.getMainResourceId()))
                .toList();
    }

    @Override
    public List<PlanTaskAdjustPO> filterPlanTaskAdjusts(Collection<PlanTaskAdjustPO> tasks) {
        if (ObjectNull.isNull(tasks)) {
            return Collections.emptyList();
        }
        if (hasFullAccess()) {
            return new ArrayList<>(tasks);
        }
        Set<String> allowedResourceIds = new HashSet<>(listAuthorizedResourceIds());
        if (allowedResourceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return tasks.stream()
                .filter(task -> allowedResourceIds.contains(task.getMainResourceId()))
                .toList();
    }

    @Override
    public List<ProductionResourcePO> filterResources(Collection<ProductionResourcePO> resources) {
        if (ObjectNull.isNull(resources)) {
            return Collections.emptyList();
        }
        if (hasFullAccess()) {
            return new ArrayList<>(resources);
        }
        Set<String> allowedResourceGroups = new HashSet<>(listAuthorizedResourceGroups());
        if (allowedResourceGroups.isEmpty()) {
            return Collections.emptyList();
        }
        return resources.stream()
                .filter(resource -> allowedResourceGroups.contains(resource.getResourceGroup()))
                .toList();
    }

    private List<String> listAllResourceGroups() {
        return listResources(null).stream()
                .map(ProductionResourcePO::getResourceGroup)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .sorted((o1, o2) -> {
                    if (ProductionResourceService.DEFAULT_RESOURCE_GROUP.equals(o1)) {
                        return -1;
                    } else if (ProductionResourceService.DEFAULT_RESOURCE_GROUP.equals(o2)) {
                        return 1;
                    }
                    return o1.compareTo(o2);
                })
                .toList();
    }

    private List<ProductionResourcePO> listResources(Collection<String> resourceGroups) {
        return productionResourceMapper.selectList(Wrappers.<ProductionResourcePO>lambdaQuery()
                .in(!ObjectNull.isNull(resourceGroups), ProductionResourcePO::getResourceGroup, resourceGroups));
    }
}
