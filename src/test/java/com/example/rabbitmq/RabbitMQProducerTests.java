package com.example.rabbitmq;

import com.example.config.RabbitMQConfig;
import com.example.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class RabbitMQProducerTests {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    public void testSendMessage() {
        User user = new User();
        user.setId(1L);
        user.setNickName("admin");
        user.setPhone("1235345346");

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME,"work", user);
        log.info("添加了消息:{}",user);

    }
}
