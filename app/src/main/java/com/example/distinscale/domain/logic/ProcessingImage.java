package com.example.distinscale.domain.logic;

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

import com.example.distinscale.domain.utilities.Constants;
import com.example.distinscale.domain.models.PreprocessParameters;
import com.example.distinscale.domain.models.SheetFormat;

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

@SuppressWarnings("FieldCanBeLocal")
public class ProcessingImage {

    public ArrayList<Mat> matArrayList = new ArrayList<>();
    public ArrayList<MatOfPoint> contoursOfMarks;
    public ArrayList<Point> corners;
    public double length;

    private SheetFormat A4;
    private Mat imgOr, imgCrop;
    private ArrayList<Point> twoPoints, newCorners;
    private final int mapScale, sideSheet;
    private final int CROP_VALUE = 10;  //Размер обрезания краёв листа
    private final int FACTOR_VALUE = 5; //Коэфициент преобразовании изображения в ортогональное
    private final int SIZE_BORDER = 30; //Размер отступа от края листа бумаги для поиска рисок


    /** 1)	Загрузка исходных данных */
    public ProcessingImage(int mapScale, int sideSheet) {
        this.mapScale = mapScale;
        this.sideSheet = sideSheet;
    }

    public void setImageStep(String filename) {
        imgOr = Imgcodecs.imread(filename);
        matArrayList.add(0, imgOr);
    }

    /** 2)	Предобработка исходного изображения */
    public void pyrMeanShiftFilteringFirstStep() {
        Mat imgMSF = imgOr.clone();
        Imgproc.pyrMeanShiftFiltering(imgMSF, imgMSF, 15, 50);
        matArrayList.add(0, imgMSF);
    }

    public void preProcessingFirstStep(int C_t2) {
        PreprocessParameters mainPreProc = new PreprocessParameters(
                3, 2, 1, 10, C_t2, 17, 2, 1);
        Mat imgThree = preProcessing(matArrayList.get(0), mainPreProc);
        matArrayList.add(0, imgThree);
    }

    /** 3)	Получение изображения листа */
    public void getCornersStep() {
        Mat imgPoints = imgOr.clone();
        corners = getCorners(matArrayList.get(0), imgPoints, 5);
        imgPoints = drawPoints(corners, imgPoints, new Scalar(0, 255, 255), 10);
        matArrayList.add(0, imgPoints);
    }

    public void reorderStep() {
        newCorners = reorder(corners);
        Mat imgReorderPoints = drawPoints(newCorners, imgOr, new Scalar(0, 255, 255), 10);
        matArrayList.add(0, imgReorderPoints);
    }

    public void warpImageStep() {
        A4 = setOrientation(newCorners);
        Mat imgWarm = getWarp(imgOr, newCorners, A4);
        matArrayList.add(0, imgWarm);
    }

    public void cropStep() {
        imgCrop = getCropImg(matArrayList.get(0));
        matArrayList.add(0, imgCrop);
    }

    /** 4)	Предобработка изображения листа */
    public void pyrMeanShiftFilteringSecondStep() {
        Mat imgCropMSF = imgCrop.clone();
        Imgproc.pyrMeanShiftFiltering(imgCropMSF, imgCropMSF, 5, 5);
        matArrayList.add(0, imgCropMSF);
    }

    public void preProcessingSecondStep() {
        PreprocessParameters secondPreProc = new PreprocessParameters(
                3, 1, 0, 10, 100, 3, 3, 2);
        Mat imgThreeSecond = preProcessing(matArrayList.get(0), secondPreProc);
        matArrayList.add(0, imgThreeSecond);
    }

    /** 5)	Получение отметок с листа */
    public void findContoursOfMarksStep() {
        contoursOfMarks = findContoursOfMarks(matArrayList.get(0), sideSheet);
    }

    public void selectPointStep() {
        Mat imgTwoPoints = imgCrop.clone();
        Imgproc.drawContours(imgTwoPoints,
                contoursOfMarks, -1, new Scalar(0, 0, 255), 2);
        twoPoints = getTwoPoints(contoursOfMarks);
        imgTwoPoints = drawPoints(twoPoints, imgTwoPoints, new Scalar(0, 255, 255), 1);
        matArrayList.add(0, imgTwoPoints);
    }

    /** 6)	Расчет длины отрезка */
    public void getLengthLineStep() {
        length = getLengthLine(imgCrop, twoPoints, sideSheet, A4);
    }

    public void drawArrowStep() {
        Mat imgArrow = drawArrow(imgCrop, twoPoints, sideSheet);
        matArrayList.add(0, imgArrow);
    }


    /** Вспомогательные функции */
    // Выполняет бинаризацию изображения
    private Mat preProcessing(Mat srcImg, PreprocessParameters pp) {
        Mat img = srcImg.clone();

        // Размывает изображение с помощью гауссовского фильтра
        Imgproc.GaussianBlur(img, img, new Size(pp.GB_size, pp.GB_size), pp.GB_sX, pp.GB_sY);
        // Преобразует изображение из одного цветового пространства в другое
        Imgproc.cvtColor(img, img, COLOR_BGR2GRAY);
        // Размывает изображение с помощью гауссовского фильтра
        Imgproc.GaussianBlur(img, img, new Size(pp.GB_size, pp.GB_size), pp.GB_sX, pp.GB_sY);
        // Находит ребра в изображении с помощью алгоритма Кэнни
        Imgproc.Canny(img, img, pp.C_t1, pp.C_t2, 3);
        // Возвращает структурирующий элемент заданного размера и формы для морфологических операций
        Mat kernel = Imgproc.getStructuringElement(MORPH_RECT, new Size(pp.k_size, pp.k_size));
        // Расширяет светлые области и сужает темные
        Imgproc.dilate(img, img, kernel, new Point(-1, -1), pp.d_i);
        // Расширяет темные области и сужает светлые
        Imgproc.erode(img, img, kernel, new Point(-1, -1), pp.e_i);

        return img;
    }

    // Определяет точки углов листа
    @SuppressWarnings("SameParameterValue")
    private ArrayList<Point> getCorners(Mat imgThree, Mat imgOr, int scale) {
        double maxArea = 0;
        Mat image = imgThree.clone();
        ArrayList<Point> corners = new ArrayList<>();
        MatOfPoint2f largestCurve = new MatOfPoint2f();
        ArrayList<MatOfPoint> contours = new ArrayList<>();
        double minArcLength = imgThree.height() + imgThree.width();
        Imgproc.findContours(image, contours, new Mat(), RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        if (contours.size() < 1) { corners.add(new Point(0, 0)); return corners; }
        for (MatOfPoint contour : contours) { double area = Imgproc.contourArea(contour);
            if (area > maxArea) { MatOfPoint2f thisContour = new MatOfPoint2f(contour.toArray());
                double peri = Imgproc.arcLength(thisContour, true);
                if (peri >= minArcLength) { MatOfPoint2f approxCurve = new MatOfPoint2f();
                    Imgproc.approxPolyDP(thisContour, approxCurve, 0.05 * peri, true);
                    boolean isConvex = isContourConvex(new MatOfPoint(approxCurve.toArray()));
                    if (approxCurve.total() == 4 && isConvex) { maxArea = area;
                        largestCurve = approxCurve;
                        Imgproc.drawContours(imgOr, Collections.singletonList(contour),
                                -1, new Scalar(255, 0, 255), scale);
                    }
                }
            }
        }
        if (largestCurve.toArray().length < 1) {corners.add(new Point(0, 0)); return corners;}
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
            sumPoints.add((int) (points.get(i).x + points.get(i).y));
            subPoints.add((int) (points.get(i).x - points.get(i).y));
        }
        newPoints.add(points.get(getMinIndex(sumPoints))); // 0
        newPoints.add(points.get(getMaxIndex(subPoints))); // 1
        newPoints.add(points.get(getMinIndex(subPoints))); // 2
        newPoints.add(points.get(getMaxIndex(sumPoints))); // 3
        return newPoints;
    }

    // Находит индекс наибольшого элемента массива
    public static int getMaxIndex(ArrayList<Integer> list) {
        int maxIndex = 0;
        int maxValue = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) >= maxValue) {
                maxIndex = i;
                maxValue = list.get(i);
            }
        }
        return maxIndex;
    }

    // Находит индекс наименьшего элемента массива
    public static int getMinIndex(ArrayList<Integer> list) {
        int minIndex = 0;
        int minValue = 1000000;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) <= minValue) {
                minIndex = i;
                minValue = list.get(i);
            }
        }
        return minIndex;
    }

    // Определяет ориентацию изображения
    private SheetFormat setOrientation(ArrayList<Point> corners) {
        double width = (corners.get(1).x - corners.get(0).x + corners.get(3).x - corners.get(2).x) / 2;
        double height = (corners.get(2).y - corners.get(0).y + corners.get(3).y - corners.get(1).y) / 2;
        double aspRatio = width / height;
        if (aspRatio >= 1.0)
            return new SheetFormat(297, 210);
        else
            return new SheetFormat(210, 297);
    }

    // Трансфомирует изображение в ортогональное
    private Mat getWarp(Mat img, ArrayList<Point> src, SheetFormat A) {
        int width = A.w * FACTOR_VALUE;
        int height = A.h * FACTOR_VALUE;
        Mat outputMat = new Mat(width, height, CvType.CV_8UC4);
        ArrayList<Point> dst = new ArrayList<>();
        dst.add(new Point(0, 0));
        dst.add(new Point(width, 0));
        dst.add(new Point(0, height));
        dst.add(new Point(width, height));
        Mat matSrc = Converters.vector_Point2f_to_Mat(src);
        Mat matDst = Converters.vector_Point2f_to_Mat(dst);
        Mat matrix = getPerspectiveTransform(matSrc, matDst);
        warpPerspective(img, outputMat, matrix, new Size(width, height));
        return outputMat;
    }

    // Обрезает края изображения
    private Mat getCropImg(Mat img) {
        Rect roi = new Rect(CROP_VALUE, CROP_VALUE,
                img.width() - (2 * CROP_VALUE), img.height() - (2 * CROP_VALUE));
        return new Mat(img, roi);
    }

    // Определяет среднюю точку контура
    private Point getMiddlePoint(MatOfPoint2f contour) {
        Point[] points = contour.toArray();
        double sumX = 0, sumY = 0;
        for (Point p : points) {
            sumX += p.x;
        }
        for (Point p : points) {
            sumY += p.y;
        }

        double mX = sumX / points.length;
        double mY = sumY / points.length;
        return new Point(mX, mY);
    }

    private Point getMiddlePoint(MatOfPoint contour) {
        Point[] points = contour.toArray();
        double sumX = 0, sumY = 0;
        for (Point p : points) { sumX += p.x; }
        for (Point p : points) { sumY += p.y; }
        double mX = sumX / points.length;
        double mY = sumY / points.length;
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
    private ArrayList<MatOfPoint> findContoursOfMarks(Mat imgThree, int sideSheet) {
        Mat image = imgThree.clone();
        ArrayList<MatOfPoint> contours = new ArrayList<>();
        ArrayList<MatOfPoint> resultContours = new ArrayList<>();
        Imgproc.findContours(image, contours, new Mat(), RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        for (MatOfPoint contour : contours) {
            MatOfPoint2f thisContour = new MatOfPoint2f(contour.toArray());
            double peri = Imgproc.arcLength(thisContour, true);
            MatOfPoint2f approxCurve = new MatOfPoint2f();
            Imgproc.approxPolyDP(thisContour, approxCurve, 0.05 * peri, true);
            MatOfPoint c = new MatOfPoint(approxCurve.toArray());
            switch (sideSheet) {
                case Constants.LEFT_SIDE_SHEET:
                    if (getMiddlePoint(approxCurve).x < SIZE_BORDER) {
                        resultContours.add(c); } break;
                case Constants.TOP_SIDE_SHEET:
                    if (getMiddlePoint(approxCurve).y < SIZE_BORDER) {
                        resultContours.add(c); } break;
                case Constants.RIGHT_SIDE_SHEET:
                    if (getMiddlePoint(approxCurve).x > image.width() - SIZE_BORDER) {
                        resultContours.add(c); } break;
                case Constants.BOTTOM_SIDE_SHEET:
                    if (getMiddlePoint(approxCurve).y > image.height() - SIZE_BORDER) {
                        resultContours.add(c); } break;
            }
        }
        if (resultContours.isEmpty()) { resultContours.add(new MatOfPoint(new Point(0, 0)));}
        return resultContours;
    }

    // Рисует стрелку
    private Mat drawArrow(Mat img, ArrayList<Point> points, int sideSheet) {

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
                x1 = img.width() - SHIFT;
                y1 = points.get(0).y;
                firstPoint = new Point(x1, y1);
                x2 = img.width() - SHIFT;
                y2 = points.get(1).y;
                secondPoint = new Point(x2, y2);
                break;
            case Constants.BOTTOM_SIDE_SHEET:
                x1 = points.get(0).x;
                y1 = img.height() - SHIFT;
                firstPoint = new Point(x1, y1);
                x2 = points.get(1).x;
                y2 = img.height() - SHIFT;
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
    private Mat drawLine(Mat img, Point point, int sideSheet) {
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
    private double getLengthLine(Mat img, ArrayList<Point> points, int sideSheet, SheetFormat A4) {

        double length;
        double realLengthSheet;
        double actualLengthSheet;

        if (sideSheet == Constants.LEFT_SIDE_SHEET || sideSheet == Constants.RIGHT_SIDE_SHEET) {
            length = Math.abs(points.get(0).y - points.get(1).y);
            realLengthSheet = A4.h;
            actualLengthSheet = img.height() + CROP_VALUE * 1.8;
        } else {
            length = Math.abs(points.get(0).x - points.get(1).x);
            realLengthSheet = A4.w;
            actualLengthSheet = img.width() + CROP_VALUE * 1.8;
        }
        return (realLengthSheet * length * mapScale) / (actualLengthSheet * 1000);
    }

    /*
    // Определяет контура, где находятся точки-отметки
    private ArrayList<MatOfPoint> findContoursOfMarks(Mat imgThree, int sideSheet) {

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
            switch (sideSheet) {
                case Constants.LEFT_SIDE_SHEET:
                    if (getMiddlePoint(approxCurve).x < SIZE_BORDER) {
                        resultContours.add(c);
                    }
                    break;
                case Constants.TOP_SIDE_SHEET:
                    if (getMiddlePoint(approxCurve).y < SIZE_BORDER) {
                        resultContours.add(c);
                    }
                    break;
                case Constants.RIGHT_SIDE_SHEET:
                    if (getMiddlePoint(approxCurve).x > image.width() - SIZE_BORDER) {
                        resultContours.add(c);
                    }
                    break;
                case Constants.BOTTOM_SIDE_SHEET:
                    if (getMiddlePoint(approxCurve).y > image.height() - SIZE_BORDER) {
                        resultContours.add(c);
                    }
                    break;
            }
        }
        if (resultContours.isEmpty()) {
            resultContours.add(new MatOfPoint(new Point(0, 0)));
        }
        return resultContours;
    }

    // Определяет точки углов листа
    @SuppressWarnings("SameParameterValue")
    private ArrayList<Point> getCorners(Mat imgThree, Mat imgOr, int scale) {

        double maxArea = 0;
        Mat image = imgThree.clone();
        ArrayList<Point> corners = new ArrayList<>();
        MatOfPoint2f largestCurve = new MatOfPoint2f();
        ArrayList<MatOfPoint> contours = new ArrayList<>();
        double minArcLength = imgThree.height() + imgThree.width();

        // Находит контуры в двоичном изображении
        Imgproc.findContours(image, contours, new Mat(), RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

        if (contours.size() < 1) {
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
                if (peri >= minArcLength) {
                    // Аппроксимирует полигональную кривую с заданной точностью
                    MatOfPoint2f approxCurve = new MatOfPoint2f();
                    //      Параметр "epsilon" представляет максимальное расстояние между приближением
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
        }

        if (largestCurve.toArray().length < 1) {
            corners.add(new Point(0, 0));
            return corners;
        }
        corners.add(largestCurve.toArray()[0]);
        corners.add(largestCurve.toArray()[1]);
        corners.add(largestCurve.toArray()[2]);
        corners.add(largestCurve.toArray()[3]);

        return corners;
    }
// Трансфомирует изображение в ортогональное
private Mat getWarp(Mat img, ArrayList<Point> src, SheetFormat A) {
    int width = A.w * FACTOR_VALUE;
    int height = A.h * FACTOR_VALUE;

    Mat outputMat = new Mat(width, height, CvType.CV_8UC4);
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

    return outputMat;
}
*/
}
