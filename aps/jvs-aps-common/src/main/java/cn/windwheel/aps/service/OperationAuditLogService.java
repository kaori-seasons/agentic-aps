package cn.windwheel.aps.service;

import cn.windwheel.aps.dto.PageOperationAuditLogDTO;
import cn.windwheel.aps.entity.OperationAuditLogPO;
import cn.windwheel.aps.vo.OperationAuditLogVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 操作审计日志
 */
public interface OperationAuditLogService extends IService<OperationAuditLogPO> {

    /**
     * 保存审计日志
     *
     * @param auditLog 审计日志
     */
    void saveAuditLog(OperationAuditLogPO auditLog);

    /**
     * 分页查询审计日志
     *
     * @param pageQuery 查询条件
     * @return 分页结果
     */
    Page<OperationAuditLogVO> page(PageOperationAuditLogDTO pageQuery);
}
