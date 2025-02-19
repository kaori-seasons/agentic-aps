package cn.bctools.aps.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 工序关系
 */
@Getter
@AllArgsConstructor
public enum ProcessRelationshipEnum {
    /**
     * 前工序结束，后工序才能开始
     */
    ES("ES"),
    /**
     * 前工序未结束，后工序可以提前开始
     */
    EE("EE"),
    ;

    @EnumValue
    @JsonValue
    private String value;
}
