@echo off
REM Script de teste para Teuthis (Windows)
echo 🧪 Testando Teuthis Server...

set BASE_URL=http://localhost:8080

echo 1️⃣ Testando Health Check...
curl -X GET "%BASE_URL%/health"
echo.

echo 2️⃣ Testando Metrics...
curl -X GET "%BASE_URL%/metrics" | findstr /C:"teuthis_"
echo.

echo 3️⃣ Testando Publish - Mensagem JSON...
curl -X POST "%BASE_URL%/publish/test-topic" ^
  -H "Content-Type: application/json" ^
  -d "{\"mensagem\": \"Mensagem teste\", \"timestamp\": %date:~-4,4%%date:~-10,2%%date:~-7,2%, \"usuario\": \"teste\"}"
echo.

echo 4️⃣ Testando Publish - Evento...
curl -X POST "%BASE_URL%/publish/events" ^
  -H "Content-Type: application/json" ^
  -d "{\"evento\": \"user_login\", \"usuario_id\": 123, \"timestamp\": %time%}"
echo.

echo 5️⃣ Testando Publish - Log...
curl -X POST "%BASE_URL%/publish/logs" ^
  -H "Content-Type: application/json" ^
  -d "{\"level\": \"INFO\", \"message\": \"Teste de log\", \"service\": \"teuthis-test\"}"
echo.

echo 6️⃣ Testando Publish - Texto Simples...
curl -X POST "%BASE_URL%/publish/test-topic" ^
  -H "Content-Type: text/plain" ^
  -d "Mensagem de texto simples - %date% %time%"
echo.

echo ✅ Testes concluídos!
echo.
echo 📊 Para verificar métricas no Prometheus:
echo    http://localhost:9090
echo.
echo 📈 Para ver dashboards no Grafana:
echo    http://localhost:3000 (admin/teuthis123)
echo.
echo 🎯 Para ver tópicos no Kafka UI:
echo    http://localhost:8090

pause