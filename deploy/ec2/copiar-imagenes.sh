#!/bin/bash
# Copia cada imagen a la EC2 de su microservicio por la red privada de la VPC
for par in frontend:10.1.0.104 auth:10.1.11.107 catalogo:10.1.2.175 compras:10.1.13.121 notificaciones:10.1.7.26 ordenes:10.1.2.150 usuarios:10.1.7.164 facturacion:10.1.6.232; do
  s=${par%%:*}; ip=${par##*:}
  for i in $(seq 1 30); do ssh -n -i ~/.ssh/p360.pem -o StrictHostKeyChecking=accept-new -o ConnectTimeout=5 ec2-user@$ip "docker info >/dev/null 2>&1" && break; sleep 10; done
  if docker save pedidos360-$s | gzip -1 | ssh -i ~/.ssh/p360.pem -o StrictHostKeyChecking=accept-new ec2-user@$ip "gunzip | docker load" > /tmp/copia-$s.log 2>&1; then echo "OK $s -> $ip"; else echo "FALLA $s -> $ip"; fi
done
echo FIN
