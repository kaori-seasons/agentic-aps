package cn.bctools.aps.entity.handler;

import cn.bctools.aps.entity.dto.ReportFieldDTO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

import java.util.LinkedList;
import java.util.List;

/**
 * @author jvs
 */
public class ReportFieldTypeHandler extends AbstractJsonTypeHandler<List<ReportFieldDTO>> {
    @Override
    protected List<ReportFieldDTO> parse(String json) {
        List<ReportFieldDTO> list = JSON.parseArray(json, ReportFieldDTO.class);
        return new LinkedList<>(list);
    }

    @Override
    protected String toJson(List<ReportFieldDTO> obj) {
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.WriteNullListAsEmpty, JSONWriter.Feature.WriteNullStringAsEmpty);
    }
}
