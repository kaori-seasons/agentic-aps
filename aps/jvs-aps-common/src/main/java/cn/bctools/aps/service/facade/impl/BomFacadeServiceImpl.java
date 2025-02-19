package cn.bctools.aps.service.facade.impl;

import cn.bctools.aps.dto.PageManufactureBomDTO;
import cn.bctools.aps.dto.SaveManufactureBomDTO;
import cn.bctools.aps.dto.UpdateManufactureBomDTO;
import cn.bctools.aps.entity.ManufactureBomPO;
import cn.bctools.aps.entity.MaterialPO;
import cn.bctools.aps.entity.dto.BomMaterialDTO;
import cn.bctools.aps.service.ManufactureBomService;
import cn.bctools.aps.service.MaterialService;
import cn.bctools.aps.service.facade.BomFacadeService;
import cn.bctools.aps.tree.Tree;
import cn.bctools.aps.util.BomUtils;
import cn.bctools.aps.vo.DetailManufactureBomVO;
import cn.bctools.aps.vo.MaterialBomVO;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author jvs
 * BOM聚合服务
 */
@Slf4j
@Service
@AllArgsConstructor
public class BomFacadeServiceImpl implements BomFacadeService {

    private final MaterialService materialService;
    private final ManufactureBomService manufactureBomService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveManufactureBom(SaveManufactureBomDTO manufactureBom) {
        ManufactureBomPO bom = BeanCopyUtil.copy(manufactureBom, ManufactureBomPO.class);
        checkCanSave(bom);
        manufactureBomService.save(bom);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateManufactureBom(UpdateManufactureBomDTO manufactureBom) {
        ManufactureBomPO bom = BeanCopyUtil.copy(manufactureBom, ManufactureBomPO.class);
        checkCanSave(bom);
        manufactureBomService.updateById(bom);
    }

    @Override
    public Page<DetailManufactureBomVO> pageManufactureBom(PageManufactureBomDTO pageQuery) {
        // 分页查询
        List<String> materialIds = null;
        if (ObjectNull.isNotNull(pageQuery.getKeywords())) {
            materialIds = materialService.list(Wrappers.<MaterialPO>lambdaQuery()
                            .like(ObjectNull.isNotNull(pageQuery.getKeywords()), MaterialPO::getName, pageQuery.getKeywords())
                            .like(ObjectNull.isNotNull(pageQuery.getKeywords()), MaterialPO::getCode, pageQuery.getKeywords()))
                    .stream()
                    .map(MaterialPO::getId)
                    .toList();
        }
        LambdaQueryWrapper<ManufactureBomPO> wrapper = Wrappers.<ManufactureBomPO>lambdaQuery()
                .in(ObjectNull.isNotNull(materialIds), ManufactureBomPO::getMaterialId, materialIds)
                .orderByDesc(ManufactureBomPO::getCreateTime);
        Page<ManufactureBomPO> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        manufactureBomService.page(page, wrapper);

        Page<DetailManufactureBomVO> pageVoList = new Page<>(page.getCurrent(), page.getSize());
        if (ObjectNull.isNull(page.getRecords())) {
            return pageVoList;
        }
        // 封装响应
        List<DetailManufactureBomVO> voList = BeanCopyUtil.copys(page.getRecords(), DetailManufactureBomVO.class);
        List<String> parentMaterialIds = voList.stream().map(DetailManufactureBomVO::getMaterialId).toList();
        Map<String, MaterialPO> materialMap = materialService.listByIds(parentMaterialIds).stream()
                .collect(Collectors.toMap(MaterialPO::getId, Function.identity()));
        voList.forEach(vo -> {
            MaterialPO parentMaterial = materialMap.get(vo.getMaterialId());
            if (ObjectNull.isNotNull(parentMaterial)) {
                vo.setMaterialCode(parentMaterial.getCode());
                vo.setMaterialName(parentMaterial.getName());
                vo.setChildMaterials(null);
            }
        });
        pageVoList
                .setRecords(voList)
                .setTotal(page.getTotal());
        return pageVoList;
    }

    @Override
    public DetailManufactureBomVO getDetail(String id) {
        ManufactureBomPO bom = manufactureBomService.getById(id);
        if (ObjectNull.isNull(bom)) {
            return null;
        }
        // 封装响应
        List<String> materialIds = bom.getChildMaterials().stream()
                .map(BomMaterialDTO::getMaterialId)
                .collect(Collectors.toList());
        materialIds.add(bom.getMaterialId());
        Map<String, MaterialPO> materialMap = materialService.listByIds(materialIds).stream()
                .collect(Collectors.toMap(MaterialPO::getId, Function.identity()));
        DetailManufactureBomVO vo = BeanCopyUtil.copy(bom, DetailManufactureBomVO.class);
        MaterialPO parentMaterial = materialMap.get(vo.getMaterialId());
        if (ObjectNull.isNotNull(parentMaterial)) {
            vo.setMaterialCode(parentMaterial.getCode());
            vo.setMaterialName(parentMaterial.getName());
        }
        vo.getChildMaterials().forEach(childMaterial -> {
            MaterialPO material = materialMap.get(childMaterial.getMaterialId());
            if (ObjectNull.isNotNull(material)) {
                childMaterial.setMaterialCode(material.getCode());
                childMaterial.setMaterialName(material.getName());
                childMaterial.setUnit(material.getUnit());
            }
        });

        return vo;
    }

    @Override
    public Tree<MaterialBomVO> treeBom(String id) {
        List<ManufactureBomPO> allBom = manufactureBomService.list();
        Optional<ManufactureBomPO> rootBomOptional = allBom.stream().filter(bom -> bom.getId().equals(id)).findAny();
        if (rootBomOptional.isEmpty()) {
            return new Tree<>();
        }
        ManufactureBomPO rootBom = rootBomOptional.get();
        Map<String, ManufactureBomPO> childBomMap = BomUtils.getAllChildren(rootBom, allBom)
                .stream()
                .collect(Collectors.toMap(ManufactureBomPO::getMaterialId, Function.identity(), (e1, e2) -> e1));
        childBomMap.put(rootBom.getMaterialId(), rootBom);
        Set<String> materialIds = childBomMap.values().stream()
                .flatMap(bom -> {
                    List<String> bomMaterialIds = bom.getChildMaterials().stream()
                            .map(BomMaterialDTO::getMaterialId)
                            .collect(Collectors.toList());
                    bomMaterialIds.add(bom.getMaterialId());
                    return bomMaterialIds.stream();
                })
                .collect(Collectors.toSet());
        Map<String, MaterialPO> materialMap = materialService.listByIdsToMap(materialIds);
        return bomToTree(rootBom, childBomMap, materialMap);
    }

    /**
     * 校验是否可以保存（新增/修改）
     *
     * @param bom 数据
     */
    private void checkCanSave(ManufactureBomPO bom) {
        // 必须配置子件
        List<BomMaterialDTO> childMaterials = bom.getChildMaterials();
        if (ObjectNull.isNull(childMaterials)) {
            throw new BusinessException("未配置子件物料");
        }
        // 子件不能重复
        Set<String> childMaterialIds = childMaterials.stream()
                .map(BomMaterialDTO::getMaterialId)
                .collect(Collectors.toSet());
        if (childMaterialIds.size() != childMaterials.size()) {
            throw new BusinessException("子件物料不能重复");
        }
        // 子件不能与父件相同
        if (childMaterialIds.contains(bom.getMaterialId())) {
            throw new BusinessException("子件物料不能与父件物料相同");
        }

        // 不能存在父子物料嵌套
        List<ManufactureBomPO> allBom = manufactureBomService.list();
        BomUtils.checkHasCycle(bom, allBom);

        // 物料是否存在
        checkExistsMaterial(bom.getMaterialId(), childMaterialIds);

        // 父件物料BOM唯一性校验
        ManufactureBomPO existsBom = manufactureBomService.getOne(Wrappers.<ManufactureBomPO>lambdaQuery()
                .select(ManufactureBomPO::getId, ManufactureBomPO::getMaterialId)
                .eq(ObjectNull.isNull(bom.getId()), ManufactureBomPO::getMaterialId, bom.getMaterialId())
                .eq(ObjectNull.isNotNull(bom.getId()), ManufactureBomPO::getId, bom.getId()));
        if (ObjectNull.isNull(existsBom)) {
            return;
        }
        if (!existsBom.getMaterialId().equals(bom.getMaterialId())) {
            throw new BusinessException("不能更换父件物料");
        }
        if (ObjectNull.isNotNull(bom.getId())) {
            return;
        }
        throw new BusinessException("BOM已存在");
    }


    /**
     * 校验物料是否存在
     *
     * @param materialId       父件物料id
     * @param childMaterialIds 子件物料id集合
     */
    private void checkExistsMaterial(String materialId, Collection<String> childMaterialIds) {
        List<String> materialCodes = new ArrayList<>(childMaterialIds);
        materialCodes.add(materialId);
        if (materialCodes.size() == materialService.listByIds(materialCodes).size()) {
            return;
        }
        throw new BusinessException("物料不存在");
    }


    /**
     * BOM转树结构
     *
     * @param rootBom     根BOM
     * @param childBomMap 所有下级BOM
     * @param materialMap 相关物料
     * @return BOM树
     */
    public Tree<MaterialBomVO> bomToTree(ManufactureBomPO rootBom, Map<String, ManufactureBomPO> childBomMap, Map<String, MaterialPO> materialMap) {
        Tree<MaterialBomVO> tree = buildTreeNode(rootBom.getId(), rootBom.getMaterialId(), new BigDecimal("1"), materialMap, 1);
        if (ObjectNull.isNull(tree)) {
            return new Tree<>();
        }
        childToBomTree(tree, rootBom.getChildMaterials(), childBomMap, materialMap, 2);
        if (log.isDebugEnabled()) {
            log.debug("{} tree: \n{}", rootBom.getId(), BomUtils.bomTreeToFormatString(tree));
        }
        return tree;
    }

    /**
     * BOM子物料转BOM树
     *
     * @param parent         上级物料
     * @param childMaterials 子物料
     * @param childBomMap    所有下级物料
     * @param materialMap    相关物料
     * @param level          层级
     */
    private void childToBomTree(Tree<MaterialBomVO> parent, List<BomMaterialDTO> childMaterials,
                                Map<String, ManufactureBomPO> childBomMap, Map<String, MaterialPO> materialMap, Integer level) {
        childMaterials.forEach(childMaterial -> {
            ManufactureBomPO childBom = childBomMap.get(childMaterial.getMaterialId());
            String childBomId = Optional.ofNullable(childBom).map(ManufactureBomPO::getId).orElseGet(() -> null);
            Tree<MaterialBomVO> child = buildTreeNode(childBomId, childMaterial.getMaterialId(), childMaterial.getQuantity(), materialMap, level);
            if (ObjectNull.isNotNull(child)) {
                parent.addChildren(child);
                if (ObjectNull.isNotNull(childBom)) {
                    Integer childLevel = level + 1;
                    childToBomTree(child, childBom.getChildMaterials(), childBomMap, materialMap, childLevel);
                }
            }
        });
    }

    /**
     * 构造BOM树节点
     *
     * @param bomId       BOM id
     * @param materialId  物料id
     * @param quantity    数量
     * @param materialMap 物料
     * @param level       层级
     * @return 树节点
     */
    private Tree<MaterialBomVO> buildTreeNode(String bomId, String materialId, BigDecimal quantity, Map<String, MaterialPO> materialMap, Integer level) {
        MaterialPO material = materialMap.get(materialId);
        if (ObjectNull.isNull(material)) {
            return null;
        }
        MaterialBomVO node = new MaterialBomVO()
                .setId(bomId)
                .setMaterialId(materialId)
                .setMaterialCode(material.getCode())
                .setMaterialName(material.getName())
                .setUnit(material.getUnit())
                .setQuantity(quantity);
        Tree<MaterialBomVO> root = new Tree<>();
        root.setLevel(level);
        root.setNode(node);
        return root;
    }
}
