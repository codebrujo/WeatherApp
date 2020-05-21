package ru.sogarkov.weatherapp;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import ru.sogarkov.weatherapp.data.WeatherRequest;

public class MainActivity extends AppCompatActivity {

    private static final String WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather?q=";
    private static final String WEATHER_OPTIONS = "metric";
    private static final String TAG = "WEATHER_MY";
    public static final boolean HANDLE_BY_RX = true;

    private EditText cityRequested;
    private EditText country;
    private EditText city;
    private EditText temperature;
    private EditText pressure;
    private EditText humidity;
    private EditText windSpeed;

    private Api mApi = Api.Instance.getApi();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }

    private void init() {
        cityRequested = findViewById(R.id.editTextViewRequest);
        country = findViewById(R.id.textCountry);
        city = findViewById(R.id.textCity);
        temperature = findViewById(R.id.textTemprature);
        pressure = findViewById(R.id.textPressure);
        humidity = findViewById(R.id.textHumidity);
        windSpeed = findViewById(R.id.textWindspeed);
        Button refresh = findViewById(R.id.refresh);
        refresh.setOnClickListener(clickListener);
    }

    private View.OnClickListener clickListener = new View.OnClickListener() {

        public void runRxHandler(View v, String city) {
            Log.i("Main.Rx.Handler", "Requesting using RxJava");
            mApi.getWeatherDataByCity(city, BuildConfig.WEATHER_API_KEY, WEATHER_OPTIONS)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(t -> onSuccess(t), e -> showError(e));
        }

        private void onSuccess(WeatherRequest t) {
            MainActivity.this.displayWeather(t);
        }

        private void showError(Throwable e) {

            if (e.getMessage().equals("HTTP 404 Not Found")){
                Toast.makeText(MainActivity.this, "Requested city is not found", Toast.LENGTH_LONG).show();
            }else{
                Log.e("ServiceCall", e.getMessage(), e);
                Log.e("ServiceCall", "Request failed", e);
            }
        }

        public void runURLConnectionHandler(View v, final Handler handler, final URL uri) {
            new Thread(new Runnable() {
                @RequiresApi(api = Build.VERSION_CODES.N)
                @Override
                public void run() {
                    HttpsURLConnection urlConnection = null;
                    try {
                        urlConnection = (HttpsURLConnection) uri.openConnection();
                        urlConnection.setRequestMethod("GET");
                        urlConnection.setReadTimeout(10000);
                        BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                        Log.i("Main.URL.Handler", "Request processed");
                        String result = getLines(in);
                        Gson gson = new Gson();
                        final WeatherRequest weatherRequest = gson.fromJson(result, WeatherRequest.class);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                displayWeather(weatherRequest);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Fail Connection", e);
                        e.printStackTrace();
                    }
                }

                @RequiresApi(api = Build.VERSION_CODES.N)
                private String getLines(BufferedReader in) {
                    Log.i("Main.thread.getLines", "Start building lines");
                    String lines = "";
                    String line = "";
                    try {
                        BufferedReader buffer = new BufferedReader(in);
                        while ((line = buffer.readLine()) != null) {
                            lines = lines + line;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Fail read buffer", e);
                        e.printStackTrace();
                    }
                    Log.i("Main.thread.getLines", "JSON string built");
                    return lines;
                    //return in.lines().collect(Collectors.joining("\n"));
                }
            }).start();
        }

        @Override
        public void onClick(View v) {
            final Handler handler = new Handler();
            String city = cityRequested.getText().toString();
            if (HANDLE_BY_RX) {
                runRxHandler(v, city);
            } else {
                try {
                    final URL uri = new URL(WEATHER_URL + city + "&appid=" + BuildConfig.WEATHER_API_KEY + "&units=" + WEATHER_OPTIONS);
                    runURLConnectionHandler(v, handler, uri);
                } catch (Exception e) {
                    Log.e(TAG, "Fail URL", e);
                    e.printStackTrace();
                }

            }

        }
    };


    private void displayWeather(WeatherRequest weatherRequest) {
        country.setText(weatherRequest.getSys().getCountry());
        city.setText(weatherRequest.getName());
        temperature.setText(String.format("%f2", weatherRequest.getMain().getTemp()));
        pressure.setText(String.format("%d", weatherRequest.getMain().getPressure()));
        humidity.setText(String.format("%d", weatherRequest.getMain().getHumidity()));
        windSpeed.setText(String.format("%f2", weatherRequest.getWind().getSpeed()));
    }
}
