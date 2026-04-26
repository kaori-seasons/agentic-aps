package cn.windwheel.aps.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 审计日志分页查询
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ApiModel("审计日志分页查询")
public class PageOperationAuditLogDTO extends PageBaseDTO {

    @ApiModelProperty("用户名")
    private String username;

    @ApiModelProperty("用户ID")
    private String userId;

    @ApiModelProperty("操作名称")
    private String operationName;

    @ApiModelProperty("权限编码")
    private String permissionCode;

    @ApiModelProperty("是否成功")
    private Boolean success;

    @ApiModelProperty("开始时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty("结束时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
