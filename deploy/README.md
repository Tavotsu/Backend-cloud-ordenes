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
| Kafka-UI | EC2 `pedidos360-stack` | 8080 y 8090 |
| ms-kafka-admin | EC2 `pedidos360-stack` | 8088 |
| ms-pago | EC2 `pedidos360-stack` | 8089 |
| ms-analytics-recommendations | EC2 `pedidos360-stack` | 8091 |
| Base de datos (7 bases) | Amazon RDS `pedidos360-db` | 5432 |
| Perfiles y eventos de analítica | Amazon DynamoDB `pedidos360-perfiles`, `pedidos360-eventos` | — |

## API Gateway

Hay dos HTTP API separadas, las dos con el mismo **VPC Link** hacia el **Network Load Balancer
interno** (`pedidos360-nlb`, un listener por puerto). Las EC2 no quedan expuestas a internet a
través de ellas.

| API | URL | Qué hace |
|---|---|---|
| `pedidos360-backend-api` | `https://lxee04oi4d.execute-api.us-east-1.amazonaws.com` | Microservicios. Valida el JWT antes de enrutar |
| `pedidos360-frontend-api` | `https://nrm625ugsd.execute-api.us-east-1.amazonaws.com` | Sirve el SPA desde la EC2 del frontend (NLB :80), con certificado válido de AWS |

**Backend.** Por cada microservicio hay rutas `GET`, `POST`, `PUT` y `DELETE` para
`/api/v1/<servicio>` y `/api/v1/<servicio>/{proxy+}` (no se usa `ANY`, porque capturaría el
preflight `OPTIONS` y rompería CORS).
- `/api/v1/...` usa el autorizador JWT `entra-id`.
- `/cognito/api/v1/...` usa el autorizador JWT `cognito`. La integración reescribe la ruta a
  `/api/v1/...`.
- Sin autorizador: `catalogo/public[/{id}]` (en los dos prefijos), los `health` de facturación,
  pagos, analytics y kafka-admin, y `pagos/webpay/retorno` (`GET` y `POST`, porque Transbank vuelve
  sin token).

Los roles (`User`, `Admin`) los valida cada microservicio con su `SecurityConfig`. El autorizador
del gateway solo verifica que el token sea válido (firma, emisor, audiencia y expiración).

**CORS.** Lo responde el API Gateway de backend:
- orígenes: la URL del frontend API, `https://44.207.141.228` y `http://localhost:4200`;
- métodos: `GET`, `POST`, `PUT`, `DELETE` y `OPTIONS`;
- headers: `authorization` y `content-type`;
- max-age: 3600.

**Frontend.** Rutas `GET /` y `GET /{proxy+}`. El nginx resuelve las rutas del router de Angular.
El WebSocket del dashboard no pasa por ningún API Gateway, porque el HTTP API no maneja WebSockets.
Si se entra por la Elastic IP, el nginx lo reenvía a ms-analytics (`ANALYTICS_UPSTREAM`). Si se
entra por el frontend API, el dashboard consulta las métricas por REST cada 10 segundos.

Para iniciar sesión desde el frontend API, su URL (con `/` final) tiene que estar como Redirect URI
SPA en Entra ID y como callback y logout URL en el app client de Cognito. Webpay devuelve al
cliente al mismo origen desde el que inició el pago.

Las pruebas de las dos API están en `probar-apis.sh`. Se puede pasar un token en `TOKEN` (Entra ID) o
en `TOKEN_COGNITO` para probar también las rutas protegidas.

## Archivos

- `docker-compose.yml`: RabbitMQ y todos los microservicios.
- `docker-compose.kafka.yml`: Zookeeper, brokers Kafka y Kafka-UI.
- `.env.example`: variables necesarias (copiar a `.env`, que nunca se sube al repo).
- `ec2/hosts.env`: IPs privadas de las EC2.
- `ec2/desplegar.sh`: compila las imágenes, levanta RabbitMQ y Kafka y despliega cada microservicio.
- `ec2/copiar-imagenes.sh`: copia cada imagen a la EC2 de su microservicio.
- `ec2/levantar-ec2.sh`: escribe la configuración de cada EC2 y levanta su contenedor.
- `probar-apis.sh`: pruebas de rutas, autorizadores y CORS de los dos API Gateway.
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
