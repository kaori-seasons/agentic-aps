package cn.bctools.aps.solve.model;

import cn.bctools.aps.annotation.Comment;
import cn.bctools.aps.entity.enums.ResourceStatusEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author jvs
 * 资源
 */
@Data
@Accessors(chain = true)
public class ProductionResource {
    @Comment("资源id")
    private String id;

    @Comment("名称")
    private String name;

    @Comment("编码")
    private String code;

    @Comment("容量")
    private BigDecimal capacity;

    @Comment("容量计量单位")
    private String unit;

    @Comment("产能")
    private String throughput;

    @Comment("资源状态")
    private ResourceStatusEnum resourceStatus;

    @Comment("日历id集合")
    List<String> workCalendarIds;
}
