#!/bin/bash
# Despliegue de Pedidos360 (se ejecuta en la EC2 stack):
# 1. compila las imágenes aquí (de a una), 2. levanta RabbitMQ, Kafka y los microservicios
#    de streaming con docker-compose, 3. copia cada imagen a su EC2 por la red privada y 4. la levanta allí
DIR=$(cd "$(dirname "$0")" && pwd)
cd "$DIR/.."
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.kafka.yml"
for s in auth catalogo compras usuarios notificaciones ordenes facturacion kafka-admin pago analytics frontend; do
  echo "=== build $s $(date +%T)"
  if $COMPOSE build $s > ~/build-$s.log 2>&1; then echo "OK $s"; else echo "FALLA $s"; fi
done
$COMPOSE up -d rabbitmq1 rabbitmq2 zookeeper1 zookeeper2 zookeeper3 kafka1 kafka2 kafka3 kafka-ui 2>&1 | tail -2
$COMPOSE up -d --no-deps kafka-admin pago analytics 2>&1 | tail -2
bash "$DIR/copiar-imagenes.sh"
bash "$DIR/levantar-ec2.sh"
echo "FIN $(date +%T)"
