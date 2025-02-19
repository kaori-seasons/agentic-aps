package cn.bctools.aps.service;

import cn.bctools.aps.dto.PageProductionOrderDTO;
import cn.bctools.aps.dto.UpdateOrderSortDTO;
import cn.bctools.aps.entity.ProductionOrderPO;
import cn.bctools.aps.vo.DetailProductionOrderVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author jvs
 * 生产订单
 */
public interface ProductionOrderService extends IService<ProductionOrderPO> {

    /**
     * 删除生产订单
     *
     * @param id 生产订单主键id
     */
    void delete(String id);

    /**
     * 修改订单排序
     *
     * @param updateOrderSort 订单数据
     */
    void updateOrderSort(UpdateOrderSortDTO updateOrderSort);

    /**
     * 分页查询生产订单
     *
     * @param pageQuery 分页条件
     * @return 分页结果
     */
    Page<DetailProductionOrderVO> page(PageProductionOrderDTO pageQuery);

    /**
     * 获取生产订单详情
     *
     * @param id 生产订单主键id
     * @return 生产订单详情
     */
    DetailProductionOrderVO getDetail(String id);

    /**
     * 修改生产订单排产状态
     *
     * @param id 订单id
     * @param canSchedule true-参与排产，false-不参与排产
     */
    void updateCanSchedule(String id, Boolean canSchedule);

    /**
     * 根据订单编码查询订单
     *
     * @param codes 订单编码集合
     * @return 订单编码集合
     */
    List<ProductionOrderPO> listByCodes(Collection<String> codes);

    /**
     * 查询订单是否有补充生产订单
     *
     * @param codes 订单编码集合
     * @return Map<订单编码, true-有补充生产订单；false-无补充生产订单>
     */
    Map<String, Boolean> supplementByCodes(Collection<String> codes);

    /**
     * 查询补充生产订单集合
     *
     * @param id 订单id
     * @return 补充生产订单集合
     */
    List<DetailProductionOrderVO> listSupplementOrder(String id);
}
