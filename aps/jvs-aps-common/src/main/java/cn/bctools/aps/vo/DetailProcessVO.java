package cn.bctools.aps.vo;

import cn.bctools.aps.entity.dto.ProcessBatchStrategyDTO;
import cn.bctools.aps.entity.enums.ProcessRelationshipEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("工序详情")
public class DetailProcessVO extends BaseVO {
    @ApiModelProperty(value = "工序名称")
    private String name;

    @ApiModelProperty(value = "工序编码")
    private String code;

    @ApiModelProperty(value = "前间隔时长")
    private String preIntervalDuration;

    @ApiModelProperty(value = "后间隔时长")
    private String postIntervalDuration;

    @ApiModelProperty(value = "工序关系")
    private ProcessRelationshipEnum processRelationship;

    @ApiModelProperty(value = "缓冲时长")
    private String bufferTime;

    @ApiModelProperty(value = "批量策略")
    private ProcessBatchStrategyDTO batchStrategy;

    @ApiModelProperty(value = "可用的主资源集合")
    private List<ProcessUseMainResourcesVO> useMainResources;

    @ApiModelProperty(value = "可用的辅助资源集合")
    private List<ProcessUseAuxiliaryResourcesVO> useAuxiliaryResources;

    @ApiModelProperty(value = "使用的物料集合")
    private List<ProcessUseMaterialsVO> useMaterials;
}
