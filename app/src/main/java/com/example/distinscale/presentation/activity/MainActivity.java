package com.example.distinscale.presentation.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

import com.example.distinscale.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
    }

    private void setListeners(){
        binding.start.setOnClickListener(v ->
                startActivity(new Intent(getApplicationContext(), ScaleActivity.class)));
        binding.manual.setOnClickListener(v ->
                startActivity(new Intent(getApplicationContext(), ManualActivity.class)));
    }

}