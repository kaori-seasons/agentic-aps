package cn.bctools.aps.service.impl;

import cn.bctools.aps.dto.PageProductionResourceDTO;
import cn.bctools.aps.dto.SaveProductionResourceDTO;
import cn.bctools.aps.dto.UpdateProductionResourceDTO;
import cn.bctools.aps.entity.ProductionResourcePO;
import cn.bctools.aps.entity.enums.ResourceStatusEnum;
import cn.bctools.aps.mapper.ProductionResourceMapper;
import cn.bctools.aps.service.ProductionResourceService;
import cn.bctools.aps.util.BigDecimalUtils;
import cn.bctools.aps.vo.DetailProductionResourceVO;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 资源
 */
@Service
public class ProductionResourceServiceImpl extends ServiceImpl<ProductionResourceMapper, ProductionResourcePO> implements ProductionResourceService {

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void save(SaveProductionResourceDTO saveProductionResource) {
        ProductionResourcePO productionResource = BeanCopyUtil.copy(saveProductionResource, ProductionResourcePO.class);
        checkCanSave(productionResource);
        setDefault(productionResource);
        save(productionResource);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void update(UpdateProductionResourceDTO updateProductionResource) {
        ProductionResourcePO productionResource = BeanCopyUtil.copy(updateProductionResource, ProductionResourcePO.class);
        checkCanSave(productionResource);
        setDefault(productionResource);
        updateById(productionResource);
    }

    @Override
    public void convertData(ProductionResourcePO resource) {
        // 去除多余的0
        if (ObjectNull.isNotNull(resource.getCapacity())) {
            resource.setCapacity(BigDecimalUtils.stripTrailingZeros(resource.getCapacity()));
        }
    }

    @Override
    public Map<String, ProductionResourcePO> listByIdsToMap(Collection<String> idList) {
        if (ObjectNull.isNull(idList)) {
            return Collections.emptyMap();
        }
        return listByIds(idList)
                .stream()
                .collect(Collectors.toMap(ProductionResourcePO::getId, Function.identity()));
    }

    @Override
    public List<ProductionResourcePO> listByCodes(Collection<String> resourceCodes) {
        if (ObjectNull.isNull(resourceCodes)) {
            throw new BusinessException("参数错误");
        }
        return list(Wrappers.<ProductionResourcePO>lambdaQuery().in(ProductionResourcePO::getCode, resourceCodes));
    }

    @Override
    public Page<DetailProductionResourceVO> page(PageProductionResourceDTO pageQuery) {
        LambdaQueryWrapper<ProductionResourcePO> wrapper = Wrappers.<ProductionResourcePO>lambdaQuery()
                .eq(ObjectNull.isNotNull(pageQuery.getResourceGroup()), ProductionResourcePO::getResourceGroup, pageQuery.getResourceGroup())
                .like(ObjectNull.isNotNull(pageQuery.getCode()), ProductionResourcePO::getCode, pageQuery.getCode())
                .like(ObjectNull.isNotNull(pageQuery.getName()), ProductionResourcePO::getName, pageQuery.getName())
                .orderByDesc(ProductionResourcePO::getCreateTime);
        Page<ProductionResourcePO> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        page(page, wrapper);
        List<DetailProductionResourceVO> voList = page.getRecords().stream().map(resource -> {
            convertData(resource);
            return BeanCopyUtil.copy(resource, DetailProductionResourceVO.class);
        }).toList();
        Page<DetailProductionResourceVO> pageVoList = new Page<>(page.getCurrent(), page.getSize());
        pageVoList
                .setRecords(voList)
                .setTotal(page.getTotal());
        return pageVoList;
    }

    @Override
    public DetailProductionResourceVO getDetail(String id) {
        ProductionResourcePO resource = getById(id);
        if (ObjectNull.isNull(resource)) {
            return null;
        }
        convertData(resource);
        return BeanCopyUtil.copy(resource, DetailProductionResourceVO.class);
    }

    @Override
    public List<String> listResourceGroups() {
        return list().stream()
                .map(ProductionResourcePO::getResourceGroup)
                .distinct()
                .sorted((o1, o2) -> {
                    if (ProductionResourceService.DEFAULT_RESOURCE_GROUP.equals(o1)) {
                        return -1;
                    } else if (ProductionResourceService.DEFAULT_RESOURCE_GROUP.equals(o2)) {
                        return 1;
                    }
                    return o1.compareTo(o2);
                })
                .toList();
    }

    /**
     * 设置默认值
     *
     * @param productionResource 生产资源
     */
    private void setDefault(ProductionResourcePO productionResource) {
        // 设置默认值
        if (ObjectNull.isNull(productionResource.getResourceGroup())) {
            productionResource.setResourceGroup(DEFAULT_RESOURCE_GROUP);
        }
        if (ObjectNull.isNull(productionResource.getResourceStatus())) {
            productionResource.setResourceStatus(ResourceStatusEnum.NORMAL);
        }
    }

    /**
     * 检查是否可保存
     *
     * @param productionResource 生产资源
     */
    private void checkCanSave(ProductionResourcePO productionResource) {
        ProductionResourcePO existsProductionResource = getOne(Wrappers.<ProductionResourcePO>lambdaQuery()
                .select(ProductionResourcePO::getId)
                .eq(ProductionResourcePO::getCode, productionResource.getCode()));
        if (ObjectNull.isNull(existsProductionResource)) {
            return;
        }
        if(existsProductionResource.getId().equals(productionResource.getId())) {
            return;
        }
        throw new BusinessException("资源编码已存在");
    }

}
