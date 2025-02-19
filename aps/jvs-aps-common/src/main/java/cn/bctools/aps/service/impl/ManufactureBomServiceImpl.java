package cn.bctools.aps.service.impl;

import cn.bctools.aps.entity.ManufactureBomPO;
import cn.bctools.aps.mapper.ManufactureBomMapper;
import cn.bctools.aps.service.ManufactureBomService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author jvs
 */
@Service
@AllArgsConstructor
public class ManufactureBomServiceImpl extends ServiceImpl<ManufactureBomMapper, ManufactureBomPO> implements ManufactureBomService {

}
