package com.pedidos360.restapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

// Evento del tópico pedidos360.pagos que publica ms-pago
@JsonIgnoreProperties(ignoreUnknown = true)
public record PagoEventDto(
        String eventoId,
        Long pagoId,
        Long ordenId,
        String usuarioEmail,
        Double monto,
        String ultimos4,
        String estado,
        String motivo,
        Instant ocurridoEn) {
}
