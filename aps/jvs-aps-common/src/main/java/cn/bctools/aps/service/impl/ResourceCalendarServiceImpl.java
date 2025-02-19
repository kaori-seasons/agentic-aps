package cn.bctools.aps.service.impl;

import cn.bctools.aps.entity.ResourceCalendarPO;
import cn.bctools.aps.mapper.ResourceCalendarMapper;
import cn.bctools.aps.service.ResourceCalendarService;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author jvs
 * 资源日历关系
 */
@Service
public class ResourceCalendarServiceImpl extends ServiceImpl<ResourceCalendarMapper, ResourceCalendarPO> implements ResourceCalendarService {

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveBatch(String calendarId, List<String> resourceIds) {
        if (ObjectNull.isNull(calendarId)) {
            throw new BusinessException("参数错误");
        }
        // 先删除旧的关系
        remove(Wrappers.<ResourceCalendarPO>lambdaQuery().eq(ResourceCalendarPO::getWorkCalendarId, calendarId));
        // 保存新的关系
        if (ObjectNull.isNull(resourceIds)) {
            return;
        }
        List<ResourceCalendarPO> resourceCalendarList = resourceIds.stream()
                .distinct()
                .map(resourceId ->
                        new ResourceCalendarPO()
                                .setWorkCalendarId(calendarId)
                                .setProductionResourceId(resourceId))
                .toList();
        saveBatch(resourceCalendarList);
    }

    @Override
    public void deleteByCalendarId(String calendarId) {
        remove(Wrappers.<ResourceCalendarPO>lambdaQuery().eq(ResourceCalendarPO::getWorkCalendarId, calendarId));
    }
}
