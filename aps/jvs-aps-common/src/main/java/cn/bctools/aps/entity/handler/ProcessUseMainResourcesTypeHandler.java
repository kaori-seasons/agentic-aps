package cn.bctools.aps.entity.handler;

import cn.bctools.aps.entity.dto.ProcessUseMainResourcesDTO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

import java.util.LinkedList;
import java.util.List;

/**
 * @author jvs
 */
public class ProcessUseMainResourcesTypeHandler extends AbstractJsonTypeHandler<List<ProcessUseMainResourcesDTO>> {

    @Override
    protected List<ProcessUseMainResourcesDTO> parse(String json) {
        List<ProcessUseMainResourcesDTO> list = JSON.parseArray(json, ProcessUseMainResourcesDTO.class);
        return new LinkedList<>(list);
    }

    @Override
    protected String toJson(List<ProcessUseMainResourcesDTO> obj) {
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.WriteNullListAsEmpty, JSONWriter.Feature.WriteNullStringAsEmpty);
    }
}
