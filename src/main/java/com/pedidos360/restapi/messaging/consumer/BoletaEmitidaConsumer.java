package com.pedidos360.restapi.messaging.consumer;

import com.pedidos360.restapi.dto.BoletaEmitidaEventDto;
import com.pedidos360.restapi.service.OrdenService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Guarda el folio de la boleta en la orden. Si la orden no existe va a la DLQ
@Component
public class BoletaEmitidaConsumer {

    private static final Logger log = LoggerFactory.getLogger(BoletaEmitidaConsumer.class);

    private final OrdenService ordenService;

    public BoletaEmitidaConsumer(OrdenService ordenService) {
        this.ordenService = ordenService;
    }

    @RabbitListener(queues = "${app.rabbitmq.queues.boleta-emitida}")
    public void consumirBoletaEmitida(BoletaEmitidaEventDto evento, Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        boolean reentregado = Boolean.TRUE.equals(message.getMessageProperties().getRedelivered());
        log.info("[RabbitMQ Consumer] Boleta recibida: {} (tag={}, redelivered={})", evento, tag, reentregado);

        try {
            ordenService.registrarBoleta(evento.ordenId(), evento.folioSii());
            channel.basicAck(tag, false);
            log.info("[RabbitMQ ACK] Folio {} asociado a la orden #{}", evento.folioSii(), evento.ordenId());

        } catch (IllegalArgumentException ex) {
            log.error("[RabbitMQ NACK -> DLQ] Boleta inválida, no se reintenta: {}", ex.getMessage());
            channel.basicNack(tag, false, false);

        } catch (TransientDataAccessException ex) {
            if (!reentregado) {
                log.warn("[RabbitMQ RETRY] Falla temporal de BD, se reencola una vez (tag={})", tag);
                channel.basicNack(tag, false, true);
            } else {
                log.error("[RabbitMQ NACK -> DLQ] Reintento agotado por falla de BD (tag={})", tag);
                channel.basicNack(tag, false, false);
            }

        } catch (Exception ex) {
            log.error("[RabbitMQ NACK -> DLQ] Error inesperado procesando la boleta: {}", ex.getMessage(), ex);
            channel.basicNack(tag, false, false);
        }
    }
}
