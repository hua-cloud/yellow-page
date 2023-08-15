package com.example.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * RabbitMQ的配置类
 */
@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_NAME = "seckill_order_queue";
    public static final String EXCHANGE_NAME = "direct_exchange";

    // 1.交换机
    @Bean("workExchange")
    public Exchange bootExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_NAME).durable(true).build();
    }

    // 2.队列
    @Bean("workQueue")
    public Queue bootQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    // 3.队列与交换机的绑定
    @Bean
    public Binding bindQueueExchange(@Qualifier("workQueue") Queue queue, @Qualifier("workExchange") Exchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("work").noargs();
    }
}
