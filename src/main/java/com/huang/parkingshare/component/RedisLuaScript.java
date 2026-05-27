package com.huang.parkingshare.component;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;


import java.util.Collections;
import java.util.List;

@Component
public class RedisLuaScript {
    @Autowired
    @Qualifier("customStringRedisTemplate")
    private RedisTemplate<String,String> redisTemplate;

    private DefaultRedisScript<Long> lockScript;

    @PostConstruct
    public void init() {//这里其实也没必要把Lua脚本抽出来，一开始以为是Lua脚本的问题，后面发现没问题索性就不改了
        lockScript = new DefaultRedisScript<>();
        lockScript.setLocation(new ClassPathResource("lua/lock_time_slices.lua"));
        lockScript.setResultType(Long.class);
    }

    public boolean lockTimeSlices(String key, List<Integer> startMinutes) {
        String[] args = startMinutes.stream().map(String::valueOf).toArray(String[]::new);
        Long result = redisTemplate.execute(
                lockScript,
                Collections.singletonList(key),
                (Object[]) args
        );
        return result != null && result == 1L;
    }
}