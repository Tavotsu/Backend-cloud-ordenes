# Despliegue de Pedidos360

Cada microservicio corre en su propia EC2. RabbitMQ (clúster de 2 nodos) corre con
docker-compose en una EC2 aparte, y la base de datos es **Amazon RDS for PostgreSQL**.

## Arquitectura en AWS

| Componente | Dónde corre | Puerto |
|---|---|---|
| Frontend (Angular + nginx, HTTPS) | EC2 `front-pedidos` — Elastic IP 3.213.91.126 | 80 / 443 |
| auth | EC2 `pedidos360-auth` | 8080 |
| catalogo | EC2 `pedidos360-catalogo` | 8081 |
| compras | EC2 `pedidos360-compra` | 8082 |
| usuarios | EC2 `pedidos360-usuario` | 8084 |
| notificaciones | EC2 `pedidos360-notificaciones` | 8085 |
| ordenes | EC2 `pedidos360-ordenes` | 8086 |
| facturacion | EC2 `pedidos360-facturacion` | 8087 |
| RabbitMQ (2 nodos, docker-compose) | EC2 `pedidos360-stack` — Elastic IP 100.30.102.86 | 5672 / 5673, panel 15672 / 15673 |
| Base de datos (6 bases) | Amazon RDS `pedidos360-db` | 5432 |

El **API Gateway** (`pedidos360-api`) valida el JWT (Entra ID en `/api/v1/...`, Cognito en
`/cognito/api/v1/...`) y llega a cada EC2 por un **VPC Link** y un **Network Load Balancer
interno** (`pedidos360-nlb`, un listener por puerto, cada uno hacia la EC2 de su
microservicio). Así las EC2 de los microservicios no quedan expuestas a internet y el
Gateway no depende de sus IPs públicas, que cambian al reiniciar.

Los microservicios se comunican por la red privada de la VPC: con RabbitMQ en
`10.1.9.105:5672,5673`, con RDS por su endpoint, y ordenes con compras y catálogo por sus
IPs privadas.

## Archivos

- `docker-compose.yml`: el stack completo. En AWS solo se levantan `rabbitmq1` y `rabbitmq2`;
  los demás servicios se compilan aquí y se ejecutan en su propia EC2.
- `.env.example`: variables necesarias (copiar a `.env`, que nunca se sube al repo).
- `ec2/desplegar.sh`: compila las imágenes, levanta RabbitMQ y despliega cada microservicio.
- `ec2/copiar-imagenes.sh`: copia cada imagen a la EC2 de su microservicio.
- `ec2/levantar-ec2.sh`: escribe la configuración de cada EC2 y levanta su contenedor.
- `Pedidos360.postman_collection.json`: pruebas del sistema.

## Actualizar

En la EC2 de RabbitMQ, con los 8 repos clonados en `~/pedidos360`:

```bash
cd ~/pedidos360
for r in */; do (cd "$r" && git pull); done
Backend-cloud-ordenes/deploy/ec2/desplegar.sh
```

## Base de datos (Amazon RDS)

Una instancia RDS PostgreSQL con una base por microservicio, creadas una sola vez:

```bash
source .env
for db in catalogo_db compras_db ordenes_db usuarios_db notificaciones_db facturacion_db; do
  docker run --rm -e PGPASSWORD="$DB_PASS" postgres:16-alpine \
    psql -h "$DB_HOST" -U "$DB_USER" -d postgres -c "CREATE DATABASE $db"
done
```

Las tablas las crea cada microservicio al arrancar (JPA `ddl-auto: update`).

## Desarrollo local

Todo en una sola máquina, con una base local en vez de RDS:

```bash
cp .env.example .env          # DB_HOST=postgres
docker compose --profile db-local up -d --build
```

## Pruebas con Postman

Importar `Pedidos360.postman_collection.json`, pegar el JWT en la variable `token`
(con un token de AWS Cognito, cambiar `prefijo` a `/cognito`) y ejecutar la colección.
Incluye la validación del JWT en el API Gateway (401 sin token), el flujo de compra,
el envío de correo de notificaciones y la API de administración de RabbitMQ.
