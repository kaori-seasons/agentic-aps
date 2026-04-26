package cn.windwheel.aps.mapper;

import cn.windwheel.aps.entity.OperationAuditLogPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作审计日志
 */
@Mapper
public interface OperationAuditLogMapper extends BaseMapper<OperationAuditLogPO> {
}
