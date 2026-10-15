package com.pedidos360.restapi.messaging.consumer;

import com.pedidos360.restapi.dto.BoletaEmitidaEventDto;
import com.pedidos360.restapi.service.OrdenService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BoletaEmitidaConsumerTest {

    private static final long TAG = 3L;

    @Mock
    private OrdenService ordenService;

    @Mock
    private Channel channel;

    @InjectMocks
    private BoletaEmitidaConsumer consumer;

    private final BoletaEmitidaEventDto boleta = new BoletaEmitidaEventDto(1L, "SII-39-ABC", 10L, "cliente@pedidos360.cl");

    private static Message mensaje() {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(TAG);
        return new Message(new byte[0], props);
    }

    @Test
    void boletaDeOrdenExistenteHaceAck() throws Exception {
        consumer.consumirBoletaEmitida(boleta, mensaje(), channel);

        verify(ordenService).registrarBoleta(10L, "SII-39-ABC");
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void boletaDeOrdenInexistenteVaALaDlq() throws Exception {
        doThrow(new IllegalArgumentException("No existe la orden #10")).when(ordenService).registrarBoleta(10L, "SII-39-ABC");

        consumer.consumirBoletaEmitida(boleta, mensaje(), channel);

        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(TAG, false);
    }
}
