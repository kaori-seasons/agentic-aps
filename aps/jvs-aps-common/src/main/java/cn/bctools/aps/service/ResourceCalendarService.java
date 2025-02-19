package cn.bctools.aps.service;

import cn.bctools.aps.entity.ResourceCalendarPO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author jvs
 * 资源日历关系
 */
public interface ResourceCalendarService extends IService<ResourceCalendarPO> {


    /**
     * 批量保存资源日历关系
     *
     * @param calendarId  日历id
     * @param resourceIds 资源id集合
     */
    void saveBatch(String calendarId, List<String> resourceIds);

    /**
     * 删除指定日历的资源关系
     *
     * @param calendarId 日历id
     */
    void deleteByCalendarId(String calendarId);
}
