package cn.bctools.aps.service;

import cn.bctools.aps.dto.PageMaterialDTO;
import cn.bctools.aps.dto.SaveMaterialDTO;
import cn.bctools.aps.dto.UpdateMaterialDTO;
import cn.bctools.aps.entity.MaterialPO;
import cn.bctools.aps.vo.DetailMaterialVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author jvs
 * 物料
 */
public interface MaterialService extends IService<MaterialPO> {

    /**
     * 新增物料
     *
     * @param material 物料
     */
    void save(SaveMaterialDTO material);

    /**
     * 修改物料
     *
     * @param material 物料
     */
    void update(UpdateMaterialDTO material);

    /**
     * 校验物料编码是否存在
     *
     * @param materialCode 物料编码
     * @return true-物料编码已存在，false-物料编码不存在
     */
    boolean existsCode(String materialCode);

    /**
     * 根据物料编码集合查询物料
     *
     * @param materialCodes 物料编码集合
     * @return 物料集合
     */
    List<MaterialPO> listByCodes(List<String> materialCodes);

    /**
     * 分页查询物料
     *
     * @param pageQuery  查询条件
     * @return 物料集合
     */
    Page<DetailMaterialVO> page(PageMaterialDTO pageQuery);

    /**
     * 获取物料详情
     *
     * @param id 主键id
     * @return 物料详情
     */
    DetailMaterialVO getDetail(String id);

    /**
     * 根据id批量查询，并转为Map
     *
     * @param idList id集合
     * @return Map<id, 物料>
     */
    Map<String, MaterialPO> listByIdsToMap(Collection<? extends Serializable> idList);

}
