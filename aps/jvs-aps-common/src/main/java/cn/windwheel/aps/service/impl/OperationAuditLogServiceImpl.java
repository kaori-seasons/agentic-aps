package cn.windwheel.aps.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.windwheel.aps.dto.PageOperationAuditLogDTO;
import cn.windwheel.aps.entity.OperationAuditLogPO;
import cn.windwheel.aps.mapper.OperationAuditLogMapper;
import cn.windwheel.aps.service.OperationAuditLogService;
import cn.windwheel.aps.vo.OperationAuditLogVO;
import cn.windwheel.common.utils.BeanCopyUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 操作审计日志
 */
@Service
public class OperationAuditLogServiceImpl extends ServiceImpl<OperationAuditLogMapper, OperationAuditLogPO> implements OperationAuditLogService {

    @Override
    public void saveAuditLog(OperationAuditLogPO auditLog) {
        if (auditLog == null) {
            return;
        }
        save(auditLog);
    }

    @Override
    public Page<OperationAuditLogVO> page(PageOperationAuditLogDTO pageQuery) {
        Page<OperationAuditLogPO> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        page(page, Wrappers.<OperationAuditLogPO>lambdaQuery()
                .like(StrUtil.isNotBlank(pageQuery.getUsername()), OperationAuditLogPO::getUsername, pageQuery.getUsername())
                .eq(StrUtil.isNotBlank(pageQuery.getUserId()), OperationAuditLogPO::getUserId, pageQuery.getUserId())
                .like(StrUtil.isNotBlank(pageQuery.getOperationName()), OperationAuditLogPO::getOperationName, pageQuery.getOperationName())
                .eq(StrUtil.isNotBlank(pageQuery.getPermissionCode()), OperationAuditLogPO::getPermissionCode, pageQuery.getPermissionCode())
                .eq(pageQuery.getSuccess() != null, OperationAuditLogPO::getSuccess, pageQuery.getSuccess())
                .ge(pageQuery.getStartTime() != null, OperationAuditLogPO::getCreateTime, pageQuery.getStartTime())
                .le(pageQuery.getEndTime() != null, OperationAuditLogPO::getCreateTime, pageQuery.getEndTime())
                .orderByDesc(OperationAuditLogPO::getCreateTime));

        List<OperationAuditLogVO> records = BeanCopyUtil.copys(page.getRecords(), OperationAuditLogVO.class);
        return new Page<OperationAuditLogVO>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(records);
    }
}
