package cn.bctools.aps.controller;


import cn.bctools.aps.dto.PageProductionResourceDTO;
import cn.bctools.aps.dto.SaveProductionResourceDTO;
import cn.bctools.aps.dto.UpdateProductionResourceDTO;
import cn.bctools.aps.service.ProductionResourceService;
import cn.bctools.aps.vo.DetailProductionResourceVO;
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
@Api(tags = "[基础数据]主资源管理")
@RestController
@RequestMapping("/production-resource")
@AllArgsConstructor
public class ProductionResourceController {
    private final ProductionResourceService service;

    @ApiOperation("新增资源")
    @PostMapping
    public R<String> save(@Validated @RequestBody SaveProductionResourceDTO productionResource) {
        service.save(productionResource);
        return R.ok();
    }

    @ApiOperation("修改资源")
    @PutMapping
    public R<String> update(@Validated @RequestBody UpdateProductionResourceDTO productionResource) {
        service.update(productionResource);
        return R.ok();
    }

    @ApiOperation("删除资源")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "资源id", required = true)
    })
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable String id) {
        service.removeById(id);
        return R.ok();
    }

    @ApiOperation("分页查询资源")
    @GetMapping("/page")
    public R<Page<DetailProductionResourceVO>> page(PageProductionResourceDTO pageQuery) {
        return R.ok(service.page(pageQuery));
    }

    @ApiOperation("资源详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "资源id", required = true)
    })
    @GetMapping("/{id}")
    public R<DetailProductionResourceVO> detail(@PathVariable String id) {
       return R.ok(service.getDetail(id));
    }

    @ApiOperation("获取资源组")
    @GetMapping("/group/list")
    public R<List<String>> listResourceGroups() {
        return R.ok(service.listResourceGroups());
    }
}
