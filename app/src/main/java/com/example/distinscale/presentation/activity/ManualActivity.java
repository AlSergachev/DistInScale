package com.example.distinscale.presentation.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.distinscale.R;
import com.example.distinscale.databinding.ActivityManualBinding;

public class ManualActivity extends AppCompatActivity {

    ActivityManualBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityManualBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog, null);
        builder.setView(view);
        AlertDialog alert = builder.create();

        setListeners(alert);
    }

    private void setListeners(AlertDialog alert) {
        binding.btnBack.setOnClickListener(v -> onBackPressed());
        binding.btnInfo.setOnClickListener(v -> alert.show());
    }
}