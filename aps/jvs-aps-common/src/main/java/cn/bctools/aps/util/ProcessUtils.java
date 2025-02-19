package cn.bctools.aps.util;

import cn.bctools.aps.entity.MaterialPO;
import cn.bctools.aps.entity.ProductionResourcePO;
import cn.bctools.aps.entity.dto.ProcessUseAuxiliaryResourcesDTO;
import cn.bctools.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.bctools.aps.entity.dto.ProcessUseMaterialsDTO;
import cn.bctools.aps.vo.DetailProcessVO;
import cn.bctools.common.utils.ObjectNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author jvs
 * 工艺工序工具
 */
public class ProcessUtils {
    private ProcessUtils() {
    }

    /**
     * 提取工序主资源id集合
     *
     * @param useMainResources 工序主资源配置
     * @return 工序主资源id集合
     */
    public static List<String> extractProcessMainResourceIds(List<ProcessUseMainResourcesDTO> useMainResources) {
        return Optional.ofNullable(useMainResources)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(ProcessUseMainResourcesDTO::getId)
                .toList();
    }

    /**
     * 提取工序辅资源id集合
     *
     * @param useAuxiliaryResources 工序辅资源配置
     * @return 工序辅资源id集合
     */
    public static List<String> extractAuxiliaryResourceIds(List<ProcessUseAuxiliaryResourcesDTO> useAuxiliaryResources) {
        return Optional.ofNullable(useAuxiliaryResources)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(ProcessUseAuxiliaryResourcesDTO::getId)
                .toList();
    }


    /**
     * 提取物料id集合
     *
     * @param useMaterials 工序物料配置
     * @return 物料id集合
     */
    public static List<String> extractMaterialIds(List<ProcessUseMaterialsDTO> useMaterials) {
        return Optional.ofNullable(useMaterials)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(ProcessUseMaterialsDTO::getId)
                .toList();
    }

    /**
     * 填充工序信息
     *
     * @param process     工序
     * @param resourceMap 资源集合
     * @param materialMap 物料集合
     */
    public static void fillProcessInfo(DetailProcessVO process, Map<String, ProductionResourcePO> resourceMap, Map<String, MaterialPO> materialMap) {
        // 填充主资源信息
        Optional.ofNullable(process.getUseMainResources())
                .orElseGet(Collections::emptyList)
                .forEach(resource -> {
                    ProductionResourcePO productionResource = resourceMap.get(resource.getId());
                    if (ObjectNull.isNotNull(productionResource)) {
                        resource.setCode(productionResource.getCode())
                                .setName(productionResource.getName());
                    }
                });

        // 填充辅资源信息
        Optional.ofNullable(process.getUseAuxiliaryResources())
                .orElseGet(Collections::emptyList)
                .forEach(resource -> {
                    ProductionResourcePO productionResource = resourceMap.get(resource.getId());
                    if (ObjectNull.isNotNull(productionResource)) {
                        resource.setCode(productionResource.getCode())
                                .setName(productionResource.getName());
                    }
                });

        // 填充物料信息
        Optional.ofNullable(process.getUseMaterials())
                .orElseGet(Collections::emptyList)
                .forEach(m -> {
                    MaterialPO material = materialMap.get(m.getId());
                    if (ObjectNull.isNotNull(material)) {
                        m.setCode(material.getCode()).setName(material.getName());
                    }
                });
    }
}
