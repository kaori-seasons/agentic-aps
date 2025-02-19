package cn.bctools.aps.controller;

import cn.bctools.aps.dto.PagePlanningProductionOrderPendingDTO;
import cn.bctools.aps.service.facade.PlanningProductionOrderFacadeService;
import cn.bctools.aps.vo.DetailProductionOrderVO;
import cn.bctools.common.utils.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author jvs
 */
@Api(tags = "[生产计划]生产订单排产操作")
@RestController
@RequestMapping("/planning-production-order")
@AllArgsConstructor
public class PlanningProductionOrderController {

    private final PlanningProductionOrderFacadeService service;

    @ApiOperation(value = "分页查询待排订单")
    @GetMapping("/page/pending/orders")
    public R<Page<DetailProductionOrderVO>> pagePendingOrder(PagePlanningProductionOrderPendingDTO pageQuery) {
        return R.ok(service.pagePendingProductionOrder(pageQuery));
    }
}
