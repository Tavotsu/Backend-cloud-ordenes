package com.pedidos360.restapi.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

// Manejo de errores de los consumidores Kafka: 3 reintentos separados por 1 segundo y,
// si el mensaje sigue fallando, se envía al tópico <original>.DLT con un log del motivo.
// Los errores de datos (payload inválido, orden inexistente) van directo al DLT.
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public KafkaTemplate<Object, Object> dltKafkaTemplate(KafkaProperties properties) {
        Map<String, Object> config = new LinkedHashMap<>(properties.buildProducerProperties());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Un mensaje que no se pudo leer se reenvía tal cual (bytes); uno válido, como JSON
        Map<Class<?>, Serializer<?>> porTipo = new LinkedHashMap<>();
        porTipo.put(byte[].class, new ByteArraySerializer());
        porTipo.put(Object.class, new JacksonJsonSerializer<>());
        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(config,
                null, new DelegatingByTypeSerializer(porTipo, true));
        return new KafkaTemplate<>(factory);
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> dltKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate,
                (registro, error) -> {
                    log.error("[Kafka -> DLT] {}-{} offset {} no se pudo procesar: {}", registro.topic(),
                            registro.partition(), registro.offset(), NestedExceptionUtils.getMostSpecificCause(error).getMessage());
                    return new TopicPartition(registro.topic() + ".DLT", registro.partition());
                });
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.setRetryListeners((registro, error, intento) ->
                log.warn("Falla {} al procesar {}-{} offset {}: {}", intento, registro.topic(), registro.partition(),
                        registro.offset(), NestedExceptionUtils.getMostSpecificCause(error).getMessage()));
        return handler;
    }
}
