package cn.bctools.aps.controller;


import cn.bctools.aps.dto.PageWorkCalendarDTO;
import cn.bctools.aps.dto.SaveWorkCalendarDTO;
import cn.bctools.aps.dto.UpdateWorkCalendarDTO;
import cn.bctools.aps.entity.WorkCalendarPO;
import cn.bctools.aps.service.facade.WorkCalendarFacadeService;
import cn.bctools.aps.vo.DetailWorkCalendarVO;
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
@Api(tags = "[基础数据]工作日历管理")
@RestController
@RequestMapping("/work-calendar")
@AllArgsConstructor
public class WorkCalendarController {
    private final WorkCalendarFacadeService workCalendarFacadeService;

    @ApiOperation("新增日历")
    @PostMapping
    public R<String> save(@Validated @RequestBody SaveWorkCalendarDTO saveWorkCalendar) {
        WorkCalendarPO workCalendar = BeanCopyUtil.copy(saveWorkCalendar, WorkCalendarPO.class);
        workCalendarFacadeService.insertOrUpdate(workCalendar, saveWorkCalendar.getWorkDaySetting(), saveWorkCalendar.getResourceIds());
        return R.ok();
    }

    @ApiOperation("修改日历")
    @PutMapping
    public R<String> update(@Validated @RequestBody UpdateWorkCalendarDTO updateWorkCalendar) {
        WorkCalendarPO workCalendar = BeanCopyUtil.copy(updateWorkCalendar, WorkCalendarPO.class);
        workCalendarFacadeService.insertOrUpdate(workCalendar, updateWorkCalendar.getWorkDaySetting(), updateWorkCalendar.getResourceIds());
        return R.ok();
    }

    @ApiOperation("删除日历")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "模式id", required = true)
    })
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable String id) {
        workCalendarFacadeService.delete(id);
        return R.ok();
    }

    @ApiOperation(("分页查询"))
    @GetMapping("/page")
    public R<Page<DetailWorkCalendarVO>> page(PageWorkCalendarDTO pageQuery) {
        return R.ok(workCalendarFacadeService.page(pageQuery));
    }

    @ApiOperation("日历详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "模式id", required = true)
    })
    @GetMapping("/{id}")
    public R<DetailWorkCalendarVO> detail(@PathVariable String id) {
        return R.ok(workCalendarFacadeService.getDetail(id));
    }

}
