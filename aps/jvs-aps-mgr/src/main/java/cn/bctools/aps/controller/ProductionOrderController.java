package cn.bctools.aps.controller;


import cn.bctools.aps.dto.PageProductionOrderDTO;
import cn.bctools.aps.dto.SaveProductionOrderDTO;
import cn.bctools.aps.dto.UpdateOrderSortDTO;
import cn.bctools.aps.dto.UpdateProductionOrderDTO;
import cn.bctools.aps.service.ProductionOrderService;
import cn.bctools.aps.service.facade.ProductionOrderFacadeService;
import cn.bctools.aps.vo.DetailProductionOrderVO;
import cn.bctools.common.utils.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author jvs
 */
@Api(tags = "[基础数据]生产订单管理")
@RestController
@RequestMapping("/production-order")
@AllArgsConstructor
public class ProductionOrderController {
    private final ProductionOrderService service;
    private final ProductionOrderFacadeService productionOrderFacadeService;

    @ApiOperation("新增生产订单")
    @PostMapping
    public R<String> save(@Validated @RequestBody SaveProductionOrderDTO productionOrder) {
        productionOrderFacadeService.save(productionOrder);
        return R.ok();
    }

    @ApiOperation("修改生产订单")
    @PutMapping
    public R<String> update(@Validated @RequestBody UpdateProductionOrderDTO productionOrder) {
        productionOrderFacadeService.update(productionOrder);
        return R.ok();
    }

    @ApiOperation("修改生产订单排产状态")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "生产订单id", required = true),
            @ApiImplicitParam(name = "canSchedule", value = "true-参与排产，false-不参与排产", required = true)
    })
    @PutMapping("/change/{id}/can/schedule")
    public R<String> updateCanSchedule(@PathVariable String id, Boolean canSchedule) {
        service.updateCanSchedule(id, canSchedule);
        return R.ok();
    }

    @ApiOperation("删除生产订单")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "生产订单id", required = true)
    })
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable String id) {
        service.delete(id);
        return R.ok();
    }

    @ApiOperation("分页查询生产订单")
    @GetMapping("/page")
    public R<Page<DetailProductionOrderVO>> page(PageProductionOrderDTO pageQuery) {
        return R.ok(service.page(pageQuery));
    }

    @ApiOperation("生产订单详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "生产订单id", required = true)
    })
    @GetMapping("/{id}")
    public R<DetailProductionOrderVO> detail(@PathVariable String id) {
        return R.ok(service.getDetail(id));
    }

    @ApiOperation("修改订单排序")
    @PutMapping("/sort")
    public R<String> updateOrderSort(@Validated @RequestBody UpdateOrderSortDTO updateOrderSort) {
        service.updateOrderSort(updateOrderSort);
        return R.ok();
    }

    @ApiOperation("查询补充订单")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "生产订单id", required = true)
    })
    @GetMapping("/supplement/list/{id}")
    public R<List<DetailProductionOrderVO>> listSupplementOrder(@PathVariable String id) {
        return R.ok(service.listSupplementOrder(id));
    }
}
