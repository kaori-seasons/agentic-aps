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
    private boolean reuseWorker = true;
    private boolean timefoldWarmStart = false;
    private int timefoldBudgetPercent = 50;
    private boolean sizeAwareBudget = true;
    public enum SearchMode { AUTOMATIC, PORTFOLIO }
    private SearchMode searchMode = SearchMode.AUTOMATIC;
    public enum OptimizationScope { FULL, LOCAL }
    private OptimizationScope optimizationScope = OptimizationScope.FULL;
    private java.util.List<String> focusTaskCodes = new java.util.ArrayList<>();
    private int neighborhoodDepth = 1;
    private int maxNeighborhoodTasks = 60;
    private long changedTaskWeight = 1000;
    private long resourceChangeWeight = 3000;
    private long startShiftMinuteWeight = 1;
    private long minNetImprovement = 1;
    private long minTardinessImprovementMinutes = 0;
    private int maxChangedTaskPercent = 100;
    public void validate() {
        if(searchMode==null || timefoldBudgetPercent<10 || timefoldBudgetPercent>80)
            throw new IllegalArgumentException("热启动预算比例必须为10–80，搜索模式不能为空");
        if(timefoldWarmStart && !useBaselineHint) throw new IllegalArgumentException("组合优化必须开启基线保护");
        if(executionMode==null || numWorkers<1 || numWorkers>8 || horizonDays<1 || horizonDays>180)
            throw new IllegalArgumentException("CP-SAT配置超出范围：线程1–8，计划窗口1–180天");
        if(optimizationScope==null || neighborhoodDepth<0 || neighborhoodDepth>5 || maxNeighborhoodTasks<1 || maxNeighborhoodTasks>300
                || focusTaskCodes==null || focusTaskCodes.size()>300 || focusTaskCodes.stream().anyMatch(x->x==null || x.isBlank())
                || new java.util.HashSet<>(focusTaskCodes).size()!=focusTaskCodes.size()
                || minNetImprovement<0 || minNetImprovement>1000000000000L || minTardinessImprovementMinutes<0
                || minTardinessImprovementMinutes>259200 || maxChangedTaskPercent<0 || maxChangedTaskPercent>100)
            throw new IllegalArgumentException("CP-SAT局部范围或采用门槛超出范围");
        new cn.windwheel.aps.cpsat.Protocol.Optimization(null,changedTaskWeight,resourceChangeWeight,startShiftMinuteWeight);
        if(optimizationScope==OptimizationScope.LOCAL && !useBaselineHint)
            throw new IllegalArgumentException("局部优化必须启用完整可行基线保护");
    }
}
