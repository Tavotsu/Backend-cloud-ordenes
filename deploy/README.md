# Despliegue de Pedidos360

Stack completo con Docker Compose en una sola EC2: RabbitMQ (clúster de 2 nodos), los 7
microservicios y el frontend. La base de datos es **Amazon RDS for PostgreSQL** (privada, solo
accesible desde la EC2).

## Estructura esperada

Los 8 repos clonados uno al lado del otro:

```
pedidos360/
├── Backend-cloud-auth/
├── backend-cloud-catalogo/
├── backend-cloud-compras/
├── backend-usuario/
├── backend-notificaciones-/        (contiene bakcend-notificaciones/)
├── Backend-cloud-ordenes/
│   └── deploy/                     <- este directorio
├── backend-facturacion/
└── front_pedido/
```

## Base de datos (Amazon RDS)

Una instancia RDS PostgreSQL con una base por microservicio. Se crean una sola vez desde la EC2:

```bash
source .env
for db in catalogo_db compras_db ordenes_db usuarios_db notificaciones_db facturacion_db; do
  docker run --rm -e PGPASSWORD="$DB_PASS" postgres:16-alpine \
    psql -h "$DB_HOST" -U "$DB_USER" -d postgres -c "CREATE DATABASE $db"
done
```

Las tablas las crea cada microservicio al arrancar (JPA `ddl-auto: update`).

## Levantar

```bash
cd Backend-cloud-ordenes/deploy
cp .env.example .env          # completar PUBLIC_HOST, DB_HOST (endpoint de RDS) y contraseñas
docker compose up -d --build
```

Para desarrollo sin RDS: `DB_HOST=postgres` y `docker compose --profile db-local up -d --build`
(levanta además un Postgres local que crea las 6 bases).

En instancias pequeñas conviene compilar las imágenes de a una
(`docker compose build auth`, `docker compose build catalogo`, ...) y después `docker compose up -d`.

## Servicios

| Servicio | Puerto |
|---|---|
| frontend | 80 / 443 |
| auth | 8080 |
| catalogo | 8081 |
| compras | 8082 |
| usuarios | 8084 |
| notificaciones | 8085 |
| ordenes | 8086 |
| facturacion | 8087 |
| RabbitMQ (panel nodo 1 / nodo 2) | 15672 / 15673 |

La alta disponibilidad de RabbitMQ (cada cola replicada en ambos nodos) la mantiene el
microservicio de facturación (`RabbitHaService`).

## Pruebas con Postman

Importar `Pedidos360.postman_collection.json`, pegar el JWT en la variable `token`
(con un token de AWS Cognito, cambiar `prefijo` a `/cognito`) y ejecutar la colección.
Incluye la validación del JWT en el API Gateway (401 sin token), el flujo de compra,
el envío de correo de notificaciones y la API de administración de RabbitMQ.
