package com.example.aac;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.example.aac.database.WeatherDao;
import com.example.aac.database.WeatherEntity;
import com.example.aac.network.WeatherNetworkDataSource;
import com.example.utils.WeatherDateUtils;
import com.example.weatherapp.AppExecutors;

import java.util.Date;
import java.util.List;

public class WeatherRepository {

    private static final String LOG_TAG = WeatherRepository.class.getSimpleName();
    private static final Object LOCK = new Object();
    private static WeatherRepository sInstance;


    private final WeatherDao weatherDao;
    private final WeatherNetworkDataSource weatherNetworkDataSource;
    private final AppExecutors executors;

    private boolean mDataFetched = false;
    private WeatherRepository(WeatherDao weatherDao, WeatherNetworkDataSource weatherNetworkDataSource, AppExecutors executors){

        this.weatherDao = weatherDao;
        this.weatherNetworkDataSource = weatherNetworkDataSource;
        this.executors = executors;
        LiveData<List<WeatherEntity>> weatherListLiveData = weatherNetworkDataSource.getmDownloadedWeatherForecasts();
        weatherListLiveData.observeForever(new Observer<List<WeatherEntity>>() {
            @Override
            public void onChanged(List<WeatherEntity> weatherEntities) {
                executors.diskIO().execute(new Runnable() {
                    @Override
                    public void run() {
                        Date date = new Date(WeatherDateUtils.getNormalizedUtcDateForToday());
                       //weatherDao.deleteOldWeather(date);
                       weatherDao.deleteAllWeather();
                        weatherDao.insertAllWeather(weatherEntities);
                    }
                });
            }
        });
    }

    private synchronized void fetchDataFromNetwork(){
        if (mDataFetched)return;
        mDataFetched = true;
        startFetchDataService();
    }
    public synchronized static WeatherRepository getInstance(WeatherDao weatherDao,WeatherNetworkDataSource weatherNetworkDataSource,AppExecutors executors){
        Log.d(LOG_TAG,"Getting the repository");
        if (sInstance==null){
            synchronized (LOCK){
                sInstance = new WeatherRepository(weatherDao,weatherNetworkDataSource,executors);
                Log.d(LOG_TAG,"Made new Repository");
            }
        }
        return sInstance;
    }
    public LiveData<List<WeatherEntity>> getWeatherList(){
        fetchDataFromNetwork();  // Download data from internet
        return weatherDao.loadAllWeather();  // update local database
    }
    public LiveData<WeatherEntity> getWeatherByDate(Date date){
        fetchDataFromNetwork();
        return weatherDao.loadWeatherByDate(date);
    }

    public void deleteWeather(WeatherEntity weatherEntity) {
        weatherDao.deleteWeather(weatherEntity);
    }

    public void refreshWeathers(){
        weatherNetworkDataSource.startWeatherService();
    }
    public void startFetchDataService(){
        weatherNetworkDataSource.startWeatherService();
    }
}
