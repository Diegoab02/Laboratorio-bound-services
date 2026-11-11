package co.edu.unipiloto.odometer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * MainActivity: se enlaza al OdometerService, muestra la distancia
 * recorrida y la ubicacion actual, y permite configurar la precision
 * y el intervalo de actualizacion desde la UI.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_LOCATION = 100;

    private OdometerService odometer;
    private boolean bound = false;

    private TextView distanceView;
    private TextView locationView;
    private EditText precisionInput;
    private EditText updateTimeInput;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            OdometerService.OdometerBinder b = (OdometerService.OdometerBinder) binder;
            odometer = b.getOdometer();
            bound = true;

            // Precargar los inputs con los valores actuales del servicio
            precisionInput.setText(String.valueOf(odometer.getPrecision()));
            updateTimeInput.setText(String.valueOf(odometer.getUpdateTimeMs() / 1000));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        distanceView    = findViewById(R.id.distance);
        locationView    = findViewById(R.id.location);
        precisionInput  = findViewById(R.id.precision);
        updateTimeInput = findViewById(R.id.updateTime);

        Button applyBtn  = findViewById(R.id.applyConfig);
        Button resetBtn  = findViewById(R.id.resetBtn);
        Button reportBtn = findViewById(R.id.reportBtn);

        applyBtn.setOnClickListener(v -> applyConfig());
        resetBtn.setOnClickListener(v -> {
            if (bound) odometer.resetDistance();
        });
        reportBtn.setOnClickListener(v -> showReport());

        // Solicitar permiso de ubicacion en tiempo de ejecucion
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_LOCATION);
        }

        displayDistance();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, OdometerService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound) {
            unbindService(connection);
            bound = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Reiniciar el servicio para que tome el permiso
                Intent i = new Intent(this, OdometerService.class);
                if (bound) unbindService(connection);
                bindService(i, connection, Context.BIND_AUTO_CREATE);
            } else {
                Toast.makeText(this,
                        "Sin permiso de ubicacion el odometro no funciona",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Toma los valores de la UI y los aplica al servicio enlazado.
     */
    private void applyConfig() {
        if (!bound) return;
        try {
            float precision = Float.parseFloat(precisionInput.getText().toString());
            int seconds     = Integer.parseInt(updateTimeInput.getText().toString());
            odometer.setPrecision(precision);
            odometer.setUpdateTimeSeconds(seconds);
            Toast.makeText(this, "Configuracion aplicada", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Valores invalidos", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Refresca la UI cada segundo con la distancia y la ubicacion.
     */
    private void displayDistance() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                double distance = 0.0;
                String locText  = "Ubicacion: esperando GPS...";
                if (bound && odometer != null) {
                    distance = odometer.getDistance();
                    Location loc = odometer.getCurrentLocation();
                    if (loc != null) {
                        locText = String.format(Locale.getDefault(),
                                "Lat: %.5f  Lon: %.5f  (%.1f m)",
                                loc.getLatitude(), loc.getLongitude(), loc.getAccuracy());
                    }
                }
                distanceView.setText(String.format(Locale.getDefault(),
                        "%.2f m recorridos", distance));
                locationView.setText(locText);
                handler.postDelayed(this, 1000);
            }
        });
    }

    /**
     * Muestra el reporte consolidado de distancias recorridas en
     * las distintas sesiones de tutoria (persistidas en SharedPreferences).
     */
    private void showReport() {
        SharedPreferences prefs = getSharedPreferences("OdometerPrefs", MODE_PRIVATE);
        float total    = prefs.getFloat("total_distance_meters", 0.0f);
        String history = prefs.getString("session_log", "(sin sesiones registradas)");

        String report = "=== REPORTE CONSOLIDADO ===\n"
                + String.format(Locale.getDefault(),
                "Total acumulado: %.2f m\n\n", total)
                + "Historial de sesiones:\n" + history;

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reporte de tutorias")
                .setMessage(report)
                .setPositiveButton("Cerrar", null)
                .show();
    }
}
