package com.pedidos360.restapi.messaging.consumer;

import com.pedidos360.restapi.dto.PagoEventDto;
import com.pedidos360.restapi.service.OrdenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// Consumidor Kafka del grupo ordenes-pagos: cuando ms-pago aprueba un pago, la orden queda PAGADA.
// La lógica de negocio está en OrdenService; aquí solo se recibe el mensaje.
@Component
public class PagoEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PagoEventConsumer.class);

    private final OrdenService ordenService;

    public PagoEventConsumer(OrdenService ordenService) {
        this.ordenService = ordenService;
    }

    @KafkaListener(topics = "${app.kafka.topicos.pagos}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumir(@Payload PagoEventDto pago,
                         @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
                         @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("[Kafka] Pago #{} de la orden #{} ({}) recibido de la partición {} offset {}",
                pago.pagoId(), pago.ordenId(), pago.estado(), particion, offset);
        ordenService.registrarPago(pago);
    }
}
