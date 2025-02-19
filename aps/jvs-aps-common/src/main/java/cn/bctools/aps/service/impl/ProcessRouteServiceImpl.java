package cn.bctools.aps.service.impl;

import cn.bctools.aps.entity.ProcessRoutePO;
import cn.bctools.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.bctools.aps.graph.Graph;
import cn.bctools.aps.mapper.ProcessRouteMapper;
import cn.bctools.aps.service.ProcessRouteService;
import cn.bctools.aps.util.ProcessRouteDesignUtils;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author jvs
 * 工艺路线
 */
@Service
@AllArgsConstructor
public class ProcessRouteServiceImpl extends ServiceImpl<ProcessRouteMapper, ProcessRoutePO> implements ProcessRouteService {

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void enableProcessRoute(String id) {
        ProcessRoutePO processRoute = getById(id);
        if (ObjectNull.isNull(processRoute)) {
            throw new BusinessException("工艺路线不存在");
        }
        // 校验路线配置是否满足启用要求
        Graph<ProcessRouteNodePropertiesDTO> routeDesign = processRoute.getRouteDesign();
        ProcessRouteDesignUtils.validateDesignCanEnabled(routeDesign);
        // 修改启用状态
        processRoute
                .setEnabled(true);
        updateById(processRoute);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void disableProcessRoute(String id) {
        ProcessRoutePO processRoute = new ProcessRoutePO()
                .setId(id)
                .setEnabled(false);
        updateById(processRoute);
    }
}
