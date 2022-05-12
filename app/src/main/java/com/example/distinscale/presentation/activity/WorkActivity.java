package com.example.distinscale.presentation.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.distinscale.domain.utilities.Constants;
import com.example.distinscale.domain.utilities.ImageAdapter;
import com.example.distinscale.databinding.ActivityWorkBinding;
import com.example.distinscale.domain.logic.ProcessingImage;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.Mat;


@SuppressWarnings("deprecation")
public class WorkActivity extends AppCompatActivity {

    ActivityWorkBinding binding;
    private static int isVisibleRecyclerView;
    private String currentPhotoPath;
    private int mapScale;
    private int sideSheet;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWorkBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        isVisibleRecyclerView = 4;

        checkLoadOpenCv();
        getData();
        setListener();
        createProgressDialog();
        startProcessing();
    }

    // Проверяет, загрузилась ли библиотека OpenCV
    private void checkLoadOpenCv() {
        if (!OpenCVLoader.initDebug()) {
            showMessage("Unable to load OpenCV!");
            onBackPressed();
        } else {
            showMessage("OpenCV loaded Successfully!");
        }
    }

    // Показывает сообщение в всплывающем уведомлении
    private void showMessage(String text) {
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }

    // Загружает данные из SourceDataActivity
    private void getData() {
        Bundle arguments = getIntent().getExtras();
        currentPhotoPath = arguments.getString("CURRENT_PHOTO_PATH");
        mapScale = arguments.getInt("SCALE");
        sideSheet = arguments.getInt("SIDE");
    }

    // ОБрабатывает нажатия на кнопки
    private void setListener() {
        binding.btmShowSteps.setOnClickListener(v -> {
            if (isVisibleRecyclerView == View.INVISIBLE) {
                binding.resultImg.setVisibility(View.INVISIBLE);
                binding.linearLayout.setVisibility(View.INVISIBLE);
                binding.recyclerView.setVisibility(View.VISIBLE);
                binding.btmShowSteps.setText("Скрыть этапы обработки");
                isVisibleRecyclerView = 0;
            } else {
                binding.recyclerView.setVisibility(View.INVISIBLE);
                binding.resultImg.setVisibility(View.VISIBLE);
                binding.linearLayout.setVisibility(View.VISIBLE);
                binding.btmShowSteps.setText("Показать этапы обработки");
                isVisibleRecyclerView = 4;
            }
        });
    }

    // Создаёт ProgressDialog
    private void createProgressDialog() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(12);              // Количество шагов
        progressDialog.setProgress(0);
        progressDialog.setMessage(Constants.listOfSteps.get(0));
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    // Конвертирует Mat в Bitmap
    private static Bitmap convertMatToBitmap(Mat inputM) {
        Bitmap bmp = null;
        Mat rgb = inputM.clone();
        try {
            bmp = Bitmap.createBitmap(rgb.cols(), rgb.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(rgb, bmp);
        } catch (CvException e) {
            Log.d("Exception", e.getMessage());
        }
        return bmp;
    }

    // Устанавливает прогресс загрузки в ProgressDialog
    private void setProgress(ProgressDialog pd, int i) {
        pd.setMessage(Constants.listOfSteps.get(i - 1) + "\n" + Constants.listOfSteps.get(i));
        pd.setProgress(i);
    }

    // Запускает обработку
    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    private void startProcessing() {
        Handler handler = new Handler();
        new Thread(() -> {

            ProcessingImage proc = new ProcessingImage(mapScale, sideSheet);

            final int[] pr = {1};

            proc.setImageStep(currentPhotoPath);
            runOnUiThread(() -> setProgress(progressDialog, pr[0]++));

            proc.pyrMeanShiftFilteringFirstStep();
            runOnUiThread(() -> setProgress(progressDialog, pr[0]++));

            int threshold2 = 256;
            do {
                proc.preProcessingFirstStep(threshold2);
                proc.getCornersStep();
                threshold2 /= 2;
            } while (proc.corners.size() < 3 && threshold2 > 3);
            runOnUiThread(() -> setProgress(progressDialog, pr[0]++));

            if (proc.corners.size() < 2) {
                handler.post(() -> {
                    progressDialog.dismiss();
                    ImageAdapter imageAdapter = new ImageAdapter(this, proc.matArrayList);
                    binding.recyclerView.setAdapter(imageAdapter);
                    //todo: добавить инструкции по улучшению качества изображения для обработки
                    showMessage("Контур листа бумаги не найден! Попробуйте сделать фото ещё раз");
                });
            } else {
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++));

                proc.reorderStep();
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++));

                proc.warpImageStep();
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++));

                proc.cropStep();
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++));

                proc.pyrMeanShiftFilteringSecondStep();
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++));

                proc.preProcessingSecondStep();
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++));

                proc.findContoursOfMarksStep();
                if (proc.contoursOfMarks.size() < 2) {
                    handler.post(() -> {
                        progressDialog.dismiss();
                        ImageAdapter imageAdapter = new ImageAdapter(this, proc.matArrayList);
                        binding.recyclerView.setAdapter(imageAdapter);
                        showMessage("Отметки на листе не найдены! Попробуйте сделать фото ещё раз");
                    });
                } else {

                    proc.selectPointStep();
                    runOnUiThread(() -> setProgress(progressDialog, pr[0]++));

                    proc.drawArrowStep();
                    runOnUiThread(() -> setProgress(progressDialog, pr[0]++));

                    proc.getLengthLineStep();
                    runOnUiThread(() -> setProgress(progressDialog, pr[0]++));

                    handler.post(() -> {
                        progressDialog.dismiss();
                        binding.resultImg.setImageBitmap(
                                convertMatToBitmap(proc.matArrayList.get(0)));
                        binding.linearLayout.setVisibility(View.VISIBLE);
                        binding.resultLength.setText(String.format("%.3f", proc.length) + "m");
                        binding.textScale.setText("1 : " + mapScale);
                        ImageAdapter imageAdapter = new ImageAdapter(this, proc.matArrayList);
                        binding.recyclerView.setAdapter(imageAdapter);
                    });
                }
            }
        }).start();
    }
}
