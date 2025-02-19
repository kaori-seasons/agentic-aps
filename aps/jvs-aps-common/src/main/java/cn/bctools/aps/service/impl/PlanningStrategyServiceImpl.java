package cn.bctools.aps.service.impl;

import cn.bctools.aps.dto.PagePlanningStrategyDTO;
import cn.bctools.aps.dto.SavePlanningStrategyDTO;
import cn.bctools.aps.dto.UpdatePlanningStrategyDTO;
import cn.bctools.aps.entity.PlanningStrategyPO;
import cn.bctools.aps.mapper.PlanningStrategyMapper;
import cn.bctools.aps.service.PlanningStrategyService;
import cn.bctools.aps.solve.component.InitSchedulingRuleDataComponent;
import cn.bctools.aps.vo.DetailPlanningStrategyVO;
import cn.bctools.aps.vo.StrategyOrderSchedulingRuleOptionVO;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author jvs
 */
@Service
public class PlanningStrategyServiceImpl extends ServiceImpl<PlanningStrategyMapper, PlanningStrategyPO> implements PlanningStrategyService {

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void save(SavePlanningStrategyDTO savePlanningStrategy) {
        PlanningStrategyPO planningStrategy = BeanCopyUtil.copy(savePlanningStrategy, PlanningStrategyPO.class);
        save(planningStrategy);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void update(UpdatePlanningStrategyDTO updatePlanningStrategy) {
        PlanningStrategyPO planningStrategy = BeanCopyUtil.copy(updatePlanningStrategy, PlanningStrategyPO.class);
        updateById(planningStrategy);
    }

    @Override
    public Page<DetailPlanningStrategyVO> page(PagePlanningStrategyDTO pageQuery) {
        LambdaQueryWrapper<PlanningStrategyPO> wrapper = Wrappers.<PlanningStrategyPO>lambdaQuery()
                .eq(ObjectNull.isNotNull(pageQuery.getActive()), PlanningStrategyPO::getActive, pageQuery.getActive())
                .like(ObjectNull.isNotNull(pageQuery.getName()), PlanningStrategyPO::getName, pageQuery.getName())
                .orderByDesc(PlanningStrategyPO::getCreateTime);
        Page<PlanningStrategyPO> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        page(page, wrapper);
        List<DetailPlanningStrategyVO> voList = BeanCopyUtil.copys(page.getRecords(), DetailPlanningStrategyVO.class);
        Page<DetailPlanningStrategyVO> pageVoList = new Page<>(page.getCurrent(), page.getSize());
        pageVoList
                .setRecords(voList)
                .setTotal(page.getTotal());
        return pageVoList;
    }

    @Override
    public DetailPlanningStrategyVO getDetail(String id) {
        PlanningStrategyPO planningStrategy = getById(id);
        if (ObjectNull.isNull(planningStrategy)) {
            return null;
        }
        return BeanCopyUtil.copy(planningStrategy, DetailPlanningStrategyVO.class);
    }

    @Override
    public List<StrategyOrderSchedulingRuleOptionVO> listStrategyOrderSchedulingRuleOption() {
        return InitSchedulingRuleDataComponent.getRuleFieldMap()
                .entrySet()
                .stream()
                .map(e ->
                        new StrategyOrderSchedulingRuleOptionVO()
                                .setFieldKey(e.getKey())
                                .setFieldName(e.getValue().getFieldName()))
                .toList();
    }

    @Override
    public void updateActive(String id, Boolean active) {
        if (ObjectNull.isNull(active)) {
            return;
        }
        update(Wrappers.<PlanningStrategyPO>lambdaUpdate()
                .set(PlanningStrategyPO::getActive, active)
                .eq(PlanningStrategyPO::getId, id));
    }
}
