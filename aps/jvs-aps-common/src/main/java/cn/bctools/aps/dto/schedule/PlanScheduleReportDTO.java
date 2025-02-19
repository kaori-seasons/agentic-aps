package cn.bctools.aps.dto.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jvs
 * 统一报告请求基类，所有报告请求类均需继承此类
 */
@Data
@Accessors(chain = true)
public class PlanScheduleReportDTO {

    @ApiModelProperty(value = "日期范围")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private List<LocalDateTime> dateRange;
}
