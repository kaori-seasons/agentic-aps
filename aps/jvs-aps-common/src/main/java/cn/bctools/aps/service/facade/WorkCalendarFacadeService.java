package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.PageWorkCalendarDTO;
import cn.bctools.aps.entity.WorkCalendarPO;
import cn.bctools.aps.enums.DayOfWeekEnum;
import cn.bctools.aps.solve.model.WorkCalendar;
import cn.bctools.aps.vo.DetailProductionResourceVO;
import cn.bctools.aps.vo.DetailWorkCalendarVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author jvs
 * 工作日历聚合服务
 */
public interface WorkCalendarFacadeService {

    /**
     * 新增或修改日历
     *
     * @param workCalendar 日历
     * @param workDaySetting 工作日设置
     * @param resourceIds 资源id集合
     */
    void insertOrUpdate(WorkCalendarPO workCalendar, Map<DayOfWeekEnum, Boolean> workDaySetting, List<String> resourceIds);

    /**
     * 删除日历
     *
     * @param id 日历id
     */
    void delete(String id);

    /**
     * 获取日历详细信息
     *
     * @param id 日历id
     * @return 日历详细信息
     */
    DetailWorkCalendarVO getDetail(String id);

    /**
     * 分页查询日历
     *
     * @param pageQuery 分页条件
     * @return 分页结果
     */
    Page<DetailWorkCalendarVO> page(PageWorkCalendarDTO pageQuery);

    /**
     * 获取使用指定日历的所有资源
     *
     * @param calendarId 日历id
     * @return 资源集合
     */
    List<DetailProductionResourceVO> getResourcesByCalendarId(String calendarId);

    /**
     * 获取资源可用于排产计算的工作日历
     *
     * @param resourceId 资源id
     * @return 可直接作用于排产计算的工作日历
     */
    List<WorkCalendar> getResourceScheduleCalendar(String resourceId);

    /**
     * 获取资源可用于排产计算的工作日历
     *
     * @param resourceIds 资源id集合
     * @return 可直接作用于排产计算的工作日历.Map<资源id, 工作日历集合>
     */
    Map<String, List<WorkCalendar>> listResourceScheduleCalendar(Collection<String> resourceIds);
}
