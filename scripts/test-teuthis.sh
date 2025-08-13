#!/bin/bash

# Script de teste para Teuthis
echo "🧪 Testando Teuthis Server..."

BASE_URL="http://localhost:8080"

echo "1️⃣ Testando Health Check..."
curl -X GET "$BASE_URL/health" -w "\n"

echo -e "\n2️⃣ Testando Metrics..."
curl -X GET "$BASE_URL/metrics" | head -10

echo -e "\n3️⃣ Testando Publish - Mensagem JSON..."
curl -X POST "$BASE_URL/publish/test-topic" \
  -H "Content-Type: application/json" \
  -d '{
    "mensagem": "Mensagem teste",
    "timestamp": '$(date +%s)',
    "usuario": "teste"
  }' -w "\n"

echo -e "\n4️⃣ Testando Publish - Evento..."
curl -X POST "$BASE_URL/publish/events" \
  -H "Content-Type: application/json" \
  -d '{
    "evento": "user_login",
    "usuario_id": 123,
    "timestamp": '$(date +%s)'
  }' -w "\n"

echo -e "\n5️⃣ Testando Publish - Log..."
curl -X POST "$BASE_URL/publish/logs" \
  -H "Content-Type: application/json" \
  -d '{
    "level": "INFO",
    "message": "Teste de log",
    "service": "teuthis-test",
    "timestamp": '$(date +%s)'
  }' -w "\n"

echo -e "\n6️⃣ Testando Publish - XML..."
curl -X POST "$BASE_URL/publish/test-topic" \
  -H "Content-Type: application/xml" \
  -d '<?xml version="1.0"?>
  <mensagem>
    <texto>Teste XML</texto>
    <timestamp>'$(date +%s)'</timestamp>
  </mensagem>' -w "\n"

echo -e "\n7️⃣ Testando Publish - SOAP..."
curl -X POST "$BASE_URL/publish/test-topic" \
  -H "Content-Type: text/xml" \
  -d '<?xml version="1.0"?>
  <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <soap:Body>
      <TestMessage>
        <text>Teste SOAP</text>
        <timestamp>'$(date +%s)'</timestamp>
      </TestMessage>
    </soap:Body>
  </soap:Envelope>' -w "\n"

echo -e "\n8️⃣ Testando Publish - Texto Simples..."
curl -X POST "$BASE_URL/publish/test-topic" \
  -H "Content-Type: text/plain" \
  -d "Mensagem de texto simples - $(date)" -w "\n"

echo -e "\n✅ Testes concluídos!"

echo -e "\n📊 Para verificar métricas no Prometheus:"
echo "   http://localhost:9090"

echo -e "\n📈 Para ver dashboards no Grafana:"
echo "   http://localhost:3000 (admin/teuthis123)"

echo -e "\n🎯 Para ver tópicos no Kafka UI:"
echo "   http://localhost:8090"