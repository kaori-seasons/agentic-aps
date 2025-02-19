package cn.bctools.aps.controller;

import cn.bctools.aps.service.facade.WorkCalendarFacadeService;
import cn.bctools.aps.vo.DetailProductionResourceVO;
import cn.bctools.common.utils.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author jvs
 */
@Api(tags = "[基础数据]资源日历关系")
@RestController
@RequestMapping("/resource-calendar")
@AllArgsConstructor
public class ResourceCalendarController {

    private final WorkCalendarFacadeService workCalendarFacadeService;

    @ApiOperation("获取使用指定日历的所有资源")
    @GetMapping("/{calendarId}/resources")
    public R<List<DetailProductionResourceVO>> getResourcesByCalendarId(@PathVariable String calendarId) {
        return R.ok(workCalendarFacadeService.getResourcesByCalendarId(calendarId));
    }
}
