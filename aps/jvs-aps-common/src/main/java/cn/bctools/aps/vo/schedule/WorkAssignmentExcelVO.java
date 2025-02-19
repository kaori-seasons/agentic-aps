package cn.bctools.aps.vo.schedule;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author jvs
 * 派工——Excel
 */
@Data
public class WorkAssignmentExcelVO {

    @ColumnWidth(20)
    @ExcelProperty("订单")
    private String orderCode;

    @ColumnWidth(20)
    @ExcelProperty("产品")
    private String materialCode;

    @ColumnWidth(20)
    @ExcelProperty("工序")
    private String processCode;

    @ColumnWidth(20)
    @ExcelProperty("计划主资源")
    private String planResourceCode;

    @ColumnWidth(20)
    @ExcelProperty("计划开始时间")
    private LocalDateTime planStartTime;

    @ColumnWidth(20)
    @ExcelProperty("计划完成时间")
    private LocalDateTime planEndTime;

    @ColumnWidth(20)
    @ExcelProperty("计划总工时")
    private String planWorkHours;

    @ColumnWidth(20)
    @ExcelProperty("总计划数")
    private BigDecimal scheduledQuantity;

    @ColumnWidth(20)
    @ExcelProperty("计划数量")
    private BigDecimal planQuantity;

    @ColumnWidth(20)
    @ExcelProperty("实际完成数量")
    private BigDecimal quantityCompleted;

    @ColumnWidth(20)
    @ExcelProperty("实际主资源")
    private String resourceCode;

    @ColumnWidth(20)
    @ExcelProperty("实际开始时间")
    private LocalDateTime startTime;

    @ColumnWidth(20)
    @ExcelProperty("实际完成时间")
    private LocalDateTime endTime;

    @ColumnWidth(20)
    @ExcelProperty("报工时间")
    private LocalDateTime reportTime;
}
