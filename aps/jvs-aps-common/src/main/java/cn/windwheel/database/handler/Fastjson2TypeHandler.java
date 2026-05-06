package cn.windwheel.database.handler;

import cn.windwheel.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.windwheel.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.hutool.core.lang.Assert;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wl
 */
@Slf4j
@MappedTypes({Object.class})
@MappedJdbcTypes(JdbcType.VARCHAR)
public class Fastjson2TypeHandler extends AbstractJsonTypeHandler<Object> {

    private final Class<?> type;

    public Fastjson2TypeHandler(Class<?> type) {
        if (log.isTraceEnabled()) {
            log.trace("FastjsonTypeHandler(" + type + ")");
        }
        Assert.notNull(type, "Type argument cannot be null");
        this.type = type;
    }

    @Override
    protected Object parse(String json) {
        try {
            return JSON.parseObject(json, type);
        } catch (JSONException ex) {
            // Backward compatibility for legacy process_info where useMainResources was string[].
            if (ProcessRouteNodePropertiesDTO.class.equals(type)) {
                return parseLegacyProcessInfo(json, ex);
            }
            throw ex;
        }
    }

    @Override
    protected String toJson(Object obj) {
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.WriteNullListAsEmpty, JSONWriter.Feature.WriteNullStringAsEmpty);
    }

    private ProcessRouteNodePropertiesDTO parseLegacyProcessInfo(String json, JSONException origin) {
        JSONObject jsonObject = JSON.parseObject(json);
        JSONArray rawResources = jsonObject.getJSONArray("useMainResources");
        if (rawResources != null && rawResources.size() > 0 && rawResources.get(0) instanceof String) {
            List<ProcessUseMainResourcesDTO> normalizedResources = new ArrayList<>();
            for (Object raw : rawResources) {
                if (raw == null) {
                    continue;
                }
                normalizedResources.add(new ProcessUseMainResourcesDTO().setId(String.valueOf(raw)));
            }
            jsonObject.put("useMainResources", normalizedResources);
            return JSON.parseObject(jsonObject.toJSONString(), ProcessRouteNodePropertiesDTO.class);
        }
        throw origin;
    }

}
