package com.huang.parkingshare.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 普通队列（用于接收订单创建消息，并设置TTL）
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay.routing";

    // 死信队列（处理超时未支付）
    public static final String ORDER_DEAD_QUEUE = "order.dead.queue";
    public static final String ORDER_DEAD_EXCHANGE = "order.dead.exchange";
    public static final String ORDER_DEAD_ROUTING_KEY = "order.dead.routing";

    /**
     * 延迟队列（普通队列，设置TTL和死信交换机）
     */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .withArgument("x-dead-letter-exchange", ORDER_DEAD_EXCHANGE)   // 死信交换机
                .withArgument("x-dead-letter-routing-key", ORDER_DEAD_ROUTING_KEY)
                .withArgument("x-message-ttl", 15 * 60 * 1000)   // 15分钟（单位毫秒）
                .build();
    }

    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(ORDER_DELAY_EXCHANGE);
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderDelayExchange())
                .with(ORDER_DELAY_ROUTING_KEY);
    }

    /**
     * 死信队列（实际消费队列）
     */
    @Bean
    public Queue orderDeadQueue() {
        return QueueBuilder.durable(ORDER_DEAD_QUEUE).build();
    }

    @Bean
    public DirectExchange orderDeadExchange() {
        return new DirectExchange(ORDER_DEAD_EXCHANGE);
    }

    @Bean
    public Binding orderDeadBinding() {
        return BindingBuilder.bind(orderDeadQueue())
                .to(orderDeadExchange())
                .with(ORDER_DEAD_ROUTING_KEY);
    }
}