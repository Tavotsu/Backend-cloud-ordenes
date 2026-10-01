package com.pedidos360.restapi.dto;

public class PedidoDto {
    private Long id;
    private Long productoId;
    private Integer cantidad;
    private String usuarioEmail;
    private Double total;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public String getUsuarioEmail() { return usuarioEmail; }
    public void setUsuarioEmail(String usuarioEmail) { this.usuarioEmail = usuarioEmail; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }
}
