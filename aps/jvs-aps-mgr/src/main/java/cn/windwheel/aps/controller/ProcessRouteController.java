package cn.windwheel.aps.controller;


import cn.windwheel.aps.dto.SaveProcessRouteDTO;
import cn.windwheel.aps.dto.UpdateProcessRouteDTO;
import cn.windwheel.aps.service.ProcessRouteService;
import cn.windwheel.aps.service.facade.ProcessFacadeService;
import cn.windwheel.aps.vo.MaterialProcessRouteVO;
import cn.windwheel.common.annotation.AuditOperation;
import cn.windwheel.common.annotation.RequirePermission;
import cn.windwheel.common.utils.R;
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
@Api(tags = "[基础数据]工艺路线管理")
@RestController
@RequestMapping("/process-route")
@AllArgsConstructor
@RequirePermission("aps:process-route:view")
public class ProcessRouteController {
    private final ProcessRouteService service;
    private final ProcessFacadeService processFacadeService;

    @ApiOperation("新增工艺路线")
    @PostMapping
    @RequirePermission("aps:process-route:edit")
    @AuditOperation("新增工艺路线")
    public R<String> save(@Validated @RequestBody SaveProcessRouteDTO processRoute) {
        processFacadeService.saveProcessRoute(processRoute);
        return R.ok();
    }

    @ApiOperation("修改工艺路线")
    @PutMapping
    @RequirePermission("aps:process-route:edit")
    @AuditOperation("修改工艺路线")
    public R<String> update(@Validated @RequestBody UpdateProcessRouteDTO processRoute) {
        processFacadeService.updateProcessRoute(processRoute);
        return R.ok();
    }

    @ApiOperation("删除工艺路线")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工艺路线id", required = true)
    })
    @DeleteMapping("/{id}")
    @RequirePermission("aps:process-route:edit")
    @AuditOperation("删除工艺路线")
    public R<String> delete(@PathVariable String id) {
        service.removeById(id);
        return R.ok();
    }

    @ApiOperation("查询指定物料的工艺路线")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "materialCode", value = "物料id", required = true)
    })
    @GetMapping("/material/{materialId}")
    public R<MaterialProcessRouteVO> getMaterialProcessRoute(@PathVariable String materialId) {
        return R.ok(processFacadeService.getMaterialProcessRoute(materialId));
    }

    @ApiOperation("启用工艺路线")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工艺路线id", required = true)
    })
    @PutMapping("/{id}/enable")
    @RequirePermission("aps:process-route:edit")
    @AuditOperation("启用工艺路线")
    public R<String> enableProcessRoute(@PathVariable String id) {
        service.enableProcessRoute(id);
        return R.ok();
    }

    @ApiOperation("禁用工艺路线")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工艺路线id", required = true)
    })
    @PutMapping("/{id}/disable")
    @RequirePermission("aps:process-route:edit")
    @AuditOperation("禁用工艺路线")
    public R<String> disableProcessRoute(@PathVariable String id) {
        service.disableProcessRoute(id);
        return R.ok();
    }

}
