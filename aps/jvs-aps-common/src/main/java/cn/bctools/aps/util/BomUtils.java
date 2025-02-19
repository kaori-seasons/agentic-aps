package cn.bctools.aps.util;

import cn.bctools.aps.entity.ManufactureBomPO;
import cn.bctools.aps.entity.dto.BomMaterialDTO;
import cn.bctools.aps.tree.Tree;
import cn.bctools.aps.vo.MaterialBomVO;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;

import java.util.*;
import java.util.function.Function;

/**
 * @author jvs
 * BOM工具
 */
public class BomUtils {

    private BomUtils() {
    }

    /**
     * 检测是否有环
     *
     * @param bom        BOM
     * @param allBomList 所有BOM
     */
    public static void checkHasCycle(ManufactureBomPO bom, List<ManufactureBomPO> allBomList) {
        // 获取指定BOM所有下级BOM
        List<ManufactureBomPO> bomList = getAllChildren(bom, allBomList);
        bomList.add(bom);

        // 深度优先遍历校验是否有环。true-有环，false-无环
        ArrayDeque<String> lineNodeStack = new ArrayDeque<>();
        lineNodeStack.push(bom.getMaterialId());
        dfs(bom, bomList, lineNodeStack);
        if(lineNodeStack.size() == lineNodeStack.stream().distinct().count()) {
            return;
        }
        throw new BusinessException("父子件物料不能嵌套");
    }

    /**
     * 获取指定BOM所有下级BOM
     *
     * @param bom        BOM
     * @param allBomList 所有BOM
     * @return 指定BOM的所有下级BOM
     */
    public static List<ManufactureBomPO> getAllChildren(ManufactureBomPO bom, List<ManufactureBomPO> allBomList) {
        allBomList.removeIf(b -> b.getMaterialId().equals(bom.getMaterialId()));
        List<ManufactureBomPO> bomList = new ArrayList<>();
        findAllChildren(bom, allBomList, bomList);
        return bomList;
    }


    /**
     * BOM树转字符串（树结构字符串）
     *
     * @param tree 树
     * @return 树结构字符串
     */
    public static String bomTreeToFormatString(Tree<MaterialBomVO> tree) {
        Function<Tree<MaterialBomVO>, String> getNameFun = t -> t.getNode().getMaterialName();
        Function<Tree<MaterialBomVO>, String> getIdFun = t -> t.getNode().getMaterialId();
        return tree.treeToString(getNameFun, getIdFun);
    }

    /**
     * 获取指定BOM所有下级BOM
     *
     * @param bom             bom
     * @param bomList      所有BOM
     * @param allChildBomList 指定BOM的所有子BOM集合
     */
    private static void findAllChildren(ManufactureBomPO bom, List<ManufactureBomPO> bomList, List<ManufactureBomPO> allChildBomList) {
        List<ManufactureBomPO> childBomList = bomList.stream()
                .filter(b -> ObjectNull.isNotNull(bom.getChildMaterials()))
                .filter(b ->
                        bom.getChildMaterials()
                                .stream()
                                .anyMatch(childMaterial -> childMaterial.getMaterialId().equals(b.getMaterialId())))
                .toList();
        if (ObjectNull.isNull(childBomList)) {
            return;
        }
        allChildBomList.addAll(childBomList);
        childBomList.forEach(childBom -> findAllChildren(childBom, bomList, allChildBomList));
    }


    /**
     * 深度优先遍历
     *
     * @param currentBom    BOM
     * @param bomList    所有BOM
     * @param lineNodeStack 当前路线栈
     */
    private static void dfs(ManufactureBomPO currentBom, List<ManufactureBomPO> bomList, ArrayDeque<String> lineNodeStack) {
        if (ObjectNull.isNull(currentBom.getChildMaterials())) {
            return;
        }
        for (BomMaterialDTO childMaterial : currentBom.getChildMaterials()) {
            String materialId = childMaterial.getMaterialId();
            if (lineNodeStack.contains(materialId)) {
                lineNodeStack.push(childMaterial.getMaterialId());
                return;
            }
            lineNodeStack.push(childMaterial.getMaterialId());
            List<ManufactureBomPO> childBomList = bomList.stream()
                    .filter(b -> b.getMaterialId().equals(childMaterial.getMaterialId()))
                    .toList();
            for (ManufactureBomPO childBom : childBomList) {
                dfs(childBom, bomList, lineNodeStack);
            }
            if (ObjectNull.isNull(childBomList)) {
                lineNodeStack.pop();
            }
        }
    }
}
