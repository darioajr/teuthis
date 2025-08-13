@echo off
REM Script para gerar carga e testar alertas de recursos
echo 🔥 Gerando carga para testar alertas de recursos...

set BASE_URL=http://localhost:8080
set /a REQUESTS=0

echo.
echo 📊 Monitoramento:
echo   Prometheus: http://localhost:9090/alerts
echo   Grafana: http://localhost:3000
echo.
echo 🎯 Enviando muitas requisições para aumentar uso de recursos...
echo.

:LOOP
set /a REQUESTS+=1
echo Requisição %REQUESTS%...

REM Enviar várias requisições simultâneas
start /b curl -s -X POST "%BASE_URL%/publish/test-topic" -H "Content-Type: application/json" -d "{\"load_test\": true, \"request\": %REQUESTS%, \"timestamp\": \"%date% %time%\", \"data\": \"Este é um payload maior para aumentar o processamento e uso de recursos do servidor Teuthis durante o teste de carga\"}"

start /b curl -s -X POST "%BASE_URL%/publish/events" -H "Content-Type: application/json" -d "{\"event\": \"load_test\", \"request_id\": %REQUESTS%, \"cpu_intensive\": true}"

start /b curl -s -X POST "%BASE_URL%/publish/logs" -H "Content-Type: application/json" -d "{\"level\": \"INFO\", \"message\": \"Load test request %REQUESTS%\", \"service\": \"load-tester\"}"

REM Pequena pausa para não sobrecarregar muito rápido
timeout /t 1 /nobreak >nul

REM Continuar por 100 requisições
if %REQUESTS% LSS 100 goto LOOP

echo.
echo ✅ Carga gerada! Agora verifique:
echo.
echo 1️⃣ Prometheus Alerts: http://localhost:9090/alerts
echo    - Procure por alertas "TeuthisResourcesOverThreshold"
echo.
echo 2️⃣ Grafana Dashboard: http://localhost:3000
echo    - Vá para "Teuthis - Kafka Producer Monitoring"
echo    - Observe os painéis de recursos
echo.
echo 3️⃣ Logs do Teuthis:
echo    docker-compose logs teuthis-app | findstr "Resource usage"
echo.
echo 4️⃣ Métricas atuais:
curl -s http://localhost:8080/metrics | findstr "teuthis_cpu_usage\|teuthis_memory_usage\|teuthis_disk_usage"

pause