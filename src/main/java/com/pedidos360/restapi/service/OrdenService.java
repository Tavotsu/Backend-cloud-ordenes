package com.pedidos360.restapi.service;

import com.pedidos360.restapi.dto.OrdenEventDto;
import com.pedidos360.restapi.dto.PedidoDto;
import com.pedidos360.restapi.model.Orden;
import com.pedidos360.restapi.model.OrdenItem;
import com.pedidos360.restapi.repository.OrdenRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.services.compras-url}")
    private String comprasUrl;

    @Value("${rabbitmq.exchange.ordenes}")
    private String exchangeOrdenes;

    @Value("${rabbitmq.routing-key.creada}")
    private String routingKeyCreada;

    // Inyectamos RabbitTemplate en el constructor
    public OrdenService(OrdenRepository ordenRepository, RestTemplate restTemplate, RabbitTemplate rabbitTemplate) {
        this.ordenRepository = ordenRepository;
        this.restTemplate = restTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    public Orden crearOrdenDesdeCarrito(String username, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<String> entity = new HttpEntity<>(headers);

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

        Orden orden = new Orden();
        orden.setUsuarioEmail(username);
        orden.setFecha(LocalDateTime.now());
        
        double totalOrden = 0.0;
        
        for (PedidoDto pedido : carrito) {
            OrdenItem item = new OrdenItem();
            item.setProductoId(pedido.getProductoId());
            item.setCantidad(pedido.getCantidad());
            
            double subtotal = pedido.getTotal() != null ? pedido.getTotal() : 0.0;
            item.setPrecioUnitario(pedido.getCantidad() > 0 ? subtotal / pedido.getCantidad() : 0.0);
            
            totalOrden += subtotal;
            orden.addItem(item);
        }
        
        orden.setTotal(totalOrden);
        
        // 1. Guardar la orden en PostgreSQL
        Orden ordenGuardada = ordenRepository.save(orden);

        // 2. Publicar evento asíncrono en RabbitMQ
        OrdenEventDto evento = new OrdenEventDto(ordenGuardada.getId(), ordenGuardada.getUsuarioEmail(), ordenGuardada.getTotal());
        rabbitTemplate.convertAndSend(exchangeOrdenes, routingKeyCreada, evento);
        System.out.println("Evento publicado en RabbitMQ: Orden " + ordenGuardada.getId() + " creada.");

        return ordenGuardada;
    }

    public List<Orden> obtenerOrdenesPorUsuario(String username) {
        return ordenRepository.findByUsuarioEmail(username);
    }
}