package cn.bctools.aps.controller;


import cn.bctools.aps.dto.PageWorkModeDTO;
import cn.bctools.aps.dto.SaveWorkModeDTO;
import cn.bctools.aps.dto.UpdateWorkModeDTO;
import cn.bctools.aps.entity.WorkModePO;
import cn.bctools.aps.service.WorkModeService;
import cn.bctools.aps.vo.DetailWorkModeVO;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author jvs
 */
@Api(tags = "[基础数据]工作模式管理")
@RestController
@RequestMapping("/work-mode")
@AllArgsConstructor
public class WorkModeController {
    private final WorkModeService service;

    @ApiOperation("新增模式")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping
    public R<String> save(@Validated @RequestBody SaveWorkModeDTO saveWorkMode) {
        WorkModePO workMode = BeanCopyUtil.copy(saveWorkMode, WorkModePO.class);
        service.save(workMode);
        return R.ok();
    }

    @ApiOperation("修改模式")
    @Transactional(rollbackFor = Exception.class)
    @PutMapping
    public R<String> update(@Validated @RequestBody UpdateWorkModeDTO updateWorkMode) {
        WorkModePO workMode = BeanCopyUtil.copy(updateWorkMode, WorkModePO.class);
        service.updateById(workMode);
        return R.ok();
    }

    @ApiOperation("删除模式")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "模式id", required = true)
    })
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable String id) {
        service.removeById(id);
        return R.ok();
    }

    @ApiOperation(("分页查询"))
    @GetMapping("/page")
    public R<Page<DetailWorkModeVO>> page(PageWorkModeDTO pageQuery) {
        return R.ok(service.page(pageQuery));
    }

    @ApiOperation("模式详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "模式id", required = true)
    })
    @GetMapping("/{id}")
    public R<DetailWorkModeVO> detail(@PathVariable String id) {
        return R.ok(service.getDetail(id));
    }

}
