package cn.bctools.aps.service;

import cn.bctools.aps.dto.PageProductionResourceDTO;
import cn.bctools.aps.dto.SaveProductionResourceDTO;
import cn.bctools.aps.dto.UpdateProductionResourceDTO;
import cn.bctools.aps.entity.ProductionResourcePO;
import cn.bctools.aps.vo.DetailProductionResourceVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author jvs
 * 生产资源
 */
public interface ProductionResourceService extends IService<ProductionResourcePO> {

    /**
     * 默认资源组
     */
    String DEFAULT_RESOURCE_GROUP = "未分组";

    /**
     * 新增生产资源
     *
     * @param productionResource 生产资源
     */
    void save(SaveProductionResourceDTO productionResource);

    /**
     * 修改生产资源
     *
     * @param productionResource 生产资源
     */
    void update(UpdateProductionResourceDTO productionResource);

    /**
     * 数据转换
     *
     * @param resource 待转换的数据
     */
    void convertData(ProductionResourcePO resource);

    /**
     * 根据id批量查询，并转为Map
     *
     * @param idList id集合
     * @return Map<id, 资源>
     */
    Map<String, ProductionResourcePO> listByIdsToMap(Collection<String> idList);

    /**
     * 根据资源编码集合查询资源
     *
     * @param resourceCodes 资源编码集合
     * @return 资源集合
     */
    List<ProductionResourcePO> listByCodes(Collection<String> resourceCodes);

    /**
     * 分页查询
     *
     * @param pageQuery 分页条件
     * @return 分页结果
     */
    Page<DetailProductionResourceVO> page(PageProductionResourceDTO pageQuery);

    /**
     * 资源详情
     *
     * @param id 资源id
     * @return 资源详情
     */
    DetailProductionResourceVO getDetail(String id);

    /**
     * 获取资源组
     *
     * @return 资源组
     */
    List<String> listResourceGroups();
}
