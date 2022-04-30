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

import com.example.distinscale.domain.ImageAdapter;
import com.example.distinscale.domain.models.Steps;
import com.example.distinscale.databinding.ActivityWorkBinding;
import com.example.distinscale.domain.usecase.ProcessingUseCase;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.Mat;


@SuppressWarnings("deprecation")
public class WorkActivity extends AppCompatActivity {

    private static int isVisibleRecyclerView;
    private String currentPhotoPath;
    private int mapScale;
    private int sideSheet;
    ActivityWorkBinding binding;
    private ProgressDialog progressDialog;

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWorkBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        isVisibleRecyclerView = 4;

        checkLoadOpenCV();
        getData();
        setListener();
        createProgressDialog();

        Handler handler = new Handler();
        new Thread(() -> {

            ProcessingUseCase proc = new ProcessingUseCase(mapScale, sideSheet);
            final int[] pr = {1};

            proc.setImageStep(currentPhotoPath);
            runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                    Steps.STEP_01 + "\n" + Steps.STEP_02));

            proc.pyrMeanShiftFilteringFirstStep();
            runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                    Steps.STEP_01 + "\n" + Steps.STEP_02 + "\n" + Steps.STEP_03));

            int threshold2 = 256;
            do {
                proc.preProcessingFirstStep(threshold2);
                proc.getCornersStep();
                threshold2 /= 2;
            } while (proc.corners.size() < 3 && threshold2 > 3);
            runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                    Steps.STEP_02 + "\n" + Steps.STEP_03 + "\n" + Steps.STEP_04));

            if (proc.corners.size() < 2) {
                handler.post(() -> {
                    progressDialog.dismiss();
                    ImageAdapter imageAdapter = new ImageAdapter(this, proc.matArrayList);
                    binding.recyclerView.setAdapter(imageAdapter);
                    //todo: добавить инструкции по улучшению качества изображения для обработки
                    showMessage("Контур листа бумаги не найден! Попробуйте сделать фото ещё раз");
                });
            } else {
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                        Steps.STEP_03 + "\n" + Steps.STEP_04 + "\n" + Steps.STEP_05));

                proc.drawPointsStep();
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                        Steps.STEP_04 + "\n" + Steps.STEP_05 + "\n" + Steps.STEP_06));

                proc.warmImageStep();
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                        Steps.STEP_05 + "\n" + Steps.STEP_06 + "\n" + Steps.STEP_07));

                proc.cropStep();
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                        Steps.STEP_06 + "\n" + Steps.STEP_07 + "\n" + Steps.STEP_08));

                proc.pyrMeanShiftFilteringSecondStep();
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                        Steps.STEP_07 + "\n" + Steps.STEP_08 + "\n" + Steps.STEP_09));

                proc.preProcessingSecondStep();
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                        Steps.STEP_08 + "\n" + Steps.STEP_09 + "\n" + Steps.STEP_10));

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
                    runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                            Steps.STEP_09 + "\n" + Steps.STEP_10 + "\n" + Steps.STEP_11));

                    proc.drawArrowStep();
                    runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                            Steps.STEP_10 + "\n" + Steps.STEP_11 + "\n" + Steps.STEP_12));

                    proc.getLengthLineStep();
                    runOnUiThread(() -> setProgress(progressDialog, pr[0],
                            Steps.STEP_11 + "\n" + Steps.STEP_12 + "\n" + Steps.STEP_FINAL));

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
    private void createProgressDialog(){
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(12);              // Количество шагов
        progressDialog.setProgress(0);
        progressDialog.setMessage(Steps.STEP_01);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }


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

    // Показывает сообщение в всплывающем уведомлении
    private void showMessage(String text) {
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }

    // Проверяет, загрузилась ли библиотека OpenCV
    private void checkLoadOpenCV() {
        if (!OpenCVLoader.initDebug()) {
            showMessage("Unable to load OpenCV!");
            onBackPressed();
        } else {
            showMessage("OpenCV loaded Successfully!");
        }
    }

    // Загружает данные из ScaleActivity
    private void getData() {
        Bundle arguments = getIntent().getExtras();
        currentPhotoPath = arguments.getString("CURRENT_PHOTO_PATH");
        mapScale = arguments.getInt("SCALE");
        sideSheet = arguments.getInt("SIDE");
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

    // Устанавливает погресс загрузки в ProgressDialog
    private void setProgress(ProgressDialog pd, int i, String msg) {
        pd.setProgress(i);
        pd.setMessage(msg);
    }

}
