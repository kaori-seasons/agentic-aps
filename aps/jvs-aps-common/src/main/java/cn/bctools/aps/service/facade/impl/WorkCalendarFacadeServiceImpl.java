package cn.bctools.aps.service.facade.impl;

import cn.bctools.aps.dto.PageWorkCalendarDTO;
import cn.bctools.aps.entity.ProductionResourcePO;
import cn.bctools.aps.entity.ResourceCalendarPO;
import cn.bctools.aps.entity.WorkCalendarPO;
import cn.bctools.aps.entity.WorkModePO;
import cn.bctools.aps.enums.DayOfWeekEnum;
import cn.bctools.aps.service.ProductionResourceService;
import cn.bctools.aps.service.ResourceCalendarService;
import cn.bctools.aps.service.WorkCalendarService;
import cn.bctools.aps.service.WorkModeService;
import cn.bctools.aps.service.facade.WorkCalendarFacadeService;
import cn.bctools.aps.solve.model.WorkCalendar;
import cn.bctools.aps.util.DateUtils;
import cn.bctools.aps.util.WorkCalendarUtils;
import cn.bctools.aps.vo.DetailProductionResourceVO;
import cn.bctools.aps.vo.DetailWorkCalendarVO;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author jvs
 */
@Service
@AllArgsConstructor
public class WorkCalendarFacadeServiceImpl implements WorkCalendarFacadeService {

    private final ResourceCalendarService resourceCalendarService;
    private final ProductionResourceService productionResourceService;
    private final WorkModeService workModeService;
    private final WorkCalendarService workCalendarService;

    /**
     * 日历默认开始时间
     */
    private static final LocalDateTime CALENDAR_BEGIN_TIME_DEFAULT = LocalDateTime.of(1999, 1, 1, 0, 0, 0);
    /**
     * 日历默认结束时间
     */
    private static final LocalDateTime CALENDAR_END_TIME_DEFAULT = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void insertOrUpdate(WorkCalendarPO workCalendar, Map<DayOfWeekEnum, Boolean> workDaySetting, List<String> resourceIds) {
        if (ObjectNull.isNull(workModeService.getById(workCalendar.getWorkModeId()))) {
            throw new BusinessException("工作模式不存在");
        }
        // 设置默认起止时间
        if (ObjectNull.isNull(workCalendar.getBeginTime())) {
            workCalendar.setBeginTime(CALENDAR_BEGIN_TIME_DEFAULT);
        }
        if (ObjectNull.isNull(workCalendar.getEndTime())) {
            workCalendar.setEndTime(CALENDAR_END_TIME_DEFAULT);
        }
        // 工作日设置转换
        byte[] workDays = WorkCalendarUtils.workDaySettingToBytes(workDaySetting);
        workCalendar.setWorkDays(workDays);
        // 校验工作日历起止时间
        if (!workCalendar.getBeginTime().isBefore(workCalendar.getEndTime())) {
            throw new BusinessException("日历开始时间必须在结束时间之前");
        }
        // 校验工作日历是否冲突
        validateCalendarConflict(workCalendar);
        // 保存日历
        if (ObjectNull.isNull(workCalendar.getId())) {
            workCalendarService.save(workCalendar);
        } else {
            workCalendarService.updateById(workCalendar);
        }

        // 批量保存资源日历关系
        resourceCalendarService.saveBatch(workCalendar.getId(), resourceIds);
    }


    @Override
    public void delete(String id) {
        workCalendarService.removeById(id);
        resourceCalendarService.deleteByCalendarId(id);
    }

    @Override
    public DetailWorkCalendarVO getDetail(String id) {
        WorkCalendarPO workCalendar = workCalendarService.getById(id);
        if (ObjectNull.isNull(workCalendar)) {
            return null;
        }
        DetailWorkCalendarVO detail = BeanCopyUtil.copy(workCalendar, DetailWorkCalendarVO.class);
        // 工作日设置转换
        Map<DayOfWeekEnum, Boolean> workDaySetting = WorkCalendarUtils.workDayBytesToSettingMap(workCalendar.getWorkDays());
        detail.setWorkDaySetting(workDaySetting);
        // 获取工作模式
        WorkModePO workMode = workModeService.getById(detail.getWorkModeId());
        if (ObjectNull.isNotNull(workMode)) {
            detail.setWorkModeName(workMode.getName());
        }
        return detail;
    }

    @Override
    public Page<DetailWorkCalendarVO> page(PageWorkCalendarDTO pageQuery) {
        Page<WorkCalendarPO> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        workCalendarService.page(page, Wrappers.<WorkCalendarPO>lambdaQuery().orderByDesc(WorkCalendarPO::getCreateTime));
        List<String> workModeIdList = page.getRecords().stream().map(WorkCalendarPO::getWorkModeId).distinct().toList();
        Map<String, String> workModeMap = ObjectNull.isNull(workModeIdList) ? Collections.emptyMap() : workModeService.listByIds(workModeIdList).stream()
                .collect(Collectors.toMap(WorkModePO::getId, WorkModePO::getName));
        List<DetailWorkCalendarVO> voList = page.getRecords().stream()
                .map(calendar -> {
                    DetailWorkCalendarVO vo = BeanCopyUtil.copy(calendar, DetailWorkCalendarVO.class);
                    // 工作日设置转换
                    Map<DayOfWeekEnum, Boolean> workDaySetting = WorkCalendarUtils.workDayBytesToSettingMap(calendar.getWorkDays());
                    vo.setWorkDaySetting(workDaySetting);
                    // 模式名称
                    vo.setWorkModeName(workModeMap.get(calendar.getWorkModeId()));
                    return vo;
                })
                .toList();
        Page<DetailWorkCalendarVO> pageVoList = new Page<>(page.getCurrent(), page.getSize());
        pageVoList
                .setRecords(voList)
                .setTotal(page.getTotal());
        return pageVoList;
    }

    @Override
    public List<DetailProductionResourceVO> getResourcesByCalendarId(String calendarId) {
        List<ResourceCalendarPO> resourceCalendarList = resourceCalendarService.list(Wrappers.<ResourceCalendarPO>lambdaQuery()
                .eq(ResourceCalendarPO::getWorkCalendarId, calendarId));
        if (ObjectNull.isNull(resourceCalendarList)) {
            return Collections.emptyList();
        }
        List<String> resourceIds = resourceCalendarList.stream()
                .map(ResourceCalendarPO::getProductionResourceId)
                .toList();
        int batchSize = 2;
        List<DetailProductionResourceVO> resourceList = new ArrayList<>();
        for (int i = 0; i < resourceIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, resourceIds.size());
            List<String> batch = resourceIds.subList(i, end);
            List<ProductionResourcePO> batchResources = productionResourceService.listByIds(batch);
            batchResources.forEach(productionResourceService::convertData);
            resourceList.addAll(BeanCopyUtil.copys(batchResources, DetailProductionResourceVO.class));
        }
        return resourceList;
    }

    @Override
    public List<WorkCalendar> getResourceScheduleCalendar(String resourceId) {
        // 查询资源日历id
        List<String> calendarIdList = resourceCalendarService.list(Wrappers.<ResourceCalendarPO>lambdaQuery()
                        .eq(ResourceCalendarPO::getProductionResourceId, resourceId))
                .stream()
                .map(ResourceCalendarPO::getWorkCalendarId)
                .toList();
        if (ObjectNull.isNull(calendarIdList)) {
            return Collections.emptyList();
        }

        // 日历
        LambdaQueryWrapper<WorkCalendarPO> wrapper = Wrappers.<WorkCalendarPO>lambdaQuery()
                .in(WorkCalendarPO::getId, calendarIdList)
                .eq(WorkCalendarPO::getEnabled, true);
        List<WorkCalendarPO> workCalendars = workCalendarService.list(wrapper);
        if (ObjectNull.isNull(workCalendars)) {
            return Collections.emptyList();
        }
        // 工作模式Map<模式id, 工作模式字符串>
        Set<String> workModelIdList = workCalendars.stream()
                .map(WorkCalendarPO::getWorkModeId)
                .collect(Collectors.toSet());
        Map<String, String> workModeMap = workModeService.listByIds(workModelIdList)
                .stream()
                .collect(Collectors.toMap(WorkModePO::getId, e -> Optional.ofNullable(e.getWorkingMode()).orElse("")));
        return WorkCalendarUtils.convertScheduleCalendar(workCalendars, workModeMap);
    }

    @Override
    public Map<String, List<WorkCalendar>> listResourceScheduleCalendar(Collection<String> resourceIds) {
        // 查询资源日历
        Map<String, Set<String>> resourceCalendarIdMap = resourceCalendarService.list(Wrappers.<ResourceCalendarPO>lambdaQuery()
                        .in(ResourceCalendarPO::getProductionResourceId, resourceIds))
                .stream()
                .collect(Collectors.groupingBy(ResourceCalendarPO::getProductionResourceId, Collectors.mapping(ResourceCalendarPO::getWorkCalendarId, Collectors.toSet())));
        // 提取日历id集合
        Set<String> calendarIdList = resourceCalendarIdMap.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        if (ObjectNull.isNull(calendarIdList)) {
            return Collections.emptyMap();
        }

        // 日历
        LambdaQueryWrapper<WorkCalendarPO> wrapper = Wrappers.<WorkCalendarPO>lambdaQuery()
                .in(WorkCalendarPO::getId, calendarIdList)
                .eq(WorkCalendarPO::getEnabled, true);
        List<WorkCalendarPO> workCalendars = workCalendarService.list(wrapper);
        if (ObjectNull.isNull(workCalendars)) {
            return Collections.emptyMap();
        }
        // 工作模式
        Set<String> workModelIdList = workCalendars.stream()
                .map(WorkCalendarPO::getWorkModeId)
                .collect(Collectors.toSet());
        Map<String, String> workModeMap = workModeService.listByIds(workModelIdList)
                .stream()
                .collect(Collectors.toMap(WorkModePO::getId, e -> Optional.ofNullable(e.getWorkingMode()).orElse("")));
        Map<String, WorkCalendar> workCalendarMap = WorkCalendarUtils.convertScheduleCalendar(workCalendars, workModeMap)
                .stream()
                .collect(Collectors.toMap(WorkCalendar::getId, Function.identity()));

        // 每个资源可用的日历
        Map<String, List<WorkCalendar>> resourceCalendarMap = new HashMap<>();
        resourceCalendarIdMap.forEach((resourceId, resourceCalendarIds) -> {
            List<WorkCalendar> resourceCalendars = resourceCalendarIds.stream()
                    .map(workCalendarMap::get)
                    .filter(ObjectNull::isNotNull)
                    .toList();
            resourceCalendarMap.put(resourceId, resourceCalendars);
        });
        return resourceCalendarMap;
    }


    /**
     * 校验日历冲突
     *
     * <p>
     * 日历起止时间有冲突，需要设置不同的优先级
     *
     * @param workCalendar 日历
     */
    private void validateCalendarConflict(WorkCalendarPO workCalendar) {
        // 没有相同优先级的日历，不需要校验
        // 不校验自身
        List<WorkCalendarPO> samePriorityWorkCalendarList = workCalendarService.list(Wrappers.<WorkCalendarPO>lambdaQuery()
                .eq(WorkCalendarPO::getPriority, workCalendar.getPriority()));
        samePriorityWorkCalendarList.removeIf(calendar -> calendar.getId().equals(workCalendar.getId()));
        if (ObjectNull.isNull(samePriorityWorkCalendarList)) {
            return;
        }
        // 校验起止时间是否冲突
        boolean calendarConflict = samePriorityWorkCalendarList.stream()
                .anyMatch(calendar ->
                        DateUtils.hasOverlap(workCalendar.getBeginTime(), workCalendar.getEndTime(), calendar.getBeginTime(), calendar.getEndTime())
                );
        if (calendarConflict) {
            throw new BusinessException("日历起止时间存在冲突,请修改优先级");
        }
    }
}
