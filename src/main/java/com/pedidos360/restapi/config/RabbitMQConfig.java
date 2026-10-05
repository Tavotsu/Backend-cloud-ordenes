package com.pedidos360.restapi.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange.ordenes}")
    private String exchangeOrdenes;

    @Bean
    public DirectExchange ordenesExchange() {
        return new DirectExchange(exchangeOrdenes);
    }

    // Convierte automáticamente los objetos Java a JSON antes de enviarlos a RabbitMQ
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}