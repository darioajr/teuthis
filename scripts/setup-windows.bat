@echo off
REM Script de setup para Windows - Baixa curl e configura ambiente
echo 🚀 Configurando ambiente Teuthis para Windows...

REM Verificar se curl já existe
where curl >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ curl já está instalado no sistema
    curl --version
    goto :SKIP_DOWNLOAD
)

echo 📥 curl não encontrado. Baixando curl para pasta scripts...

REM Criar pasta scripts se não existir
if not exist "scripts" mkdir scripts

REM Baixar curl para Windows
echo Baixando curl...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://curl.se/windows/dl-8.4.0_3/curl-8.4.0_3-win64-mingw.zip' -OutFile 'scripts\curl.zip'}"

if not exist "scripts\curl.zip" (
    echo ❌ Erro ao baixar curl. Tentando método alternativo...
    echo.
    echo 📝 Instalação manual necessária:
    echo 1. Vá para: https://curl.se/windows/
    echo 2. Baixe a versão Windows 64-bit
    echo 3. Extraia o curl.exe para a pasta scripts\
    echo.
    pause
    goto :END
)

echo 📂 Extraindo curl...
powershell -Command "Expand-Archive -Path 'scripts\curl.zip' -DestinationPath 'scripts\temp' -Force"

REM Mover curl.exe para pasta scripts
if exist "scripts\temp\curl-8.4.0_3-win64-mingw\bin\curl.exe" (
    move "scripts\temp\curl-8.4.0_3-win64-mingw\bin\curl.exe" "scripts\"
    echo ✅ curl instalado em scripts\curl.exe
) else (
    echo ❌ Erro na extração. Verifique o arquivo manualmente.
)

REM Limpar arquivos temporários
if exist "scripts\curl.zip" del "scripts\curl.zip"
if exist "scripts\temp" rmdir /s /q "scripts\temp"

:SKIP_DOWNLOAD

REM Testar curl
echo.
echo 🧪 Testando curl...
if exist "scripts\curl.exe" (
    echo Usando curl local: scripts\curl.exe
    scripts\curl.exe --version
    set CURL_CMD=scripts\curl.exe
) else (
    echo Usando curl do sistema
    curl --version
    set CURL_CMD=curl
)

REM Criar script de teste usando curl local
echo.
echo 📝 Criando scripts de teste com curl local...

REM Atualizar test-teuthis.bat para usar curl local
(
echo @echo off
echo REM Script de teste para Teuthis ^(Windows^) - Usa curl local
echo echo 🧪 Testando Teuthis Server...
echo.
echo set BASE_URL=http://localhost:8080
echo set CURL_CMD=%CURL_CMD%
echo.
echo echo 1️⃣ Testando Health Check...
echo %%CURL_CMD%% -X GET "%%BASE_URL%%/health"
echo echo.
echo.
echo echo 2️⃣ Testando Metrics...
echo %%CURL_CMD%% -X GET "%%BASE_URL%%/metrics" ^| findstr /C:"teuthis_"
echo echo.
echo.
echo echo 3️⃣ Testando Publish - Mensagem JSON...
echo %%CURL_CMD%% -X POST "%%BASE_URL%%/publish/test-topic" ^^
echo   -H "Content-Type: application/json" ^^
echo   -d "{\"mensagem\": \"Mensagem teste\", \"timestamp\": %%date:~-4,4%%%%date:~-10,2%%%%date:~-7,2%%, \"usuario\": \"teste\"}"
echo echo.
echo.
echo echo 4️⃣ Testando Publish - Evento...
echo %%CURL_CMD%% -X POST "%%BASE_URL%%/publish/events" ^^
echo   -H "Content-Type: application/json" ^^
echo   -d "{\"evento\": \"user_login\", \"usuario_id\": 123, \"timestamp\": \"%%time%%\"}"
echo echo.
echo.
echo echo 5️⃣ Testando Publish - Log...
echo %%CURL_CMD%% -X POST "%%BASE_URL%%/publish/logs" ^^
echo   -H "Content-Type: application/json" ^^
echo   -d "{\"level\": \"INFO\", \"message\": \"Teste de log\", \"service\": \"teuthis-test\"}"
echo echo.
echo.
echo echo 6️⃣ Testando Publish - Texto Simples...
echo %%CURL_CMD%% -X POST "%%BASE_URL%%/publish/test-topic" ^^
echo   -H "Content-Type: text/plain" ^^
echo   -d "Mensagem de texto simples - %%date%% %%time%%"
echo echo.
echo.
echo echo ✅ Testes concluídos!
echo echo.
echo echo 📊 Para verificar métricas no Prometheus:
echo echo    http://localhost:9090
echo echo.
echo echo 📈 Para ver dashboards no Grafana:
echo echo    http://localhost:3000 ^(admin/teuthis123^)
echo echo.
echo echo 🎯 Para ver tópicos no Kafka UI:
echo echo    http://localhost:8090
echo.
echo pause
) > scripts\test-teuthis.bat

REM Atualizar load-test.bat para usar curl local
(
echo @echo off
echo REM Script para gerar carga e testar alertas de recursos
echo echo 🔥 Gerando carga para testar alertas de recursos...
echo.
echo set BASE_URL=http://localhost:8080
echo set CURL_CMD=%CURL_CMD%
echo set /a REQUESTS=0
echo.
echo echo.
echo echo 📊 Monitoramento:
echo echo   Prometheus: http://localhost:9090/alerts
echo echo   Grafana: http://localhost:3000
echo echo.
echo echo 🎯 Enviando muitas requisições para aumentar uso de recursos...
echo echo.
echo.
echo :LOOP
echo set /a REQUESTS+=1
echo echo Requisição %%REQUESTS%%...
echo.
echo REM Enviar várias requisições simultâneas
echo start /b %%CURL_CMD%% -s -X POST "%%BASE_URL%%/publish/test-topic" -H "Content-Type: application/json" -d "{\"load_test\": true, \"request\": %%REQUESTS%%, \"timestamp\": \"%%date%% %%time%%\", \"data\": \"Este é um payload maior para aumentar o processamento e uso de recursos do servidor Teuthis durante o teste de carga\"}"
echo.
echo start /b %%CURL_CMD%% -s -X POST "%%BASE_URL%%/publish/events" -H "Content-Type: application/json" -d "{\"event\": \"load_test\", \"request_id\": %%REQUESTS%%, \"cpu_intensive\": true}"
echo.
echo start /b %%CURL_CMD%% -s -X POST "%%BASE_URL%%/publish/logs" -H "Content-Type: application/json" -d "{\"level\": \"INFO\", \"message\": \"Load test request %%REQUESTS%%\", \"service\": \"load-tester\"}"
echo.
echo REM Pequena pausa para não sobrecarregar muito rápido
echo timeout /t 1 /nobreak ^>nul
echo.
echo REM Continuar por 100 requisições
echo if %%REQUESTS%% LSS 100 goto LOOP
echo.
echo echo.
echo echo ✅ Carga gerada! Agora verifique:
echo echo.
echo echo 1️⃣ Prometheus Alerts: http://localhost:9090/alerts
echo echo    - Procure por alertas "TeuthisResourcesOverThreshold"
echo echo.
echo echo 2️⃣ Grafana Dashboard: http://localhost:3000
echo echo    - Vá para "Teuthis - Kafka Producer Monitoring"
echo echo    - Observe os painéis de recursos
echo echo.
echo echo 3️⃣ Logs do Teuthis:
echo echo    docker-compose logs teuthis-app ^| findstr "Resource usage"
echo echo.
echo echo 4️⃣ Métricas atuais:
echo %%CURL_CMD%% -s http://localhost:8080/metrics ^| findstr "teuthis_cpu_usage\^|teuthis_memory_usage\^|teuthis_disk_usage"
echo.
echo pause
) > scripts\load-test.bat

echo.
echo ✅ Setup concluído!
echo.
echo 📁 Scripts criados na pasta scripts\:
echo   - test-teuthis.bat  (testes básicos)
echo   - load-test.bat     (teste de carga)
echo.
echo 🚀 Como usar:
echo   cd scripts
echo   test-teuthis.bat
echo   load-test.bat
echo.

:END
pause