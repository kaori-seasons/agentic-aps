package cn.bctools.common.utils;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.beans.BeanMap;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * bean > map
 * map > bean
 * json > bean
 * bean > bean
 * bean<T> > list<T>
 * <p>
 * MAP 转 bean ：JSON.to成功，JSON.copyTo失败
 * JSON字符串 转 bean : JSON.to成功，JSON.copyTo失败
 * bean 转 bean : JSON.to失败，JSON.copyTo成功
 *
 * @author guojing
 * @describe
 */
@Slf4j
public class BeanCopyUtil {

    /**
     * @describe 把数据集转换为另一种数据集
     * @author guojing
     * @returnType
     */
    public static <T> List<T> copys(List source, Class<T> cls) {
        return (List<T>) source.stream().map(e -> copy(e, cls)).collect(Collectors.toList());
    }

    /**
     * @param source 源对象
     * @param cls    目标对象
     * @param <T>
     * @return
     */
    public static <T> T copy(Object source, Class<T> cls) {
        if (source instanceof Map) {
            return JSON.to(cls, source);
        } else if (source instanceof String) {
            return JSON.to(cls, source);
        } else {
            return JSON.to(cls, JSON.toJSONString(source));
        }
    }

    /**
     * @param cls    目标对象
     * @param source 每一个对象，按顺序copy
     * @param <T>
     * @return
     */
    public static <T> T copy(Class<T> cls, Object... source) {
        if (source.length == 1) {
            return copy(source[0], cls);
        }
        T o = null;
        try {
            o = cls.newInstance();
            for (Object o1 : source) {
                BeanUtil.copyProperties(o1, o);
            }
            return o;
        } catch (InstantiationException e) {
            log.info("转化错误", e);
        } catch (IllegalAccessException e) {
            log.info("转化错误", e);
        }
        return o;
    }


    /**
     * 将对象装换为map
     *
     * @param bean
     * @return
     */
    public static <T> Map<String, Object> beanToMap(T bean) {
        Map<String, Object> map = new HashMap<>(5);
        if (bean != null) {
            BeanMap beanMap = BeanMap.create(bean);
            for (Object key : beanMap.keySet()) {
                Object value = beanMap.get(key);
                if (value != null) {
                    map.put(key + "", value);
                }
            }
        }
        return map;
    }

    /**
     * 将List<T>转换为List<Map<String, Object>>
     *
     * @param objList
     * @return
     */
    public static <T> List<Map<String, Object>> objectsToMaps(List<T> objList) {
        List<Map<String, Object>> list = new ArrayList<>(5);
        if (objList != null && objList.size() > 0) {
            Map<String, Object> map = null;
            T bean = null;
            for (int i = 0, size = objList.size(); i < size; i++) {
                bean = objList.get(i);
                map = beanToMap(bean);
                list.add(map);
            }
        }
        return list;
    }

    /**
     * List深拷贝 条件:需要model实现Serializable
     */
    public static <T> List<T> deepCopy(List<T> t) {
        try {
            // 写入字节流
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(byteOut);
            out.writeObject(t);

            ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
            ObjectInputStream in = new ObjectInputStream(byteIn);
            return (List<T>) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.info("deepCopy failure!! error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 对象深拷贝 条件:需要model实现Serializable
     */
    public static <T> T deepCopy(T t) {
        if (t instanceof List) {
            return (T) deepCopy((List) t);
        }
        try {
            // 写入字节流
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(t);

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.info("deepCopy failure!! error={}", e.getMessage());
            return null;
        }
    }

}
