# Queries do Prometheus para Monitoramento de Recursos do Teuthis

## ⚙️ Configuração Dinâmica

O threshold é configurável via:
- **application.properties**: `resources.threshold=0.85`
- **Variável de ambiente**: `RESOURCES_THRESHOLD=0.85`
- **Docker compose**: `RESOURCES_THRESHOLD: 0.85`

## 📊 Métricas Básicas de Recursos

### CPU Usage (%)
teuthis_cpu_usage * 100

### Memory Usage (%)
teuthis_memory_usage * 100

### Disk Usage (%)
teuthis_disk_usage * 100

## 🚨 Alertas e Thresholds Dinâmicos

### Recursos acima do threshold configurado - ESPECÍFICOS
(teuthis_cpu_usage > scalar(teuthis_resource_threshold)) * 1 + (teuthis_memory_usage > scalar(teuthis_resource_threshold)) * 2 + (teuthis_disk_usage > scalar(teuthis_resource_threshold)) * 4

### CPU especificamente acima do threshold
(teuthis_cpu_usage > scalar(teuthis_resource_threshold)) * teuthis_cpu_usage * 100

### Memory especificamente acima do threshold
(teuthis_memory_usage > scalar(teuthis_resource_threshold)) * teuthis_memory_usage * 100

### Disk especificamente acima do threshold
(teuthis_disk_usage > scalar(teuthis_resource_threshold)) * teuthis_disk_usage * 100

### Qual recurso está mais alto
max_by_group(group() by (), teuthis_cpu_usage * 100, teuthis_memory_usage * 100, teuthis_disk_usage * 100)

### Contagem de recursos críticos com detalhes
count((teuthis_cpu_usage > scalar(teuthis_resource_threshold))) + count((teuthis_memory_usage > scalar(teuthis_resource_threshold))) + count((teuthis_disk_usage > scalar(teuthis_resource_threshold)))

### Status textual dos recursos (usando threshold dinâmico)
label_replace(
  label_replace(
    label_replace(
      (teuthis_cpu_usage > scalar(teuthis_resource_threshold)) * 1,
      "resource_status", "CPU_HIGH", "value", "1"
    ),
    "resource_status", "MEMORY_HIGH", "value", "2"
  ),
  "resource_status", "DISK_HIGH", "value", "4"
)

## 📈 Tendências e Médias

### CPU médio nos últimos 5 minutos
avg_over_time(teuthis_cpu_usage[5m]) * 100

### Memory médio nos últimos 5 minutos
avg_over_time(teuthis_memory_usage[5m]) * 100

### Disk médio nos últimos 5 minutos
avg_over_time(teuthis_disk_usage[5m]) * 100

### Máximo CPU nos últimos 10 minutos
max_over_time(teuthis_cpu_usage[10m]) * 100

### Máximo Memory nos últimos 10 minutos
max_over_time(teuthis_memory_usage[10m]) * 100

### Máximo Disk nos últimos 10 minutos
max_over_time(teuthis_disk_usage[10m]) * 100

## 🎯 Queries para Dashboards

### Para Gauge (valor atual)
teuthis_cpu_usage * 100
teuthis_memory_usage * 100  
teuthis_disk_usage * 100

### Para Time Series (histórico)
rate(teuthis_cpu_usage[1m]) * 100
rate(teuthis_memory_usage[1m]) * 100
rate(teuthis_disk_usage[1m]) * 100

### Para Status/Stat panels
clamp_max(teuthis_cpu_usage * 100, 100)
clamp_max(teuthis_memory_usage * 100, 100)
clamp_max(teuthis_disk_usage * 100, 100)

## 🔔 Queries para Alertas no Grafana

### CPU crítico (>threshold por mais de 2 minutos)
avg_over_time(teuthis_cpu_usage[2m]) > scalar(teuthis_resource_threshold)

### Qualquer recurso crítico
max(teuthis_cpu_usage, teuthis_memory_usage, teuthis_disk_usage) > scalar(teuthis_resource_threshold)

### Recursos múltiplos críticos
count((teuthis_cpu_usage > scalar(teuthis_resource_threshold)) + (teuthis_memory_usage > scalar(teuthis_resource_threshold)) + (teuthis_disk_usage > scalar(teuthis_resource_threshold))) > 1

### Threshold atual em percentual
teuthis_resource_threshold * 100

## 📊 Uso no Grafana

1. **Gauge Panel**: Use queries básicas (teuthis_cpu_usage * 100)
2. **Time Series**: Use para ver tendências ao longo do tempo
3. **Stat Panel**: Para valores atuais com thresholds
4. **Alert Rules**: Configure com queries de tendência (avg_over_time)

## 🎨 Configuração de Thresholds no Grafana (Dinâmica)

```json
"thresholds": {
  "steps": [
    {"color": "green", "value": null},
    {"color": "yellow", "value": 70},
    {"color": "orange", "value": 80}, 
    {"color": "red", "value": "{{ teuthis_resource_threshold * 100 }}"}
  ]
}
```

### Para thresholds fixos (alternativa):
```json
"thresholds": {
  "steps": [
    {"color": "green", "value": null},
    {"color": "yellow", "value": "{{ (teuthis_resource_threshold * 100) - 15 }}"},
    {"color": "orange", "value": "{{ (teuthis_resource_threshold * 100) - 5 }}"}, 
    {"color": "red", "value": "{{ teuthis_resource_threshold * 100 }}"}
  ]
}
```