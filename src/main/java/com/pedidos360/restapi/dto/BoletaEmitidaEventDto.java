package com.pedidos360.restapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BoletaEmitidaEventDto(Long boletaId, String folioSii, Long ordenId, String usuarioEmail) {}
