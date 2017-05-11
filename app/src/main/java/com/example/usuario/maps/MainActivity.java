package com.example.usuario.maps;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements OnMapReadyCallback {

    private MapFragment mapFragment;
    private Geocoder geo;
    private Polyline polyline;

    private List<LatLng>    listLatitudeLongitude = new ArrayList<>();
    private List<Address>   listDeEnderecos = new ArrayList<>();

    private LayoutInflater layoutInflater;
    private int distance;

    private GoogleMap map;

    private Button btnAddEnd;
    private Button btnConfEnd;

    private LinearLayout vbRecebeEndereco;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vbRecebeEndereco = (LinearLayout)findViewById(R.id.vb_recebe_endereco);

        btnAddEnd = (Button)findViewById(R.id.btn_add_end);
        btnConfEnd = (Button)findViewById(R.id.btn_conf_end);

        layoutInflater = (LayoutInflater) MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        inflate();

        btnAddEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                inflate();

            }
        });

        btnConfEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                for (int i = 0; i < vbRecebeEndereco.getChildCount(); i++) {

                    View view = vbRecebeEndereco.getChildAt(i);

                    EditText edtRua = (EditText)view.findViewById(R.id.edt_rua);
                    String local = edtRua.getText().toString();

                    geo = new Geocoder(MainActivity.this);

                    try {
                        listDeEnderecos = geo.getFromLocationName(local, 1);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    for (int position = 0; position < listDeEnderecos.size(); position++) {

                        Address endereco = listDeEnderecos.get(position);

                        Double lat = endereco.getLatitude();
                        Double lng = endereco.getLongitude();

                        LatLng latLng = new LatLng(lat, lng);

                        listLatitudeLongitude.add(latLng);

                        for(i=0;i<(listLatitudeLongitude.size()-1);i++){

                            LatLng endOrigem  = listLatitudeLongitude.get(i);
                            LatLng endDestino = listLatitudeLongitude.get(i+1);

                            getRoute(endOrigem , endDestino);

                        }
                    }
                }
            }
        });
    }

    private void inflate(){

        View view = layoutInflater.inflate(R.layout.view_layout_endereco, null, false);
        vbRecebeEndereco.addView(view);
    }

    public void onMapReady(final GoogleMap googleMap) {

        map = googleMap;

    }

    private void desenharRota(){

        PolylineOptions po;

        if(polyline == null){

            po = new PolylineOptions();

            for (int i = 0, tam = listLatitudeLongitude.size(); i <tam ; i++){
                po.add(listLatitudeLongitude.get(i));
            }
            po.color(Color.BLACK);
            polyline = map.addPolyline(po);
        }
        else {

            polyline.setPoints(listLatitudeLongitude);

        }
    }

    public void getRoute(final LatLng origin, final LatLng destination){
        new Thread(){
            public void run(){
                String url= "http://maps.googleapis.com/maps/api/directions/json?origin="
                        + origin.latitude+","+origin.longitude+"&destination="
                        + destination.latitude+","+destination.longitude+"&sensor=false";

                HttpResponse response;
                HttpGet request;
                AndroidHttpClient client = AndroidHttpClient.newInstance("route");

                request = new HttpGet(url);
                try {
                    response = client.execute(request);
                    final String answer = EntityUtils.toString(response.getEntity());

                    runOnUiThread(new Runnable(){
                        public void run(){
                            try {

                                listLatitudeLongitude = buildJSONRoute(answer);
                                desenharRota();
                            }
                            catch(JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
                catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public List<LatLng> buildJSONRoute(String json) throws JSONException {

        JSONObject result = new JSONObject(json);
        JSONArray routes = result.getJSONArray("routes");

        distance = routes.getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONObject("distance").getInt("value");

        JSONArray steps = routes.getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONArray("steps");
        List<LatLng> lines = new ArrayList<LatLng>();

        for(int i=0; i < steps.length(); i++) {

            String polyline = steps.getJSONObject(i).getJSONObject("polyline").getString("points");

            for(LatLng p : decodePolyline(polyline)) {
                lines.add(p);

            }
        }

        return(lines);
    }
    private List<LatLng> decodePolyline(String encoded) {

        List<LatLng> listPoints = new ArrayList<LatLng>();

        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)), (((double) lng / 1E5)));
            listPoints.add(p);
        }
        return listPoints;
    }
}


