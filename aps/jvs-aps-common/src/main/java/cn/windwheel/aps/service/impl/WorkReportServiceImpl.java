package cn.windwheel.aps.service.impl;

import cn.windwheel.aps.entity.WorkReportPO;
import cn.windwheel.aps.mapper.WorkReportMapper;
import cn.windwheel.aps.service.WorkReportService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * @author jvs
 * 报工记录
 */
@Service
public class WorkReportServiceImpl extends ServiceImpl<WorkReportMapper, WorkReportPO> implements WorkReportService {
}
