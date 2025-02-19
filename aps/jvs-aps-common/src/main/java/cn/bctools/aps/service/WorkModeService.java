package cn.bctools.aps.service;

import cn.bctools.aps.dto.PageWorkModeDTO;
import cn.bctools.aps.entity.WorkModePO;
import cn.bctools.aps.vo.DetailWorkModeVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author jvs
 * 工作模式
 */
public interface WorkModeService extends IService<WorkModePO> {

    /**
     * 分页查询工作模式
     *
     * @param pageQuery 分页条件
     * @return 分页结果
     */
    Page<DetailWorkModeVO> page(PageWorkModeDTO pageQuery);

    /**
     * 获取工作模式详情
     *
     * @param id 工作模式主键id
     * @return 工作模式详情
     */
    DetailWorkModeVO getDetail(String id);
}
