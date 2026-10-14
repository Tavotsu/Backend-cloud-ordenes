package com.pedidos360.restapi.service;

import com.pedidos360.restapi.dto.OrdenCreadaEventDto;
import com.pedidos360.restapi.dto.PedidoDto;
import com.pedidos360.restapi.messaging.producer.OrdenEventPublisher;
import com.pedidos360.restapi.model.Orden;
import com.pedidos360.restapi.model.OrdenItem;
import com.pedidos360.restapi.repository.OrdenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrdenService {

    private static final Logger log = LoggerFactory.getLogger(OrdenService.class);

    private final OrdenRepository ordenRepository;
    private final RestTemplate restTemplate;
    private final OrdenEventPublisher ordenEventPublisher;

    @Value("${app.services.compras-url}")
    private String comprasUrl;

    public OrdenService(OrdenRepository ordenRepository, RestTemplate restTemplate, OrdenEventPublisher ordenEventPublisher) {
        this.ordenRepository = ordenRepository;
        this.restTemplate = restTemplate;
        this.ordenEventPublisher = ordenEventPublisher;
    }

    public Orden crearOrdenDesdeCarrito(String username, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<List<PedidoDto>> response = restTemplate.exchange(
                comprasUrl + "/carrito",
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<PedidoDto>>() {}
        );

        List<PedidoDto> carrito = response.getBody();
        if (carrito == null || carrito.isEmpty()) {
            throw new IllegalStateException("El carrito está vacío");
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

        // 2. Vaciar el carrito en compras para que la próxima orden no arrastre estos ítems
        restTemplate.exchange(comprasUrl + "/carrito", HttpMethod.DELETE, entity, Void.class);

        // 3. Publicar evento asíncrono (notificaciones, catálogo y facturación lo consumen)
        List<OrdenCreadaEventDto.Item> items = ordenGuardada.getItems().stream()
                .map(i -> new OrdenCreadaEventDto.Item(i.getProductoId(), i.getCantidad(), i.getPrecioUnitario()))
                .toList();
        ordenEventPublisher.publicarOrdenCreada(new OrdenCreadaEventDto(
                ordenGuardada.getId(), ordenGuardada.getUsuarioEmail(), ordenGuardada.getTotal(), items));

        return ordenGuardada;
    }

    public List<Orden> obtenerOrdenesPorUsuario(String username) {
        return ordenRepository.findByUsuarioEmail(username);
    }

    @Transactional
    public void registrarBoleta(Long ordenId, String folioBoleta) {
        if (ordenId == null || folioBoleta == null || folioBoleta.isBlank()) {
            throw new IllegalArgumentException("Evento de boleta sin ordenId o folio");
        }
        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la orden #" + ordenId));
        orden.setFolioBoleta(folioBoleta);
        log.info("Orden #{} actualizada con folio de boleta {}", ordenId, folioBoleta);
    }
}
