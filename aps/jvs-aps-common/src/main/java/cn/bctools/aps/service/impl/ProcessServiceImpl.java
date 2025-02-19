package cn.bctools.aps.service.impl;

import cn.bctools.aps.entity.ProcessPO;
import cn.bctools.aps.mapper.ProcessMapper;
import cn.bctools.aps.service.ProcessService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author jvs
 * 工序
 */
@Service
@AllArgsConstructor
public class ProcessServiceImpl extends ServiceImpl<ProcessMapper, ProcessPO> implements ProcessService {

}
