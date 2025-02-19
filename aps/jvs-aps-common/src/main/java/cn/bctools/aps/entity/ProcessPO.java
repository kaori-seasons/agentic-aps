package cn.bctools.aps.entity;

import cn.bctools.aps.entity.dto.ProcessBatchStrategyDTO;
import cn.bctools.aps.entity.dto.ProcessUseAuxiliaryResourcesDTO;
import cn.bctools.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.bctools.aps.entity.dto.ProcessUseMaterialsDTO;
import cn.bctools.aps.entity.enums.ProcessRelationshipEnum;
import cn.bctools.aps.entity.handler.ProcessUseAuxiliaryResourcesTypeHandler;
import cn.bctools.aps.entity.handler.ProcessUseMainResourcesTypeHandler;
import cn.bctools.aps.entity.handler.ProcessUseMaterialsTypeHandler;
import cn.bctools.database.entity.po.BasalPo;
import cn.bctools.database.handler.Fastjson2TypeHandler;
import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @author jvs
 * 工序
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName(value = "jvs_process", autoResultMap = true)
public class ProcessPO extends BasalPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 名称
     */
    @TableField("name")
    private String name;

    /**
     * 编码
     */
    @TableField("code")
    private String code;

    /**
     * 前间隔时长
     */
    @TableField(value = "pre_interval_duration", fill = FieldFill.INSERT_UPDATE)
    private String preIntervalDuration;

    /**
     * 后间隔时长
     */
    @TableField(value = "post_interval_duration", fill = FieldFill.INSERT_UPDATE)
    private String postIntervalDuration;

    /**
     * 工序关系
     */
    @TableField("process_relationship")
    private ProcessRelationshipEnum processRelationship;

    /**
     * 关系为EE时的缓冲时长
     */
    @TableField(value = "buffer_time", fill = FieldFill.INSERT_UPDATE)
    private String bufferTime;

    /**
     * 批量策略
     */
    @TableField(value = "batch_strategy", typeHandler = Fastjson2TypeHandler.class)
    private ProcessBatchStrategyDTO batchStrategy;

    /**
     * 可用的主资源集合
     */
    @TableField(value = "use_main_resources", typeHandler = ProcessUseMainResourcesTypeHandler.class)
    private List<ProcessUseMainResourcesDTO> useMainResources;

    /**
     * 可用的辅助资源集合
     */
    @TableField(value = "use_auxiliary_resources", typeHandler = ProcessUseAuxiliaryResourcesTypeHandler.class)
    private List<ProcessUseAuxiliaryResourcesDTO> useAuxiliaryResources;

    /**
     * 使用的物料
     */
    @TableField(value = "use_materials", typeHandler = ProcessUseMaterialsTypeHandler.class)
    private List<ProcessUseMaterialsDTO> useMaterials;

    /**
     * 是否删除 0未删除  1已删除
     */
    @TableField("del_flag")
    private Boolean delFlag;
}
