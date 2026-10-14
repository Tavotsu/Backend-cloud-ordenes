package com.pedidos360.restapi.dto;

import java.util.List;

public record OrdenCreadaEventDto(Long ordenId, String usuarioEmail, Double total, List<Item> items) {

    public record Item(Long productoId, Integer cantidad, Double precioUnitario) {}
}
