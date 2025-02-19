package cn.bctools.aps.entity.dto;

import cn.bctools.aps.entity.enums.ProcessRelationshipEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("工艺路线设计-工序属性")
public class ProcessRouteNodePropertiesDTO {
    @ApiModelProperty(value = "工序名称")
    private String name;

    @ApiModelProperty(value = "工序编码")
    @NotBlank(message = "工序编码不能为空")
    private String code;

    @ApiModelProperty(value = "前间隔时长")
    private String preIntervalDuration;

    @ApiModelProperty(value = "后间隔时长")
    private String postIntervalDuration;

    @ApiModelProperty(value = "工序关系")
    private ProcessRelationshipEnum processRelationship;

    @ApiModelProperty(value = "缓冲时长。 关系为EE时必填")
    private String bufferTime;

    @ApiModelProperty(value = "批量策略")
    private ProcessBatchStrategyDTO batchStrategy;

    @ApiModelProperty(value = "可用的主资源集合")
    private List<ProcessUseMainResourcesDTO> useMainResources;

    @ApiModelProperty(value = "可用的辅助资源集合")
    private List<ProcessUseAuxiliaryResourcesDTO> useAuxiliaryResources;

    @ApiModelProperty(value = "使用的物料集合")
    private List<ProcessUseMaterialsDTO> useMaterials;
}
