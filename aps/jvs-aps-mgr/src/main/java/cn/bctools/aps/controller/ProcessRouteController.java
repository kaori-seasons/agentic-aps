package cn.bctools.aps.controller;


import cn.bctools.aps.dto.SaveProcessRouteDTO;
import cn.bctools.aps.dto.UpdateProcessRouteDTO;
import cn.bctools.aps.service.ProcessRouteService;
import cn.bctools.aps.service.facade.ProcessFacadeService;
import cn.bctools.aps.vo.MaterialProcessRouteVO;
import cn.bctools.common.utils.R;
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
public class ProcessRouteController {
    private final ProcessRouteService service;
    private final ProcessFacadeService processFacadeService;

    @ApiOperation("新增工艺路线")
    @PostMapping
    public R<String> save(@Validated @RequestBody SaveProcessRouteDTO processRoute) {
        processFacadeService.saveProcessRoute(processRoute);
        return R.ok();
    }

    @ApiOperation("修改工艺路线")
    @PutMapping
    public R<String> update(@Validated @RequestBody UpdateProcessRouteDTO processRoute) {
        processFacadeService.updateProcessRoute(processRoute);
        return R.ok();
    }

    @ApiOperation("删除工艺路线")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工艺路线id", required = true)
    })
    @DeleteMapping("/{id}")
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
    public R<String> enableProcessRoute(@PathVariable String id) {
        service.enableProcessRoute(id);
        return R.ok();
    }

    @ApiOperation("禁用工艺路线")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工艺路线id", required = true)
    })
    @PutMapping("/{id}/disable")
    public R<String> disableProcessRoute(@PathVariable String id) {
        service.disableProcessRoute(id);
        return R.ok();
    }

}
