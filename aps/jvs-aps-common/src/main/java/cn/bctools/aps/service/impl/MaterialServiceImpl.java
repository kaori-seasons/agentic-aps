package cn.bctools.aps.service.impl;

import cn.bctools.aps.dto.PageMaterialDTO;
import cn.bctools.aps.dto.SaveMaterialDTO;
import cn.bctools.aps.dto.UpdateMaterialDTO;
import cn.bctools.aps.entity.MaterialPO;
import cn.bctools.aps.mapper.MaterialMapper;
import cn.bctools.aps.service.MaterialService;
import cn.bctools.aps.util.BigDecimalUtils;
import cn.bctools.aps.vo.DetailMaterialVO;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 物料
 */
@Service
public class MaterialServiceImpl extends ServiceImpl<MaterialMapper, MaterialPO> implements MaterialService {

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void save(SaveMaterialDTO saveMaterial) {
        MaterialPO material = BeanCopyUtil.copy(saveMaterial, MaterialPO.class);
        checkCanSave(material);
        setDefaultValue(material);
        save(material);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void update(UpdateMaterialDTO updateMaterial) {
        MaterialPO material = BeanCopyUtil.copy(updateMaterial, MaterialPO.class);
        checkCanSave(material);
        setDefaultValue(material);
        updateById(material);
    }

    @Override
    public boolean existsCode(String materialCode) {
        return baseMapper.exists(Wrappers.<MaterialPO>lambdaQuery().eq(MaterialPO::getCode, materialCode));
    }

    @Override
    public List<MaterialPO> listByCodes(List<String> materialCodes) {
        if (ObjectNull.isNull(materialCodes)) {
            throw new BusinessException("参数错误");
        }
        return list(Wrappers.<MaterialPO>lambdaQuery().in(MaterialPO::getCode, materialCodes));
    }

    @Override
    public Page<DetailMaterialVO> page(PageMaterialDTO pageQuery) {
        LambdaQueryWrapper<MaterialPO> wrapper = Wrappers.<MaterialPO>lambdaQuery()
                .eq(ObjectNull.isNotNull(pageQuery.getType()), MaterialPO::getType, pageQuery.getType())
                .eq(ObjectNull.isNotNull(pageQuery.getSource()), MaterialPO::getSource, pageQuery.getSource())
                .like(ObjectNull.isNotNull(pageQuery.getKeywords()), MaterialPO::getCode, pageQuery.getKeywords())
                .like(ObjectNull.isNotNull(pageQuery.getKeywords()), MaterialPO::getName, pageQuery.getKeywords())
                .like(ObjectNull.isNotNull(pageQuery.getCode()), MaterialPO::getCode, pageQuery.getCode())
                .like(ObjectNull.isNotNull(pageQuery.getName()), MaterialPO::getName, pageQuery.getName())
                .orderByDesc(MaterialPO::getCreateTime);
        Page<MaterialPO> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        page(page, wrapper);
        List<DetailMaterialVO> voList = page.getRecords().stream()
                .map(material -> {
                    DetailMaterialVO vo = BeanCopyUtil.copy(material, DetailMaterialVO.class);
                    // 去除多余的0
                    vo.setQuantity(BigDecimalUtils.stripTrailingZeros(material.getQuantity()));
                    vo.setSafetyStock(BigDecimalUtils.stripTrailingZeros(material.getSafetyStock()));
                    return vo;
                }).toList();
        Page<DetailMaterialVO> pageVoList = new Page<>(page.getCurrent(), page.getSize());
        pageVoList
                .setRecords(voList)
                .setTotal(page.getTotal());
        return pageVoList;
    }

    @Override
    public DetailMaterialVO getDetail(String id) {
        MaterialPO material = getById(id);
        if (ObjectNull.isNull(material)) {
            return null;
        }
        DetailMaterialVO vo = BeanCopyUtil.copy(material, DetailMaterialVO.class);
        // 去除多余的0
        vo.setQuantity(BigDecimalUtils.stripTrailingZeros(material.getQuantity()));
        vo.setSafetyStock(BigDecimalUtils.stripTrailingZeros(material.getSafetyStock()));
        return vo;
    }

    @Override
    public Map<String, MaterialPO> listByIdsToMap(Collection<? extends Serializable> idList) {
        if (ObjectNull.isNull(idList)) {
            return Collections.emptyMap();
        }
        return listByIds(idList)
                .stream()
                .collect(Collectors.toMap(MaterialPO::getId, Function.identity()));
    }

    /**
     * 设置默认值
     *
     * @param material 物料
     */
    private void setDefaultValue(MaterialPO material) {
        // 设置默认值
        if (ObjectNull.isNull(material.getQuantity())) {
            material.setQuantity(BigDecimal.ZERO);
        }
        if (ObjectNull.isNull(material.getSafetyStock())) {
            material.setSafetyStock(BigDecimal.ZERO);
        }
    }

    /**
     * 校验是否可以保存（新增/修改）
     *
     * @param material 物料
     */
    private void checkCanSave(MaterialPO material) {
        MaterialPO existsMaterial = getOne(Wrappers.<MaterialPO>lambdaQuery()
                .select(MaterialPO::getId)
                .eq(MaterialPO::getCode, material.getCode()));
        if (ObjectNull.isNull(existsMaterial)) {
            return;
        }
        if (existsMaterial.getId().equals(material.getId())) {
            return;
        }
        throw new BusinessException("物料编码已存在");
    }
}
