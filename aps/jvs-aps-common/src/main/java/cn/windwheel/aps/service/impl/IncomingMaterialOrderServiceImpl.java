package cn.windwheel.aps.service.impl;

import cn.windwheel.aps.entity.IncomingMaterialOrderPO;
import cn.windwheel.aps.mapper.IncomingMaterialPlanMapper;
import cn.windwheel.aps.service.IncomingMaterialOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author jvs
 * 来料订单
 */
@Service
@AllArgsConstructor
public class IncomingMaterialOrderServiceImpl extends ServiceImpl<IncomingMaterialPlanMapper, IncomingMaterialOrderPO> implements IncomingMaterialOrderService {

}
