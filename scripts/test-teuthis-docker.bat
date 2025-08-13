@echo off
REM Script de teste para Teuthis (Windows) - Usa Docker
echo 🧪 Testando Teuthis Server via Docker...

set BASE_URL=http://teuthis-application:8080
set NETWORK=teuthis-network

echo.
echo 🐳 Verificando se a rede Docker existe...
docker network ls | findstr %NETWORK% >nul
if %errorlevel% neq 0 (
    echo ❌ Rede %NETWORK% não encontrada. Execute: docker-compose up -d
    pause
    exit /b 1
)

echo ✅ Rede Docker %NETWORK% encontrada

echo.
echo 1️⃣ Testando Health Check...
docker run --rm --network %NETWORK% curlimages/curl:latest -X GET "%BASE_URL%/health"
echo.

echo 2️⃣ Testando Metrics (primeiras linhas)...
docker run --rm --network %NETWORK% curlimages/curl:latest -s "%BASE_URL%/metrics" | findstr "teuthis_"
echo.

echo 3️⃣ Testando Publish - Mensagem JSON...
docker run --rm --network %NETWORK% curlimages/curl:latest ^
  -X POST "%BASE_URL%/publish/test-topic" ^
  -H "Content-Type: application/json" ^
  -d "{\"mensagem\": \"Mensagem teste via Docker\", \"timestamp\": \"%date% %time%\", \"usuario\": \"docker-test\"}"
echo.

echo 4️⃣ Testando Publish - Evento...
docker run --rm --network %NETWORK% curlimages/curl:latest ^
  -X POST "%BASE_URL%/publish/events" ^
  -H "Content-Type: application/json" ^
  -d "{\"evento\": \"user_login_docker\", \"usuario_id\": 123, \"timestamp\": \"%time%\"}"
echo.

echo 5️⃣ Testando Publish - Log...
docker run --rm --network %NETWORK% curlimages/curl:latest ^
  -X POST "%BASE_URL%/publish/logs" ^
  -H "Content-Type: application/json" ^
  -d "{\"level\": \"INFO\", \"message\": \"Teste de log via Docker\", \"service\": \"teuthis-docker-test\"}"
echo.

echo 6️⃣ Testando Publish - Texto Simples...
docker run --rm --network %NETWORK% curlimages/curl:latest ^
  -X POST "%BASE_URL%/publish/test-topic" ^
  -H "Content-Type: text/plain" ^
  -d "Mensagem de texto simples via Docker - %date% %time%"
echo.

echo 7️⃣ Testando Resource Threshold...
echo Verificando threshold atual:
docker run --rm --network %NETWORK% curlimages/curl:latest -s "%BASE_URL%/metrics" | findstr "teuthis_resource_threshold"
echo.

echo ✅ Testes concluídos via Docker!
echo.
echo 📊 Para verificar métricas no Prometheus:
echo    http://localhost:9090
echo.
echo 📈 Para ver dashboards no Grafana:
echo    http://localhost:3000 (admin/teuthis123)
echo.
echo 🎯 Para ver tópicos no Kafka UI:
echo    http://localhost:8090
echo.
echo 🐳 Vantagens do uso via Docker:
echo    ✅ Não precisa instalar curl
echo    ✅ Testa dentro da rede do Docker
echo    ✅ Reproduz ambiente real

pause