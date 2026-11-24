package com.pedidos360.restapi.service;

import com.pedidos360.restapi.dto.PagoEventDto;
import com.pedidos360.restapi.messaging.producer.OrdenEventPublisher;
import com.pedidos360.restapi.model.Orden;
import com.pedidos360.restapi.repository.OrdenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdenServicePagoTest {

    @Mock
    private OrdenRepository repository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OrdenEventPublisher publisher;

    private OrdenService service;
    private Orden orden;

    @BeforeEach
    void preparar() {
        service = new OrdenService(repository, restTemplate, publisher);
        orden = new Orden();
        orden.setId(10L);
        orden.setUsuarioEmail("ana@duocuc.cl");
        orden.setTotal(7990.0);
        orden.setEstado(OrdenService.ESTADO_PENDIENTE);
    }

    private PagoEventDto pago(String estado, String email, double monto) {
        return new PagoEventDto("pago-1", 1L, 10L, email, monto, "1111", estado, null, Instant.now());
    }

    @Test
    void pagoAprobadoDejaLaOrdenPagada() {
        when(repository.findById(10L)).thenReturn(Optional.of(orden));

        service.registrarPago(pago("APROBADO", "ana@duocuc.cl", 7990));

        assertThat(orden.getEstado()).isEqualTo(OrdenService.ESTADO_PAGADA);
        assertThat(orden.getPagoId()).isEqualTo(1L);
    }

    @Test
    void pagoBloqueadoDejaLaOrdenRechazada() {
        when(repository.findById(10L)).thenReturn(Optional.of(orden));

        service.registrarPago(pago("BLOQUEADO_FRAUDE", "ana@duocuc.cl", 7990));

        assertThat(orden.getEstado()).isEqualTo(OrdenService.ESTADO_RECHAZADO);
    }

    @Test
    void unRechazoPosteriorNoDeshaceUnaOrdenPagada() {
        orden.setEstado(OrdenService.ESTADO_PAGADA);
        when(repository.findById(10L)).thenReturn(Optional.of(orden));

        service.registrarPago(pago("RECHAZADO", "ana@duocuc.cl", 7990));

        assertThat(orden.getEstado()).isEqualTo(OrdenService.ESTADO_PAGADA);
    }

    @Test
    void montoDistintoAlTotalVaAlDlt() {
        when(repository.findById(10L)).thenReturn(Optional.of(orden));

        assertThatThrownBy(() -> service.registrarPago(pago("APROBADO", "ana@duocuc.cl", 100)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(orden.getEstado()).isEqualTo(OrdenService.ESTADO_PENDIENTE);
    }

    @Test
    void pagoDeOtroUsuarioVaAlDlt() {
        when(repository.findById(10L)).thenReturn(Optional.of(orden));

        assertThatThrownBy(() -> service.registrarPago(pago("APROBADO", "otro@duocuc.cl", 7990)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordenInexistenteVaAlDlt() {
        when(repository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarPago(pago("APROBADO", "ana@duocuc.cl", 7990)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
