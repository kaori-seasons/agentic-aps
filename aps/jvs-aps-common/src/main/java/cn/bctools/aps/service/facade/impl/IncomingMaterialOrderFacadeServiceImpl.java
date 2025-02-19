package cn.bctools.aps.service.facade.impl;

import cn.bctools.aps.dto.PageIncomingMaterialOrderDTO;
import cn.bctools.aps.dto.SaveIncomingMaterialOrderDTO;
import cn.bctools.aps.dto.UpdateIncomingMaterialOrderDTO;
import cn.bctools.aps.entity.IncomingMaterialOrderPO;
import cn.bctools.aps.service.IncomingMaterialOrderService;
import cn.bctools.aps.service.MaterialService;
import cn.bctools.aps.service.facade.IncomingMaterialOrderFacadeService;
import cn.bctools.aps.util.BigDecimalUtils;
import cn.bctools.aps.vo.DetailIncomingMaterialOrderVO;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author jvs
 */
@Service
@AllArgsConstructor
public class IncomingMaterialOrderFacadeServiceImpl implements IncomingMaterialOrderFacadeService {

    private final MaterialService materialService;
    private final IncomingMaterialOrderService incomingMaterialOrderService;


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void save(SaveIncomingMaterialOrderDTO saveIncomingMaterialOrder) {
        IncomingMaterialOrderPO incomingMaterialOrder = BeanCopyUtil.copy(saveIncomingMaterialOrder, IncomingMaterialOrderPO.class);
        checkCanSave(incomingMaterialOrder);
        incomingMaterialOrderService.save(incomingMaterialOrder);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void update(UpdateIncomingMaterialOrderDTO updateIncomingMaterialOrder) {
        IncomingMaterialOrderPO incomingMaterialOrder = BeanCopyUtil.copy(updateIncomingMaterialOrder, IncomingMaterialOrderPO.class);
        checkCanSave(incomingMaterialOrder);
        incomingMaterialOrderService.updateById(incomingMaterialOrder);
    }

    @Override
    public Page<DetailIncomingMaterialOrderVO> page(PageIncomingMaterialOrderDTO pageQuery) {
        LambdaQueryWrapper<IncomingMaterialOrderPO> wrapper = Wrappers.<IncomingMaterialOrderPO>lambdaQuery()
                .eq(ObjectNull.isNotNull(pageQuery.getOrderStatus()), IncomingMaterialOrderPO::getOrderStatus, pageQuery.getOrderStatus())
                .like(ObjectNull.isNotNull(pageQuery.getCode()), IncomingMaterialOrderPO::getCode, pageQuery.getCode())
                .like(ObjectNull.isNotNull(pageQuery.getMaterialCode()), IncomingMaterialOrderPO::getMaterialCode, pageQuery.getMaterialCode())
                .orderByDesc(IncomingMaterialOrderPO::getCreateTime);
        Page<IncomingMaterialOrderPO> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        incomingMaterialOrderService.page(page, wrapper);
        List<DetailIncomingMaterialOrderVO> voList = page.getRecords().stream()
                .map(order -> {
                    DetailIncomingMaterialOrderVO vo = BeanCopyUtil.copy(order, DetailIncomingMaterialOrderVO.class);
                    // 去除多余的0
                    vo.setQuantity(BigDecimalUtils.stripTrailingZeros(order.getQuantity()));
                    return vo;
                }).toList();
        Page<DetailIncomingMaterialOrderVO> pageVoList = new Page<>(page.getCurrent(), page.getSize());
        pageVoList
                .setRecords(voList)
                .setTotal(page.getTotal());
        return pageVoList;
    }

    @Override
    public DetailIncomingMaterialOrderVO getDetail(String id) {
        IncomingMaterialOrderPO incomingMaterialOrder = incomingMaterialOrderService.getById(id);
        if (ObjectNull.isNull(incomingMaterialOrder)) {
            return null;
        }
        DetailIncomingMaterialOrderVO vo = BeanCopyUtil.copy(incomingMaterialOrder, DetailIncomingMaterialOrderVO.class);
        // 去除多余的0
        vo.setQuantity(BigDecimalUtils.stripTrailingZeros(incomingMaterialOrder.getQuantity()));
        return vo;
    }

    /**
     * 校验是否可以保存（新增/修改）
     *
     * @param incomingMaterialOrder 来料订单
     */
    public void checkCanSave(IncomingMaterialOrderPO incomingMaterialOrder) {
        // 校验订单号+物料编码唯一
        checkExistsIncomingOrder(incomingMaterialOrder.getId(), incomingMaterialOrder.getCode(), incomingMaterialOrder.getMaterialCode());
        // 校验物料编码是否存在
        checkExistsMaterialCode(incomingMaterialOrder.getMaterialCode());
    }

    /**
     * 校验订单号+物料编码唯一
     *
     * @param id   来料订单id
     * @param code 来料订单号
     * @param materialCode 物料编码
     */
    private void checkExistsIncomingOrder(String id, String code, String materialCode) {
        IncomingMaterialOrderPO existsIncomingOrder = incomingMaterialOrderService.getOne(Wrappers.<IncomingMaterialOrderPO>lambdaQuery()
                .select(IncomingMaterialOrderPO::getId)
                .eq(IncomingMaterialOrderPO::getCode, code)
                .eq(IncomingMaterialOrderPO::getMaterialCode, materialCode));
        if (ObjectNull.isNull(existsIncomingOrder)) {
            return;
        }
        if (existsIncomingOrder.getId().equals(id)) {
            return;
        }
        throw new BusinessException("订单号与物料编码重复");
    }

    /**
     * 校验物料编码是否存在
     *
     * @param materialCode 物料编码
     */
    private void checkExistsMaterialCode(String materialCode) {
        if (materialService.existsCode(materialCode)) {
            return;
        }
        throw new BusinessException("物料编码不存在");
    }
}
