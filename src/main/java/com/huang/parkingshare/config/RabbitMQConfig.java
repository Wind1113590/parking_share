package com.huang.parkingshare.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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

    // 新增：车位同步到 ES 的队列
    public static final String PARKING_SLOT_SYNC_QUEUE = "parking.slot.sync.queue";
    public static final String PARKING_SLOT_SYNC_EXCHANGE = "parking.slot.sync.exchange";
    public static final String PARKING_SLOT_SYNC_ROUTING_KEY = "parking.slot.sync";

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


    // 新增：车位同步队列相关 Bean
    @Bean
    public Queue parkingSlotSyncQueue() {
        return QueueBuilder.durable(PARKING_SLOT_SYNC_QUEUE).build();
    }
    @Bean
    public DirectExchange parkingSlotSyncExchange() {
        return new DirectExchange(PARKING_SLOT_SYNC_EXCHANGE);
    }
    @Bean
    public Binding parkingSlotSyncBinding() {
        return BindingBuilder.bind(parkingSlotSyncQueue())
                .to(parkingSlotSyncExchange())
                .with(PARKING_SLOT_SYNC_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        // 使用 Jackson 转换器，自动处理类映射
        return new Jackson2JsonMessageConverter();
    }

    // 如果同时需要发送 JSON 消息，还需要配置 RabbitTemplate
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

}