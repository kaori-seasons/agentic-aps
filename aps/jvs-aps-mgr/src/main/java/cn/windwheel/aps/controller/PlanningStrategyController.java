package cn.windwheel.aps.controller;

import cn.windwheel.aps.dto.PagePlanningStrategyDTO;
import cn.windwheel.aps.dto.SavePlanningStrategyDTO;
import cn.windwheel.aps.dto.UpdatePlanningStrategyDTO;
import cn.windwheel.aps.service.PlanningStrategyService;
import cn.windwheel.aps.vo.DetailPlanningStrategyVO;
import cn.windwheel.aps.vo.StrategyOrderSchedulingRuleOptionVO;
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

import java.util.List;

/**
 * @author jvs
 */
@Api(tags = "[生产计划]计划策略")
@RestController
@RequestMapping("/planning-strategy")
@AllArgsConstructor
@RequirePermission("aps:strategy:view")
public class PlanningStrategyController {

    private final PlanningStrategyService service;

    @ApiOperation("新增策略")
    @PostMapping
    @RequirePermission("aps:strategy:edit")
    @AuditOperation("新增计划策略")
    public R<String> save(@Validated @RequestBody SavePlanningStrategyDTO planningStrategy) {
        service.save(planningStrategy);
        return R.ok();
    }

    @ApiOperation("修改策略")
    @PutMapping
    @RequirePermission("aps:strategy:edit")
    @AuditOperation("修改计划策略")
    public R<String> update(@Validated @RequestBody UpdatePlanningStrategyDTO planningStrategy) {
        service.update(planningStrategy);
        return R.ok();
    }

    @ApiOperation("修改策略有效状态")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "策略id", required = true),
            @ApiImplicitParam(name = "active", value = "false-无效，true-有效", required = true)
    })
    @PutMapping("/change/{id}/active")
    @RequirePermission("aps:strategy:edit")
    @AuditOperation("修改计划策略有效状态")
    public R<String> updateActive(@PathVariable String id, Boolean active) {
        service.updateActive(id, active);
        return R.ok();
    }

    @ApiOperation("删除策略")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "策略id", required = true)
    })
    @DeleteMapping("/{id}")
    @RequirePermission("aps:strategy:edit")
    @AuditOperation("删除计划策略")
    public R<String> delete(@PathVariable String id) {
        service.removeById(id);
        return R.ok();
    }

    @ApiOperation(("分页查询"))
    @GetMapping("/page")
    public R<Page<DetailPlanningStrategyVO>> page(PagePlanningStrategyDTO pageQuery) {
        return R.ok(service.page(pageQuery));
    }

    @ApiOperation("策略详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "策略id", required = true)
    })
    @GetMapping("/{id}")
    public R<DetailPlanningStrategyVO> detail(@PathVariable String id) {
        return R.ok(service.getDetail(id));
    }

    @ApiOperation("获取订单规则可选项")
    @GetMapping("/scheduling-rule/options")
    public R<List<StrategyOrderSchedulingRuleOptionVO>> listStrategyOrderSchedulingRuleOption() {
        return R.ok(service.listStrategyOrderSchedulingRuleOption());
    }

}
