package cn.bctools.aps.mapper;

import cn.bctools.aps.entity.IncomingMaterialOrderPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author jvs
 * 来料订单
 */
@Mapper
public interface IncomingMaterialPlanMapper extends BaseMapper<IncomingMaterialOrderPO> {

}
