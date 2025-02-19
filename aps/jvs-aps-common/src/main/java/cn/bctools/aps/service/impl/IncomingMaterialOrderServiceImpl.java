package cn.bctools.aps.service.impl;

import cn.bctools.aps.entity.IncomingMaterialOrderPO;
import cn.bctools.aps.mapper.IncomingMaterialPlanMapper;
import cn.bctools.aps.service.IncomingMaterialOrderService;
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
