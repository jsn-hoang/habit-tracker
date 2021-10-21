package com.example.habittracker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

public class CustomList extends ArrayAdapter<com.example.habittracker.Habit> {

    private ArrayList<com.example.habittracker.Habit> cities;
    private Context context;

    public CustomList(Context context, ArrayList<com.example.habittracker.Habit> cities){
        super(context,0, cities);
        this.cities = cities;
        this.context = context;
    }

    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup
            parent) {
// return super.getView(position, convertView, parent);
        View view = convertView;
        if(view == null){
            view = LayoutInflater.from(context).inflate(R.layout.content, parent,false);
        }
        com.example.habittracker.Habit city = cities.get(position);
        TextView cityName = view.findViewById(R.id.city_text);
        TextView provinceName = view.findViewById(R.id.province_text);
        cityName.setText(city.getCity());
        provinceName.setText(city.getProvince());
        return view;
    }
}