@echo off
REM Script para testar alertas específicos de recursos
echo 🎯 Testando alertas específicos de recursos...

set BASE_URL=http://localhost:8080

REM Definir comando curl
if exist "curl.exe" (
    set CURL_CMD=curl.exe
) else (
    set CURL_CMD=curl
)

echo.
echo 📊 Estado inicial dos recursos:
%CURL_CMD% -s "%BASE_URL%/metrics" | findstr "teuthis_cpu_usage\|teuthis_memory_usage\|teuthis_disk_usage"

echo.
echo 🔥 Gerando carga intensa para forçar recursos altos...

REM Gerar muitas requisições simultaneamente para aumentar CPU e memory
echo Enviando 50 requisições grandes simultâneas...

for /L %%i in (1,1,50) do (
    start /b %CURL_CMD% -s -X POST "%BASE_URL%/publish/test-topic" ^
      -H "Content-Type: application/json" ^
      -d "{\"load_test\": true, \"request\": %%i, \"large_payload\": \"Este é um payload muito grande para aumentar o uso de CPU e memória durante o processamento. Repetindo dados para aumentar o tamanho: Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.\"}"
)

echo Aguardando processamento...
timeout /t 3 /nobreak >nul

echo.
echo 📈 Estado dos recursos após carga:
%CURL_CMD% -s "%BASE_URL%/metrics" | findstr "teuthis_cpu_usage\|teuthis_memory_usage\|teuthis_disk_usage"

echo.
echo 🧪 Testando request quando recursos podem estar altos...
echo Resposta esperada se algum recurso ^>= 85%%:
echo "HTTP 429 Too Many Requests"
echo "Recursos limitados: [CPU/RAM/Disco específico] ^>= 85%%"
echo.

%CURL_CMD% -v -X POST "%BASE_URL%/publish/test-topic" ^
  -H "Content-Type: application/json" ^
  -d "{\"test\": \"checking resource limits\"}"

echo.
echo 📋 Verificando logs do Teuthis para ver recursos específicos:
echo Procurando por "Resource usage" ou "Recursos limitados"...
docker-compose logs --tail=20 teuthis-app | findstr /C:"Resource usage" /C:"Recursos limitados" /C:"High resource"

echo.
echo 📊 URLs para monitorar:
echo   Prometheus Alerts: http://localhost:9090/alerts
echo   Grafana Dashboard: http://localhost:3000
echo   Métricas diretas: %BASE_URL%/metrics

pause