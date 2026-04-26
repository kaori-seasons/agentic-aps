package cn.windwheel.aps.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 审计日志视图
 */
@Data
@Accessors(chain = true)
@ApiModel("审计日志")
public class OperationAuditLogVO {

    @ApiModelProperty("ID")
    private String id;

    @ApiModelProperty("租户ID")
    private String tenantId;

    @ApiModelProperty("用户ID")
    private String userId;

    @ApiModelProperty("用户名")
    private String username;

    @ApiModelProperty("角色编码")
    private String roleCodes;

    @ApiModelProperty("操作名称")
    private String operationName;

    @ApiModelProperty("权限编码")
    private String permissionCode;

    @ApiModelProperty("HTTP方法")
    private String httpMethod;

    @ApiModelProperty("请求路径")
    private String requestUri;

    @ApiModelProperty("客户端IP")
    private String clientIp;

    @ApiModelProperty("请求摘要")
    private String requestParams;

    @ApiModelProperty("执行结果")
    private Boolean success;

    @ApiModelProperty("失败原因")
    private String errorMessage;

    @ApiModelProperty("耗时毫秒")
    private Long durationMs;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
}
