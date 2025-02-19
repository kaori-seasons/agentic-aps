package cn.bctools.aps.service.impl;

import cn.bctools.aps.entity.PlanTaskPendingPO;
import cn.bctools.aps.mapper.PlanTaskPendingMapper;
import cn.bctools.aps.service.PlanTaskPendingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * @author jvs
 * 待确认排产计划任务
 */
@Service
public class PlanTaskPendingServiceImpl extends ServiceImpl<PlanTaskPendingMapper, PlanTaskPendingPO> implements PlanTaskPendingService {

}
