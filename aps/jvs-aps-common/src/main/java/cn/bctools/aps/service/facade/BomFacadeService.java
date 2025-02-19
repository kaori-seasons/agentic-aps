package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.PageManufactureBomDTO;
import cn.bctools.aps.dto.SaveManufactureBomDTO;
import cn.bctools.aps.dto.UpdateManufactureBomDTO;
import cn.bctools.aps.tree.Tree;
import cn.bctools.aps.vo.DetailManufactureBomVO;
import cn.bctools.aps.vo.MaterialBomVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * @author jvs
 * BOM聚合服务
 */
public interface BomFacadeService {

    /**
     * 新增制造BOM
     *
     * @param manufactureBom BOM
     */
    void saveManufactureBom(SaveManufactureBomDTO manufactureBom);

    /**
     * 修改制造BOM
     *
     * @param manufactureBom BOM
     */
    void updateManufactureBom(UpdateManufactureBomDTO manufactureBom);

    /**
     * 分页查询制造BOM
     *
     * @param pageQuery 分页条件
     * @return 分页结果
     */
    Page<DetailManufactureBomVO> pageManufactureBom(PageManufactureBomDTO pageQuery);

    /**
     * 获取BOM详情
     *
     * @param id BOM主键id
     * @return BOM详情
     */
    DetailManufactureBomVO getDetail(String id);

    /**
     * 获取bom树结构
     *
     * @param id BOM主键id
     */
    Tree<MaterialBomVO> treeBom(String id);
}
