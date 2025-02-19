package cn.bctools.aps.service.impl;

import cn.bctools.aps.entity.PlanTaskOrderPO;
import cn.bctools.aps.mapper.PlanTaskOrderMapper;
import cn.bctools.aps.service.PlanTaskOrderService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * @author jvs
 */
@Service
public class PlanTaskOrderServiceImpl extends ServiceImpl<PlanTaskOrderMapper, PlanTaskOrderPO> implements PlanTaskOrderService {

    @Override
    public void removeByOrderIds(Collection<String> orderIds) {
        remove(Wrappers.<PlanTaskOrderPO>lambdaQuery().in(PlanTaskOrderPO::getOrderId, orderIds));
    }

    @Override
    public PlanTaskOrderPO getByOrderId(String orderId) {
        return getOne(Wrappers.<PlanTaskOrderPO>lambdaQuery()
                .eq(PlanTaskOrderPO::getOrderId, orderId));
    }

    @Override
    public List<PlanTaskOrderPO> listByOrderIds(Collection<String> orderIds) {
        return list(Wrappers.<PlanTaskOrderPO>lambdaQuery().in(PlanTaskOrderPO::getOrderId, orderIds));
    }
}
