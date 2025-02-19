package cn.bctools.aps.mapper;

import cn.bctools.aps.entity.ProductionOrderPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author jvs
 * 生产订单
 */
@Mapper
public interface ProductionOrderMapper extends BaseMapper<ProductionOrderPO> {

}
