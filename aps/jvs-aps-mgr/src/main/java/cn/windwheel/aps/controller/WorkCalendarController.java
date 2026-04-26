package cn.windwheel.aps.controller;


import cn.windwheel.aps.dto.PageWorkCalendarDTO;
import cn.windwheel.aps.dto.SaveWorkCalendarDTO;
import cn.windwheel.aps.dto.UpdateWorkCalendarDTO;
import cn.windwheel.aps.entity.WorkCalendarPO;
import cn.windwheel.aps.service.facade.WorkCalendarFacadeService;
import cn.windwheel.aps.vo.DetailWorkCalendarVO;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author jvs
 */
@Api(tags = "[基础数据]工作日历管理")
@RestController
@RequestMapping("/work-calendar")
@AllArgsConstructor
@RequirePermission("aps:work-calendar:view")
public class WorkCalendarController {
    private final WorkCalendarFacadeService workCalendarFacadeService;

    @ApiOperation("新增日历")
    @PostMapping
    @RequirePermission("aps:work-calendar:edit")
    @AuditOperation("新增工作日历")
    public R<String> save(@Validated @RequestBody SaveWorkCalendarDTO saveWorkCalendar) {
        WorkCalendarPO workCalendar = BeanCopyUtil.copy(saveWorkCalendar, WorkCalendarPO.class);
        workCalendarFacadeService.insertOrUpdate(workCalendar, saveWorkCalendar.getWorkDaySetting(), saveWorkCalendar.getResourceIds());
        return R.ok();
    }

    @ApiOperation("修改日历")
    @PutMapping
    @RequirePermission("aps:work-calendar:edit")
    @AuditOperation("修改工作日历")
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
    @RequirePermission("aps:work-calendar:edit")
    @AuditOperation("删除工作日历")
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
