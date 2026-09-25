package com.mydictionary.android;

import android.app.Application;
import android.content.Context;

import com.mydictionary.android.db.DictionaryDbHelper;
import com.mydictionary.android.sample.SampleDataInitializer;

public class MyDictionaryApplication extends Application {
    private DictionaryDbHelper dbHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new DictionaryDbHelper(this);
        SampleDataInitializer.ensureSampleData(dbHelper);
    }

    public DictionaryDbHelper getDbHelper() {
        return dbHelper;
    }

    public static MyDictionaryApplication from(Context context) {
        return (MyDictionaryApplication) context.getApplicationContext();
    }
}
