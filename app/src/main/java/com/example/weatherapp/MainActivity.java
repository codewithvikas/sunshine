package com.example.weatherapp;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.aac.WeatherRepository;
import com.example.aac.database.WeatherDao;
import com.example.aac.database.WeatherDatabase;
import com.example.aac.database.WeatherEntity;
import com.example.aac.network.WeatherNetworkDataSource;
import com.example.aac.ui.WeatherListViewModel;
import com.example.aac.ui.WeatherListViewModelFactory;
import com.example.data.WeatherContract;
import com.example.data.WeatherPreferences;
import com.example.utils.Constants;
import com.example.utils.NotificationUtil;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MainActivity extends AppCompatActivity implements ForeCastAdapter.ItemClickHandler, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private ForeCastAdapter mForeCastAdapter;
    ProgressBar loadingIndicator;
    TextView errorTextView;
    RecyclerView recyclerView;

    WeatherListViewModel weatherListViewModel;

    IntentFilter mChargingIntentFilter;
    BroadcastReceiver mChargingReceiver;

    private static  boolean UNIT_HAVE_BEEN_UPDATED = false;
    private static boolean LOCATION_HAVE_BEEN_UPDATED = false;

    private static final int  LOADER_ID = 11;

    public static final String[] projection = {
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID
    };

    public static final int INDEX_COLUMN_DATE = 0;
    public static final int INDEX_COLUMN_MAX = 1;
    public static final int INDEX_COLUMN_MIN = 2;
    public static final int INDEX_COLUMN_ID = 3;


    ItemTouchHelper.SimpleCallback recyclerViewItemTouchHelper = new ItemTouchHelper.SimpleCallback(0,ItemTouchHelper.RIGHT|ItemTouchHelper.LEFT) {
        @Override
        public boolean onMove(@NonNull @NotNull RecyclerView recyclerView, @NonNull @NotNull RecyclerView.ViewHolder viewHolder, @NonNull @NotNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull @NotNull RecyclerView.ViewHolder viewHolder, int direction) {
                 AppExecutors.getInstance().diskIO().execute(new Runnable() {
                    @Override
                    public void run() {
                        WeatherEntity weatherEntity = mForeCastAdapter.getWeatherByPosition(viewHolder.getAdapterPosition());
                        weatherListViewModel.deleteWeather(weatherEntity);
                    }
                });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forecast);
        getSupportActionBar().setElevation(0);

        recyclerView = findViewById(R.id.forecast_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);


        mForeCastAdapter = new ForeCastAdapter(this,this);

        recyclerView.setAdapter(mForeCastAdapter);

        new ItemTouchHelper(recyclerViewItemTouchHelper).attachToRecyclerView(recyclerView);

        loadingIndicator = findViewById(R.id.pb_loading_indecator);

        errorTextView = findViewById(R.id.tv_error_display_msg);

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        setupWeatherViewModel();


        //Broadcast receiver

        mChargingIntentFilter = new IntentFilter();
        mChargingIntentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        mChargingIntentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);

        mChargingReceiver = new ChargingBroadcastReceiver();


    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onResume() {
        super.onResume();
        BatteryManager  batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        showCharging(batteryManager.isCharging());
        registerReceiver(mChargingReceiver,mChargingIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mChargingReceiver);
    }

    void setupWeatherViewModel(){

        WeatherDao weatherDao = WeatherDatabase.getInstance(getApplicationContext()).weatherDao();
        AppExecutors appExecutors = AppExecutors.getInstance();
        WeatherNetworkDataSource weatherNetworkDataSource = WeatherNetworkDataSource.getInstance(getApplicationContext(),appExecutors);
        WeatherRepository weatherRepository = WeatherRepository.getInstance(weatherDao,weatherNetworkDataSource,appExecutors);

        WeatherListViewModelFactory weatherListViewModelFactory = new WeatherListViewModelFactory(weatherRepository);
        weatherListViewModel = new ViewModelProvider(MainActivity.this,weatherListViewModelFactory).get(WeatherListViewModel.class);

        weatherListViewModel.getWeathersLiveData().observe(MainActivity.this, new Observer<List<WeatherEntity>>() {
                            @Override
                            public void onChanged(List<WeatherEntity> weatherEntities) {
                                loadingIndicator.setVisibility(View.INVISIBLE);
                                mForeCastAdapter.swapCursor(weatherEntities);
                                if (weatherEntities!=null && weatherEntities.size() > 0){
                                    showDataView();
                                    NotificationUtil.remindUserWeatherListUpdate(MainActivity.this);
                                }
                                else {
                                    showErrorView();
                                }
                            }
                        });
                    }


    @Override
    protected void onStart() {
        super.onStart();
        if (LOCATION_HAVE_BEEN_UPDATED){
            LOCATION_HAVE_BEEN_UPDATED = false;
            UNIT_HAVE_BEEN_UPDATED = false;
            weatherListViewModel.refreshWeathers();
        }
        else if (UNIT_HAVE_BEEN_UPDATED){
          mForeCastAdapter.notifyDataSetChanged();
          UNIT_HAVE_BEEN_UPDATED = false;
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.forecast,menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case R.id.action_refresh:
                mForeCastAdapter.swapCursor(null);
                weatherListViewModel.refreshWeathers();
                return true;
            case R.id.action_open_map:
                openMapInLocation();
                return true;
            case R.id.action_settings:
                openSettingActivity();

            default:
                return super.onOptionsItemSelected(item);

                        }
    }

    private void openSettingActivity() {
        Intent intent = new Intent(MainActivity.this,SettingsActivity.class);
        startActivity(intent);
    }

    void openMapInLocation(){
        String query = WeatherPreferences.getPreferredWeatherLocation(this);
        Uri data = Uri.parse("geo:0,0?q="+ query);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(data);

        if (intent.resolveActivity(getPackageManager())!=null){
            startActivity(intent);
        }
        else {
            Toast.makeText(this,"Could not open map. No any App is installed",Toast.LENGTH_SHORT).show();
        }
    }
    void showDataView(){
        recyclerView.setVisibility(View.VISIBLE);
        errorTextView.setVisibility(View.INVISIBLE);
    }
    void showErrorView(){
        errorTextView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onClick(long date) {
        Intent intent = new Intent(this,DetailActivity.class);
        intent.putExtra(Constants.DATE_EXTRA,date);
        startActivity(intent);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        if (key.equals(getString(R.string.pref_location_key))){
            LOCATION_HAVE_BEEN_UPDATED = true;
        }
        if (key.equals(getString(R.string.pref_units_key))){
            UNIT_HAVE_BEEN_UPDATED = true;
        }
    }

    //Broadcast receiver

    void showCharging(boolean isCharging){
        if (isCharging){
            Toast.makeText(MainActivity.this,"Mobile is Charging !!",Toast.LENGTH_SHORT).show();
            //weatherListViewModel.refreshWeathers();
        }

    }
    private class ChargingBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                boolean isCharging  = action.equals(Intent.ACTION_POWER_CONNECTED);
                showCharging(isCharging);
        }
    }
}