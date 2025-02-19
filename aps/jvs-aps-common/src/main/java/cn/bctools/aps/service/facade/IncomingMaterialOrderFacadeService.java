package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.PageIncomingMaterialOrderDTO;
import cn.bctools.aps.dto.SaveIncomingMaterialOrderDTO;
import cn.bctools.aps.dto.UpdateIncomingMaterialOrderDTO;
import cn.bctools.aps.vo.DetailIncomingMaterialOrderVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * @author jvs
 * 来料订单聚合服务
 */
public interface IncomingMaterialOrderFacadeService {

    /**
     * 新增来料订单
     *
     * @param incomingMaterialOrder 来料订单
     */
    void save(SaveIncomingMaterialOrderDTO incomingMaterialOrder);

    /**
     * 修改来料订单
     *
     * @param incomingMaterialOrder 来料订单
     */
    void update(UpdateIncomingMaterialOrderDTO incomingMaterialOrder);

    /**
     * 分页查询来料订单
     *
     * @param pageQuery 分页条件
     * @return 分页结果
     */
    Page<DetailIncomingMaterialOrderVO> page(PageIncomingMaterialOrderDTO pageQuery);

    /**
     * 来料订单详情
     *
     * @param id 来料订单主键id
     * @return 来料订单详情
     */
    DetailIncomingMaterialOrderVO getDetail(String id);
}
