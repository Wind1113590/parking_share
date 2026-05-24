package com.huang.parkingshare.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huang.parkingshare.entity.TimeSlice;
import com.huang.parkingshare.mapper.TimeSliceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RedisTimeSliceService {

    @Autowired
    @Qualifier("customStringRedisTemplate")  // 注意指定名称
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private TimeSliceMapper timeSliceMapper;

    private static final String SLICE_KEY_PREFIX = "slice:slot:";

    /**
     * 初始化某车位某一天的所有时间片到 Redis Hash
     *
     * @param slotId  车位ID
     * @param date    日期
     * @param minutes 该天的所有起始分钟列表
     */
    public void initTimeSlicesForDay(Long slotId, LocalDate date, List<Integer> minutes) {
        String key = SLICE_KEY_PREFIX + slotId + ":" + date.toString();
        // 使用 Hash 结构，field = startMinute, value = "0" (空闲)
        for (Integer minute : minutes) {
            redisTemplate.opsForHash().put(key, minute.toString(), "0");
        }
        // 设置过期时间（例如30天，避免无限堆积）
        redisTemplate.expire(key, 30, TimeUnit.DAYS);
    }

    // 在 RedisTimeSliceService 中添加
    public boolean areTimeSlicesFree(Long slotId, LocalDate date, List<Integer> startMinutes) {
        String key = "slice:slot:" + slotId + ":" + date.toString(); // 注意格式一致
        // 检查 key 是否存在
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            // Redis 中该天的数据丢失，从 MySQL 恢复
            reloadTimeSlicesFromDB(slotId, date);
        }


        if (startMinutes == null || startMinutes.isEmpty()) {
            throw new IllegalArgumentException("预定时间不得小等于0");
        }
        // 批量获取所有 field 的值，注意需要 List<Object> 类型
        List<Object> fields = startMinutes.stream()
                .map(minute -> (Object) minute.toString())  // 转换为 Object
                .collect(Collectors.toList());
        List<Object> values = redisTemplate.opsForHash().multiGet(key, fields);
        if (values == null || values.size() != fields.size()) {
            // 如果某些时间片不存在，视为不可用（应该都有，但防御）
            return false;
        }
        for (Object val : values) {
            // 状态不是 "0" 表示已被占用
            if (val == null || !"0".equals(val.toString())) {
                return false;
            }
        }
        return true;
    }

    private void reloadTimeSlicesFromDB(Long slotId, LocalDate date) {
        // 从 MySQL 的 time_slice 表中查询该天所有时间片（status 字段）
        List<TimeSlice> slices = timeSliceMapper.selectList(
                new LambdaQueryWrapper<TimeSlice>()
                        .eq(TimeSlice::getSlotId, slotId)
                        .eq(TimeSlice::getSliceDate, date)
        );
        String key = "slice:slot:" + slotId + ":" + date;
        for (TimeSlice ts : slices) {
            // 将 status 转成字符串写入 Redis
            redisTemplate.opsForHash().put(key, String.valueOf(ts.getStartMinute()), String.valueOf(ts.getStatus()));
        }
        redisTemplate.expire(key, 30, TimeUnit.DAYS);
    }

    public boolean unlockTimeSlices(Long slotId, LocalDate date, List<Integer> minutes) {
        String key = SLICE_KEY_PREFIX + slotId + ":" + date;
        // 使用 Lua 脚本原子地将指定 field 从 "1" 改回 "0"
        String lua = "for i, minute in ipairs(ARGV) do " +
                "redis.call('hset', KEYS[1], minute, \"0\") " +
                "end " +
                "return 1";
        String[] args = minutes.stream().map(String::valueOf).toArray(String[]::new);
        try {
            redisTemplate.execute(
                    new DefaultRedisScript<>(lua, Long.class),
                    Collections.singletonList(key),
                    (Object[]) args
            );
            return true;
        } catch (Exception e) {
            log.error("解锁 Redis 时间片失败", e);
            return false;
        }
    }

    public boolean bookTimeSlices(Long slotId, LocalDate date, List<Integer> minutes) {
        String key = SLICE_KEY_PREFIX + slotId + ":" + date;
        // Lua 脚本：仅当当前值为 "1" 时才改为 "2"
        String lua =
                "for i, minute in ipairs(ARGV) do " +
                        "   local cur = redis.call('hget', KEYS[1], minute) " +
                        "   if cur == '1' then " +
                        "       redis.call('hset', KEYS[1], minute, \"2\") " +
                        "   end " +
                        "end" +
                        " return 1";
        String[] args = minutes.stream().map(String::valueOf).toArray(String[]::new);
        try {
            redisTemplate.execute(
                    new DefaultRedisScript<>(lua, Long.class),
                    Collections.singletonList(key),
                    (Object[]) args
            );
            return true;
        } catch (Exception e) {
            log.error("预约时间片失败", e);
            return false;
        }
    }

    public boolean occupyFreeTimeSlices(Long slotId, LocalDate date, List<Integer> minutes) {
        String key = SLICE_KEY_PREFIX + slotId + ":" + date;
        // Lua 脚本：检查所有 field 是否为 "0"，若是则改为 "2"，否则返回 0
        String lua =
                "local fields = ARGV\n" +
                        "for i, minute in ipairs(fields) do\n" +
                        "    local cur = redis.call('hget', KEYS[1], minute)\n" +
                        "    if cur == false or tostring(cur) ~= '0' then\n" +
                        "        return 0\n" +
                        "    end\n" +
                        "end\n" +

                        "for i, minute in ipairs(fields) do\n" +
                        "    redis.call('hset', KEYS[1], minute, '2')\n" +
                        "end\n" +
                        "return 1";
        String[] args = minutes.stream().map(String::valueOf).toArray(String[]::new);
        try {
            Long result = redisTemplate.execute(
                    new DefaultRedisScript<>(lua, Long.class),
                    Collections.singletonList(key),
                    (Object[]) args
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("占用空闲时间片失败", e);
            return false;
        }
    }

    public boolean stopTimeSlices(Long slotId, LocalDate date, List<Integer> minutes) {
        String key = SLICE_KEY_PREFIX + slotId + ":" + date;
        // Lua 脚本：检查所有 field 是否为 "0"，若是则改为 "3"，否则返回 0
        String lua =
        "local fields = ARGV\n" +
                "for i, minute in ipairs(fields) do\n" +
                "    local cur = redis.call('hget', KEYS[1], minute)\n" +
                "    if cur == false or tostring(cur) ~= '0' then\n" +
                "        return 0\n" +
                "    end\n" +
                "end\n" +


                "for i, minute in ipairs(fields) do\n" +
                "    redis.call('hset', KEYS[1], minute, '3')\n" +
                "end\n" +
                "return 1";
        String[] args = minutes.stream().map(String::valueOf).toArray(String[]::new);
        try {
            Long result = redisTemplate.execute(
                    new DefaultRedisScript<>(lua, Long.class),
                    Collections.singletonList(key),
                    (Object[]) args
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("占用空闲时间片失败", e);
            return false;
        }
    }

    public boolean startTimeSlices(Long slotId, LocalDate date, List<Integer> minutes) {
        String key = SLICE_KEY_PREFIX + slotId + ":" + date;
        // Lua 脚本：检查所有 field 是否为 "3"，若是则改为 "0"，否则返回 0
        String lua =
                "local fields = ARGV\n" +
                        "for i, minute in ipairs(fields) do\n" +
                        "    local cur = redis.call('hget', KEYS[1], minute)\n" +
                        "    if cur == false or tostring(cur) ~= '3' then\n" +
                        "        return 0\n" +
                        "    end\n" +
                        "end\n" +


                        "for i, minute in ipairs(fields) do\n" +
                        "    redis.call('hset', KEYS[1], minute, '0')\n" +
                        "end\n" +
                        "return 1";
        String[] args = minutes.stream().map(String::valueOf).toArray(String[]::new);
        try {
            Long result = redisTemplate.execute(
                    new DefaultRedisScript<>(lua, Long.class),
                    Collections.singletonList(key),
                    (Object[]) args
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("占用空闲时间片失败", e);
            return false;
        }
    }

    public void deleteTimeSlicesForDay(Long slotId, LocalDate date) {
        String key = SLICE_KEY_PREFIX + slotId + ":" + date.toString();
        // 使用 Hash 结构，field = startMinute, value = "0" (空闲)
        redisTemplate.delete(key);
    }

}