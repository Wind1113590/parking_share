package com.huang.parkingshare;

import com.huang.parkingshare.config.RabbitMQConfig;
import com.huang.parkingshare.entity.User;
import com.huang.parkingshare.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@SpringBootTest
public class ParkingShareApplicationTests {

    @Autowired
    private UserMapper userRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    public void testMysql() {
        User user = new User();
        user.setPhone("13800138000");
        user.setRole(0);
        user.setBalance(new java.math.BigDecimal("100.00"));
        userRepository.insert(user);
        System.out.println("插入用户成功，ID: " + user.getId());
    }

    @Test
    public void testRedis() {
        redisTemplate.opsForValue().set("test:key", "hello parking share");
        String value = (String) redisTemplate.opsForValue().get("test:key");
        System.out.println("Redis读取: " + value);
    }

    @Test
    public void testRabbitMQ() {
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_DELAY_EXCHANGE,
                RabbitMQConfig.ORDER_DELAY_ROUTING_KEY,
                "test message");
        System.out.println("消息已发送到延迟队列");
    }
}