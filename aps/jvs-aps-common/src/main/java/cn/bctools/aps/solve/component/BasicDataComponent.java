package cn.bctools.aps.solve.component;

import cn.bctools.aps.entity.*;
import cn.bctools.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.bctools.aps.entity.enums.IncomingMaterialOrderStatusEnum;
import cn.bctools.aps.entity.enums.OrderSchedulingStatusEnum;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.entity.enums.OrderStatusEnum;
import cn.bctools.aps.graph.Graph;
import cn.bctools.aps.service.*;
import cn.bctools.aps.solve.model.*;
import cn.bctools.aps.solve.util.DurationUtils;
import cn.bctools.aps.util.WorkCalendarUtils;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 排产基础数据服务
 */
@Component
@AllArgsConstructor
public class BasicDataComponent {


    private final MaterialService materialService;
    private final ProcessRouteService processRouteService;
    private final WorkCalendarService workCalendarService;
    private final WorkModeService workModeService;
    private final ProductionResourceService productionResourceService;
    private final ResourceCalendarService resourceCalendarService;
    private final IncomingMaterialOrderService incomingMaterialOrderService;
    private final ProductionOrderService productionOrderService;
    private final ManufactureBomService manufactureBomService;
    private final PlanTaskService planTaskService;
    private PlanTaskAdjustService planTaskAdjustService;


    /**
     * 获取基础数据
     *
     * @return 基础数据
     */
    public BasicData getBasicData() {
        List<Material> materials = listMaterial();
        Map<String, Material> materialMap = materials.stream().collect(Collectors.toMap(Material::getId, Function.identity()));
        Map<String, String> materialCodeMap = materials.stream().collect(Collectors.toMap(Material::getCode, Material::getId));
        List<ProductionOrder> productionOrders = listProductionOrder(materialCodeMap);
        return new BasicData()
                .setMaterialMap(materialMap)
                .setProcessRouteMap(getProcessRoute())
                .setWorkCalendarMap(getWorkCalendar())
                .setProductionResources(listProductionResources())
                .setBomMap(getBom())
                .setIncomingMaterialOrderMap(getIncomingMaterialOrder(materialCodeMap))
                .setProductionOrders(productionOrders)
                .setPinnedTasks(listPinnedTasks(productionOrders));
    }

    /**
     * 查询所有物料
     *
     * @return 物料集合
     */
    private List<Material> listMaterial() {
        List<MaterialPO> materials = materialService.list();
        if (ObjectNull.isNull(materials)) {
            return Collections.emptyList();
        }
        return materials.stream()
                .map(material -> {
                    Duration leadTimeDuration = DurationUtils.convertDuration(material.getLeadTime());
                    Duration bufferTimeDuration = DurationUtils.convertDuration(material.getBufferTime());
                    return BeanCopyUtil.copy(material, Material.class)
                            .setLeadTimeDuration(leadTimeDuration)
                            .setBufferTimeDuration(bufferTimeDuration);

                })
                .collect(Collectors.toList());
    }

    /**
     * 查询所有已启用的工艺路线
     *
     * @return Map<物料id, 工艺路线>
     */
    private Map<String, Graph<ProcessRouteNodePropertiesDTO>> getProcessRoute() {
        LambdaQueryWrapper<ProcessRoutePO> wrapper = Wrappers.<ProcessRoutePO>lambdaQuery()
                .eq(ProcessRoutePO::getEnabled, true);
        List<ProcessRoutePO> processRoutes = processRouteService.list(wrapper);
        if (ObjectNull.isNull(processRoutes)) {
            return Collections.emptyMap();
        }
        return processRoutes.stream()
                .map(route -> {
                    // 置空排程计算不需要的属性
                    route.getRouteDesign().getNodes().forEach(node -> node.setStyle(null));
                    return BeanCopyUtil.copy(route, ProcessRoute.class);
                })
                .collect(Collectors.toMap(ProcessRoute::getMaterialId, ProcessRoute::getRouteDesign));
    }

    /**
     * 查询所有已启用的日历
     *
     * @return 日历集合
     */
    private Map<String, WorkCalendar> getWorkCalendar() {
        // 日历
        LambdaQueryWrapper<WorkCalendarPO> wrapper = Wrappers.<WorkCalendarPO>lambdaQuery()
                .eq(WorkCalendarPO::getEnabled, true);
        List<WorkCalendarPO> workCalendars = workCalendarService.list(wrapper);
        if (ObjectNull.isNull(workCalendars)) {
            return Collections.emptyMap();
        }
        // 工作模式Map<模式id, 工作模式字符串>
        Map<String, String> workModeMap = workModeService.list()
                .stream()
                .collect(Collectors.toMap(WorkModePO::getId, e -> Optional.ofNullable(e.getWorkingMode()).orElse("")));
        List<WorkCalendar> workCalendarList = WorkCalendarUtils.convertScheduleCalendar(workCalendars, workModeMap);
        return workCalendarList.stream().collect(Collectors.toMap(WorkCalendar::getId, Function.identity()));
    }

    /**
     * 查询所有资源
     *
     * @return 资源集合
     */
    private List<ProductionResource> listProductionResources() {
        List<ProductionResourcePO> productionResources = productionResourceService.list();
        if (ObjectNull.isNull(productionResources)) {
            return Collections.emptyList();
        }
        // 获取资源使用的日历
        Map<String, List<String>> resourceCalendarMap = resourceCalendarService.list()
                .stream()
                .collect(Collectors.groupingBy(ResourceCalendarPO::getProductionResourceId, Collectors.mapping(ResourceCalendarPO::getWorkCalendarId, Collectors.toList())));

        return productionResources.stream()
                .map(resource -> {
                    List<String> calendarIds = resourceCalendarMap.get(resource.getId());
                    return BeanCopyUtil.copy(resource, ProductionResource.class).setWorkCalendarIds(calendarIds);
                })
                .toList();
    }

    /**
     * 查询BOM
     *
     * @return Map<物料id, 子件物料集合>
     */
    private Map<String, List<BomMaterial>> getBom() {
        List<ManufactureBomPO> manufactureBomList = manufactureBomService.list();
        if (ObjectNull.isNull(manufactureBomList)) {
            return Collections.emptyMap();
        }

        return manufactureBomList.stream()
                .collect(Collectors.toMap(ManufactureBomPO::getMaterialId, bom -> BeanCopyUtil.copys(bom.getChildMaterials(), BomMaterial.class)));
    }

    /**
     * 查询状态为“在途”的来料订单
     *
     * @param materialCodeMap Map<物料编码, 物料id>
     * @return Map<物料id, 来料订单集合>
     */
    private Map<String, List<IncomingMaterialOrder>> getIncomingMaterialOrder(Map<String, String> materialCodeMap) {
        LambdaQueryWrapper<IncomingMaterialOrderPO> wrapper = Wrappers.<IncomingMaterialOrderPO>lambdaQuery()
                .eq(IncomingMaterialOrderPO::getOrderStatus, IncomingMaterialOrderStatusEnum.PROCESSING);
        List<IncomingMaterialOrderPO> incomingMaterialOrders = incomingMaterialOrderService.list(wrapper);
        if (ObjectNull.isNull(incomingMaterialOrders)) {
            return Collections.emptyMap();
        }
        return incomingMaterialOrders.stream()
                .filter(order -> materialCodeMap.containsKey(order.getMaterialCode()))
                .map(order -> {
                    IncomingMaterialOrder incomingMaterialOrder = BeanCopyUtil.copy(order, IncomingMaterialOrder.class);
                    incomingMaterialOrder.setMaterialId(materialCodeMap.get(order.getMaterialCode()));
                    return incomingMaterialOrder;
                })
                .collect(Collectors.groupingBy(IncomingMaterialOrder::getMaterialId));
    }

    /**
     * 查询待未完结的生产订单
     *
     * @param materialCodeMap Map<物料编码, 物料id>
     * @return 生产订单集合
     */
    private List<ProductionOrder> listProductionOrder(Map<String, String> materialCodeMap) {
        LambdaQueryWrapper<ProductionOrderPO> wrapper = Wrappers.<ProductionOrderPO>lambdaQuery()
                .eq(ProductionOrderPO::getOrderStatus, OrderStatusEnum.PENDING)
                .ne(ProductionOrderPO::getSchedulingStatus, OrderSchedulingStatusEnum.COMPLETED)
                .eq(ProductionOrderPO::getCanSchedule, true);
        List<ProductionOrderPO> productionOrders = productionOrderService.list(wrapper);
        if (ObjectNull.isNull(productionOrders)) {
            throw new BusinessException("没有需要排产的订单");
        }
        return productionOrders.stream()
                .filter(order -> materialCodeMap.containsKey(order.getMaterialCode()))
                .map(order -> BeanCopyUtil.copy(order, ProductionOrder.class)
                        .setMaterialId(materialCodeMap.get(order.getMaterialCode())))
                .collect(Collectors.toList());
    }

    /**
     * 查询待排产订单已锁定|已完成的生产任务
     *
     * @param productionOrders 待排产的生产订单集合
     * @return 已锁定|已完成的生产任务集合
     */
    private List<PlanTaskAdjustPO> listPinnedTasks(List<ProductionOrder> productionOrders) {
        if (ObjectNull.isNull(productionOrders)) {
            return Collections.emptyList();
        }
        // 查询待排产订单已锁定的生产任务
        List<String> orderIds = productionOrders.stream().map(ProductionOrder::getId).toList();
        // 查询锁定的已调整任务
        List<PlanTaskAdjustPO> planTaskAdjustList = planTaskAdjustService.list(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                .in(PlanTaskAdjustPO::getMainOrderId, orderIds)
                .eq(PlanTaskAdjustPO::getDiscard, false)
                .eq(PlanTaskAdjustPO::getPinned, true));
        // 查询合并任务
        Set<String> mergeTaskAdjustCodes = planTaskAdjustList.stream()
                .map(PlanTaskAdjustPO::getMergeTaskCode)
                .filter(ObjectNull::isNotNull)
                .collect(Collectors.toSet());
        if (ObjectNull.isNotNull(mergeTaskAdjustCodes)) {
            List<PlanTaskAdjustPO> mergeTaskList = planTaskAdjustService.list(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                    .in(PlanTaskAdjustPO::getCode, mergeTaskAdjustCodes)
                    .eq(PlanTaskAdjustPO::getDiscard, false)
                    .eq(PlanTaskAdjustPO::getPinned, true));
            planTaskAdjustList.addAll(mergeTaskList);
        }

        // 查询已排产任务
        List<PlanTaskPO> planTaskList = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                        .in(PlanTaskPO::getMainOrderId, orderIds))
                .stream()
                .filter(task -> task.getPinned() || PlanTaskStatusEnum.COMPLETED.equals(task.getTaskStatus()))
                .collect(Collectors.toList());
        // 查询合并任务
        Set<String> mergeTaskCodes = planTaskList.stream()
                .map(PlanTaskPO::getMergeTaskCode)
                .filter(ObjectNull::isNotNull)
                .collect(Collectors.toSet());
        if (ObjectNull.isNotNull(mergeTaskCodes)) {
            List<PlanTaskPO> mergeTaskList = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery().in(PlanTaskPO::getCode, mergeTaskCodes));
            planTaskList.addAll(mergeTaskList);
        }

        // 以已调整的任务优先（从已排产任务集合中排除已调整的任务）
        List<String> taskAdjustCodeList = planTaskAdjustList.stream()
                .map(PlanTaskAdjustPO::getCode)
                .toList();
        planTaskList.removeIf(task -> taskAdjustCodeList.contains(task.getCode()));
        planTaskList.forEach(task -> task.setPinned(true));

        // 合并任务
        if (ObjectNull.isNotNull(planTaskList)) {
            planTaskList.forEach(task -> {
                PlanTaskAdjustPO taskAdjust = BeanCopyUtil.copy(task, PlanTaskAdjustPO.class)
                        .setDiscard(false)
                        .setCompliant(true);
                planTaskAdjustList.add(taskAdjust);
            });
        }

        return planTaskAdjustList;
    }

}



