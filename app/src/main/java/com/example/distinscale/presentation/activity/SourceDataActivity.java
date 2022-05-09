package com.example.distinscale.presentation.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import com.example.distinscale.databinding.ActivitySourceDataBinding;
import com.example.distinscale.domain.utilities.Constants;


import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class SourceDataActivity extends AppCompatActivity {

    private ActivitySourceDataBinding binding;
    private static int side;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int CAMERA_PERM_CODE = 101;
    private String currentPhotoPath = "err";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySourceDataBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
    }

    private void setListeners(){
        binding.takePhoto.setOnClickListener(v ->
                        askCameraPermission());

        binding.confirm.setOnClickListener(v -> {
            int scalePosition = binding.spinnerScale.getSelectedItemPosition();
            int scale = setScale(scalePosition);
            side = binding.spinnerSide.getSelectedItemPosition();
            if (scale > 0 && !currentPhotoPath.equals("err"))
                startNewActivity(scale);
            else
                showMessage("Сделайте фото");
        });
    }

    private void showMessage(String text){
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }

    private int setScale(int scale){
        switch (scale) {
            case Constants.MILLION:
                return 1000000;
            case Constants.ONE_HUNDRED_THOUSAND:
                return 100000;
            case Constants.FIFTY_THOUSAND:
                return 50000;
            case Constants.TWENTY_FIVE_THOUSAND:
                return 25000;
            case Constants.TEN_THOUSAND:
                return 10000;
            case Constants.FIVE_THOUSAND:
                return 5000;
            case Constants.TWO_THOUSAND:
                return 2000;
            case Constants.OWN_THOUSAND:
                return 1000;
            case Constants.FIVE_HUNDRED:
                return 500;
            case Constants.TWO_HUNDRED:
                return 200;
            case Constants.HUNDRED:
                return 100;
            case Constants.FIFTY :
                return 50;
            default:
                showMessage("Указан неверный масштаб");
                return -1;
        }
    }

    private void askCameraPermission() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.CAMERA},
                    CAMERA_PERM_CODE);
        } else {
            dispatchTakePictureIntent();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERM_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                showMessage("Для использования приложения необходимо дать разрешение камере");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            File f = new File(currentPhotoPath);
            binding.thumbnail.setImageURI(Uri.fromFile(f));
        }
    }

    private File createImageFile() throws IOException {
        @SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "DISTINSCALE_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @SuppressWarnings("deprecation")
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                showMessage("Произошла ошибка при создании файла!");
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private void startNewActivity(int proven_scale){
        Intent intent = new Intent(this, WorkActivity.class);
        intent.putExtra("SCALE", proven_scale);
        intent.putExtra("SIDE", side);
        intent.putExtra("CURRENT_PHOTO_PATH", currentPhotoPath);
        startActivity(intent);
    }

}