package com.pedidos360.restapi.service;

import com.pedidos360.restapi.dto.OrdenCreadaEventDto;
import com.pedidos360.restapi.dto.PagoEventDto;
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

    public static final String ESTADO_PENDIENTE = "PENDIENTE_PAGO";
    public static final String ESTADO_PAGADA = "PAGADA";
    public static final String ESTADO_RECHAZADO = "PAGO_RECHAZADO";

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
        orden.setEstado(ESTADO_PENDIENTE);

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

        // 3. Publicar evento asíncrono (notificaciones, catálogo y facturación lo consumen).
        //    Se aísla en try/catch: la orden ya está persistida, así que una falla de
        //    mensajería (broker caído, un consumidor apagado como facturación, etc.) NO
        //    debe tumbar la compra. El evento queda pendiente y se puede reprocesar luego.
        try {
            List<OrdenCreadaEventDto.Item> items = ordenGuardada.getItems().stream()
                    .map(i -> new OrdenCreadaEventDto.Item(i.getProductoId(), i.getCantidad(), i.getPrecioUnitario()))
                    .toList();
            ordenEventPublisher.publicarOrdenCreada(new OrdenCreadaEventDto(
                    ordenGuardada.getId(), ordenGuardada.getUsuarioEmail(), ordenGuardada.getTotal(), items));
        } catch (Exception ex) {
            log.error("La orden #{} se guardó correctamente, pero no se pudo publicar el evento 'orden.creada' "
                    + "(la compra continúa igual). Causa: {}", ordenGuardada.getId(), ex.getMessage());
        }

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

    // Aplica el resultado de un pago publicado por ms-pago en Kafka
    @Transactional
    public void registrarPago(PagoEventDto pago) {
        if (pago.ordenId() == null || pago.estado() == null) {
            throw new IllegalArgumentException("Evento de pago sin ordenId o sin estado");
        }
        Orden orden = ordenRepository.findById(pago.ordenId())
                .orElseThrow(() -> new IllegalArgumentException("No existe la orden #" + pago.ordenId()));
        if (!orden.getUsuarioEmail().equalsIgnoreCase(pago.usuarioEmail())) {
            throw new IllegalArgumentException("El pago #" + pago.pagoId() + " no pertenece al dueño de la orden #" + orden.getId());
        }
        if (ESTADO_PAGADA.equals(orden.getEstado())) {
            log.info("La orden #{} ya estaba pagada, se ignora el evento del pago #{}", orden.getId(), pago.pagoId());
            return;
        }
        if ("APROBADO".equals(pago.estado())) {
            if (pago.monto() == null || Math.abs(pago.monto() - orden.getTotal()) > 1) {
                throw new IllegalArgumentException("El monto del pago #" + pago.pagoId() + " ($" + pago.monto()
                        + ") no coincide con el total de la orden #" + orden.getId() + " ($" + orden.getTotal() + ")");
            }
            orden.setEstado(ESTADO_PAGADA);
            orden.setPagoId(pago.pagoId());
            log.info("Orden #{} PAGADA con el pago #{}", orden.getId(), pago.pagoId());
        } else {
            orden.setEstado(ESTADO_RECHAZADO);
            log.info("Orden #{} con pago {} ({})", orden.getId(), pago.estado(), pago.motivo());
        }
    }
}
