package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.*;
import cn.bctools.aps.vo.DetailProcessVO;
import cn.bctools.aps.vo.MaterialProcessRouteVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * @author jvs
 * 工艺聚合服务
 */
public interface ProcessFacadeService {

    /**
     * 新增工序
     *
     * @param process 工序
     */
    void saveProcess(SaveProcessDTO process);

    /**
     * 修改工序
     *
     * @param process 工序
     */
    void updateProcess(UpdateProcessDTO process);

    /**
     * 分页查询工序
     *
     * @param pageQuery 分页条件
     * @return 分页结果
     */
    Page<DetailProcessVO> pageProcess(PageProcessDTO pageQuery);

    /**
     * 获取工序详细信息
     *
     * @param id 工序id
     * @return 工序详细信息
     */
    DetailProcessVO getProcessDetail(String id);

    /**
     * 新增工艺路线
     *
     * @param processRoute 工艺路线
     */
    void saveProcessRoute(SaveProcessRouteDTO processRoute);

    /**
     * 修改工艺路线
     *
     * @param processRoute 工艺路线
     */
    void updateProcessRoute(UpdateProcessRouteDTO processRoute);

    /**
     * 查询指定物料的工艺路线
     *
     * @param materialId 物料id
     * @return 工艺路线
     */
    MaterialProcessRouteVO getMaterialProcessRoute(String materialId);
}
