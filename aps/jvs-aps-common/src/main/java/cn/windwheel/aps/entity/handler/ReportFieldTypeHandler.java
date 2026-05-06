package cn.windwheel.aps.entity.handler;

import cn.windwheel.aps.entity.dto.ReportFieldDTO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
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
        JSONArray rawArray = JSON.parseArray(json);
        List<ReportFieldDTO> list = new LinkedList<>();
        for (Object item : rawArray) {
            if (item == null) {
                continue;
            }
            if (item instanceof String key) {
                list.add(new ReportFieldDTO().setFieldKey(key).setFieldName(key));
                continue;
            }
            list.add(JSON.parseObject(JSON.toJSONString(item), ReportFieldDTO.class));
        }
        return new LinkedList<>(list);
    }

    @Override
    protected String toJson(List<ReportFieldDTO> obj) {
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.WriteNullListAsEmpty, JSONWriter.Feature.WriteNullStringAsEmpty);
    }
}
