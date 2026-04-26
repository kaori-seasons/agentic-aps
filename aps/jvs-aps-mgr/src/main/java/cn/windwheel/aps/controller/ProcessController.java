package cn.windwheel.aps.controller;


import cn.windwheel.aps.dto.PageProcessDTO;
import cn.windwheel.aps.dto.SaveProcessDTO;
import cn.windwheel.aps.dto.UpdateProcessDTO;
import cn.windwheel.aps.service.ProcessService;
import cn.windwheel.aps.service.facade.ProcessFacadeService;
import cn.windwheel.aps.vo.DetailProcessVO;
import cn.windwheel.common.annotation.AuditOperation;
import cn.windwheel.common.annotation.RequirePermission;
import cn.windwheel.common.utils.R;
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
@Api(tags = "[基础数据]工序模板")
@RestController
@RequestMapping("/process")
@AllArgsConstructor
@RequirePermission("aps:process:view")
public class ProcessController {
    private final ProcessService processService;
    private final ProcessFacadeService processFacadeService;

    @ApiOperation("新增工序模板")
    @PostMapping
    @RequirePermission("aps:process:edit")
    @AuditOperation("新增工序模板")
    public R<String> save(@Validated @RequestBody SaveProcessDTO process) {
        processFacadeService.saveProcess(process);
        return R.ok();
    }

    @ApiOperation("修改工序模板")
    @PutMapping
    @RequirePermission("aps:process:edit")
    @AuditOperation("修改工序模板")
    public R<String> update(@Validated @RequestBody UpdateProcessDTO process) {
        processFacadeService.updateProcess(process);
        return R.ok();
    }

    @ApiOperation("删除工序模板")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工序模板id", required = true)
    })
    @DeleteMapping("/{id}")
    @RequirePermission("aps:process:edit")
    @AuditOperation("删除工序模板")
    public R<String> delete(@PathVariable String id) {
        processService.removeById(id);
        return R.ok();
    }

    @ApiOperation("分页查询工序模板")
    @GetMapping("/page")
    public R<Page<DetailProcessVO>> page(PageProcessDTO pageQuery) {
        return R.ok(processFacadeService.pageProcess(pageQuery));
    }

    @ApiOperation("工序模板详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工序模板id", required = true)
    })
    @GetMapping("/{id}")
    public R<DetailProcessVO> detail(@PathVariable String id) {
        return R.ok(processFacadeService.getProcessDetail(id));
    }

}
