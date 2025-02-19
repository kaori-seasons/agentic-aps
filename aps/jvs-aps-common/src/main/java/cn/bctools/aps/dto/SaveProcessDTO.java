package cn.bctools.aps.dto;

import cn.bctools.aps.annotation.DurationFormat;
import cn.bctools.aps.entity.dto.ProcessUseAuxiliaryResourcesDTO;
import cn.bctools.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.bctools.aps.entity.dto.ProcessUseMaterialsDTO;
import cn.bctools.aps.entity.enums.ProcessRelationshipEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("新增工序模板")
public class SaveProcessDTO {
    @ApiModelProperty(value = "工序名称", required = true)
    @NotBlank(message = "工序名称不能为空")
    private String name;

    @ApiModelProperty(value = "工序编码", required = true)
    @NotBlank(message = "工序编码不能为空")
    private String code;

    @ApiModelProperty(value = "前间隔时长")
    @DurationFormat(message = "前间隔时长格式错误")
    private String preIntervalDuration;

    @ApiModelProperty(value = "后间隔时长")
    @DurationFormat(message = "后间隔时长格式错误")
    private String postIntervalDuration;

    @ApiModelProperty(value = "工序关系", required = true)
    @NotNull(message = "工序关系不能为空")
    private ProcessRelationshipEnum processRelationship;

    @ApiModelProperty(value = "缓冲时长。 关系为EE时必填")
    @DurationFormat(message = "缓冲时长格式错误")
    private String bufferTime;

    @ApiModelProperty(value = "可用的主资源集合")
    @Valid
    private List<ProcessUseMainResourcesDTO> useMainResources;

    @ApiModelProperty(value = "可用的辅助资源集合")
    @Valid
    private List<ProcessUseAuxiliaryResourcesDTO> useAuxiliaryResources;

    @ApiModelProperty(value = "使用的物料集合")
    @Valid
    private List<ProcessUseMaterialsDTO> useMaterials;
}
