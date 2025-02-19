package cn.bctools.aps.service;

import cn.bctools.aps.dto.PagePlanningStrategyDTO;
import cn.bctools.aps.dto.SavePlanningStrategyDTO;
import cn.bctools.aps.dto.UpdatePlanningStrategyDTO;
import cn.bctools.aps.entity.PlanningStrategyPO;
import cn.bctools.aps.vo.DetailPlanningStrategyVO;
import cn.bctools.aps.vo.StrategyOrderSchedulingRuleOptionVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author jvs
 * 排产计划策略
 */
public interface PlanningStrategyService extends IService<PlanningStrategyPO> {

    /**
     * 新增策略
     *
     * @param planningStrategy 策略
     */
    void save(SavePlanningStrategyDTO planningStrategy);

    /**
     * 修改策略
     *
     * @param planningStrategy 策略
     */
    void update(UpdatePlanningStrategyDTO planningStrategy);

    /**
     * 分页查询
     *
     * @param pageQuery 分页条件
     * @return 分页结果
     */
    Page<DetailPlanningStrategyVO> page(PagePlanningStrategyDTO pageQuery);

    /**
     * 获取策略详情
     *
     * @param id 策略主键id
     * @return 策略详情
     */
    DetailPlanningStrategyVO getDetail(String id);

    /**
     * 获取订单规则可选项
     *
     * @return 订单规则可选项
     */
    List<StrategyOrderSchedulingRuleOptionVO> listStrategyOrderSchedulingRuleOption();

    /**
     * 修改策略有效状态
     *
     * @param id 策略id
     * @param active false-无效，true-有效
     */
    void updateActive(String id, Boolean active);
}
