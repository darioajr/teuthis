@echo off
REM Script para configurar threshold de recursos do Teuthis
echo 🎛️ Configurador de Threshold de Recursos Teuthis

echo.
echo 📊 Threshold atual definido:
findstr "resources.threshold" src\main\resources\application.properties

echo.
echo 🎯 Opções de threshold disponíveis:
echo   1. 75%% (0.75) - Muito restritivo
echo   2. 80%% (0.80) - Restritivo  
echo   3. 85%% (0.85) - Padrão atual
echo   4. 90%% (0.90) - Permissivo
echo   5. 95%% (0.95) - Muito permissivo
echo   6. Customizado

echo.
set /p choice="Escolha uma opção (1-6): "

if "%choice%"=="1" set NEW_THRESHOLD=0.75
if "%choice%"=="2" set NEW_THRESHOLD=0.80
if "%choice%"=="3" set NEW_THRESHOLD=0.85
if "%choice%"=="4" set NEW_THRESHOLD=0.90
if "%choice%"=="5" set NEW_THRESHOLD=0.95
if "%choice%"=="6" (
    set /p NEW_THRESHOLD="Digite o valor customizado (ex: 0.70): "
)

if "%NEW_THRESHOLD%"=="" (
    echo ❌ Opção inválida. Saindo...
    pause
    exit /b 1
)

echo.
echo 🔧 Configurando threshold para %NEW_THRESHOLD%...

REM Backup do arquivo original
copy "src\main\resources\application.properties" "src\main\resources\application.properties.backup" >nul

REM Substituir a linha no arquivo
powershell -Command "(Get-Content 'src\main\resources\application.properties') -replace 'resources.threshold=.*', 'resources.threshold=${RESOURCES_THRESHOLD:%NEW_THRESHOLD%}' | Set-Content 'src\main\resources\application.properties'"

echo ✅ Threshold atualizado!

echo.
echo 📊 Nova configuração:
findstr "resources.threshold" src\main\resources\application.properties

echo.
echo 🔄 Para aplicar a mudança:
echo   1. Rebuild: .\mvnw clean package
echo   2. Restart: docker-compose restart teuthis-app
echo   3. Ou rebuild completo: docker-compose build teuthis-app

echo.
echo 📊 Após aplicar, verifique:
echo   - Métricas: curl http://localhost:8080/metrics ^| findstr "teuthis_resource_threshold"
echo   - Prometheus: http://localhost:9090 (query: teuthis_resource_threshold)
echo   - Grafana: Dashboards usarão automaticamente o novo threshold

echo.
echo 🧪 Para testar:
echo   scripts\test-resource-limits.bat

echo.
echo 💡 Dica: Para usar via variável de ambiente (sem rebuild):
echo   set RESOURCES_THRESHOLD=%NEW_THRESHOLD%
echo   docker-compose restart teuthis-app

pause