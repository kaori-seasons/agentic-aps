package cn.bctools.aps.util;

import cn.bctools.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.bctools.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.bctools.aps.entity.enums.ProcessRelationshipEnum;
import cn.bctools.aps.graph.Graph;
import cn.bctools.aps.graph.GraphUtils;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 工艺路线工具类
 */
public class ProcessRouteDesignUtils {
    private ProcessRouteDesignUtils() {
    }

    /**
     * 校验工艺路线设计
     *
     * @param routeDesign 工艺路线设计
     */
    public static void validateDesign(Graph<ProcessRouteNodePropertiesDTO> routeDesign) {
        if (ObjectNull.isNull(routeDesign) || ObjectNull.isNull(routeDesign.getNodes())) {
            throw new BusinessException("未设计工艺路线");
        }
        // 工序编码不能重复
        if (hasDuplicateProcessCode(routeDesign)) {
            throw new BusinessException("工序编码不能重复");
        }
        // 检测是否有多个节点无出度
        if (GraphUtils.checkMultipleNodeNoOutDegree(routeDesign)) {
            throw new BusinessException("只能有一个最终节点");
        }
        // 检测是否有环
        if (GraphUtils.checkHasCycle(routeDesign)) {
            throw new BusinessException("不能有环路");
        }
    }

    /**
     * 校验工艺路线设计是否可启用
     *
     * @param routeDesign 工艺路线设计
     */
    public static void validateDesignCanEnabled(Graph<ProcessRouteNodePropertiesDTO> routeDesign) {
        if (ObjectNull.isNull(routeDesign) || ObjectNull.isNull(routeDesign.getNodes())) {
            throw new BusinessException("未设计工艺路线");
        }
        Set<String> errorNodes = new HashSet<>();
        routeDesign.getNodes().forEach(node -> {
            ProcessRouteNodePropertiesDTO process = node.getData();
            if (ProcessRelationshipEnum.EE.equals(process.getProcessRelationship()) && ObjectNull.isNull(process.getBufferTime())) {
                throw new BusinessException("工序关系为EE时必须填写缓冲时长");
            }
            // 校验主资源配置
            List<ProcessUseMainResourcesDTO> useMainResources = process.getUseMainResources();
            if (ObjectNull.isNull(useMainResources)) {
                errorNodes.add(process.getName());
            }
            boolean emptyThroughput = useMainResources.stream().anyMatch(resource -> ObjectNull.isNull(resource.getThroughput()));
            if (emptyThroughput) {
                errorNodes.add(process.getName());
            }
        });
        if (ObjectNull.isNotNull(errorNodes)) {
            throw new BusinessException("请完善主资源及主资源产能配置", String.join(",", errorNodes));
        }
    }


    /**
     * 工艺路线中是否存在编码相同的工序
     *
     * @param routeDesign 工艺路线
     * @return true-存在编码相同的工序，false-不存在编码相同的工序
     */
    private static boolean hasDuplicateProcessCode(Graph<ProcessRouteNodePropertiesDTO> routeDesign) {
        Set<String> codes = routeDesign.getNodes().stream()
                .map(node -> node.getData().getCode())
                .collect(Collectors.toSet());
        return codes.size() != routeDesign.getNodes().size();
    }
}
