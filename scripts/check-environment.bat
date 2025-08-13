@echo off
REM Script de verificação rápida do ambiente Teuthis
echo 🔍 Verificando ambiente Teuthis...

echo.
echo 1️⃣ Verificando Docker...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker não encontrado. Instale o Docker Desktop primeiro.
    echo    https://www.docker.com/products/docker-desktop
    goto :ERROR
) else (
    echo ✅ Docker instalado
    docker --version
)

echo.
echo 2️⃣ Verificando Docker Compose...
docker-compose --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker Compose não encontrado
    goto :ERROR
) else (
    echo ✅ Docker Compose instalado
    docker-compose --version
)

echo.
echo 3️⃣ Verificando curl...
echo 🐳 Usando container Docker para curl (não precisa instalar)
docker run --rm curlimages/curl:latest --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ⚠️ Não foi possível baixar imagem curl via Docker
    echo Verificando curl local...
    if exist "curl.exe" (
        echo ✅ curl local encontrado: curl.exe
        curl.exe --version
    ) else (
        curl --version >nul 2>&1
        if %errorlevel% neq 0 (
            echo ⚠️ curl não encontrado localmente
            goto :WARNING
        ) else (
            echo ✅ curl do sistema encontrado
            curl --version
        )
    )
) else (
    echo ✅ Docker curl disponível (curlimages/curl:latest)
    docker run --rm curlimages/curl:latest --version
)

echo.
echo 4️⃣ Verificando serviços Teuthis...
echo Verificando se os containers estão rodando...

docker-compose ps 2>nul | findstr "teuthis" >nul
if %errorlevel% neq 0 (
    echo ⚠️ Containers Teuthis não estão rodando
    echo 💡 Execute: docker-compose up -d
) else (
    echo ✅ Containers Teuthis rodando
    docker-compose ps
)

echo.
echo 5️⃣ Testando conectividade...

echo Testando Teuthis Health via Docker...
docker run --rm --network teuthis-network curlimages/curl:latest -s -f http://teuthis-application:8080/health >nul 2>&1
if %errorlevel% neq 0 (
    echo ⚠️ Teste via Docker falhou, tentando localhost...
    REM Fallback para curl local se disponível
    if exist "curl.exe" (
        set CURL_CMD=curl.exe
    ) else (
        set CURL_CMD=curl
    )
    %CURL_CMD% -s -f http://localhost:8080/health >nul 2>&1
    if %errorlevel% neq 0 (
        echo ❌ Teuthis não responde em http://localhost:8080/health
        echo 💡 Verifique se o container está rodando: docker-compose logs teuthis-app
    ) else (
        echo ✅ Teuthis Health OK (via localhost)
    )
) else (
    echo ✅ Teuthis Health OK (via Docker network)
)

echo Testando Prometheus...
docker run --rm --network teuthis-network curlimages/curl:latest -s -f http://teuthis-prometheus:9090/-/healthy >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Prometheus não responde via Docker network
) else (
    echo ✅ Prometheus OK (via Docker network)
)

echo Testando Grafana...
docker run --rm --network teuthis-network curlimages/curl:latest -s -f http://teuthis-grafana:3000/api/health >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Grafana não responde via Docker network
) else (
    echo ✅ Grafana OK (via Docker network)
)

echo Testando Kafka UI...
docker run --rm --network teuthis-network curlimages/curl:latest -s -f http://teuthis-kafka-ui:8080 >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Kafka UI não responde via Docker network
) else (
    echo ✅ Kafka UI OK (via Docker network)
)

echo.
echo 6️⃣ Resumo dos URLs:
echo   🌐 Teuthis API:    http://localhost:8080
echo   🏥 Health Check:   http://localhost:8080/health  
echo   📊 Métricas:       http://localhost:8080/metrics
echo   📈 Prometheus:     http://localhost:9090
echo   📊 Grafana:        http://localhost:3000 (admin/teuthis123)
echo   🎯 Kafka UI:       http://localhost:8090

echo.
echo 7️⃣ Scripts disponíveis:
if exist "scripts\test-teuthis-docker.bat" (
    echo   ✅ scripts\test-teuthis-docker.bat  (testes via Docker - RECOMENDADO)
) else (
    echo   ❌ scripts\test-teuthis-docker.bat  (não encontrado)
)

if exist "scripts\test-teuthis.bat" (
    echo   ✅ scripts\test-teuthis.bat         (testes locais - requer curl)
) else (
    echo   ❌ scripts\test-teuthis.bat         (não encontrado)
)

if exist "scripts\load-test-docker.bat" (
    echo   ✅ scripts\load-test-docker.bat     (teste de carga via Docker - RECOMENDADO)
) else (
    echo   ❌ scripts\load-test-docker.bat     (não encontrado)
)

if exist "scripts\debug-docker.bat" (
    echo   ✅ scripts\debug-docker.bat         (debug via Docker)
) else (
    echo   ❌ scripts\debug-docker.bat         (não encontrado)
)

echo.
echo 8️⃣ Próximos passos:
echo   1. Se containers não estão rodando: docker-compose up -d
echo   2. Para testar (RECOMENDADO): scripts\test-teuthis-docker.bat
echo   3. Para carga (RECOMENDADO): scripts\load-test-docker.bat
echo   4. Para debug: scripts\debug-docker.bat
echo.
echo 🐳 Scripts Docker são RECOMENDADOS pois:
echo   ✅ Não precisam de curl instalado
echo   ✅ Testam na mesma rede dos containers
echo   ✅ Reproduzem ambiente real

goto :SUCCESS

:WARNING
echo.
echo ⚠️ Alguns componentes precisam de atenção, mas pode funcionar.
goto :END

:ERROR
echo.
echo ❌ Erro crítico encontrado. Resolva antes de continuar.
goto :END

:SUCCESS
echo.
echo ✅ Ambiente Teuthis verificado com sucesso!

:END
echo.
pause