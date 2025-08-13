@echo off
REM Script de debug para Teuthis via Docker - testa um request simples
echo 🐛 Debug simples do Teuthis via Docker...

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
echo 1️⃣ Testando Health primeiro via Docker...
docker run --rm --network %NETWORK% curlimages/curl:latest -v -X GET "%BASE_URL%/health"
echo.

echo 2️⃣ Testando Metrics via Docker...
echo Métricas do Teuthis:
docker run --rm --network %NETWORK% curlimages/curl:latest -s "%BASE_URL%/metrics" | findstr "teuthis_"
echo.

echo 3️⃣ Testando POST simples via Docker...
echo Enviando mensagem JSON simples...
docker run --rm --network %NETWORK% curlimages/curl:latest -v -X POST "%BASE_URL%/publish/test-topic" ^
  -H "Content-Type: application/json" ^
  -d "{\"mensagem\": \"Teste debug simples via Docker\"}"

echo.
echo 4️⃣ Verificando logs do container...
echo Últimas 10 linhas do log:
docker-compose logs --tail=10 teuthis-app

echo.
echo 5️⃣ Testando conectividade dentro da rede Docker...
echo Ping do container curl para o Teuthis:
docker run --rm --network %NETWORK% curlimages/curl:latest -s --connect-timeout 5 "%BASE_URL%/health" && echo "✅ Conectividade OK" || echo "❌ Falha na conectividade"

echo.
echo 🐳 Debug via Docker concluído!
echo.
echo 💡 Vantagens:
echo    ✅ Testa dentro da rede Docker
echo    ✅ Isola problemas de rede local
echo    ✅ Usa exatamente o mesmo ambiente

pause