package cn.bctools.aps.solve.component;

import cn.bctools.aps.annotation.Comment;
import cn.bctools.aps.solve.model.ProductionOrder;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author jvs
 * 排产规则-数据初始化
 */
@Component
public class InitSchedulingRuleDataComponent {

    /**
     * 规则字段
     * Map<字段key, 字段信息>
     */
    private static final Map<String, RuleField> RULE_FIELD_MAP = new LinkedHashMap<>();

    /**
     * 需要排除的字段
     */
    private static final List<String> EXCLUDE_FIELDS = Stream.of(
            "serialVersionUID",
                    "id",
                    "delFlag",
                    "orderStatus",
                    "schedulingStatus",
                    "type",
                    "canSchedule",
                    "color",
                    "supplement",
                    "parentOrderCode")
            .toList();

    /**
     * 默认字段描述
     */
    private static final Map<String, String> DEFAULT_FIELD_COMMENT = Map.of(
            "createTime", "创建时间",
            "updateTime", "修改时间"
    );

    @PostConstruct
    public void init() {
        RULE_FIELD_MAP.putAll(generateFieldMap(ProductionOrder.class));
    }

    /**
     * 获取规则字段
     *
     * @return 规则字段
     */
    public static Map<String, RuleField> getRuleFieldMap() {
        return RULE_FIELD_MAP;
    }

    /**
     * 获取规则字段
     *
     * @param clazz class对象
     * @return 规则字段
     */
    private Map<String, RuleField> generateFieldMap(Class<?> clazz) {
        Map<String, RuleField> fieldMap = new LinkedHashMap<>();
        List<Field> fields = getAllFields(new ArrayList<>(), clazz);
        for (Field field : fields) {
            String fieldKey = field.getName();
            if (EXCLUDE_FIELDS.contains(fieldKey)) {
                continue;
            }
            String fieldName = getFieldName(field);
            if (ObjectNull.isNull(fieldName)) {
                continue;
            }

            Method getterMethod = getGetterMethod(clazz, fieldKey);
            if (ObjectNull.isNull(getterMethod)) {
                continue;
            }
            Function<Object, Comparable> functionGetter = obj -> {
                try {
                    Object objValue = getterMethod.invoke(obj);
                    if (objValue instanceof Comparable value) {
                        return value;
                    }
                    return null;
                } catch (Exception e) {
                    throw new BusinessException(e.getMessage());
                }
            };
            if (ObjectNull.isNull(functionGetter)) {
                continue;
            }
            fieldMap.put(fieldKey, new RuleField(fieldName, functionGetter));
        }
        return fieldMap;
    }

    /**
     * 获取所有字段
     *
     * @param fields 字段集合
     * @param clazz class对象
     * @return 所有字段
     */
    private List<Field> getAllFields(List<Field> fields, Class<?> clazz) {
        fields.addAll(Arrays.stream(clazz.getDeclaredFields()).toList());
        if (ObjectNull.isNotNull(clazz.getSuperclass())) {
            getAllFields(fields, clazz.getSuperclass());
        }
        return fields;
    }


    /**
     * 获取字段名
     * <p>
     *     用@Comment注解的值作为字段的名称
     *
     * @param field 字段
     * @return 字段名
     */
    private String getFieldName(Field field) {
        String defaultFieldComment = DEFAULT_FIELD_COMMENT.get(field.getName());
        if (ObjectNull.isNotNull(defaultFieldComment)) {
            return defaultFieldComment;
        }
        Comment annotation = field.getAnnotation(Comment.class);
        return ObjectNull.isNotNull(annotation) ? annotation.value() : "";
    }

    /**
     * 获取字段的getter方法
     *
     * @param clazz class对昂
     * @param fieldKey 字段key
     * @return 字段的getter方法
     */
    private Method getGetterMethod(Class<?> clazz, String fieldKey) {
        try {
            String getterName = "get" + Character.toUpperCase(fieldKey.charAt(0)) + fieldKey.substring(1);
            return clazz.getMethod(getterName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
