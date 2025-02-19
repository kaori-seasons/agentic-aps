package cn.bctools.aps.controller;


import cn.bctools.aps.dto.PageProcessDTO;
import cn.bctools.aps.dto.SaveProcessDTO;
import cn.bctools.aps.dto.UpdateProcessDTO;
import cn.bctools.aps.service.ProcessService;
import cn.bctools.aps.service.facade.ProcessFacadeService;
import cn.bctools.aps.vo.DetailProcessVO;
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
@Api(tags = "[基础数据]工序模板")
@RestController
@RequestMapping("/process")
@AllArgsConstructor
public class ProcessController {
    private final ProcessService processService;
    private final ProcessFacadeService processFacadeService;

    @ApiOperation("新增工序模板")
    @PostMapping
    public R<String> save(@Validated @RequestBody SaveProcessDTO process) {
        processFacadeService.saveProcess(process);
        return R.ok();
    }

    @ApiOperation("修改工序模板")
    @PutMapping
    public R<String> update(@Validated @RequestBody UpdateProcessDTO process) {
        processFacadeService.updateProcess(process);
        return R.ok();
    }

    @ApiOperation("删除工序模板")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "工序模板id", required = true)
    })
    @DeleteMapping("/{id}")
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
