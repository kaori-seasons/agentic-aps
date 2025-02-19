package cn.bctools.aps.entity.handler;

import cn.bctools.aps.entity.dto.plan.PlanTaskInputMaterialDTO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

import java.util.LinkedList;
import java.util.List;

/**
 * @author jvs
 */
public class PlanTaskInputMaterialTypeHandler extends AbstractJsonTypeHandler<List<PlanTaskInputMaterialDTO>> {
    @Override
    protected List<PlanTaskInputMaterialDTO> parse(String json) {
        List<PlanTaskInputMaterialDTO> list = JSON.parseArray(json, PlanTaskInputMaterialDTO.class);
        return new LinkedList<>(list);
    }

    @Override
    protected String toJson(List<PlanTaskInputMaterialDTO> obj) {
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.WriteNullListAsEmpty, JSONWriter.Feature.WriteNullStringAsEmpty);
    }
}
