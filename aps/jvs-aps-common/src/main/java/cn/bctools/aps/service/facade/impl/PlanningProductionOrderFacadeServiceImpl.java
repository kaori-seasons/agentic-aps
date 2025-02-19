package cn.bctools.aps.service.facade.impl;

import cn.bctools.aps.dto.PagePlanningProductionOrderPendingDTO;
import cn.bctools.aps.entity.ProductionOrderPO;
import cn.bctools.aps.entity.enums.OrderStatusEnum;
import cn.bctools.aps.service.ProductionOrderService;
import cn.bctools.aps.service.facade.PlanningProductionOrderFacadeService;
import cn.bctools.aps.util.BigDecimalUtils;
import cn.bctools.aps.vo.DetailProductionOrderVO;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author jvs
 * 生产订单排产操作
 */
@Service
@AllArgsConstructor
public class PlanningProductionOrderFacadeServiceImpl implements PlanningProductionOrderFacadeService {

    private final ProductionOrderService productionOrderService;

    @Override
    public Page<DetailProductionOrderVO> pagePendingProductionOrder(PagePlanningProductionOrderPendingDTO pageQuery) {
        LambdaQueryWrapper<ProductionOrderPO> wrapper = Wrappers.<ProductionOrderPO>lambdaQuery()
                .eq(ProductionOrderPO::getOrderStatus, OrderStatusEnum.PENDING)
                .eq(ObjectNull.isNotNull(pageQuery.getSchedulingStatus()), ProductionOrderPO::getSchedulingStatus, pageQuery.getSchedulingStatus())
                // 只查询主生产订单，不查询补充生产订单
                .eq(ProductionOrderPO::getSupplement, false)
                .ne(ObjectNull.isNotNull(pageQuery.getExcludeId()), ProductionOrderPO::getId, pageQuery.getExcludeId())
                .like(ObjectNull.isNotNull(pageQuery.getCode()), ProductionOrderPO::getCode, pageQuery.getCode())
                .orderByDesc(ProductionOrderPO::getPriority)
                .orderByAsc(ProductionOrderPO::getSequence)
                .orderByAsc(ProductionOrderPO::getDeliveryTime);
        Page<ProductionOrderPO> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        productionOrderService.page(page, wrapper);

        // 查询是否有补充生产订单
        List<String> orderCodes = page.getRecords().stream().map(ProductionOrderPO::getCode).toList();
        Map<String, Boolean> supplementMap = productionOrderService.supplementByCodes(orderCodes);

        // 转换为VO对象
        List<DetailProductionOrderVO> voList = page.getRecords().stream()
                .map(order -> {
                    DetailProductionOrderVO detailProductionOrder = BeanCopyUtil.copy(order, DetailProductionOrderVO.class);
                    detailProductionOrder.setQuantity(BigDecimalUtils.stripTrailingZeros(order.getQuantity()));
                    detailProductionOrder.setHasSupplement(supplementMap.get(detailProductionOrder.getCode()));
                    return detailProductionOrder;
                })
                .toList();
        Page<DetailProductionOrderVO> pageVoList = new Page<>(page.getCurrent(), page.getSize());
        pageVoList
                .setRecords(voList)
                .setTotal(page.getTotal());
        return pageVoList;
    }
}
