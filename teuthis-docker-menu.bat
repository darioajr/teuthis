@echo off
REM Menu principal para scripts Teuthis via Docker
echo 🐳 Teuthis - Scripts Docker Menu

echo.
echo 🎯 Menu de Scripts Docker (Recomendado):
echo   1. 🔍 Verificar ambiente
echo   2. 🧪 Testes básicos (via Docker)
echo   3. 🔥 Teste de carga (via Docker)  
echo   4. 🐛 Debug simples (via Docker)
echo   5. 📊 Ver métricas atuais
echo   6. 📈 Abrir dashboards
echo   7. 🔧 Configurar threshold
echo   8. 📋 Ver logs
echo   9. ❌ Sair

echo.
set /p choice="Escolha uma opção (1-9): "

if "%choice%"=="1" (
    echo.
    echo 🔍 Verificando ambiente...
    call scripts\check-environment.bat
)

if "%choice%"=="2" (
    echo.
    echo 🧪 Executando testes básicos via Docker...
    call scripts\test-teuthis-docker.bat
)

if "%choice%"=="3" (
    echo.
    echo 🔥 Executando teste de carga via Docker...
    call scripts\load-test-docker.bat
)

if "%choice%"=="4" (
    echo.
    echo 🐛 Executando debug via Docker...
    call scripts\debug-docker.bat
)

if "%choice%"=="5" (
    echo.
    echo 📊 Métricas atuais via Docker:
    docker run --rm --network teuthis-network curlimages/curl:latest -s http://teuthis-application:8080/metrics | findstr "teuthis_"
    echo.
    pause
)

if "%choice%"=="6" (
    echo.
    echo 📈 Abrindo dashboards no navegador...
    start http://localhost:3000
    start http://localhost:9090
    start http://localhost:8090
    echo ✅ URLs abertas:
    echo   📊 Grafana: http://localhost:3000 (admin/teuthis123)
    echo   📈 Prometheus: http://localhost:9090
    echo   🎯 Kafka UI: http://localhost:8090
)

if "%choice%"=="7" (
    echo.
    echo 🔧 Configurando threshold...
    call configure-threshold.bat
)

if "%choice%"=="8" (
    echo.
    echo 📋 Logs do Teuthis (últimas 20 linhas):
    docker-compose logs --tail=20 teuthis-app
    echo.
    pause
)

if "%choice%"=="9" (
    echo.
    echo 👋 Saindo...
    exit /b 0
)

if "%choice%"=="" (
    echo ❌ Opção inválida. Tente novamente.
    goto :EOF
)

REM Voltar ao menu se não for sair
if not "%choice%"=="9" (
    echo.
    echo 🔄 Voltando ao menu...
    timeout /t 2 /nobreak >nul
    cls
    goto :EOF
)

pause