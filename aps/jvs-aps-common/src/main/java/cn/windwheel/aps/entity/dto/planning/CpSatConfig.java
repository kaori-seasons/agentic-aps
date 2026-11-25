package cn.windwheel.aps.entity.dto.planning;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CpSatConfig {
    public enum ExecutionMode { SHADOW, CANDIDATE }
    private ExecutionMode executionMode = ExecutionMode.SHADOW;
    private int numWorkers = 1;
    private int horizonDays = 30;
    private boolean useBaselineHint = true;
    public void validate() {
        if(executionMode==null || numWorkers<1 || numWorkers>8 || horizonDays<1 || horizonDays>180)
            throw new IllegalArgumentException("CP-SAT配置超出范围：线程1–8，计划窗口1–180天");
    }
}
