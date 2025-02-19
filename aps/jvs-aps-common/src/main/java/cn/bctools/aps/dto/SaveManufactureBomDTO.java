package cn.bctools.aps.dto;

import cn.bctools.aps.entity.dto.BomMaterialDTO;
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
@ApiModel("新增制造BOM")
public class SaveManufactureBomDTO {
    @ApiModelProperty(value = "父件物料id", required = true)
    @NotBlank(message = "未配置父件物料")
    private String materialId;

    @ApiModelProperty(value = "子件物料", required = true)
    @NotNull(message = "未配置子件物料")
    @Valid
    private List<BomMaterialDTO> childMaterials;
}
