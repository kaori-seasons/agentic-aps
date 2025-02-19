package cn.bctools.aps.service.impl;

import cn.bctools.aps.dto.PageProductionOrderDTO;
import cn.bctools.aps.dto.UpdateOrderSortDTO;
import cn.bctools.aps.entity.ProductionOrderPO;
import cn.bctools.aps.entity.enums.OrderStatusEnum;
import cn.bctools.aps.enums.OrderSortPositionEnum;
import cn.bctools.aps.mapper.ProductionOrderMapper;
import cn.bctools.aps.service.ProductionOrderService;
import cn.bctools.aps.util.BigDecimalUtils;
import cn.bctools.aps.vo.DetailProductionOrderVO;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 生产订单
 */
@Service
@AllArgsConstructor
public class ProductionOrderServiceImpl extends ServiceImpl<ProductionOrderMapper, ProductionOrderPO> implements ProductionOrderService {

    @Override
    public void delete(String id) {
        ProductionOrderPO order = getOne(Wrappers.<ProductionOrderPO>lambdaQuery().eq(ProductionOrderPO::getId, id));
        if (ObjectNull.isNull(order)) {
            return;
        }
        // 删除订单
        removeById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateOrderSort(UpdateOrderSortDTO updateOrderSort) {
        // 待移动的订单
        ProductionOrderPO moveOrder = Optional.ofNullable(getById(updateOrderSort.getMoveOrderId()))
                .orElseThrow(() -> new BusinessException("订单不存在"));
        // 目标订单
        ProductionOrderPO targetOrder = Optional.ofNullable(getById(updateOrderSort.getTargetOrderId()))
                .orElseThrow(() -> new BusinessException("订单不存在"));
        // 目标订单的相邻订单
        ProductionOrderPO adjacentOrder = null;
        if (OrderSortPositionEnum.BEFORE.equals(updateOrderSort.getPosition())) {
            adjacentOrder = getOne(Wrappers.<ProductionOrderPO>lambdaQuery()
                    .lt(ProductionOrderPO::getSequence, targetOrder.getSequence())
                    .orderByDesc(ProductionOrderPO::getSequence)
                    .last("limit 1"));
        } else {
            adjacentOrder = getOne(Wrappers.<ProductionOrderPO>lambdaQuery()
                    .gt(ProductionOrderPO::getSequence, targetOrder.getSequence())
                    .orderByAsc(ProductionOrderPO::getSequence)
                    .last("limit 1"));
        }

        // 排序计算
        if (ObjectNull.isNull(adjacentOrder)) {
            if (OrderSortPositionEnum.AFTER.equals(updateOrderSort.getPosition())) {
                moveOrder.setSequence(targetOrder.getSequence().add(BigDecimal.ONE));
                updateOrderSequence(moveOrder);
            } else {
                moveOrder.setSequence(targetOrder.getSequence());
                updateOrderSequence(moveOrder);
            }
        } else {
            if (adjacentOrder.getId().equals(moveOrder.getId())) {
                return;
            }
            BigDecimal newSequence = calculateSequence(targetOrder.getSequence(), adjacentOrder.getSequence());
            moveOrder.setSequence(newSequence);
            updateOrderSequence(moveOrder);
        }
    }

    @Override
    public Page<DetailProductionOrderVO> page(PageProductionOrderDTO pageQuery) {
        LambdaQueryWrapper<ProductionOrderPO> wrapper = Wrappers.<ProductionOrderPO>lambdaQuery()
                .eq(ObjectNull.isNotNull(pageQuery.getOrderStatus()), ProductionOrderPO::getOrderStatus, pageQuery.getOrderStatus())
                .eq(ObjectNull.isNotNull(pageQuery.getSchedulingStatus()), ProductionOrderPO::getSchedulingStatus, pageQuery.getSchedulingStatus())
                .eq(ProductionOrderPO::getSupplement, false)
                .like(ObjectNull.isNotNull(pageQuery.getCode()), ProductionOrderPO::getCode, pageQuery.getCode())
                .orderByDesc(ProductionOrderPO::getCreateTime);
        Page<ProductionOrderPO> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        page(page, wrapper);

        // 查询是否有补充生产订单
        List<String> orderCodes = page.getRecords().stream().map(ProductionOrderPO::getCode).toList();
        Map<String, Boolean> supplementMap = supplementByCodes(orderCodes);
        // 转换为VO对象
        List<DetailProductionOrderVO> voList = page.getRecords().stream()
                .map(order -> {
                    DetailProductionOrderVO detailProductionOrder = BeanCopyUtil.copy(order, DetailProductionOrderVO.class);
                    detailProductionOrder.setQuantity(BigDecimalUtils.stripTrailingZeros(order.getQuantity()))
                            .setHasSupplement(supplementMap.get(detailProductionOrder.getCode()));
                    return detailProductionOrder;
                })
                .toList();
        Page<DetailProductionOrderVO> pageVoList = new Page<>(page.getCurrent(), page.getSize());
        pageVoList
                .setRecords(voList)
                .setTotal(page.getTotal());
        return pageVoList;
    }

    @Override
    public DetailProductionOrderVO getDetail(String id) {
        ProductionOrderPO productionOrder = getById(id);
        if (ObjectNull.isNull(productionOrder)) {
            return null;
        }
        return BeanCopyUtil.copy(productionOrder, DetailProductionOrderVO.class);
    }

    @Override
    public void updateCanSchedule(String id, Boolean canSchedule) {
        if (ObjectNull.isNull(canSchedule)) {
            return;
        }
        ProductionOrderPO productionOrder = getById(id);
        if (ObjectNull.isNull(productionOrder)) {
            throw new BusinessException("订单不存在");
        }
        if (!OrderStatusEnum.PENDING.equals(productionOrder.getOrderStatus())) {
            throw new BusinessException("不能修改已结束的订单");
        }
        productionOrder.setCanSchedule(canSchedule);
        updateById(productionOrder);
    }

    @Override
    public List<ProductionOrderPO> listByCodes(Collection<String> codes) {
        if (ObjectNull.isNull(codes)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<ProductionOrderPO>lambdaQuery()
                .in(ProductionOrderPO::getCode, codes));
    }

    @Override
    public Map<String, Boolean> supplementByCodes(Collection<String> codes) {
        if (ObjectNull.isNull(codes)) {
            return Collections.emptyMap();
        }
        Set<String> hasSupplementCodes = list(Wrappers.<ProductionOrderPO>lambdaQuery()
                .select(ProductionOrderPO::getParentOrderCode)
                .in(ProductionOrderPO::getParentOrderCode, codes))
                .stream()
                .map(ProductionOrderPO::getParentOrderCode)
                .collect(Collectors.toSet());

        return codes.stream()
                .collect(Collectors.toMap(code -> code, hasSupplementCodes::contains));
    }

    @Override
    public List<DetailProductionOrderVO> listSupplementOrder(String id) {
        ProductionOrderPO order = getById(id);
        if (ObjectNull.isNull(order)) {
            throw new BusinessException("订单不存在");
        }
        List<ProductionOrderPO> supplementOrders = list(Wrappers.<ProductionOrderPO>lambdaQuery()
                .eq(ProductionOrderPO::getParentOrderCode, order.getCode()));
        if (ObjectNull.isNull(supplementOrders)) {
            return Collections.emptyList();
        }
        return BeanCopyUtil.copys(supplementOrders, DetailProductionOrderVO.class);
    }

    /**
     * 修改订单序号
     *
     * @param order 订单
     */
    private void updateOrderSequence(ProductionOrderPO order) {
        long count = count(Wrappers.<ProductionOrderPO>lambdaQuery().eq(ProductionOrderPO::getSequence, order.getSequence()));
        // 序号已存在，后续所有数据序号+1
        if (count >= 1) {
            LambdaUpdateWrapper<ProductionOrderPO> updateWrapper = Wrappers.<ProductionOrderPO>lambdaUpdate()
                    .ge(ProductionOrderPO::getSequence, order.getSequence())
                    .setSql("sequence = sequence + 1");
            update(null, updateWrapper);
        }
        updateById(order);
    }


    /**
     * 计算序号
     *
     * @param num1 序号1
     * @param num2 序号2
     * @return 计算序号
     */
    private static BigDecimal calculateSequence(BigDecimal num1, BigDecimal num2) {
        BigDecimal sum = num1.add(num2);
        BigDecimal average = sum.divide(new BigDecimal(2), 6, RoundingMode.HALF_UP);
        return BigDecimalUtils.stripTrailingZeros(average);
    }
}
