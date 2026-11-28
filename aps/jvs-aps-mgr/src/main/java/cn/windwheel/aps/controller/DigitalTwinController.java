package cn.windwheel.aps.controller;

import cn.windwheel.aps.digitaltwin.DigitalTwinSnapshotService;
import cn.windwheel.common.annotation.RequirePermission;
import cn.windwheel.common.utils.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数字孪生快照：当前租户的设备台账、订单、已确认计划、候选计划与来料计划一次取齐，
 * KPI 与决策收益在服务端算好。孪生前端只负责呈现，不自己拼数据。
 */
@Api(tags = "[生产计划]数字孪生")
@RestController
@RequestMapping("/digital-twin")
@RequiredArgsConstructor
@RequirePermission("aps:schedule:view")
public class DigitalTwinController {

    private final DigitalTwinSnapshotService snapshotService;

    @ApiOperation("当前租户的数字孪生快照（已确认计划 + 候选计划 + 来料 + 资源）")
    @GetMapping("/snapshot")
    public R<DigitalTwinSnapshotService.Snapshot> snapshot() {
        return R.ok(snapshotService.snapshot());
    }
}
