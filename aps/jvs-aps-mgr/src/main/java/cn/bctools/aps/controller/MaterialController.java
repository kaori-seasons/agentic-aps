package cn.bctools.aps.controller;


import cn.bctools.aps.dto.PageMaterialDTO;
import cn.bctools.aps.dto.PageMaterialProducedDTO;
import cn.bctools.aps.dto.SaveMaterialDTO;
import cn.bctools.aps.dto.UpdateMaterialDTO;
import cn.bctools.aps.entity.enums.MaterialSourceEnum;
import cn.bctools.aps.service.MaterialService;
import cn.bctools.aps.vo.DetailMaterialVO;
import cn.bctools.common.utils.BeanCopyUtil;
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
@Api(tags = "[基础数据]物料管理")
@RestController
@RequestMapping("/material")
@AllArgsConstructor
public class MaterialController {
    private final MaterialService service;

    @ApiOperation("新增物料")
    @PostMapping
    public R<String> save(@Validated @RequestBody SaveMaterialDTO material) {
        service.save(material);
        return R.ok();
    }

    @ApiOperation("修改物料")
    @PutMapping
    public R<String> update(@Validated @RequestBody UpdateMaterialDTO material) {
        service.update(material);
        return R.ok();
    }

    @ApiOperation("删除物料")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "物料id", required = true)
    })
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable String id) {
        service.removeById(id);
        return R.ok();
    }

    @ApiOperation("分页查询物料")
    @GetMapping("/page")
    public R<Page<DetailMaterialVO>> page(PageMaterialDTO pageQuery) {
        return R.ok(service.page(pageQuery));
    }

    @ApiOperation("物料详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "物料id", required = true)
    })
    @GetMapping("/{id}")
    public R<DetailMaterialVO> detail(@PathVariable String id) {
        return R.ok(service.getDetail(id));
    }

    @ApiOperation(value = "分页查询'制造'物料", notes = "来源为'制造'的物料，可以设置工艺路线")
    @GetMapping("/produced/page")
    public R<Page<DetailMaterialVO>> page(PageMaterialProducedDTO pageQuery) {
        PageMaterialDTO queryCriteriaDto = BeanCopyUtil.copy(pageQuery, PageMaterialDTO.class);
        queryCriteriaDto.setSource(MaterialSourceEnum.PRODUCED);
        return R.ok(service.page(queryCriteriaDto));
    }
}
