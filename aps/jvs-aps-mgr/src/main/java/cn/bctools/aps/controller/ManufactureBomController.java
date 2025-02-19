package cn.bctools.aps.controller;

import cn.bctools.aps.dto.PageManufactureBomDTO;
import cn.bctools.aps.dto.SaveManufactureBomDTO;
import cn.bctools.aps.dto.UpdateManufactureBomDTO;
import cn.bctools.aps.service.ManufactureBomService;
import cn.bctools.aps.service.facade.BomFacadeService;
import cn.bctools.aps.tree.Tree;
import cn.bctools.aps.vo.DetailManufactureBomVO;
import cn.bctools.aps.vo.MaterialBomVO;
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
@Api(tags = "[基础数据]制造BOM管理")
@RestController
@RequestMapping("/manufacture-bom")
@AllArgsConstructor
public class ManufactureBomController {
    private BomFacadeService bomFacadeService;
    private ManufactureBomService manufactureBomService;

    @ApiOperation("新增BOM")
    @PostMapping
    public R<String> save(@Validated @RequestBody SaveManufactureBomDTO manufactureBom) {
        bomFacadeService.saveManufactureBom(manufactureBom);
        return R.ok();
    }

    @ApiOperation("修改BOM")
    @PutMapping
    public R<String> update(@Validated @RequestBody UpdateManufactureBomDTO manufactureBom) {
        bomFacadeService.updateManufactureBom(manufactureBom);
        return R.ok();
    }

    @ApiOperation("删除BOM")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "bom id", required = true)
    })
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable String id) {
        manufactureBomService.removeById(id);
        return R.ok();
    }

    @ApiOperation("分页查询已有BOM的物料")
    @GetMapping("/page")
    public R<Page<DetailManufactureBomVO>> page(PageManufactureBomDTO pageQuery) {
        return R.ok(bomFacadeService.pageManufactureBom(pageQuery));
    }

    @ApiOperation("BOM详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "bom id", required = true)
    })
    @GetMapping("/{id}")
    public R<DetailManufactureBomVO> detail(@PathVariable String id) {
        return R.ok(bomFacadeService.getDetail(id));
    }

    @ApiOperation("BOM树")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "bom id", required = true)
    })
    @GetMapping("/{id}/tree")
    public R<Tree<MaterialBomVO>> tree(@PathVariable String id) {
        return R.ok(bomFacadeService.treeBom(id));
    }
}
