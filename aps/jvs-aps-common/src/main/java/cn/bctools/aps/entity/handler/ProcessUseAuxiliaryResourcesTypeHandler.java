package cn.bctools.aps.entity.handler;

import cn.bctools.aps.entity.dto.ProcessUseAuxiliaryResourcesDTO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

import java.util.LinkedList;
import java.util.List;

/**
 * @author jvs
 */
public class ProcessUseAuxiliaryResourcesTypeHandler extends AbstractJsonTypeHandler<List<ProcessUseAuxiliaryResourcesDTO>> {
    @Override
    protected List<ProcessUseAuxiliaryResourcesDTO> parse(String json) {
        List<ProcessUseAuxiliaryResourcesDTO> list = JSON.parseArray(json, ProcessUseAuxiliaryResourcesDTO.class);
        return new LinkedList<>(list);
    }

    @Override
    protected String toJson(List<ProcessUseAuxiliaryResourcesDTO> obj) {
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.WriteNullListAsEmpty, JSONWriter.Feature.WriteNullStringAsEmpty);
    }
}
