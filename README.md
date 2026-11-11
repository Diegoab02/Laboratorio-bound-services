# Laboratorio Bound Services — Odometro de Tutorias

**Curso:** Computacion Convergente — Universidad Piloto de Colombia
**Estudiante:** Diego Alejandro Betancur Herrera

## Objetivo

Implementar un `bound service` enlazado a una actividad principal para registrar y mostrar la distancia recorrida (en metros) por los estudiantes que asisten a sesiones de tutoria presencial, usando los servicios de localizacion de Android.

## Estructura del proyecto

```
app/src/main/
├── AndroidManifest.xml
├── java/co/edu/unipiloto/odometer/
│   ├── MainActivity.java
│   └── OdometerService.java
└── res/
    ├── layout/activity_main.xml
    └── values/strings.xml
```

## Componentes implementados

1. **`OdometerService`** (bound service)
   - Extiende `Service` y expone `OdometerBinder` como IBinder.
   - Usa `LocationManager` + `LocationListener` para recibir actualizaciones del GPS.
   - Calcula distancia acumulada con `Location.distanceTo()`.
   - Persiste la distancia total y un log de sesiones en `SharedPreferences`.

2. **`MainActivity`**
   - Se enlaza al servicio en `onStart()` y se desconecta en `onStop()`.
   - Muestra distancia recorrida y ubicacion actual (lat/lon + precision) cada segundo.
   - Permite configurar desde la UI:
     - Precision del movimiento (metros)
     - Tiempo de actualizacion (segundos)
   - Solicita permiso `ACCESS_FINE_LOCATION` en tiempo de ejecucion.
   - Boton "Ver reporte" muestra el reporte consolidado persistido.

## Variables de configuracion capturadas en UI

| Variable | Tipo | Default | Metodo del servicio |
|---|---|---|---|
| Precision del movimiento (m) | float | 1.0 | `setPrecision(float)` |
| Tiempo de actualizacion (s) | int   | 1   | `setUpdateTimeSeconds(int)` |
| Ubicacion actual | Location | — | `getCurrentLocation()` |

Al pulsar "Aplicar configuracion" se re-registra el `LocationListener` con los nuevos valores.

## Persistencia para reporte consolidado

- `SharedPreferences` con nombre `OdometerPrefs`:
  - `total_distance_meters` (float): metros acumulados entre sesiones.
  - `session_log` (String): lineas con timestamp + metros por sesion.
- El reporte se puede consultar desde la UI con el boton "Ver reporte".

## Instrucciones de uso

1. Crear un proyecto nuevo en Android Studio con package `co.edu.unipiloto.odometer`.
2. Reemplazar los archivos generados por los de este repo (ver rutas en "Estructura").
3. Agregar la dependencia AppCompat en `build.gradle` (ya viene por defecto en proyectos nuevos).
4. Ejecutar en un dispositivo real (el emulador no genera lecturas de GPS reales sin configuracion extra).
5. Conceder el permiso de ubicacion cuando se solicite.

## Flujo de verificacion

1. Abrir la app → aceptar permiso de ubicacion.
2. Modificar precision a `5.0` y tiempo a `2` → pulsar "Aplicar configuracion".
3. Caminar unos metros → verificar que el odometro incrementa.
4. Cerrar y reabrir → el total acumulado se mantiene (persistencia).
5. Pulsar "Ver reporte" → aparece el historial de sesiones.
