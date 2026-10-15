#!/bin/bash
# Configura y levanta cada microservicio en su propia EC2 (contraseñas desde el .env de esta EC2)
set -a; . ~/pedidos360/Backend-cloud-ordenes/deploy/.env; set +a
RABBIT=10.1.9.105
comun() {
  cat <<ENV
DB_HOST=$DB_HOST
DB_PORT=$DB_PORT
DB_USER=$DB_USER
DB_PASS=$DB_PASS
RABBITMQ_ADDRESSES=$RABBIT:5672,$RABBIT:5673
RABBITMQ_USER=$RABBITMQ_USER
RABBITMQ_PASS=$RABBITMQ_PASS
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
levantar auth 10.1.11.107 8080 ""
levantar catalogo 10.1.2.175 8081 "DB_NAME=catalogo_db\n"
levantar compras 10.1.13.121 8082 "DB_NAME=compras_db\n"
levantar usuarios 10.1.7.164 8084 "DB_NAME=usuarios_db\n"
levantar notificaciones 10.1.7.26 8085 "DB_NAME=notificaciones_db\n"
levantar ordenes 10.1.2.150 8086 "DB_NAME=ordenes_db\nCOMPRAS_URL=http://10.1.13.121:8082/api/v1/compras\nCATALOGO_URL=http://10.1.2.175:8081/api/v1/catalogo\n"
levantar facturacion 10.1.6.232 8087 "DB_NAME=facturacion_db\nRABBITMQ_MANAGEMENT_URLS=http://$RABBIT:15672,http://$RABBIT:15673\n"
