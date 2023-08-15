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
public class RabbitMQConsumerTests {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    public void testReceiveMessage() throws InterruptedException {
        // receiveAndConvert 方法只返回消息的 body 部分，不包含完整的 Message 对象。
        // 具体来说，它将直接返回消息的主体内容，而不返回消息的其他元数据，如消息的属性和消息头部信息。
        while (true) {
            User user = (User) rabbitTemplate.receiveAndConvert(RabbitMQConfig.QUEUE_NAME);
            if (user != null) {
                log.debug("接收到了消息:{}",user);
            } else {
                log.error("消息为空");
            }
            Thread.sleep(2000);
            System.out.println("暂停结束，尝试再次获取...");
        }
    }

}
