# 🚀 Teuthis - Quick Start

## 📋 Setup Rápido (Windows)

### 🐳 Via Docker (RECOMENDADO)
```cmd
# 1. Verificar ambiente
scripts\check-environment.bat

# 2. Iniciar serviços
docker-compose up -d

# 3. Menu interativo
teuthis-docker-menu.bat
```

### 📦 Via Setup Local
```cmd
# 1. Setup automático (baixa curl)
setup-windows.bat

# 2. Verificar ambiente
scripts\check-environment.bat

# 3. Iniciar serviços
docker-compose up -d

# 4. Testar
scripts\test-teuthis.bat
```

## 🎯 URLs Principais

- **API**: http://localhost:8080
- **Grafana**: http://localhost:3000 (admin/teuthis123)
- **Prometheus**: http://localhost:9090
- **Kafka UI**: http://localhost:8090

## 🧪 Comandos de Teste

```cmd
# Teste básico
scripts\test-teuthis.bat

# Teste de carga (alertas)
scripts\load-test.bat

# Manual
curl -X POST http://localhost:8080/publish/test-topic \
  -H "Content-Type: application/json" \
  -d '{"mensagem": "teste"}'
```

## 📊 Monitoramento

- **CPU/Memory/Disk** > 85% = requests rejeitados
- **Alertas**: Prometheus → Alerts
- **Dashboards**: Grafana → Teuthis Dashboard
- **Logs**: `docker-compose logs teuthis-app`