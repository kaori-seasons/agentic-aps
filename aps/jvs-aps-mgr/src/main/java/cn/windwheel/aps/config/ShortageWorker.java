package cn.windwheel.aps.config;

import cn.windwheel.aps.shortage.ShortageNotifyDispatcher;
import cn.windwheel.aps.shortage.ShortageScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 缺料预警的后台任务：投递推送与周期复评。
 * <p>
 * 周期复评不是可选项。同一份缺口今天只值 P3，三天后自然升到 P2，等级随时间变化，
 * 只有周期性地把未关闭的条目重新过一遍规则，升级才会发生。
 * <p>
 * 多节点同时跑是安全的，不需要另加分布式锁：投递靠行级租约认领，各节点拿到的是不同的行；
 * 复评靠台账的唯一索引去重，撞键时后到的一方走更新。单机上 fixedDelay 本身保证两次执行不重叠。
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aps.shortage.worker-enabled", havingValue = "true", matchIfMissing = true)
public class ShortageWorker {
    private final ShortageNotifyDispatcher dispatcher;
    private final ShortageScanService scan;

    @Scheduled(fixedDelayString = "${aps.shortage.poll-ms:2000}")
    public void deliver() {
        try {
            dispatcher.dispatchDue(LocalDateTime.now());
        } catch (Exception e) {
            // 调度线程不能被单个周期的失败带走，否则后续所有轮询都会停摆。
            log.error("缺料预警投递轮询失败", e);
        }
    }

    @Scheduled(fixedDelayString = "${aps.shortage.sweep-ms:300000}",
            initialDelayString = "${aps.shortage.sweep-initial-delay-ms:20000}")
    public void sweep() {
        try {
            var outcome = scan.sweepAll(LocalDateTime.now());
            if (outcome.getCreated() > 0 || outcome.getFailed() > 0) {
                log.info("缺料复评完成 新建预警={} 重复观测={} 关闭={} 失败={}", outcome.getCreated(),
                        outcome.getRepeated(), outcome.getResolved(), outcome.getFailed());
            }
        } catch (Exception e) {
            log.error("缺料复评失败", e);
        }
    }
}
