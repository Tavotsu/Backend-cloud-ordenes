#!/bin/bash
# Despliegue de Pedidos360 con un microservicio por EC2 (se ejecuta en la EC2 de RabbitMQ):
# 1. compila las imágenes aquí (de a una), 2. levanta RabbitMQ con docker-compose,
# 3. copia cada imagen a su EC2 por la red privada y 4. la levanta allí
DIR=$(cd "$(dirname "$0")" && pwd)
cd "$DIR/.."
for s in auth catalogo compras usuarios notificaciones ordenes facturacion frontend; do
  echo "=== build $s $(date +%T)"
  if docker compose build $s > ~/build-$s.log 2>&1; then echo "OK $s"; else echo "FALLA $s"; fi
done
docker compose up -d rabbitmq1 rabbitmq2 2>&1 | tail -2
"$DIR/copiar-imagenes.sh"
"$DIR/levantar-ec2.sh"
ssh -i ~/.ssh/p360.pem ec2-user@10.1.0.104 "~/levantar.sh" >/dev/null && echo "OK frontend en 10.1.0.104"
echo "FIN $(date +%T)"
