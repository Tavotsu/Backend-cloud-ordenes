package com.pedidos360.restapi.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Ordenes publica orden.creada y consume boleta.emitida de facturación
@Configuration
public class RabbitMQConfig {

    private final RabbitMQProperties props;

    public RabbitMQConfig(RabbitMQProperties props) {
        this.props = props;
    }

    // Exchanges

    @Bean
    public TopicExchange ordenesTopicExchange() {
        return ExchangeBuilder.topicExchange(props.getExchanges().getOrdenesTopic()).durable(true).build();
    }

    @Bean
    public DirectExchange facturacionDirectExchange() {
        return ExchangeBuilder.directExchange(props.getExchanges().getFacturacionDirect()).durable(true).build();
    }

    @Bean
    public DirectExchange ordenesDeadLetterExchange() {
        return ExchangeBuilder.directExchange(props.getExchanges().getDlx()).durable(true).build();
    }

    // Boleta emitida

    @Bean
    public Queue boletaEmitidaQueue() {
        return QueueBuilder.durable(props.getQueues().getBoletaEmitida())
                .withArgument("x-dead-letter-exchange", props.getExchanges().getDlx())
                .withArgument("x-dead-letter-routing-key", props.getRoutingKeys().getBoletaEmitidaDlq())
                .build();
    }

    @Bean
    public Queue boletaEmitidaDlq() {
        return QueueBuilder.durable(props.getQueues().getBoletaEmitidaDlq()).build();
    }

    @Bean
    public Binding bindingBoletaEmitida() {
        return BindingBuilder.bind(boletaEmitidaQueue())
                .to(facturacionDirectExchange())
                .with(props.getRoutingKeys().getBoletaEmitida());
    }

    @Bean
    public Binding bindingBoletaEmitidaDlq() {
        return BindingBuilder.bind(boletaEmitidaDlq())
                .to(ordenesDeadLetterExchange())
                .with(props.getRoutingKeys().getBoletaEmitidaDlq());
    }

    // Las DLQ se leen como texto para poder registrar también mensajes con JSON inválido
    @Bean
    public SimpleRabbitListenerContainerFactory dlqListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new SimpleMessageConverter());
        return factory;
    }

    // Convierte los objetos a JSON al enviar y recibir
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
