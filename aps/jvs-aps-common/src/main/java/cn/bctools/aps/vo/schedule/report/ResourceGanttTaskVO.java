package cn.bctools.aps.vo.schedule.report;

import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("资源甘特图任务信息")
public class ResourceGanttTaskVO {

    @ApiModelProperty("任务编码")
    private String code;

    @ApiModelProperty("显示颜色")
    private String color;

    @ApiModelProperty("计划开始时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty("计划完成时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @ApiModelProperty("计划主资源id")
    private String mainResourceId;

    @ApiModelProperty("任务状态")
    private PlanTaskStatusEnum taskStatus;

    @ApiModelProperty("是否锁定任务")
    private Boolean pinned;

    @ApiModelProperty("是否是合并任务")
    private Boolean mergeTask;

    @ApiModelProperty(value = "逾期时长", notes = "不为空，表示任务已逾期")
    private String overdueTimeString;

    @ApiModelProperty(value = "后置任务编码集合", notes = "只存储后一级任务")
    private Set<String> nextTaskCodes;

    @ApiModelProperty(value = "进度时间", notes = "根据已报工数量计算进度时间,用以在甘特图上展示任务进度")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime taskProgressTime;

    @ApiModelProperty("true-合规，false-不合规")
    private Boolean compliant;

    @ApiModelProperty("true-已调整， false-未调整")
    private Boolean adjusted;

    @ApiModelProperty(value = "单任务扩展数据", notes = "根据用户配置动态返回的数据，非合并任务的扩展数据")
    private Map<String, Object> extras;

    @ApiModelProperty(value = "合并任务扩展数据", notes = "根据用户配置动态返回的数据，合并任务的扩展数据")
    private List<Map<String, Object>> mergeExtras;
}
