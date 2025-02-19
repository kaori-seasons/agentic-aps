package cn.bctools.aps.controller;


import cn.bctools.aps.dto.PageIncomingMaterialOrderDTO;
import cn.bctools.aps.dto.SaveIncomingMaterialOrderDTO;
import cn.bctools.aps.dto.UpdateIncomingMaterialOrderDTO;
import cn.bctools.aps.service.IncomingMaterialOrderService;
import cn.bctools.aps.service.facade.IncomingMaterialOrderFacadeService;
import cn.bctools.aps.vo.DetailIncomingMaterialOrderVO;
import cn.bctools.common.utils.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author jvs
 */
@Api(tags = "[基础数据]来料订单管理")
@RestController
@RequestMapping("/incoming-material-order")
@AllArgsConstructor
public class IncomingMaterialOrderController {
    private final IncomingMaterialOrderService incomingMaterialOrderService;
    private final IncomingMaterialOrderFacadeService incomingMaterialOrderFacadeService;

    @ApiOperation("新增来料订单")
    @PostMapping
    public R<String> save(@Validated @RequestBody SaveIncomingMaterialOrderDTO saveIncomingMaterialOrder) {
        incomingMaterialOrderFacadeService.save(saveIncomingMaterialOrder);
        return R.ok();
    }

    @ApiOperation("修改来料订单")
    @PutMapping
    public R<String> update(@Validated @RequestBody UpdateIncomingMaterialOrderDTO updateIncomingMaterialOrder) {
        incomingMaterialOrderFacadeService.update(updateIncomingMaterialOrder);
        return R.ok();
    }

    @ApiOperation("删除来料订单")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "来料订单id", required = true)
    })
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable String id) {
        incomingMaterialOrderService.removeById(id);
        return R.ok();
    }

    @ApiOperation("分页查询来料订单")
    @GetMapping("/page")
    public R<Page<DetailIncomingMaterialOrderVO>> page(PageIncomingMaterialOrderDTO pageQuery) {
        return R.ok(incomingMaterialOrderFacadeService.page(pageQuery));
    }

    @ApiOperation("来料订单详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "来料订单id", required = true)
    })
    @GetMapping("/{id}")
    public R<DetailIncomingMaterialOrderVO> detail(@PathVariable String id) {
        return R.ok(incomingMaterialOrderFacadeService.getDetail(id));
    }

}
