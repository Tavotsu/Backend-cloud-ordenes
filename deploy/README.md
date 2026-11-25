# Despliegue de Pedidos360

Los 7 microservicios originales corren cada uno en su propia EC2. La EC2 `pedidos360-stack`
levanta con docker-compose el clúster de RabbitMQ (2 nodos), la arquitectura Kafka
(3 Zookeeper, 3 brokers y Kafka-UI) y los 3 microservicios de streaming. La base de datos es
**Amazon RDS for PostgreSQL** y la analítica guarda perfiles en **Amazon DynamoDB**.

> El Learner Lab permite como máximo 9 EC2 encendidas: por eso ms-kafka-admin, ms-pago y
> ms-analytics comparten la EC2 de Kafka en vez de tener una cada uno.

## Arquitectura en AWS

| Componente | Dónde corre | Puerto |
|---|---|---|
| Frontend (Angular + nginx, HTTPS) | EC2 `pedidos360-front` (Elastic IP) | 80 / 443 |
| auth | EC2 `pedidos360-auth` | 8080 |
| catalogo | EC2 `pedidos360-catalogo` | 8081 |
| compras | EC2 `pedidos360-compras` | 8082 |
| usuarios | EC2 `pedidos360-usuarios` | 8084 |
| notificaciones | EC2 `pedidos360-notificaciones` | 8085 |
| ordenes | EC2 `pedidos360-ordenes` | 8086 |
| facturacion | EC2 `pedidos360-facturacion` | 8087 |
| RabbitMQ (2 nodos) | EC2 `pedidos360-stack` (Elastic IP) | 5672 / 5673, panel 15672 / 15673 |
| Kafka (3 Zookeeper + 3 brokers) | EC2 `pedidos360-stack` | 9092 / 9093 / 9094 |
| Kafka-UI | EC2 `pedidos360-stack` | 8090 |
| ms-kafka-admin | EC2 `pedidos360-stack` | 8088 |
| ms-pago | EC2 `pedidos360-stack` | 8089 |
| ms-analytics-recommendations | EC2 `pedidos360-stack` | 8091 |
| Base de datos (7 bases) | Amazon RDS `pedidos360-db` | 5432 |
| Perfiles y eventos de analítica | Amazon DynamoDB `pedidos360-perfiles`, `pedidos360-eventos` | — |

El **API Gateway** (`pedidos360-api`) valida el JWT (Entra ID en `/api/v1/...`, Cognito en
`/cognito/api/v1/...`) y llega a cada microservicio por un **VPC Link** y un **Network Load
Balancer interno** (`pedidos360-nlb`, un listener por puerto). Las EC2 de los microservicios no
quedan expuestas a internet.

El WebSocket del dashboard no pasa por el API Gateway (el HTTP API no maneja WebSockets): el
nginx del frontend lo reenvía a ms-analytics por la red privada (`ANALYTICS_UPSTREAM`).

## Archivos

- `docker-compose.yml`: RabbitMQ y todos los microservicios.
- `docker-compose.kafka.yml`: Zookeeper, brokers Kafka y Kafka-UI.
- `.env.example`: variables necesarias (copiar a `.env`, que nunca se sube al repo).
- `ec2/hosts.env`: IPs privadas de las EC2.
- `ec2/desplegar.sh`: compila las imágenes, levanta RabbitMQ y Kafka y despliega cada microservicio.
- `ec2/copiar-imagenes.sh`: copia cada imagen a la EC2 de su microservicio.
- `ec2/levantar-ec2.sh`: escribe la configuración de cada EC2 y levanta su contenedor.
- `Pedidos360.postman_collection.json`: pruebas del sistema.

## Actualizar

En la EC2 stack, con los repos en `~/pedidos360`:

```bash
cd ~/pedidos360
for r in */; do (cd "$r" && git pull); done
bash Backend-cloud-ordenes/deploy/ec2/desplegar.sh
```

## Base de datos (Amazon RDS)

Una instancia RDS PostgreSQL con una base por microservicio, creadas una sola vez:

```bash
set -a; . .env; set +a
for db in catalogo_db compras_db ordenes_db usuarios_db notificaciones_db facturacion_db pagos_db; do
  docker run --rm -e PGPASSWORD="$DB_PASS" postgres:16-alpine \
    psql -h "$DB_HOST" -U "$DB_USER" -d postgres -c "CREATE DATABASE $db"
done
```

Las tablas las crea cada microservicio al arrancar (JPA `ddl-auto: update`).

## Desarrollo local

Todo en una sola máquina, con una base local en vez de RDS:

```bash
cp .env.example .env          # DB_HOST=postgres
docker compose -f docker-compose.yml -f docker-compose.kafka.yml --profile db-local up -d --build
```

## Pruebas con Postman

Importar `Pedidos360.postman_collection.json`, pegar el JWT en la variable `token`
(con un token de AWS Cognito, cambiar `prefijo` a `/cognito`) y ejecutar la colección.
