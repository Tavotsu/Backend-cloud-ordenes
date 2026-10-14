package com.pedidos360.restapi.messaging.producer;

import com.pedidos360.restapi.config.RabbitMQProperties;
import com.pedidos360.restapi.dto.OrdenCreadaEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

// Publica los eventos de órdenes en RabbitMQ
@Component
public class OrdenEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrdenEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties props;

    public OrdenEventPublisher(RabbitTemplate rabbitTemplate, RabbitMQProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    public void publicarOrdenCreada(OrdenCreadaEventDto evento) {
        rabbitTemplate.convertAndSend(
                props.getExchanges().getOrdenesTopic(),
                props.getRoutingKeys().getOrdenCreada(),
                evento);
        log.info("[RabbitMQ Producer] Orden #{} publicada en '{}' con key '{}'",
                evento.ordenId(), props.getExchanges().getOrdenesTopic(), props.getRoutingKeys().getOrdenCreada());
    }
}
