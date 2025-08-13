# 🪟 Teuthis - Guia para Windows

## 🚀 Setup Inicial

### 1. Preparar Ambiente (Docker - RECOMENDADO)
```cmd
# 1. Verificar ambiente
scripts\check-environment.bat

# 2. Menu interativo com scripts Docker
teuthis-docker-menu.bat
```

### 2. Setup Tradicional (com curl local)
```cmd
# 1. Execute o script de setup (baixa curl automaticamente)
setup-windows.bat

# 2. Verifique se tudo está funcionando
scripts\check-environment.bat
```

### 3. Iniciar Teuthis
```cmd
# Iniciar todos os serviços
docker-compose up -d

# Verificar status
docker-compose ps
```

## 🧪 Executar Testes

### 🐳 Testes via Docker (RECOMENDADO)
```cmd
# Menu interativo
teuthis-docker-menu.bat

# Ou diretamente:
scripts\test-teuthis-docker.bat
scripts\load-test-docker.bat
scripts\debug-docker.bat
```

### 📊 Testes Tradicionais
```cmd
cd scripts
test-teuthis.bat
load-test.bat
```

## 📊 URLs de Acesso

- **🌐 Teuthis API**: http://localhost:8080
- **🏥 Health Check**: http://localhost:8080/health
- **📊 Métricas**: http://localhost:8080/metrics
- **📈 Prometheus**: http://localhost:9090
- **📊 Grafana**: http://localhost:3000 (admin/teuthis123)
- **🎯 Kafka UI**: http://localhost:8090

## 🔧 Troubleshooting

### curl não encontrado
```cmd
# Execute o setup que baixa curl automaticamente
setup-windows.bat
```

### Containers não iniciam
```cmd
# Ver logs
docker-compose logs

# Restart
docker-compose down
docker-compose up -d
```

### Teste manual (sem scripts)
```cmd
# Usando curl do sistema (se disponível)
curl -X GET http://localhost:8080/health

# Ou usando curl baixado
scripts\curl.exe -X GET http://localhost:8080/health
```

## 📁 Estrutura de Scripts

```
scripts/
├── curl.exe           # curl baixado automaticamente (se necessário)
├── test-teuthis.bat   # testes básicos dos endpoints
└── load-test.bat      # teste de carga para alertas
```

## 🎯 Monitoramento de Recursos

O sistema monitora automaticamente:
- **CPU** > 85% 
- **Memory** > 85%
- **Disk** > 85%

Quando qualquer recurso excede 85%, o Teuthis rejeita requests com:
```
HTTP 429 Too Many Requests
"Recursos limitados: CPU/RAM/Disco ≥ 85%"
```

### Ver alertas:
1. **Prometheus**: http://localhost:9090/alerts
2. **Grafana**: Dashboard "Teuthis - Kafka Producer Monitoring"
3. **Logs**: `docker-compose logs teuthis-app | findstr "Resource usage"`

## 🆘 Comandos Úteis

```cmd
# Verificar ambiente completo
check-environment.bat

# Ver logs do Teuthis
docker-compose logs teuthis-app

# Ver logs de todos os serviços
docker-compose logs

# Restart apenas Teuthis
docker-compose restart teuthis-app

# Parar tudo
docker-compose down

# Limpar volumes (reset completo)
docker-compose down -v
```

## 📈 Exemplo de Uso

```cmd
# 1. Setup inicial
setup-windows.bat

# 2. Verificar ambiente  
check-environment.bat

# 3. Iniciar serviços
docker-compose up -d

# 4. Executar testes
cd scripts
test-teuthis.bat

# 5. Monitorar no Grafana
# Abrir http://localhost:3000

# 6. Gerar carga para testar alertas
load-test.bat
```