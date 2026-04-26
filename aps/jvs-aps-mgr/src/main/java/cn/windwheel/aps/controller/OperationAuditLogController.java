package cn.windwheel.aps.controller;

import cn.windwheel.aps.dto.PageOperationAuditLogDTO;
import cn.windwheel.aps.service.OperationAuditLogService;
import cn.windwheel.aps.vo.OperationAuditLogVO;
import cn.windwheel.common.annotation.RequirePermission;
import cn.windwheel.common.utils.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作审计日志
 */
@Api(tags = "[系统治理]操作审计日志")
@RestController
@RequestMapping("/operation-audit")
@AllArgsConstructor
@RequirePermission("aps:audit:view")
public class OperationAuditLogController {

    private final OperationAuditLogService operationAuditLogService;

    @ApiOperation("分页查询审计日志")
    @GetMapping("/page")
    public R<Page<OperationAuditLogVO>> page(PageOperationAuditLogDTO pageQuery) {
        return R.ok(operationAuditLogService.page(pageQuery));
    }
}
