package my.edu.utar.taskmate;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapPickerActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap map;
    private SearchView searchView;
    private Button btnConfirm;
    private Marker currentMarker;
    private LatLng selectedLatLng;
    private String selectedAddress; // store human-readable address

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_picker);

        // Check Google Play Services availability
        int gmsStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (gmsStatus != ConnectionResult.SUCCESS) {
            GoogleApiAvailability.getInstance().getErrorDialog(this, gmsStatus, 0, dialog -> finish()).show();
            Toast.makeText(this, "Google Play services not available", Toast.LENGTH_LONG).show();
            return;
        }

        searchView = findViewById(R.id.searchLocation);
        btnConfirm = findViewById(R.id.btnConfirm);

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // Search location by address
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchLocation(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        // Confirm button → send address + lat/lng back
        btnConfirm.setOnClickListener(v -> {
            if (selectedLatLng != null) {
                Intent result = new Intent();
                result.putExtra("lat", selectedLatLng.latitude);
                result.putExtra("lng", selectedLatLng.longitude);
                result.putExtra("address", selectedAddress != null ? selectedAddress : "Unknown location");
                setResult(RESULT_OK, result);
                finish();
            } else {
                Toast.makeText(MapPickerActivity.this, "Please pick a location first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        LatLng defaultLatLng = new LatLng(3.139, 101.6869); // Kuala Lumpur
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLatLng, 12));

        map.setOnMapClickListener(latLng -> {
            if (currentMarker != null) currentMarker.remove();
            currentMarker = map.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
            selectedLatLng = latLng;
            selectedAddress = getAddressFromLatLng(latLng);
        });
    }

    private void searchLocation(String address) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            LatLng resultLatLng = null;
            try {
                String urlStr = "https://nominatim.openstreetmap.org/search?format=json&q=" + Uri.encode(address);
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "TaskmateApp");
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);

                JSONArray arr = new JSONArray(sb.toString());
                if (arr.length() > 0) {
                    JSONObject obj = arr.getJSONObject(0);
                    double lat = obj.getDouble("lat");
                    double lon = obj.getDouble("lon");
                    resultLatLng = new LatLng(lat, lon);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            LatLng finalResultLatLng = resultLatLng;
            handler.post(() -> {
                if (finalResultLatLng != null && map != null) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(finalResultLatLng, 15));
                    if (currentMarker != null) currentMarker.remove();
                    currentMarker = map.addMarker(new MarkerOptions().position(finalResultLatLng).title(address));
                    selectedLatLng = finalResultLatLng;
                    selectedAddress = address;
                } else {
                    Toast.makeText(MapPickerActivity.this, "Location not found", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // Convert LatLng to address using Geocoder
    private String getAddressFromLatLng(LatLng latLng) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                return addresses.get(0).getAddressLine(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}