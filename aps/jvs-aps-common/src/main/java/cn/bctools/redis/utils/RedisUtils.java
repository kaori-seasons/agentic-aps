package cn.bctools.redis.utils;

import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationUtils;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 可以使用${@link cn.bctools.common.constant.SysConstant#redisKey(String, String) 方法规范redis的key }
 * 或者使用${@link RedisUtils#setPrefix(String)} 设置统一的key前缀
 * 1. Key的命名应该具有可读性，方便开发者理解和维护。可以使用类似于命名空间的方式，将相关的key放在同一个前缀下，例如"user:id"和"order:id"。
 * <p>
 * 2. Key的命名应该尽量简短，以减少内存占用和提高查询效率。
 * <p>
 * 3. Key的命名应该避免使用特殊字符和空格，可以使用下划线或者破折号代替。
 * <p>
 * 4. Key的命名应该避免使用过于具体的词汇，例如"order:2021-01-01:customer:123"，应该改为"order:123"，并将日期信息存储在其他字段中。
 *
 * @author guojing
 */
@Slf4j
public class RedisUtils {

    @Autowired
    @SuppressWarnings("all")
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisLockUtil redisLockUtil;
    /**
     * 所有key的前缀
     */
    @Getter
    public static String prefix = "jvs:";

    /**
     * 设置redis前缀进行操作
     *
     * @param prefix
     */
    public static void setPrefix(String prefix) {
        RedisUtils.prefix = prefix;
    }

    /**
     * 判断是否有这个缓存key
     *
     * @param key 缓存key
     * @author: guojing
     * @return: boolean
     */
    public boolean exists(final String key) {
        return redisTemplate.hasKey(prefix + key);
    }

    public Set<String> keys(final String pattern) {
        return redisTemplate.keys(prefix + pattern);
    }

    /**
     * 指定缓存失效时间
     *
     * @param key  键
     * @param time 时间(秒)
     * @return Boolean
     */
    public void expire(String key, int time, TimeUnit timeUnit) {
        redisTemplate.expire(prefix + key, time, timeUnit);
    }

    public Boolean expire(String key, Long time) {
        try {
            if (time > 0) {
                redisTemplate.expire(prefix + key, time, TimeUnit.SECONDS);
            }
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 添加到带有 过期时间的  缓存
     *
     * @param key      redis主键
     * @param value    值
     * @param time     过期时间
     * @param timeUnit 过期时间单位
     */
    public void setExpire(final String key, final Object value, final long time, final TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(prefix + key, value, time, timeUnit);
    }

    public void setExpire(final String key, final Object value, final long time, final TimeUnit timeUnit, RedisSerializer<Object> valueSerializer) {
        byte[] rawKey = rawKey(prefix + key);
        byte[] rawValue = rawValue(value, valueSerializer);

        redisTemplate.execute(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                potentiallyUsePsetEx(connection);
                return null;
            }

            public void potentiallyUsePsetEx(RedisConnection connection) {
                if (!TimeUnit.MILLISECONDS.equals(timeUnit) || !failsafeInvokePsetEx(connection)) {
                    connection.setEx(rawKey, TimeoutUtils.toSeconds(time, timeUnit), rawValue);
                }
            }

            private boolean failsafeInvokePsetEx(RedisConnection connection) {
                boolean failed = false;
                try {
                    connection.pSetEx(rawKey, time, rawValue);
                } catch (UnsupportedOperationException e) {
                    failed = true;
                }
                return !failed;
            }
        }, true);
    }

    /**
     * 根据key获取过期时间
     *
     * @param key 键 不能为 null
     * @return 时间(秒) 返回 0代表为永久有效
     */
    public Long getExpire(String key) {
        return redisTemplate.getExpire(prefix + key, TimeUnit.SECONDS);
    }

    /**
     * 判断 key是否存在
     *
     * @param key 键
     * @return true 存在 false不存在
     */
    public Boolean hasKey(String key) {
        try {
            return redisTemplate.hasKey(prefix + key);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 删除缓存
     *
     * @param key 可以传一个值 或多个
     */
    public void del(String... key) {
        if (key != null && key.length > 0) {
            if (key.length == 1) {
                redisTemplate.delete(prefix + key[0]);
            } else {
                redisTemplate.delete(Arrays.asList(key).stream().map(e -> prefix + e).collect(Collectors.toList()));
            }
        }
    }

    public void del(Collection<String> keys) {
        redisTemplate.delete(keys.stream().map(e -> prefix + e).collect(Collectors.toList()));
    }

    /**
     * 普通缓存获取
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(prefix + key);
    }

    /**
     * 根据key获取对象
     *
     * @param key             the key
     * @param valueSerializer 序列化
     * @return the string
     */
    public Object get(final String key, RedisSerializer<Object> valueSerializer) {
        byte[] rawKey = rawKey(prefix + key);
        return redisTemplate.execute(connection -> deserializeValue(connection.get(rawKey), valueSerializer), true);
    }

    /**
     * 普通缓存放入
     *
     * @param key   键
     * @param value 值
     * @return true成功 false失败
     */
    public Boolean set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(prefix + key, value);
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 普通缓存放入并设置时间
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒) time要大于0 如果time小于等于0 将设置无限期
     * @return true成功 false 失败
     */
    public Boolean set(String key, Object value, Long time) {
        try {
            if (time > 0) {
                redisTemplate.opsForValue().set(prefix + key, value, time, TimeUnit.SECONDS);
            } else {
                set(key, value);
            }
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 普通缓存放入并设置时间
     *
     * @param key   键
     * @param value 值
     * @return true成功 false 失败
     */
    public Boolean set(String key, Object value, Duration timeout) {

        try {
            Assert.notNull(timeout, "Timeout must not be null!");
            if (TimeoutUtils.hasMillis(timeout)) {
                redisTemplate.opsForValue().set(prefix + key, value, timeout.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                redisTemplate.opsForValue().set(prefix + key, value, timeout.getSeconds(), TimeUnit.SECONDS);
            }
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 递增
     *
     * @param key   键
     * @param delta 要增加几(大于0)
     * @return Long
     */
    public Long incr(String key, Long delta) {
        return redisTemplate.opsForValue().increment(prefix + key, delta);
    }

    /**
     * 递减
     *
     * @param key   键
     * @param delta 要减少几(小于0)
     * @return Long
     */
    public Long decr(String key, Long delta) {
        return redisTemplate.opsForValue().increment(prefix + key, -delta);
    }

    /**
     * HashGet
     *
     * @param key  键 不能为 null
     * @param item 项 不能为 null
     * @return 值
     */
    public Object hget(String key, String item) {
        return redisTemplate.opsForHash().get(prefix + key, item);
    }

    /**
     * 获取 hashKey对应的所有键值
     *
     * @param key 键
     * @return 对应的多个键值
     */
    public Map<Object, Object> hmget(String key) {
        return redisTemplate.opsForHash().entries(prefix + key);
    }

    /**
     * HashSet
     *
     * @param key 键
     * @param map 对应多个键值
     * @return true 成功 false 失败
     */
    public Boolean hmset(String key, Map<String, Object> map) {
        try {
            redisTemplate.opsForHash().putAll(prefix + key, map);
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * HashSet 并设置时间
     *
     * @param key  键
     * @param map  对应多个键值
     * @param time 时间(秒)
     * @return true成功 false失败
     */
    public Boolean hmset(String key, Map<String, Object> map, Long time) {
        try {
            redisTemplate.opsForHash().putAll(prefix + key, map);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     *
     * @param key   键
     * @param item  项
     * @param value 值
     * @return true 成功 false失败
     */
    public Boolean hset(String key, String item, Object value) {
        try {
            redisTemplate.opsForHash().put(prefix + key, item, value);
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     *
     * @param key   键
     * @param item  项
     * @param value 值
     * @param time  时间(秒) 注意:如果已存在的hash表有时间,这里将会替换原有的时间
     * @return true 成功 false失败
     */
    public Boolean hset(String key, String item, Object value, Long time) {
        try {
            redisTemplate.opsForHash().put(prefix + key, item, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 删除hash表中的值
     *
     * @param key  键 不能为 null
     * @param item 项 可以使多个不能为 null
     */
    public void hdel(String key, Object... item) {
        redisTemplate.opsForHash().delete(prefix + key, item);
    }

    /**
     * 判断hash表中是否有该项的值
     *
     * @param key  键 不能为 null
     * @param item 项 不能为 null
     * @return true 存在 false不存在
     */
    public Boolean hHasKey(String key, String item) {
        return redisTemplate.opsForHash().hasKey(prefix + key, item);
    }

    /**
     * hash递增 如果不存在,就会创建一个 并把新增后的值返回
     *
     * @param key  键
     * @param item 项
     * @param by   要增加几(大于0)
     * @return Double
     */
    public Double hincr(String key, String item, Double by) {
        return redisTemplate.opsForHash().increment(prefix + key, item, by);
    }

    /**
     * hash递减
     *
     * @param key  键
     * @param item 项
     * @param by   要减少记(小于0)
     * @return Double
     */
    public Double hdecr(String key, String item, Double by) {
        return redisTemplate.opsForHash().increment(prefix + key, item, -by);
    }

    /**
     * 根据 key获取 Set中的所有值
     *
     * @param key 键
     * @return Set
     */
    public Set<Object> sGet(String key) {
        try {
            return redisTemplate.opsForSet().members(prefix + key);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据value从一个set中查询,是否存在
     *
     * @param key   键
     * @param value 值
     * @return true 存在 false不存在
     */
    public Boolean sHasKey(String key, Object value) {
        try {
            return redisTemplate.opsForSet().isMember(prefix + key, value);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 将数据放入set缓存
     *
     * @param key    键
     * @param values 值 可以是多个
     * @return 成功个数
     */
    public Long sSet(String key, Object... values) {
        try {
            return redisTemplate.opsForSet().add(prefix + key, values);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 将set数据放入缓存
     *
     * @param key    键
     * @param time   时间(秒)
     * @param values 值 可以是多个
     * @return 成功个数
     */
    public Long sSetAndTime(String key, Long time, Object... values) {
        try {
            Long count = redisTemplate.opsForSet().add(prefix + key, values);
            if (time > 0) {
                expire(key, time);
            }
            return count;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 获取set缓存的长度
     *
     * @param key 键
     * @return Long
     */
    public Long sGetSetSize(String key) {
        try {
            return redisTemplate.opsForSet().size(prefix + key);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 移除值为value的
     *
     * @param key    键
     * @param values 值 可以是多个
     * @return 移除的个数
     */
    public Long setRemove(String key, Object... values) {
        try {
            return redisTemplate.opsForSet().remove(prefix + key, values);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 获取list缓存的内容
     *
     * @param key   键
     * @param start 开始
     * @param end   结束 0 到 -1代表所有值
     * @return List
     */
    public List<Object> lGet(String key, int start, int end) {
        try {
            return redisTemplate.opsForList().range(prefix + key, start, end);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取list缓存的长度
     *
     * @param key 键
     * @return Long
     */
    public Long lGetListSize(String key) {
        try {
            return redisTemplate.opsForList().size(prefix + key);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 通过索引 获取list中的值
     *
     * @param key   键
     * @param index 索引 index>=0时， 0 表头，1 第二个元素，依次类推；
     *              index<0时，-1，表尾，-2倒数第二个元素，依次类推
     * @return Object
     */
    public Object lGetIndex(String key, Long index) {
        try {
            return redisTemplate.opsForList().index(prefix + key, index);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @return Boolean
     */
    public Boolean lSet(String key, Object value) {
        try {
            redisTemplate.opsForSet().add(prefix + key, value);
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒)
     * @return Boolean
     */
    public Boolean lSet(String key, Object value, Long time) {
        try {
            redisTemplate.opsForSet().add(prefix + key, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 往list头部插入值
     *
     * @param key   键
     * @param value 值
     * @return Boolean
     */
    public Boolean lSetList(String key, Object value) {
        try {
            redisTemplate.opsForList().leftPush(prefix + key, value);
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 往list尾部插入值
     *
     * @param key   键
     * @param value 值
     * @return Boolean
     */
    public Boolean lSetListRight(String key, Object value) {
        try {
            redisTemplate.opsForList().rightPush(prefix + key, value);
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    public List<Object> getSetList(String key) {
        try {
            return redisTemplate.opsForList().range(prefix + key, 0, -1);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Object> getSetList(String key, long start, long end) {
        try {
            return redisTemplate.opsForList().range(prefix + key, start, end);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return new ArrayList<>();
        }
    }


    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @return Boolean
     */
    public Boolean lSetList(String key, List<Object> value) {
        try {
            redisTemplate.opsForList().rightPushAll(prefix + key, value);
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒)
     * @return Boolean
     */
    public Boolean lSet(String key, List<Object> value, Long time) {
        try {
            redisTemplate.opsForList().rightPushAll(prefix + key, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 根据索引修改list中的某条数据
     *
     * @param key   键
     * @param index 索引
     * @param value 值
     * @return Boolean
     */
    public Boolean lUpdateIndex(String key, Long index, Object value) {
        try {
            redisTemplate.opsForList().set(prefix + key, index, value);
            return true;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 移除N个值为value
     *
     * @param key   键
     * @param count 移除多少个
     * @param value 值
     * @return 移除的个数
     */
    public Long lRemove(String key, Long count, Object value) {
        try {
            return redisTemplate.opsForList().remove(prefix + key, count, value);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * redis List数据结构 : 返回列表 key 中指定区间内的元素，区间以偏移量 start 和 end 指定。
     *
     * @param key             the key
     * @param start           the start
     * @param end             the end
     * @param valueSerializer 序列化
     * @return the list
     */
    public List<Object> getList(String key, int start, int end, RedisSerializer<Object> valueSerializer) {
        byte[] rawKey = rawKey(prefix + key);
        return redisTemplate.execute(connection -> deserializeValues(connection.lRange(rawKey, start, end), valueSerializer), true);
    }

    private byte[] rawKey(Object key) {
        Assert.notNull(key, "non null key required");

        if (key instanceof byte[]) {
            return (byte[]) key;
        }
        RedisSerializer<Object> redisSerializer = (RedisSerializer<Object>) redisTemplate.getKeySerializer();
        return redisSerializer.serialize(key);
    }

    private byte[] rawValue(Object value, RedisSerializer valueSerializer) {
        if (value instanceof byte[]) {
            return (byte[]) value;
        }

        return valueSerializer.serialize(value);
    }

    private List deserializeValues(List<byte[]> rawValues, RedisSerializer<Object> valueSerializer) {
        if (valueSerializer == null) {
            return rawValues;
        }
        return SerializationUtils.deserialize(rawValues, valueSerializer);
    }

    private Object deserializeValue(byte[] value, RedisSerializer<Object> valueSerializer) {
        if (valueSerializer == null) {
            return value;
        }
        return valueSerializer.deserialize(value);
    }

    public void publish(String channal, Object obj) {
        redisTemplate.convertAndSend(channal, JSONObject.toJSONString(obj));
    }

    public void publish(String channal, String obj) {
        redisTemplate.convertAndSend(channal, obj);
    }

    /**
     * 分布式锁
     *
     * @param key        分布式锁key
     * @param expireTime 持有锁的最长时间 (redis过期时间) 秒为单位
     * @return 返回获取锁状态 成功失败
     */
    public boolean tryLock(String key, int expireTime) {
        final JSONObject lock = new JSONObject();
        lock.put("id", key);
        // startTime
        lock.put("st", System.currentTimeMillis());
        // keepSeconds
        lock.put("ks", expireTime);
        return redisLockUtil.tryLock(prefix + key, "", expireTime);
    }

    public void unLock(String key) {
        redisLockUtil.releaseLock(prefix + key, "");
    }

}
