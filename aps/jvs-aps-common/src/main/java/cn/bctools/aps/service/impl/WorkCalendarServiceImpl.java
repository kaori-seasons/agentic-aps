package cn.bctools.aps.service.impl;

import cn.bctools.aps.entity.WorkCalendarPO;
import cn.bctools.aps.mapper.WorkCalendarMapper;
import cn.bctools.aps.service.WorkCalendarService;
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
