#!/bin/bash

let orderId=$(date +%s)

while [ 1 == 1 ]; do
  curl -kv  -H "Content-Type: application/json" \
      -d "{\"id\":\"${orderId}\",\"qty\":45.2,\"product\":{\"assetClass\":\"Fixed Income\",\"isin\":\"US03783310\"}}" \
      -X POST  http://localhost:8080/order/v1/new
  if [ $(expr $orderId % 10) -eq 0 ] ; then
    sleep 1
  fi
  let orderId=orderId+1
done
