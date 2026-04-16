package cn.windwheel.aps.service.impl;

import cn.windwheel.aps.entity.WorkCalendarPO;
import cn.windwheel.aps.mapper.WorkCalendarMapper;
import cn.windwheel.aps.service.WorkCalendarService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author jvs
 * 工作日历
 */
@Service
@AllArgsConstructor
public class WorkCalendarServiceImpl extends ServiceImpl<WorkCalendarMapper, WorkCalendarPO> implements WorkCalendarService {

}
