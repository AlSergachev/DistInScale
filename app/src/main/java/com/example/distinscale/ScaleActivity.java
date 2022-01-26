package com.example.distinscale;

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

import com.example.distinscale.databinding.ActivityScaleBinding;


import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class ScaleActivity extends AppCompatActivity {

    private ActivityScaleBinding binding;
    private static int scale;
    private static int side;
    private static final int MAX_NUMBER_OF_SCALES = 7;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int CAMERA_PERM_CODE = 101;
    private String currentPhotoPath = "err";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScaleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
    }

    private void setListeners(){
        binding.takePhoto.setOnClickListener(v ->
                        askCameraPermission());

        binding.confirm.setOnClickListener(v -> {
            scale = binding.spinnerScale.getSelectedItemPosition();
            int proven_scale = checkScale(scale);
            side = binding.spinnerSide.getSelectedItemPosition();
            if (proven_scale < MAX_NUMBER_OF_SCALES && !currentPhotoPath.equals("err"))
                startNewActivity(proven_scale);
            else
                showMessage("Сделайте фото");
        });
    }

    private void showMessage(String text){
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }

    private int checkScale(int scale){
        switch (scale) {
            case Constants.MILLION:
                return Constants.MILLION;
            case Constants.ONE_HUNDRED_THOUSAND:
                return Constants.ONE_HUNDRED_THOUSAND;
            case Constants.FIFTY_THOUSAND:
                return Constants.FIFTY_THOUSAND;
            case Constants.TWENTY_FIVE_THOUSAND:
                return Constants.TWENTY_FIVE_THOUSAND;
            case Constants.TEN_THOUSAND:
                return Constants.TEN_THOUSAND;
            case Constants.FIVE_THOUSAND:
                return Constants.FIVE_THOUSAND;
            case Constants.TWO_THOUSAND:
                return Constants.TWO_THOUSAND;
            default:
                showMessage("Указан неверный масштаб");
                return 10;
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