package cn.bctools.aps.entity.handler;

import cn.bctools.aps.entity.dto.planning.StrategyOptimizeRuleDTO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

import java.util.LinkedList;
import java.util.List;

/**
 * @author jvs
 */
public class StrategyOptimizeRuleTypeHandler extends AbstractJsonTypeHandler<List<StrategyOptimizeRuleDTO>> {

    @Override
    protected List<StrategyOptimizeRuleDTO> parse(String json) {
        List<StrategyOptimizeRuleDTO> list = JSON.parseArray(json, StrategyOptimizeRuleDTO.class);
        return new LinkedList<>(list);
    }

    @Override
    protected String toJson(List<StrategyOptimizeRuleDTO> obj) {
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.WriteNullListAsEmpty, JSONWriter.Feature.WriteNullStringAsEmpty);
    }
}
