package cn.bctools.aps.solve.dto;

import cn.bctools.aps.solve.enums.SolveProgressStatusEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author jvs
 * 排程进度
 */
@Data
@Accessors(chain = true)
public class SolveProgressDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 排程key
     */
    private String planKey;

    /**
     * 开始排产时间
     */
    private LocalDateTime startTime;

    /**
     * 排程进度状态
     */
    private SolveProgressStatusEnum status;

    /**
     * 进度百分比
     */
    private Long ratio;

    /**
     * 总步骤数
     */
    private Integer totalStep;

    /**
     * 开始排产求解时间
     */
    private LocalDateTime solveStartTime;


}
