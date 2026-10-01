package com.pedidos360.restapi.controller;

import com.pedidos360.restapi.model.Orden;
import com.pedidos360.restapi.service.OrdenService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ordenes")
public class OrdenController {

    private final OrdenService ordenService;

    public OrdenController(OrdenService ordenService) {
        this.ordenService = ordenService;
    }

    @PostMapping("/completar")
    public Orden completarOrden(Authentication authentication) {
        String username = authentication.getName();
        String token = null;

        // Extraer el token JWT
        if (authentication.getCredentials() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getCredentials();
            token = jwt.getTokenValue();
        }

        if (token == null) {
            throw new RuntimeException("No se pudo obtener el token de autenticación");
        }

        return ordenService.crearOrdenDesdeCarrito(username, token);
    }

    @GetMapping
    public List<Orden> misOrdenes(Authentication authentication) {
        String username = authentication.getName();
        return ordenService.obtenerOrdenesPorUsuario(username);
    }
}
