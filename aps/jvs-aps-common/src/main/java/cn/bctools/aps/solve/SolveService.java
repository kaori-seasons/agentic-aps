package cn.bctools.aps.solve;

import cn.bctools.aps.solve.model.BasicData;
import cn.bctools.aps.solve.model.PlanningStrategy;

import java.util.UUID;

/**
 * @author jvs
 * 排产求解
 */
public interface SolveService {

    /**
     * 排产计算
     *
     * @param strategy 策略
     * @param basicData 排产基础数据
     */
    void solve(UUID planId, PlanningStrategy strategy, BasicData basicData);

    /**
     * 是否有正在计算的排产任务
     *
     * @return true-当前租户存在正在求解的排产任务，false-当前租户不存在正在求解的排产任务
     */
    boolean existsSolving();

    /**
     * 停止运行中的排产计划
     */
    void cancel();
}
