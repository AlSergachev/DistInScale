package com.example.distinscale.presentation.activity;

import static org.opencv.core.Core.FILLED;
import static org.opencv.core.Core.FONT_HERSHEY_SIMPLEX;
import static org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE;
import static org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY;
import static org.opencv.imgproc.Imgproc.MORPH_RECT;
import static org.opencv.imgproc.Imgproc.RETR_EXTERNAL;
import static org.opencv.imgproc.Imgproc.arrowedLine;
import static org.opencv.imgproc.Imgproc.getPerspectiveTransform;
import static org.opencv.imgproc.Imgproc.isContourConvex;
import static org.opencv.imgproc.Imgproc.line;
import static org.opencv.imgproc.Imgproc.warpPerspective;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.distinscale.domain.Constants;
import com.example.distinscale.domain.ImageAdapter;
import com.example.distinscale.domain.models.PreprocessParameters;
import com.example.distinscale.domain.models.SheetFormat;
import com.example.distinscale.domain.models.Steps;
import com.example.distinscale.databinding.ActivityWorkBinding;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;


import java.util.ArrayList;
import java.util.Collections;

public class WorkActivity extends AppCompatActivity {

    private static final int CROP_VALUE = 10;
    private static final int FACTOR_VALUE = 5;
    private static final int SIZE_BORDER = 30; //Размер отступа от края листа бумаги для поиска рисок
    private static final SheetFormat A4_V = new SheetFormat(210, 297);
    private static final SheetFormat A4_H = new SheetFormat(297, 210);
    private static int isVisibleRecyclerView;
    private String currentPhotoPath;
    private int mapScale;
    private int sideSheet;
    ActivityWorkBinding binding;


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

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(12);              // Количество шагов
        progressDialog.setProgress(0);
        progressDialog.setMessage(Steps.STEP_01);
        progressDialog.setCancelable(false);
        progressDialog.show();

        Handler handler = new Handler();
        new Thread(() -> {
            final int[] pr = {1};
            ArrayList<Mat> matArrayList = new ArrayList<>();
            Mat imgOr = Imgcodecs.imread(currentPhotoPath);
            Mat imgM = imgOr.clone();
            Mat imgMSF = imgOr.clone();
            matArrayList.add(0, imgOr);
            runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                    Steps.STEP_01 + "\n" + Steps.STEP_02));

            Imgproc.pyrMeanShiftFiltering(imgM, imgMSF, 30, 80);
            matArrayList.add(0, imgMSF);
            runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                    Steps.STEP_01 + "\n" + Steps.STEP_02 + "\n" + Steps.STEP_03));

            PreprocessParameters mainPreProc = new PreprocessParameters(
                    3, 2, 1, 310, 90, 17, 2, 1);
            Mat imgThree = preProcessing(imgMSF, mainPreProc);
            matArrayList.add(0, imgThree);
            runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                    Steps.STEP_02 + "\n" + Steps.STEP_03 + "\n" + Steps.STEP_04));

            ArrayList<Point> corners = getCorners(imgThree, imgM, 10);
            if(corners.size() < 2) {
                handler.post(() -> {
                    progressDialog.dismiss();
                    ImageAdapter imageAdapter = new ImageAdapter(this, matArrayList);
                    binding.recyclerView.setAdapter(imageAdapter);
                    //todo: добавить инструкции по улучшению качества изображения для обработки
                    showMessage("Контур листа бумаги не найден! Попробуйте сделать фото ещё раз");
                });
            }else {
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                        Steps.STEP_03 + "\n" + Steps.STEP_04 + "\n" + Steps.STEP_05));

                ArrayList<Point> newCorners = reorder(corners);
                Mat imgPoints = drawPoints(newCorners, imgOr, new Scalar(0, 255, 255), 10);
                matArrayList.add(0, imgPoints);
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                        Steps.STEP_04 + "\n" + Steps.STEP_05 + "\n" + Steps.STEP_06));

                SheetFormat A4 = setOrientation(newCorners);
                Mat imgWarm =  getWarp(imgOr, newCorners, A4);
                matArrayList.add(0, imgWarm);
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                        Steps.STEP_05 + "\n" + Steps.STEP_06 + "\n" + Steps.STEP_07));

                Mat imgCrop = getCropImg(imgWarm);
                matArrayList.add(0, imgCrop);
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                        Steps.STEP_06 + "\n" + Steps.STEP_07 + "\n" + Steps.STEP_08));

                Mat imgCropMSF = imgCrop.clone();
                Imgproc.pyrMeanShiftFiltering(imgCrop, imgCropMSF, 30, 30);
                matArrayList.add(0, imgCropMSF);
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                        Steps.STEP_07 + "\n" + Steps.STEP_08 + "\n" + Steps.STEP_09));

                PreprocessParameters SecondPreProc = new PreprocessParameters(
                        3, 1, 0, 100, 100, 3, 3, 2);
                Mat imgThreeSecond = preProcessing(imgCropMSF, SecondPreProc);
                matArrayList.add(0, imgThreeSecond);
                runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                        Steps.STEP_08 + "\n" + Steps.STEP_09 + "\n" + Steps.STEP_10));

                ArrayList<MatOfPoint> contoursOfMarks = findContoursOfMarks(imgThreeSecond, sideSheet);
                if(contoursOfMarks.size() < 2) {
                    handler.post(() -> {
                        progressDialog.dismiss();
                        ImageAdapter imageAdapter = new ImageAdapter(this, matArrayList);
                        binding.recyclerView.setAdapter(imageAdapter);
                        showMessage("Отметки на листе не найдены! Попробуйте сделать фото ещё раз");
                    });
                }else {
                    Mat imgTwoPoints = imgCrop.clone();
                    Imgproc.drawContours(imgTwoPoints,
                            contoursOfMarks, -1, new Scalar(0, 0, 255), 2);
                    ArrayList<Point> twoPoints = getTwoPoints(contoursOfMarks);
                    imgTwoPoints = drawPoints(twoPoints, imgTwoPoints, new Scalar(0, 255, 255), 1);
                    matArrayList.add(0, imgTwoPoints);
                    runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                            Steps.STEP_09 + "\n" + Steps.STEP_10 + "\n" + Steps.STEP_11));

                    Mat imgArrow = drawArrow(imgCrop, twoPoints, sideSheet);
                    matArrayList.add(0, imgArrow);
                    runOnUiThread(() -> setProgress(progressDialog, pr[0]++,
                            Steps.STEP_10 + "\n" + Steps.STEP_11 + "\n" + Steps.STEP_12));

                    double length = getLengthLine(imgCrop, twoPoints, sideSheet, A4);
                    runOnUiThread(() -> setProgress(progressDialog, pr[0],
                            Steps.STEP_11 + "\n" + Steps.STEP_12 + "\n" + Steps.STEP_FINAL));

                    handler.post(() -> {
                        progressDialog.dismiss();
                        binding.resultImg.setImageBitmap(
                                convertMatToBitmap(matArrayList.get(0)));
                        binding.linearLayout.setVisibility(View.VISIBLE);
                        binding.resultLength.setText(String.format("%.3f",length) + "m");
                        binding.textScale.setText(getStringScale(mapScale));
                        ImageAdapter imageAdapter = new ImageAdapter(this, matArrayList);
                        binding.recyclerView.setAdapter(imageAdapter);
                    });
                }
            }
        }).start();

    }

    /*--------------------------------------------------------------------------------------------------------------*/

    private void setListener(){
        binding.btmShowSteps.setOnClickListener(v -> {
            if(isVisibleRecyclerView == View.INVISIBLE){
                binding.resultImg.setVisibility(View.INVISIBLE);
                binding.linearLayout.setVisibility(View.INVISIBLE);
                binding.recyclerView.setVisibility(View.VISIBLE);
                binding.btmShowSteps.setText("Скрыть этапы обработки");
                isVisibleRecyclerView = 0;
            }
            else {
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
        }
        else {
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

    // Выполняет бинаризацию изображения
    private Mat preProcessing(Mat img, PreprocessParameters pp) {
        Mat imgGray = new Mat();
        Mat imgCanny = new Mat();
        Mat imgBlur = new Mat();
        Mat imgDil = new Mat();
        Mat imgErode = new Mat();

        // Преобразует изображение из одного цветового пространства в другое
        Imgproc.cvtColor(img, imgGray, COLOR_BGR2GRAY);
        // Размывает изображение с помощью гауссовского фильтра
        Imgproc.GaussianBlur(imgGray, imgBlur, new Size(pp.GB_size, pp.GB_size), pp.GB_sX, pp.GB_sY);
        // Находит ребра в изображении с помощью алгоритма Кэнни
        Imgproc.Canny(imgBlur, imgCanny, pp.C_t1, pp.C_t2);
        // Возвращает структурирующий элемент заданного размера и формы для морфологических операций
        Mat kernel = Imgproc.getStructuringElement(MORPH_RECT, new Size(pp.k_size, pp.k_size));
        Imgproc.dilate(imgCanny, imgDil, kernel, new Point(-1, -1), pp.d_i);
        Imgproc.erode(imgDil, imgErode, kernel, new Point(-1, -1), pp.e_i);

        return imgErode;
    }

    // Определяет точки углов листа
    private ArrayList<Point> getCorners(Mat imgThree, Mat imgOr, int scale) {

        double maxArea = 0;
        Mat image = imgThree.clone();
        ArrayList<Point> corners = new ArrayList<>();
        MatOfPoint2f largestCurve = new MatOfPoint2f();
        ArrayList<MatOfPoint> contours = new ArrayList<>();

        // Находит контуры в двоичном изображении
        Imgproc.findContours(image, contours, new Mat(), RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

        if(contours.size() < 1){
            corners.add(new Point(0, 0));
            return corners;
        }

        for (MatOfPoint contour : contours) {
            //Площадь контура
            double area = Imgproc.contourArea(contour);

            // Выбирает наибольний контур
            if (area > maxArea) {
                MatOfPoint2f thisContour = new MatOfPoint2f(contour.toArray());

                // Вычисляет длину кривой или периметр замкнутого контура
                double peri = Imgproc.arcLength(thisContour, true);

                // Аппроксимирует полигональную кривую с заданной точностью
                MatOfPoint2f approxCurve = new MatOfPoint2f();
                //      параметр "epsilon" представляет максимальное расстояние между приближением
                //      контура формы входного многоугольника и исходным входным многоугольником
                Imgproc.approxPolyDP(thisContour, approxCurve, 0.05 * peri, true);
                boolean isConvex = isContourConvex(new MatOfPoint(approxCurve.toArray()));
                if (approxCurve.total() == 4 && isConvex) {
                    maxArea = area;
                    largestCurve = approxCurve;
                    Imgproc.drawContours(imgOr, Collections.singletonList(contour),
                            -1, new Scalar(255, 0, 255), scale);
                }
            }
        }

        if(largestCurve.toArray().length < 1 ){
            corners.add(new Point(0, 0));
            return corners;
        }
        corners.add(largestCurve.toArray()[0]);
        corners.add(largestCurve.toArray()[1]);
        corners.add(largestCurve.toArray()[2]);
        corners.add(largestCurve.toArray()[3]);

        return corners;
    }

    // Рисует номера точек
    private Mat drawPoints(ArrayList<Point> points, Mat img, Scalar color, int scale) {
        Mat outputMat = img.clone();
        for (int i = 0; i < points.size(); i++) {
            Imgproc.circle(outputMat, points.get(i), 3 * scale, color, FILLED);
            Imgproc.putText(outputMat, Integer.toString(i + 1), points.get(i), FONT_HERSHEY_SIMPLEX, scale, color, scale);
        }
        return outputMat;
    }

    // Сортирует точки
    private ArrayList<Point> reorder(ArrayList<Point> points) {
        ArrayList<Point> newPoints = new ArrayList<>();
        ArrayList<Integer> sumPoints = new ArrayList<>();
        ArrayList<Integer> subPoints = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            sumPoints.add( (int)(points.get(i).x + points.get(i).y));
            subPoints.add( (int)(points.get(i).x - points.get(i).y));
        }
        newPoints.add(points.get(getMinIndex(sumPoints))); // 0
        newPoints.add(points.get(getMaxIndex(subPoints))); // 1
        newPoints.add(points.get(getMinIndex(subPoints))); // 2
        newPoints.add(points.get(getMaxIndex(sumPoints))); // 3
        return newPoints;
    }

    // Находит индекс наибольшого элемента массива
    public static int getMaxIndex(ArrayList<Integer> list){
        int maxIndex = 0;
        int maxValue = 0;
        for(int i = 0; i < list.size(); i++){
            if(list.get(i) >= maxValue){
                maxIndex = i;
                maxValue = list.get(i);
            }
        }
        return maxIndex;
    }

    // Находит индекс наименьшего элемента массива
    public static int getMinIndex(ArrayList<Integer> list){
        int minIndex = 0;
        int minValue = 1000000;
        for(int i = 0; i < list.size(); i++){
            if(list.get(i) <= minValue){
                minIndex = i;
                minValue = list.get(i);
            }
        }
        return minIndex;
    }

    // Устанавливает погресс загрузки в ProgressDialog
    private void setProgress(ProgressDialog pd, int i, String msg){
        pd.setProgress(i); pd.setMessage(msg);
    }

    // Определяет ориентацию изображения
    private SheetFormat setOrientation(ArrayList<Point> corners) {
        double width  = (corners.get(1).x - corners.get(0).x + corners.get(3).x - corners.get(2).x)/2;
        double height = (corners.get(2).y - corners.get(0).y + corners.get(3).y - corners.get(1).y)/2;
        double aspRatio = width / height;
        if (aspRatio >= 1.0)
            return A4_H;
        else
            return A4_V;
    }

    // Трансфомирует изображение в ортогональное
    private Mat getWarp(Mat img, ArrayList<Point> src, SheetFormat A){
        int width = A.w * FACTOR_VALUE;
        int height = A.h * FACTOR_VALUE;

        Mat outputMat  = new Mat(width, height, CvType.CV_8UC4);
        ArrayList<Point> dst = new ArrayList<>();

        dst.add(new Point(0, 0));
        dst.add(new Point(width, 0));
        dst.add(new Point(0, height));
        dst.add(new Point(width, height));

        Mat matSrc = Converters.vector_Point2f_to_Mat(src);
        Mat matDst = Converters.vector_Point2f_to_Mat(dst);

        // Вычисляет преобразование перспективы из четырех пар соответствующих точек
        Mat matrix = getPerspectiveTransform(matSrc, matDst);
        // Применяет преобразование перспективы к изображению
        warpPerspective(img, outputMat, matrix, new Size(width, height));

        return outputMat ;
    }

    // Обрезает края изображения
    private Mat getCropImg(Mat img){
        Rect roi = new Rect(CROP_VALUE, CROP_VALUE,
                img.width() - (2 * CROP_VALUE), img.height() - (2 * CROP_VALUE));
        return new Mat(img, roi);
    }

    // Определяет среднюю точку контура
    private Point getMiddlePoint(MatOfPoint2f contour){
        Point p1 = contour.toArray()[0];
        Point p2 = contour.toArray()[1];
        Point p3 = contour.toArray()[2];
        Point p4 = contour.toArray()[3];

        double mX = (p1.x + p2.x + p3.x + p4.x)/4;
        double mY = (p1.y + p2.y + p3.y + p4.y)/4;
        return new Point(mX, mY);
    }
    private Point getMiddlePoint(MatOfPoint contour){
        Point p1 = contour.toArray()[0];
        Point p2 = contour.toArray()[1];
        Point p3 = contour.toArray()[2];
        Point p4 = contour.toArray()[3];

        double mX = (p1.x + p2.x + p3.x + p4.x)/4;
        double mY = (p1.y + p2.y + p3.y + p4.y)/4;
        return new Point(mX, mY);
    }

    // Находит точки отметки в контурах
    private ArrayList<Point> getTwoPoints(ArrayList<MatOfPoint> contours) {
        ArrayList<Point> twoPoints = new ArrayList<>();
        twoPoints.add(getMiddlePoint(contours.get(0)));
        twoPoints.add(getMiddlePoint(contours.get(1)));
        return twoPoints;
    }

    // Определяет контура, где находятся точки-отметки
    private ArrayList<MatOfPoint> findContoursOfMarks(Mat imgThree, int sideSheet){

        Mat image = imgThree.clone();
        ArrayList<MatOfPoint> contours = new ArrayList<>();
        ArrayList<MatOfPoint> resultContours = new ArrayList<>();

        // Находит контуры в двоичном изображении
        Imgproc.findContours(image, contours, new Mat(), RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

        for (MatOfPoint contour : contours) {
            MatOfPoint2f thisContour = new MatOfPoint2f(contour.toArray());

            // Вычисляет длину кривой или периметр замкнутого контура
            double peri = Imgproc.arcLength(thisContour, true);

            // Аппроксимирует полигональную кривую с заданной точностью
            MatOfPoint2f approxCurve = new MatOfPoint2f();
            //Параметр "epsilon" представляет максимальное расстояние между приближением
            //      контура формы входного многоугольника и исходным входным многоугольником
            Imgproc.approxPolyDP(thisContour, approxCurve, 0.05 * peri, true);

            MatOfPoint c = new MatOfPoint(approxCurve.toArray());
            if (approxCurve.total() == 4) {
                switch (sideSheet) {
                    case Constants.LEFT_SIDE_SHEET:
                        if(getMiddlePoint(approxCurve).x < SIZE_BORDER){
                            resultContours.add(c);
                        }
                        break;
                    case Constants.TOP_SIDE_SHEET:
                        if(getMiddlePoint(approxCurve).y < SIZE_BORDER){
                            resultContours.add(c);
                        }
                        break;
                    case Constants.RIGHT_SIDE_SHEET:
                        if(getMiddlePoint(approxCurve).x > image.width() - SIZE_BORDER){
                            resultContours.add(c);
                        }
                        break;
                    case Constants.BOTTOM_SIDE_SHEET:
                        if(getMiddlePoint(approxCurve).y > image.height() - SIZE_BORDER){
                            resultContours.add(c);
                        }
                        break;
                }
            }
        }
        if(resultContours.isEmpty()){resultContours.add(new MatOfPoint(new Point(0,0)));}
        return resultContours;
    }

    // Рисует стрелку
    private Mat drawArrow(Mat img, ArrayList<Point> points, int sideSheet){

        Mat imgOut = img.clone();
        imgOut = drawLine(imgOut, points.get(0), sideSheet);
        imgOut = drawLine(imgOut, points.get(1), sideSheet);

        double x1, y1, x2, y2;
        int SHIFT = 40;
        Point firstPoint = new Point();
        Point secondPoint = new Point();

        switch (sideSheet) {
            case Constants.LEFT_SIDE_SHEET:
                x1 = SHIFT;
                y1 = points.get(0).y;
                firstPoint = new Point(x1, y1);
                x2 = SHIFT;
                y2 = points.get(1).y;
                secondPoint = new Point(x2, y2);
                break;
            case Constants.TOP_SIDE_SHEET:
                x1 = points.get(0).x;
                y1 = SHIFT;
                firstPoint = new Point(x1, y1);
                x2 = points.get(1).x;
                y2 = SHIFT;
                secondPoint = new Point(x2, y2);
                break;
            case Constants.RIGHT_SIDE_SHEET:
                x1 = img.width()-SHIFT;
                y1 = points.get(0).y;
                firstPoint = new Point(x1, y1);
                x2 = img.width()-SHIFT;
                y2 = points.get(1).y;
                secondPoint = new Point(x2, y2);
                break;
            case Constants.BOTTOM_SIDE_SHEET:
                x1 = points.get(0).x;
                y1 = img.height()-SHIFT;
                firstPoint = new Point(x1, y1);
                x2 = points.get(1).x;
                y2 = img.height()-SHIFT;
                secondPoint = new Point(x2, y2);
                break;
        }
        arrowedLine(imgOut, firstPoint, secondPoint, new Scalar(5, 0, 250), 2,
                Imgproc.LINE_4, 0, 0.05);
        arrowedLine(imgOut, secondPoint, firstPoint, new Scalar(5, 0, 250), 2,
                Imgproc.LINE_4, 0, 0.05);
        return imgOut;
    }

    // Рисует линию
    private Mat drawLine(Mat img, Point point, int sideSheet){
        Mat imgOut = img.clone();
        int SIZE_LINE = 60;
        double x1, y1, x2, y2;
        Point firstPoint = new Point();
        Point secondPoint = new Point();
        switch (sideSheet) {
            case Constants.LEFT_SIDE_SHEET:
                x1 = 0;
                y1 = point.y;
                firstPoint = new Point(x1, y1);
                x2 = SIZE_LINE;
                y2 = point.y;
                secondPoint = new Point(x2, y2);
                break;
            case Constants.TOP_SIDE_SHEET:
                x1 = point.x;
                y1 = 0;
                firstPoint = new Point(x1, y1);
                x2 = point.x;
                y2 = SIZE_LINE;
                secondPoint = new Point(x2, y2);
                break;
            case Constants.RIGHT_SIDE_SHEET:
                x1 = img.width();
                y1 = point.y;
                firstPoint = new Point(x1, y1);
                x2 = img.width() - SIZE_LINE;
                y2 = point.y;
                secondPoint = new Point(x2, y2);
                break;
            case Constants.BOTTOM_SIDE_SHEET:
                x1 = point.x;
                y1 = img.height();
                firstPoint = new Point(x1, y1);
                x2 = point.x;
                y2 = img.height() - SIZE_LINE;
                secondPoint = new Point(x2, y2);
                break;
        }

        line(imgOut, firstPoint, secondPoint, new Scalar(255, 0, 0), 2);
        return imgOut;
    }

    // Расчитывает длину линии
    private double getLengthLine(Mat img, ArrayList<Point> points, int sideSheet, SheetFormat A4){

        double length;
        double realLengthSheet;
        double actualLengthSheet;

        if(sideSheet == Constants.LEFT_SIDE_SHEET || sideSheet == Constants.RIGHT_SIDE_SHEET){
            length = Math.abs(points.get(0).y - points.get(1).y);
            realLengthSheet = A4.h;
            actualLengthSheet = img.height() + CROP_VALUE * 2;
        }
        else{
            length = Math.abs(points.get(0).x - points.get(1).x);
            realLengthSheet = A4.w;
            actualLengthSheet = img.width() + CROP_VALUE * 1.5;
        }
        return (realLengthSheet * length * getIntScale(mapScale))/(actualLengthSheet*1000);
    }

    private String getStringScale(int SCALE){
        switch (SCALE){
            case Constants.MILLION:
                return Constants.S_MILLION;
            case Constants.ONE_HUNDRED_THOUSAND:
                return Constants.S_ONE_HUNDRED_THOUSAND;
            case Constants.FIFTY_THOUSAND:
                return Constants.S_FIFTY_THOUSAND;
            case Constants.TWENTY_FIVE_THOUSAND:
                return Constants.S_TWENTY_FIVE_THOUSAND;
            case Constants.TEN_THOUSAND:
                return Constants.S_TEN_THOUSAND;
            case Constants.FIVE_THOUSAND:
                return Constants.S_FIVE_THOUSAND;
            case Constants.TWO_THOUSAND:
                return Constants.S_TWO_THOUSAND;
        }
        return "ERROR";
    }

    private int getIntScale(int SCALE){
        switch (SCALE){
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
        }
        return 0;
    }

}
