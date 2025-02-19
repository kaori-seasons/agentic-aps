package cn.bctools.aps.entity.dto.plan;

import cn.bctools.aps.annotation.Comment;
import cn.bctools.aps.entity.enums.MaterialSourceEnum;
import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("排产结果冗余物料信息")
public class PlanMaterialInfoDTO {
    @Comment("编码")
    private String code;

    @Comment("名称")
    private String name;

    @Comment("来源")
    private MaterialSourceEnum source;

    @Comment("计量单位")
    private String unit;
}
