package cn.bctools.aps.solve.component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.function.Function;

/**
 * @author jvs
 * 规则字段
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
public class RuleField {
    /**
     * 字段名
     */
    private String fieldName;

    /**
     * getter
     */
    private Function<Object, Comparable> getter;
}
