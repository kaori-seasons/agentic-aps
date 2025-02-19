package cn.bctools.aps.entity.handler;

import cn.bctools.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.bctools.aps.graph.Graph;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

/**
 * @author jvs
 */
public class ProcessRouteDesignTypeHandler extends AbstractJsonTypeHandler<Graph<ProcessRouteNodePropertiesDTO>> {
    @Override
    protected Graph<ProcessRouteNodePropertiesDTO> parse(String json) {
        return JSON.parseObject(json, new TypeReference<>() {});
    }

    @Override
    protected String toJson(Graph<ProcessRouteNodePropertiesDTO> obj) {
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.WriteNullListAsEmpty, JSONWriter.Feature.WriteNullStringAsEmpty);
    }
}
