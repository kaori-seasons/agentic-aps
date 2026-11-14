package cn.windwheel.aps.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("jvs_solve_audit")
public class SolveAuditPO {
    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String status;
    private String inputHash;
    private String inputJson;
    private String resultJson;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
