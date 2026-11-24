#!/bin/bash
# Copia cada imagen a la EC2 de su microservicio por la red privada de la VPC
DIR=$(cd "$(dirname "$0")" && pwd)
. "$DIR/hosts.env"
for par in frontend:$FRONT auth:$AUTH catalogo:$CATALOGO compras:$COMPRAS notificaciones:$NOTIFICACIONES ordenes:$ORDENES usuarios:$USUARIOS facturacion:$FACTURACION; do
  s=${par%%:*}; ip=${par##*:}
  for i in $(seq 1 30); do ssh -n -i ~/.ssh/p360.pem -o StrictHostKeyChecking=accept-new -o ConnectTimeout=5 ec2-user@$ip "docker info >/dev/null 2>&1" && break; sleep 10; done
  if docker save pedidos360-$s | gzip -1 | ssh -i ~/.ssh/p360.pem -o StrictHostKeyChecking=accept-new ec2-user@$ip "gunzip | docker load" > /tmp/copia-$s.log 2>&1; then echo "OK $s -> $ip"; else echo "FALLA $s -> $ip"; fi
done
echo FIN
