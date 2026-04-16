package cn.windwheel.aps.service.facade;

import cn.windwheel.aps.dto.PageManufactureBomDTO;
import cn.windwheel.aps.dto.SaveManufactureBomDTO;
import cn.windwheel.aps.dto.UpdateManufactureBomDTO;
import cn.windwheel.aps.tree.Tree;
import cn.windwheel.aps.vo.DetailManufactureBomVO;
import cn.windwheel.aps.vo.MaterialBomVO;
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
