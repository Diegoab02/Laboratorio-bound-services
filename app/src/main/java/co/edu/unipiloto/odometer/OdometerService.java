package co.edu.unipiloto.odometer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Bound Service que registra la distancia recorrida por el estudiante
 * usando Android Location Services. Expone parametros configurables
 * (precision en metros y tiempo de actualizacion) y persiste la
 * distancia acumulada para el reporte consolidado de tutorias.
 */
public class OdometerService extends Service {

    private static final String TAG = "OdometerService";
    public static final String PERMISSION_STRING =
            android.Manifest.permission.ACCESS_FINE_LOCATION;

    // Constantes de persistencia
    private static final String PREFS_NAME = "OdometerPrefs";
    private static final String KEY_TOTAL_DISTANCE = "total_distance_meters";
    private static final String KEY_SESSION_LOG = "session_log";

    // Valores por defecto para los parametros configurables
    private static final float DEFAULT_PRECISION_METERS = 1.0f;
    private static final long DEFAULT_UPDATE_TIME_MS = 1000L;

    private final IBinder binder = new OdometerBinder();
    private LocationListener listener;
    private LocationManager locManager;

    // Distancia de la sesion actual (en metros)
    private static double distanceInMeters = 0.0;
    private static Location lastLocation = null;
    private static Location currentLocation = null;

    // Parametros configurables desde la UI
    private float precisionMeters = DEFAULT_PRECISION_METERS;
    private long updateTimeMs = DEFAULT_UPDATE_TIME_MS;

    /**
     * Binder que permite a MainActivity obtener una referencia al servicio.
     */
    public class OdometerBinder extends Binder {
        public OdometerService getOdometer() {
            return OdometerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Cargar distancia acumulada persistida previamente
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        distanceInMeters = (double) prefs.getFloat(KEY_TOTAL_DISTANCE, 0.0f);

        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                currentLocation = location;
                if (lastLocation == null) {
                    lastLocation = location;
                    return;
                }
                float delta = location.distanceTo(lastLocation);
                distanceInMeters += delta;
                lastLocation = location;

                // Persistir en cada actualizacion para no perder datos
                persistDistance();

                Log.d(TAG, "Delta: " + delta + " m | Total: " + distanceInMeters + " m");
            }

            @Override public void onProviderDisabled(String provider) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        };

        locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        startLocationUpdates();
    }

    /**
     * Solicita actualizaciones al proveedor de ubicacion con los
     * parametros actuales de tiempo y precision.
     */
    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, PERMISSION_STRING)
                == PackageManager.PERMISSION_GRANTED) {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            String provider = locManager.getBestProvider(criteria, true);
            if (provider != null) {
                locManager.requestLocationUpdates(
                        provider,
                        updateTimeMs,
                        precisionMeters,
                        listener);
                Log.d(TAG, "Updates: provider=" + provider +
                        " time=" + updateTimeMs + "ms precision=" + precisionMeters + "m");
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locManager != null && listener != null) {
            if (ContextCompat.checkSelfPermission(this, PERMISSION_STRING)
                    == PackageManager.PERMISSION_GRANTED) {
                locManager.removeUpdates(listener);
            }
            // Guardar sesion al destruir el servicio
            persistDistance();
            logSession();
            locManager = null;
            listener = null;
        }
    }

    // ============================================================
    //  API publica expuesta a MainActivity mediante el Binder
    // ============================================================

    /** Distancia recorrida en metros (odometro). */
    public double getDistance() {
        return distanceInMeters;
    }

    /** Ubicacion actual del dispositivo (o null si aun no hay fix). */
    public Location getCurrentLocation() {
        return currentLocation;
    }

    /** Actualiza la precision (metros) y re-registra el listener. */
    public void setPrecision(float meters) {
        this.precisionMeters = meters;
        reRegisterListener();
    }

    /** Actualiza el intervalo de refresco (segundos) y re-registra. */
    public void setUpdateTimeSeconds(int seconds) {
        this.updateTimeMs = seconds * 1000L;
        reRegisterListener();
    }

    public float getPrecision() { return precisionMeters; }
    public long  getUpdateTimeMs() { return updateTimeMs; }

    /** Reinicia el contador de la sesion actual. */
    public void resetDistance() {
        distanceInMeters = 0.0;
        lastLocation = null;
        persistDistance();
    }

    private void reRegisterListener() {
        if (locManager != null && listener != null
                && ContextCompat.checkSelfPermission(this, PERMISSION_STRING)
                == PackageManager.PERMISSION_GRANTED) {
            locManager.removeUpdates(listener);
            startLocationUpdates();
        }
    }

    // ============================================================
    //  Persistencia para el reporte consolidado
    // ============================================================

    private void persistDistance() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putFloat(KEY_TOTAL_DISTANCE, (float) distanceInMeters).apply();
    }

    /** Agrega una entrada al log de sesiones (fecha + metros). */
    private void logSession() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String current = prefs.getString(KEY_SESSION_LOG, "");
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date());
        String entry = ts + " | " + String.format(Locale.getDefault(),
                "%.2f m%n", distanceInMeters);
        prefs.edit().putString(KEY_SESSION_LOG, current + entry).apply();
    }
}
