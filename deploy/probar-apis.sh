#!/bin/bash
# Pruebas de los dos API Gateway de Pedidos360 (se puede correr desde cualquier equipo con curl).
#   pedidos360-backend-api   microservicios: JWT de Entra ID en /api/v1, de Cognito en /cognito/api/v1
#   pedidos360-frontend-api  SPA servido desde la EC2 del frontend
# Uso: bash probar-apis.sh [URL_BACKEND] [URL_FRONTEND]
# Con TOKEN=<jwt de Entra ID> o TOKEN_COGNITO=<id token> también prueba las rutas protegidas.
BACK=${1:-https://lxee04oi4d.execute-api.us-east-1.amazonaws.com}
FRONT=${2:-https://nrm625ugsd.execute-api.us-east-1.amazonaws.com}
ORIGENES="$FRONT https://44.207.141.228 http://localhost:4200"
SERVICIOS="auth catalogo compras usuarios notificaciones ordenes facturacion kafka-admin pagos analytics"

ok=0; fallas=0
revisar() { # descripción esperado obtenido
  if [ "$2" = "$3" ]; then ok=$((ok + 1)); else fallas=$((fallas + 1)); echo "FALLA  $1: se esperaba $2 y llegó $3"; fi
}
codigo() { curl -s -o /dev/null -w '%{http_code}' -m 15 "$@"; }

echo "== Backend: rutas públicas"
for pre in "" /cognito; do
  revisar "GET $pre/api/v1/catalogo/public" 200 "$(codigo "$BACK$pre/api/v1/catalogo/public")"
  revisar "GET $pre/api/v1/catalogo/public/1" 200 "$(codigo "$BACK$pre/api/v1/catalogo/public/1")"
done
for s in facturacion pagos analytics kafka-admin; do
  revisar "GET /api/v1/$s/health" 200 "$(codigo "$BACK/api/v1/$s/health")"
done
revisar "GET /api/v1/pagos/webpay/retorno (redirige al front)" 302 "$(codigo "$BACK/api/v1/pagos/webpay/retorno")"
revisar "POST /api/v1/pagos/webpay/retorno (redirige al front)" 302 "$(codigo -X POST "$BACK/api/v1/pagos/webpay/retorno")"

echo "== Backend: sin token o con token inválido el autorizador responde 401"
for pre in "" /cognito; do
  for s in $SERVICIOS; do
    for m in GET POST PUT DELETE; do
      revisar "$m $pre/api/v1/$s/x sin token" 401 "$(codigo -X $m "$BACK$pre/api/v1/$s/x")"
    done
    revisar "GET $pre/api/v1/$s con token inválido" 401 \
      "$(codigo -H 'Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.e30.firma' "$BACK$pre/api/v1/$s")"
  done
done
revisar "Ruta que no existe" 404 "$(codigo "$BACK/api/v1/no-existe")"

echo "== Backend: CORS"
for o in $ORIGENES; do
  r=$(curl -s -o /dev/null -D - -m 15 -X OPTIONS "$BACK/api/v1/compras/carrito" -H "Origin: $o" \
      -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: authorization,content-type' | tr -d '\r')
  revisar "preflight desde $o" 204 "$(echo "$r" | head -1 | awk '{print $2}')"
  revisar "allow-origin para $o" "$o" "$(echo "$r" | grep -i '^access-control-allow-origin:' | awk '{print $2}')"
  revisar "allow-headers para $o" "authorization,content-type" \
    "$(echo "$r" | grep -i '^access-control-allow-headers:' | awk '{print $2}')"
  r=$(curl -s -o /dev/null -D - -m 15 "$BACK/api/v1/catalogo/public" -H "Origin: $o" | tr -d '\r')
  revisar "GET con Origin $o lleva allow-origin" "$o" "$(echo "$r" | grep -i '^access-control-allow-origin:' | awk '{print $2}')"
done
r=$(curl -s -o /dev/null -D - -m 15 -X OPTIONS "$BACK/api/v1/compras/carrito" -H 'Origin: https://sitio-ajeno.example' \
    -H 'Access-Control-Request-Method: POST' | tr -d '\r')
revisar "origen no permitido sin allow-origin" "" "$(echo "$r" | grep -i '^access-control-allow-origin:' | awk '{print $2}')"

if [ -n "$TOKEN" ] || [ -n "$TOKEN_COGNITO" ]; then
  echo "== Backend: rutas protegidas con token"
  for par in "${TOKEN:+:$TOKEN}" "${TOKEN_COGNITO:+/cognito:$TOKEN_COGNITO}"; do
    [ -z "$par" ] && continue
    pre=${par%%:*}; tk=${par#*:}
    for ruta in auth/me usuarios/me catalogo/privado compras/carrito ordenes facturacion/boletas/mias pagos/mios analytics/recomendaciones; do
      revisar "GET $pre/api/v1/$ruta con token" 200 "$(codigo -H "Authorization: Bearer $tk" "$BACK$pre/api/v1/$ruta")"
    done

    # Antifraude con tarjeta: la misma tarjeta 4 veces en menos de 10 s (orden de prueba que no existe)
    orden=$((900000 + RANDOM))
    cuerpo="{\"ordenId\":$orden,\"monto\":1000,\"numeroTarjeta\":\"4111 1111 1111 1111\",\"titular\":\"Prueba\",\"vencimiento\":\"12/30\",\"cvv\":\"123\"}"
    codigos=""
    for i in 1 2 3 4; do
      codigos="$codigos $(codigo -X POST -H "Authorization: Bearer $tk" -H 'Content-Type: application/json' -d "$cuerpo" "$BACK$pre/api/v1/pagos")"
    done
    revisar "$pre antifraude con tarjeta (201, 400, 400, 403)" " 201 400 400 403" "$codigos"

    # Antifraude en Webpay: el mismo usuario inicia 4 pagos en menos de 10 s
    codigos=""
    for i in 1 2 3 4; do
      codigos="$codigos $(codigo -X POST -H "Authorization: Bearer $tk" -H 'Content-Type: application/json' \
        -d "{\"ordenId\":$((orden + 1)),\"monto\":1000}" "$BACK$pre/api/v1/pagos/webpay")"
    done
    revisar "$pre antifraude en Webpay (201, 201, 201, 403)" " 201 201 201 403" "$codigos"
    sleep 11   # deja pasar la ventana antes de probar con el otro proveedor
  done
fi

echo "== Frontend"
revisar "GET / (sin -k: certificado válido)" 200 "$(codigo "$FRONT/")"
revisar "GET / es HTML" "text/html" "$(curl -s -o /dev/null -w '%{content_type}' "$FRONT/")"
for ruta in /carrito /ordenes /perfil /ingresar /admin/dashboard "/ordenes?pago=aprobado&orden=1"; do
  revisar "GET $ruta (ruta del router)" 200 "$(codigo "$FRONT$ruta")"
done
bundles=$(curl -s -m 15 "$FRONT/" | grep -oE '(main|chunk|polyfills)-[A-Z0-9]+\.js' | sort -u)
apunta=""
for js in $bundles; do
  revisar "GET /$js" 200 "$(codigo "$FRONT/$js")"
  curl -s -m 15 "$FRONT/$js" | grep -q "${BACK#https://}" && apunta=si
done
revisar "el bundle apunta al API de backend" si "$apunta"
revisar "POST / no está permitido" 404 "$(codigo -X POST "$FRONT/")"

# El API de frontend no tiene CORS: el navegador pide la página desde su mismo origen
echo "Resultado: $ok correctas, $fallas fallas"
[ "$fallas" -eq 0 ]
