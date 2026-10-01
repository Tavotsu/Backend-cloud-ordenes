package com.pedidos360.restapi.service;

import com.pedidos360.restapi.dto.PedidoDto;
import com.pedidos360.restapi.model.Orden;
import com.pedidos360.restapi.model.OrdenItem;
import com.pedidos360.restapi.repository.OrdenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrdenService {

    private final OrdenRepository ordenRepository;
    private final RestTemplate restTemplate;

    @Value("${app.services.compras-url}")
    private String comprasUrl;

    public OrdenService(OrdenRepository ordenRepository, RestTemplate restTemplate) {
        this.ordenRepository = ordenRepository;
        this.restTemplate = restTemplate;
    }

    public Orden crearOrdenDesdeCarrito(String username, String token) {
        // Preparar headers con el token
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Llamar a MS compras
        ResponseEntity<List<PedidoDto>> response = restTemplate.exchange(
                comprasUrl + "/carrito",
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<PedidoDto>>() {}
        );

        List<PedidoDto> carrito = response.getBody();

        if (carrito == null || carrito.isEmpty()) {
            throw new RuntimeException("El carrito está vacío");
        }

        // Crear la orden
        Orden orden = new Orden();
        orden.setUsuarioEmail(username);
        orden.setFecha(LocalDateTime.now());
        
        double totalOrden = 0.0;
        
        for (PedidoDto pedido : carrito) {
            OrdenItem item = new OrdenItem();
            item.setProductoId(pedido.getProductoId());
            item.setCantidad(pedido.getCantidad());
            
            // Asumimos que el total en pedido es el subtotal del item
            double subtotal = pedido.getTotal() != null ? pedido.getTotal() : 0.0;
            // Para guardar el precio unitario aproximado
            item.setPrecioUnitario(pedido.getCantidad() > 0 ? subtotal / pedido.getCantidad() : 0.0);
            
            totalOrden += subtotal;
            orden.addItem(item);
        }
        
        orden.setTotal(totalOrden);
        
        // Guardar la orden en BD
        return ordenRepository.save(orden);
    }

    public List<Orden> obtenerOrdenesPorUsuario(String username) {
        return ordenRepository.findByUsuarioEmail(username);
    }
}
