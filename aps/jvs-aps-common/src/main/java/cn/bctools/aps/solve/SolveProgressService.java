package cn.bctools.aps.solve;

import cn.bctools.aps.solve.dto.SolveProgressDTO;
import cn.bctools.aps.solve.enums.SolveProgressStatusEnum;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jvs
 * 排程进度
 */
public interface SolveProgressService {

   /**
    * 初始化进度
    * @param planKey 排程key
    */
   void initProgress(String planKey);

   /**
    * 保存开始排程计算的时间
    *
    * @param time 开始排程计算的时间
    */
   void setProgressSolveStartTime(LocalDateTime time);

   /**
    * 保存排程步骤
    *
    * @param totalStep 步骤数量
    */
   void saveProgressSolveStep(Integer totalStep);

   /**
    * 添加进度日志
    *
    * @param status 进度状态
    * @param content 内容
    */
   void addProgressLog(SolveProgressStatusEnum status, String content);

   /**
    * 添加进度日志
    *
    * @param content 内容
    * @param processingTime 耗时时长
    * @param step 步骤
    */
   void addProgressLog(SolveProgressStatusEnum status, String content, Long processingTime, Integer step);

   /**
    * 获取排产进度
    *
    * @return 排产进度
    */
   SolveProgressDTO getProgress();

   /**
    * 获取排产进度日志
    *
    * @return 排产进度日志
    */
   List<Object> listProgressLog();

   /**
    * 删除进度
    */
   void removeProgress();

   /**
    * 耗时时长格式转换
    *
    * @param startTime 开始时间
    * @param endTime 结束时间
    * @return 耗时时长格式化后的字符串
    */
   String formatProcessingTime(LocalDateTime startTime, LocalDateTime endTime);
}
