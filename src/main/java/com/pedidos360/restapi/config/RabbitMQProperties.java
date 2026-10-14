package com.pedidos360.restapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Nombres de colas y exchanges definidos en application.yml
@Configuration
@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMQProperties {

    private Exchanges exchanges = new Exchanges();
    private Queues queues = new Queues();
    private RoutingKeys routingKeys = new RoutingKeys();

    public Exchanges getExchanges() { return exchanges; }
    public void setExchanges(Exchanges exchanges) { this.exchanges = exchanges; }

    public Queues getQueues() { return queues; }
    public void setQueues(Queues queues) { this.queues = queues; }

    public RoutingKeys getRoutingKeys() { return routingKeys; }
    public void setRoutingKeys(RoutingKeys routingKeys) { this.routingKeys = routingKeys; }

    public static class Exchanges {
        private String ordenesTopic;
        private String facturacionDirect;
        private String dlx;

        public String getOrdenesTopic() { return ordenesTopic; }
        public void setOrdenesTopic(String ordenesTopic) { this.ordenesTopic = ordenesTopic; }

        public String getFacturacionDirect() { return facturacionDirect; }
        public void setFacturacionDirect(String facturacionDirect) { this.facturacionDirect = facturacionDirect; }

        public String getDlx() { return dlx; }
        public void setDlx(String dlx) { this.dlx = dlx; }
    }

    public static class Queues {
        private String boletaEmitida;
        private String boletaEmitidaDlq;

        public String getBoletaEmitida() { return boletaEmitida; }
        public void setBoletaEmitida(String boletaEmitida) { this.boletaEmitida = boletaEmitida; }

        public String getBoletaEmitidaDlq() { return boletaEmitidaDlq; }
        public void setBoletaEmitidaDlq(String boletaEmitidaDlq) { this.boletaEmitidaDlq = boletaEmitidaDlq; }
    }

    public static class RoutingKeys {
        private String ordenCreada;
        private String boletaEmitida;
        private String boletaEmitidaDlq;

        public String getOrdenCreada() { return ordenCreada; }
        public void setOrdenCreada(String ordenCreada) { this.ordenCreada = ordenCreada; }

        public String getBoletaEmitida() { return boletaEmitida; }
        public void setBoletaEmitida(String boletaEmitida) { this.boletaEmitida = boletaEmitida; }

        public String getBoletaEmitidaDlq() { return boletaEmitidaDlq; }
        public void setBoletaEmitidaDlq(String boletaEmitidaDlq) { this.boletaEmitidaDlq = boletaEmitidaDlq; }
    }
}
