package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.PagePlanningProductionOrderPendingDTO;
import cn.bctools.aps.vo.DetailProductionOrderVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * @author jvs
 * 生产订单排产操作聚合服务
 */
public interface PlanningProductionOrderFacadeService {

    /**
     * 分页查询待排产生产订单
     *
     * @param pageQuery 分页条件
     * @return 分页结果
     */
    Page<DetailProductionOrderVO> pagePendingProductionOrder(PagePlanningProductionOrderPendingDTO pageQuery);
}
