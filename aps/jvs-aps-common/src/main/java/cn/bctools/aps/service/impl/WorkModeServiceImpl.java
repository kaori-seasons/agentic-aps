package cn.bctools.aps.service.impl;

import cn.bctools.aps.dto.PageWorkModeDTO;
import cn.bctools.aps.entity.WorkModePO;
import cn.bctools.aps.mapper.WorkModeMapper;
import cn.bctools.aps.service.WorkModeService;
import cn.bctools.aps.vo.DetailWorkModeVO;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author jvs
 * 工作模式
 */
@Service
public class WorkModeServiceImpl extends ServiceImpl<WorkModeMapper, WorkModePO> implements WorkModeService {

    @Override
    public Page<DetailWorkModeVO> page(PageWorkModeDTO pageQuery) {
        LambdaQueryWrapper<WorkModePO> wrapper = Wrappers.<WorkModePO>lambdaQuery()
                .orderByDesc(WorkModePO::getCreateTime);
        Page<WorkModePO> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        page(page, wrapper);
        List<DetailWorkModeVO> voList = BeanCopyUtil.copys(page.getRecords(), DetailWorkModeVO.class);
        Page<DetailWorkModeVO> pageVoList = new Page<>(page.getCurrent(), page.getSize());
        pageVoList
                .setRecords(voList)
                .setTotal(page.getTotal());
        return pageVoList;
    }

    @Override
    public DetailWorkModeVO getDetail(String id) {
        WorkModePO workMode = getById(id);
        if (ObjectNull.isNull(workMode)) {
            return null;
        }
        return BeanCopyUtil.copy(workMode, DetailWorkModeVO.class);
    }
}
