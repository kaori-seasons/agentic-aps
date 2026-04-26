package cn.windwheel.aps.entity;

import cn.windwheel.database.entity.po.BasalPo;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 操作审计日志
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName("jvs_operation_audit_log")
public class OperationAuditLogPO extends BasalPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("tenant_id")
    private String tenantId;

    @TableField("user_id")
    private String userId;

    @TableField("username")
    private String username;

    @TableField("role_codes")
    private String roleCodes;

    @TableField("operation_name")
    private String operationName;

    @TableField("permission_code")
    private String permissionCode;

    @TableField("http_method")
    private String httpMethod;

    @TableField("request_uri")
    private String requestUri;

    @TableField("client_ip")
    private String clientIp;

    @TableField("request_params")
    private String requestParams;

    @TableField("success")
    private Boolean success;

    @TableField("error_message")
    private String errorMessage;

    @TableField("duration_ms")
    private Long durationMs;
}
