package com.example.aac;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.data.WeatherContract;

import java.util.Date;
import java.util.List;

@Dao
public interface WeatherDao {

    @Query("Select * from weather ORDER BY date")
    LiveData<List<WeatherEntity>> loadAllWeather();

    @Query("Select * from weather where date = :givenDate")
    LiveData<WeatherEntity> loadWeatherByDate(long givenDate);

    @Insert
    void insertWeather(WeatherEntity weatherEntity);
    @Insert
    void insertAllWeather(List<WeatherEntity> weatherEntities);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void updateWeather(WeatherEntity weatherEntity);

    @Delete
    void deleteWeather(WeatherEntity weatherEntity);
}
