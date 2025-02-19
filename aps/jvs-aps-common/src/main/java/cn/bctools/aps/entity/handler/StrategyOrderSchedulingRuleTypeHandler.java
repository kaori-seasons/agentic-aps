package cn.bctools.aps.entity.handler;

import cn.bctools.aps.entity.dto.planning.StrategyOrderSchedulingRuleDTO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

import java.util.LinkedList;
import java.util.List;

/**
 * @author jvs
 */
public class StrategyOrderSchedulingRuleTypeHandler extends AbstractJsonTypeHandler<List<StrategyOrderSchedulingRuleDTO>> {

    @Override
    protected List<StrategyOrderSchedulingRuleDTO> parse(String json) {
        List<StrategyOrderSchedulingRuleDTO> list = JSON.parseArray(json, StrategyOrderSchedulingRuleDTO.class);
        return new LinkedList<>(list);
    }

    @Override
    protected String toJson(List<StrategyOrderSchedulingRuleDTO> obj) {
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.WriteNullListAsEmpty, JSONWriter.Feature.WriteNullStringAsEmpty);
    }
}
