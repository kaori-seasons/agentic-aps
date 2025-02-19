package cn.bctools.aps.solve.impl;

import cn.bctools.aps.enums.DurationFormatTypeEnum;
import cn.bctools.aps.solve.SolveProgressService;
import cn.bctools.aps.solve.dto.SolveProgressDTO;
import cn.bctools.aps.solve.enums.SolveProgressStatusEnum;
import cn.bctools.aps.solve.util.DurationUtils;
import cn.bctools.common.constant.SysConstant;
import cn.bctools.common.utils.ObjectNull;
import cn.bctools.common.utils.TenantContextHolder;
import cn.bctools.redis.utils.RedisUtils;
import cn.hutool.cron.CronUtil;
import cn.hutool.cron.task.Task;
import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 排产进度
 */
@Slf4j
@Service
@AllArgsConstructor
public class SolveProgressServiceImpl implements SolveProgressService {

    private final RedisUtils redisUtils;

    /**
     * 排程进度缓存key
     */
    private static final String SOLVE_PROGRESS = "smart:planning:progress";

    /**
     * 排程进度日志缓存key
     */
    private static final String SOLVE_PROGRESS_LOG = "smart:planning:progress:log";


    /**
     * 迭代排程心跳集合
     */
    private static final Map<String, String> SOLVE_HEARTBEAT = new HashMap<>();
    /**
     * 心跳key前缀
     */
    private static final String SOLVE_HEARTBEAT_KEY = "smart:planning:progress";
    /**
     * 心跳超时时间. 超过该时长，表示该排程已经挂了
     */
    private static final Long SOLVE_HEARTBEAT_MAX_TIME_OUT = 2 * 60 * 1000L;
    /**
     * 检查心跳锁
     */
    private static final String CHECK_LOCK = "smart:planning:progress:lock";
    /**
     * 检查心跳锁时长
     */
    private static final Integer CHECK_LOCK_TTL = 60;
    /**
     * 缓存项过期时长
     */
    private static final Long TTL = 86400L;

    /**
     * 服务停止，未结束的排程异常提示
     */
    private static final String SERVER_DESTROY_TASK_ERROR_MESSAGE = "服务停止或重启导致排程失败";

    @Override
    public void initProgress(String planKey) {
        SolveProgressDTO progress = new SolveProgressDTO()
                .setPlanKey(planKey)
                .setStartTime(LocalDateTime.now())
                .setRatio(0L)
                .setStatus(SolveProgressStatusEnum.SCHEDULING);
        String progressKey = getProgressKey(TenantContextHolder.getTenantId());
        saveProgressCache(progressKey, progress);
        // 删除排程日志
        String progressLogKey = getProgressLogKey(TenantContextHolder.getTenantId());
        redisUtils.del(progressLogKey);
        // 将排程key加入待发送心跳集合
        SOLVE_HEARTBEAT.put(planKey, TenantContextHolder.getTenantId());
        sendHeartbeat(planKey, TenantContextHolder.getTenantId());
    }


    @Override
    public void setProgressSolveStartTime(LocalDateTime time) {
        String progressKey = getProgressKey(TenantContextHolder.getTenantId());
        SolveProgressDTO progress = getProgressCache(progressKey);
        progress.setSolveStartTime(time);
        saveProgressCache(progressKey, progress);
    }

    @Override
    public void saveProgressSolveStep(Integer totalStep) {
        String progressKey = getProgressKey(TenantContextHolder.getTenantId());
        SolveProgressDTO progress = getProgressCache(progressKey);
        // 默认将求解计算看作一个步骤，占50%
        progress.setTotalStep(totalStep * 2);
        saveProgressCache(progressKey, progress);
    }


    @Override
    public void addProgressLog(SolveProgressStatusEnum status, String content) {
        addProgressLog(status, content, null, null, TenantContextHolder.getTenantId());
    }

    @Override
    public void addProgressLog(SolveProgressStatusEnum status, String content, Long processingTime, Integer step) {
        addProgressLog(status, content, processingTime, step, TenantContextHolder.getTenantId());
    }

    private void addProgressLog(SolveProgressStatusEnum status, String content, Long processingTime, Integer step, String tenantId) {
        String progressKey = getProgressKey(tenantId);
        SolveProgressDTO progress = getProgressCache(progressKey);
        if (ObjectNull.isNull(progress)) {
            return;
        }
        // 拼装内容
        content = content + formatProcessingTime(progress, status, processingTime);
        // 缓存进度日志
        String progressLogKey = getProgressLogKey(tenantId);
        redisUtils.lSetListRight(progressLogKey, content);

        // 修改进度
        changeProgress(progressKey, progress, status, step);
    }

    @Override
    public SolveProgressDTO getProgress() {
        String progressKey = getProgressKey(TenantContextHolder.getTenantId());
        SolveProgressDTO progress = getProgressCache(progressKey);
        if (ObjectNull.isNotNull(progress)) {
            return progress;
        }
        // 没有进度时，构造进度
        progress = new SolveProgressDTO()
                .setStatus(SolveProgressStatusEnum.NONE);
        return progress;
    }

    @Override
    public List<Object> listProgressLog() {
        String progressLogKey = getProgressLogKey(TenantContextHolder.getTenantId());
        return redisUtils.lGet(progressLogKey, 0, -1);
    }

    @Override
    public void removeProgress() {
        String progressKey = getProgressKey(TenantContextHolder.getTenantId());
        SolveProgressDTO progress = getProgressCache(progressKey);
        redisUtils.del(progressKey);
        String progressLogKey = getProgressLogKey(TenantContextHolder.getTenantId());
        redisUtils.del(progressLogKey);
        if (ObjectNull.isNotNull(progress)) {
            removeHeart(progress.getPlanKey(), null);
        }

    }

    @Override
    public String formatProcessingTime(LocalDateTime startTime, LocalDateTime endTime) {
        Duration duration = Duration.between(startTime, endTime);
        return formatProcessingTime(duration);
    }


    /**
     * 缓存进度
     *
     * @param progressKey 进度key
     * @param progress 进度
     */
    private void saveProgressCache(String progressKey, SolveProgressDTO progress) {
        redisUtils.set(progressKey, JSON.toJSONString(progress));
    }

    /**
     * 从缓存获取进度
     *
     * @param progressKey 进度key
     * @return 进度
     */
    private SolveProgressDTO getProgressCache(String progressKey) {
        return JSON.parseObject((String) redisUtils.get(progressKey), SolveProgressDTO.class);
    }

    /**
     * 修改进度
     *
     * @param progressKey 进度key
     * @param progress 进度信息
     * @param status 进度状态
     * @param step 步骤
     */
    private void changeProgress(String progressKey, SolveProgressDTO progress, SolveProgressStatusEnum status, Integer step) {
        switch (status) {
            case SCHEDULING:
                // 排产中
                if (ObjectNull.isNull(step)) {
                    return;
                }
                long ratio = Math.max(Math.round(((double) step / progress.getTotalStep()) * 100), 10);
                progress.setRatio(ratio);
                if (progress.getRatio() == 100) {
                    status = SolveProgressStatusEnum.SUCCESS;
                }
                break;
            case SUCCESS, NO_SCHEDULED:
                progress.setRatio(100L);
                break;
            default:
                break;
        }
        progress.setStatus(status);
        saveProgressCache(progressKey, progress);
    }

    /**
     * 获取排程进度缓存key
     *
     * @param tenantId 租户id
     * @return 排程进度key
     */
    private String getProgressKey(String tenantId) {
        return SysConstant.redisKey(SOLVE_PROGRESS, tenantId);
    }

    /**
     * 获取排程进度日志缓存key
     *
     * @param tenantId 租户id
     * @return 排程进度日志key
     */
    private String getProgressLogKey(String tenantId) {
        return SysConstant.redisKey(SOLVE_PROGRESS_LOG, tenantId);
    }

    /**
     * 耗时时长格式转换
     *
     * @param processingTime 时长（ms）
     * @return 耗时时长格式化后的字符串
     */
    private String formatProcessingTime(SolveProgressDTO progress, SolveProgressStatusEnum status, Long processingTime) {
        String format = "";
        switch (status) {
            case SCHEDULING:
                // 排产中
                if (ObjectNull.isNotNull(processingTime)) {
                    Duration duration = Duration.ofMillis(processingTime);
                    format = formatProcessingTime(duration);
                }
                break;
            case SUCCESS, NO_SCHEDULED:
                Duration duration = Duration.between(progress.getStartTime(), LocalDateTime.now());
                format = formatProcessingTime(duration);
                break;
            default:
                break;
        }
        return format;
    }

    /**
     * 耗时时长格式转换
     *
     * @param processingTime 时长
     * @return 耗时时长格式化后的字符串
     */
    private String formatProcessingTime(Duration processingTime) {
        return "，耗时：" + DurationUtils.formatDuration(DurationFormatTypeEnum.HOURS_MINUTES_SECONDS, processingTime);
    }


    @PostConstruct
    private void heart() {
        // 每10秒发送一次心跳
        CronUtil.schedule("*/10 * * * * *", (Task) () -> {
            try {
                sendHeartbeat();
            } catch (Exception e) {
                log.error("每10秒发送一次排程心跳：{}", e.getMessage());
            }
        });

        // 每分钟检查一次心跳
        CronUtil.schedule("0 * * * * *", (Task) () -> {
            try {
                checkHeartbeat();
            } catch (Exception e) {
                log.error("每分钟检查一次排程心跳：{}", e.getMessage());
            }
        });
    }

    @PreDestroy
    private void destroy() {
        SOLVE_HEARTBEAT.forEach((key, value) -> {
            removeHeart(key, SERVER_DESTROY_TASK_ERROR_MESSAGE);
            redisUtils.del(key);
        });
    }

    /**
     * 心跳key
     */
    private String getHeartKey() {
        return SysConstant.redisKey(SOLVE_HEARTBEAT_KEY, "heart");
    }


    /**
     * 检查心跳锁key
     */
    private String getCheckPlanKey() {
        return SysConstant.redisKey(CHECK_LOCK, "check");
    }

    /**
     * 发送心跳
     */
    private void sendHeartbeat() {
        SOLVE_HEARTBEAT.forEach(this::sendHeartbeat);
    }

    /**
     * 发送心跳
     *
     * @param planKey 排程锁
     * @param tenantId 租户id
     */
    private void sendHeartbeat(String planKey, String tenantId) {
        redisUtils.hset(getHeartKey(), getHeartbeatItemKey(planKey, tenantId), System.currentTimeMillis(), TTL);
    }

    /**
     * 获取排程心跳缓存项
     *
     * @param planKey 排程锁
     * @param tenantId 租户id
     * @return 心跳缓存项
     */
    private String getHeartbeatItemKey(String planKey, String tenantId) {
        return planKey + "_" + tenantId;
    }

    /**
     * 删除心跳
     *
     * @param planKey   锁
     * @param errorMsg 异常消息
     */
    public void removeHeart(String planKey, String errorMsg) {
        String tenantId = SOLVE_HEARTBEAT.get(planKey);
        if (ObjectNull.isNotNull(errorMsg)) {
            taskFailureEnd(tenantId, errorMsg);
        }
        // 删除心跳缓存
        redisUtils.hdel(getHeartKey(), getHeartbeatItemKey(planKey, tenantId));
        SOLVE_HEARTBEAT.remove(planKey);
    }

    /**
     * 检查排程心跳
     */
    private void checkHeartbeat() {
        String checkPlanKey = getCheckPlanKey();
        boolean lock = redisUtils.tryLock(checkPlanKey, CHECK_LOCK_TTL);
        if (Boolean.FALSE.equals(lock)) {
            return;
        }
        try {
            Map<Object, Object> heartMap = redisUtils.hmget(getHeartKey());
            if (ObjectNull.isNull(heartMap)) {
                return;
            }
            long now = System.currentTimeMillis();
            List<String> heartbeatTimeOutKey = heartMap.entrySet().stream()
                    .filter(e -> now - (Long) e.getValue() > SOLVE_HEARTBEAT_MAX_TIME_OUT)
                    .map(e -> (String) e.getKey())
                    .collect(Collectors.toList());
            if (ObjectNull.isNull(heartbeatTimeOutKey)) {
                return;
            }

            heartbeatTimeOutKey.forEach(h -> {
                String[] arr = h.split("_");
                String planKey = arr[0];
                String tenantId = arr[1];
                redisUtils.unLock(planKey);
                taskFailureEnd(tenantId, SERVER_DESTROY_TASK_ERROR_MESSAGE);
            });

            redisUtils.hdel(getHeartKey(), heartbeatTimeOutKey.toArray());
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            redisUtils.unLock(checkPlanKey);
        }
    }


    /**
     * 排程异常停止
     *
     * @param tenantId      租户id
     * @param errorMessage 异常消息
     */
    private void taskFailureEnd(String tenantId, String errorMessage) {
        addProgressLog(SolveProgressStatusEnum.FAILURE, errorMessage, null, null, tenantId);
    }
}
