# Script de Verificación Rápida de RabbitMQ - Evaluación Parcial 3
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   VIDASALUD - VERIFICACION DE MENSAJERIA RABBITMQ       " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Probar Creación de Cola Dinámica en ms-vidasalud-admin (8085)
Write-Host "`n[1/4] Probando ms-vidasalud-admin: Creacion de Cola Dinamica..." -ForegroundColor White
$createQueueBody = @{
    name = "q.cmd.prueba.eval3"
    durable = $true
    autoDelete = $false
} | ConvertTo-Json

try {
    $resAdmin = Invoke-RestMethod -Uri "http://localhost:8085/api/admin/queues" -Method Post -ContentType "application/json" -Body $createQueueBody
    Write-Host "  -> Exito: $($resAdmin.message)" -ForegroundColor Green
} catch {
    Write-Host "  -> Error contactando ms-admin en 8085: $($_.Exception.Message)" -ForegroundColor Red
}

# 2. Probar Publicación de Evento Exitoso (ACK) en ms-vidasalud-appointments (8081)
Write-Host "`n[2/4] Probando Publicacion con ACK Manual (Flujo Exitoso)..." -ForegroundColor White
$urlAck = 'http://localhost:8081/api/appointments/test-publish?flow=email&message=ConfirmacionCitaExito'
try {
    $resAck = Invoke-RestMethod -Uri $urlAck -Method Post
    Write-Host "  -> Exito: $($resAck.mensaje)" -ForegroundColor Green
    Write-Host "  -> Evento ID: $($resAck.evento.id) | RoutingKey: $($resAck.flujo)" -ForegroundColor Gray
} catch {
    Write-Host "  -> Error publicando evento: $($_.Exception.Message)" -ForegroundColor Red
}

# 3. Probar Publicación con Error -> NACK hacia Dead Letter Queue (DLQ)
Write-Host "`n[3/4] Probando Redireccion a DLQ via basicNack (Flujo de Fallo)..." -ForegroundColor White
$urlDlq = 'http://localhost:8081/api/appointments/test-publish?flow=email&message=ERROR_PRUEBA_DLQ'
try {
    $resDlq = Invoke-RestMethod -Uri $urlDlq -Method Post
    Write-Host "  -> Exito: $($resDlq.mensaje)" -ForegroundColor Green
    Write-Host "  -> Mensaje enviado con 'ERROR' para activar NACK sin requeue -> Enrutado a q.cmd.email.dlq" -ForegroundColor Yellow
} catch {
    Write-Host "  -> Error en prueba de DLQ: $($_.Exception.Message)" -ForegroundColor Red
}

# 4. Probar Eliminación de Cola Dinámica
Write-Host "`n[4/4] Probando Eliminacion de Cola Dinamica..." -ForegroundColor White
try {
    $resDel = Invoke-RestMethod -Uri "http://localhost:8085/api/admin/queues/q.cmd.prueba.eval3" -Method Delete
    Write-Host "  -> Exito: $($resDel.message)" -ForegroundColor Green
} catch {
    Write-Host "  -> Error eliminando cola: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "Pruebas finalizadas. Revisa los logs de 'ms-vidasalud-notify'" -ForegroundColor Cyan
Write-Host "y la consola web de RabbitMQ en: http://localhost:15672 (guest/guest)" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
