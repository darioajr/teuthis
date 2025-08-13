@echo off
REM Script para gerar carga e testar alertas de recursos via Docker
echo 🔥 Gerando carga para testar alertas de recursos via Docker...

set BASE_URL=http://teuthis-application:8080
set NETWORK=teuthis-network
set /a REQUESTS=0

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
echo 📊 Estado inicial dos recursos:
docker run --rm --network %NETWORK% curlimages/curl:latest -s "%BASE_URL%/metrics" | findstr "teuthis_cpu_usage\|teuthis_memory_usage\|teuthis_disk_usage\|teuthis_resource_threshold"

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

REM Enviar várias requisições simultâneas via Docker
start /b docker run --rm --network %NETWORK% curlimages/curl:latest -s -X POST "%BASE_URL%/publish/test-topic" -H "Content-Type: application/json" -d "{\"load_test\": true, \"request\": %REQUESTS%, \"timestamp\": \"%date% %time%\", \"data\": \"Este é um payload maior para aumentar o processamento e uso de recursos do servidor Teuthis durante o teste de carga via Docker. Adicionando mais dados para consumir mais CPU e memória durante o processamento.\"}"

start /b docker run --rm --network %NETWORK% curlimages/curl:latest -s -X POST "%BASE_URL%/publish/events" -H "Content-Type: application/json" -d "{\"event\": \"load_test_docker\", \"request_id\": %REQUESTS%, \"cpu_intensive\": true, \"large_data\": \"Dados adicionais para aumentar carga\"}"

start /b docker run --rm --network %NETWORK% curlimages/curl:latest -s -X POST "%BASE_URL%/publish/logs" -H "Content-Type: application/json" -d "{\"level\": \"INFO\", \"message\": \"Load test request %REQUESTS% via Docker\", \"service\": \"load-tester-docker\"}"

REM Pausar menos para gerar mais carga
timeout /t 0 /nobreak >nul 2>&1

REM Continuar por 50 requisições (menos para não sobrecarregar)
if %REQUESTS% LSS 50 goto LOOP

echo.
echo ⏳ Aguardando processamento...
timeout /t 5 /nobreak >nul

echo.
echo 📈 Estado dos recursos após carga:
docker run --rm --network %NETWORK% curlimages/curl:latest -s "%BASE_URL%/metrics" | findstr "teuthis_cpu_usage\|teuthis_memory_usage\|teuthis_disk_usage"

echo.
echo 🧪 Testando request quando recursos podem estar altos...
echo Resposta esperada se algum recurso ^>= threshold:
echo "HTTP 429 Too Many Requests"
echo "Recursos limitados: [CPU/RAM/Disco específico] ^>= threshold%%"
echo.

docker run --rm --network %NETWORK% curlimages/curl:latest -v -X POST "%BASE_URL%/publish/test-topic" -H "Content-Type: application/json" -d "{\"test\": \"checking resource limits via Docker\"}"

echo.
echo ✅ Carga gerada via Docker! Agora verifique:
echo.
echo 1️⃣ Prometheus Alerts: http://localhost:9090/alerts
echo    - Procure por alertas "TeuthisResourcesOverThreshold"
echo.
echo 2️⃣ Grafana Dashboard: http://localhost:3000
echo    - Vá para "Teuthis - Kafka Producer Monitoring"
echo    - Observe os painéis de recursos
echo.
echo 3️⃣ Logs do Teuthis:
echo    docker-compose logs --tail=20 teuthis-app ^| findstr "Resource usage"
echo.
echo 4️⃣ Threshold atual:
docker run --rm --network %NETWORK% curlimages/curl:latest -s "%BASE_URL%/metrics" | findstr "teuthis_resource_threshold"

echo.
echo 🐳 Usando Docker para testes:
echo    ✅ Testa na mesma rede dos containers
echo    ✅ Não depende de ferramentas locais
echo    ✅ Reproduz ambiente real

pause