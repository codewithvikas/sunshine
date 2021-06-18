package com.example.aac.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.aac.database.WeatherDatabase;

import org.jetbrains.annotations.NotNull;

public class WeatherDetailViewModelFactory extends ViewModelProvider.NewInstanceFactory {

    WeatherDatabase weatherDatabase;
    long mDate;
    /**
     * Creates a {@code AndroidViewModelFactory}
     *
     * @param weatherDatabase an application to pass in {@link AndroidViewModel}
     */
    public WeatherDetailViewModelFactory(@NonNull @NotNull WeatherDatabase weatherDatabase,long date) {
        this.weatherDatabase = weatherDatabase;
        this.mDate = date;

    }

    @NonNull
    @NotNull
    @Override
    public <T extends ViewModel> T create(@NonNull @NotNull Class<T> modelClass) {
        return (T) new WeatherDetailViewModel(weatherDatabase,mDate);
    }
}