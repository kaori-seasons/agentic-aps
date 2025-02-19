package cn.bctools.aps.entity.handler;

import cn.bctools.aps.entity.dto.ProcessUseMaterialsDTO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

import java.util.LinkedList;
import java.util.List;

/**
 * @author jvs
 */
public class ProcessUseMaterialsTypeHandler extends AbstractJsonTypeHandler<List<ProcessUseMaterialsDTO>> {
    @Override
    protected List<ProcessUseMaterialsDTO> parse(String json) {
        List<ProcessUseMaterialsDTO> list = JSON.parseArray(json, ProcessUseMaterialsDTO.class);
        return new LinkedList<>(list);
    }

    @Override
    protected String toJson(List<ProcessUseMaterialsDTO> obj) {
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.WriteNullListAsEmpty, JSONWriter.Feature.WriteNullStringAsEmpty);
    }
}
