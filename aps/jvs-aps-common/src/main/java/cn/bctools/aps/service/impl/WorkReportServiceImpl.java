package cn.bctools.aps.service.impl;

import cn.bctools.aps.entity.WorkReportPO;
import cn.bctools.aps.mapper.WorkReportMapper;
import cn.bctools.aps.service.WorkReportService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * @author jvs
 * 报工记录
 */
@Service
public class WorkReportServiceImpl extends ServiceImpl<WorkReportMapper, WorkReportPO> implements WorkReportService {
}
