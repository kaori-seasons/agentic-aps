package cn.windwheel.aps.service.impl;

import cn.windwheel.aps.entity.ProcessPO;
import cn.windwheel.aps.mapper.ProcessMapper;
import cn.windwheel.aps.service.ProcessService;
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
