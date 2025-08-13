@echo off
REM Script de debug para Teuthis - testa um request simples
echo 🐛 Debug simples do Teuthis...

set BASE_URL=http://localhost:8080

REM Definir comando curl
if exist "curl.exe" (
    set CURL_CMD=curl.exe
) else (
    set CURL_CMD=curl
)

echo.
echo 1️⃣ Testando Health primeiro...
%CURL_CMD% -v -X GET "%BASE_URL%/health"
echo.

echo 2️⃣ Testando Metrics...
%CURL_CMD% -s "%BASE_URL%/metrics" | findstr "teuthis_"
echo.

echo 3️⃣ Testando POST simples...
echo Enviando mensagem JSON simples...
%CURL_CMD% -v -X POST "%BASE_URL%/publish/test-topic" ^
  -H "Content-Type: application/json" ^
  -d "{\"mensagem\": \"Teste debug simples\"}"

echo.
echo 4️⃣ Verificando logs do container...
echo Últimas 10 linhas do log:
docker-compose logs --tail=10 teuthis-app

echo.
pause