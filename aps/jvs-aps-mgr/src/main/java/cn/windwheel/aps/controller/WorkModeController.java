package cn.windwheel.aps.controller;


import cn.windwheel.aps.dto.PageWorkModeDTO;
import cn.windwheel.aps.dto.SaveWorkModeDTO;
import cn.windwheel.aps.dto.UpdateWorkModeDTO;
import cn.windwheel.aps.entity.WorkModePO;
import cn.windwheel.aps.service.WorkModeService;
import cn.windwheel.aps.vo.DetailWorkModeVO;
import cn.windwheel.common.annotation.AuditOperation;
import cn.windwheel.common.annotation.RequirePermission;
import cn.windwheel.common.utils.BeanCopyUtil;
import cn.windwheel.common.utils.R;
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
@RequirePermission("aps:work-mode:view")
public class WorkModeController {
    private final WorkModeService service;

    @ApiOperation("新增模式")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping
    @RequirePermission("aps:work-mode:edit")
    @AuditOperation("新增工作模式")
    public R<String> save(@Validated @RequestBody SaveWorkModeDTO saveWorkMode) {
        WorkModePO workMode = BeanCopyUtil.copy(saveWorkMode, WorkModePO.class);
        service.save(workMode);
        return R.ok();
    }

    @ApiOperation("修改模式")
    @Transactional(rollbackFor = Exception.class)
    @PutMapping
    @RequirePermission("aps:work-mode:edit")
    @AuditOperation("修改工作模式")
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
    @RequirePermission("aps:work-mode:edit")
    @AuditOperation("删除工作模式")
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
