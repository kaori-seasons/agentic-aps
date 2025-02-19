package cn.bctools.aps.entity.handler;

import cn.bctools.aps.entity.dto.BomMaterialDTO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

import java.util.LinkedList;
import java.util.List;

/**
 * @author jvs
 */
public class ChildMaterialTypeHandler extends AbstractJsonTypeHandler<List<BomMaterialDTO>> {
    @Override
    protected List<BomMaterialDTO> parse(String json) {
        List<BomMaterialDTO> list = JSON.parseArray(json, BomMaterialDTO.class);
        return new LinkedList<>(list);
    }

    @Override
    protected String toJson(List<BomMaterialDTO> obj) {
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.WriteNullListAsEmpty, JSONWriter.Feature.WriteNullStringAsEmpty);
    }
}
