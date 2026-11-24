#!/bin/bash
# Configura y levanta cada microservicio en su propia EC2 (contraseñas desde el .env de esta EC2)
DIR=$(cd "$(dirname "$0")" && pwd)
. "$DIR/hosts.env"
set -a; . "$DIR/../.env"; set +a
comun() {
  cat <<ENV
DB_HOST=$DB_HOST
DB_PORT=$DB_PORT
DB_USER=$DB_USER
DB_PASS=$DB_PASS
RABBITMQ_ADDRESSES=$STACK:5672,$STACK:5673
RABBITMQ_USER=$RABBITMQ_USER
RABBITMQ_PASS=$RABBITMQ_PASS
KAFKA_BOOTSTRAP_SERVERS=$STACK:9092,$STACK:9093,$STACK:9094
COGNITO_ISSUER=$COGNITO_ISSUER
COGNITO_CLIENT_ID=$COGNITO_CLIENT_ID
CORS_ALLOWED_ORIGINS=https://$PUBLIC_HOST,http://localhost:4200
ENV
}
levantar() { # servicio ip puerto extra
  s=$1; ip=$2; p=$3; extra=$4
  { comun; printf "%b" "$extra"; } | ssh -i ~/.ssh/p360.pem -o StrictHostKeyChecking=accept-new ec2-user@$ip "umask 077; cat > ~/pedidos360.env; cat > ~/levantar.sh <<SH
#!/bin/bash
# Levanta el microservicio $s de Pedidos360 en esta EC2
docker rm -f $s >/dev/null 2>&1
docker run -d --name $s --restart unless-stopped --memory 700m -p $p:$p --env-file ~/pedidos360.env pedidos360-$s
SH
chmod +x ~/levantar.sh && ~/levantar.sh >/dev/null && echo \"OK $s en $ip:$p\""
}
levantar auth $AUTH 8080 ""
levantar catalogo $CATALOGO 8081 "DB_NAME=catalogo_db\n"
levantar compras $COMPRAS 8082 "DB_NAME=compras_db\n"
levantar usuarios $USUARIOS 8084 "DB_NAME=usuarios_db\n"
levantar notificaciones $NOTIFICACIONES 8085 "DB_NAME=notificaciones_db\nMAIL_ENABLED=${MAIL_ENABLED:-false}\nMAIL_USERNAME=$MAIL_USERNAME\nMAIL_PASSWORD=$MAIL_PASSWORD\nMAIL_FROM=$MAIL_FROM\n"
levantar ordenes $ORDENES 8086 "DB_NAME=ordenes_db\nCOMPRAS_URL=http://$COMPRAS:8082/api/v1/compras\nCATALOGO_URL=http://$CATALOGO:8081/api/v1/catalogo\n"
levantar facturacion $FACTURACION 8087 "DB_NAME=facturacion_db\nRABBITMQ_MANAGEMENT_URLS=http://$STACK:15672,http://$STACK:15673\n"

# Frontend: nginx reenvía el WebSocket del dashboard a ms-analytics (EC2 stack)
ssh -i ~/.ssh/p360.pem -o StrictHostKeyChecking=accept-new ec2-user@$FRONT "cat > ~/levantar.sh <<SH
#!/bin/bash
# Levanta el frontend de Pedidos360 en esta EC2
docker rm -f frontend >/dev/null 2>&1
docker run -d --name frontend --restart unless-stopped --memory 128m -p 80:80 -p 443:443 -e ANALYTICS_UPSTREAM=$STACK:8091 pedidos360-frontend
SH
chmod +x ~/levantar.sh && ~/levantar.sh >/dev/null && echo \"OK frontend en $FRONT\""
