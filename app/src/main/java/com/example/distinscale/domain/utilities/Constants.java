package com.example.distinscale.domain.utilities;

import java.util.Arrays;
import java.util.List;

public class Constants {
    public static final int MILLION = 0;
    public static final int ONE_HUNDRED_THOUSAND = 1;
    public static final int FIFTY_THOUSAND = 2;
    public static final int TWENTY_FIVE_THOUSAND = 3;
    public static final int TEN_THOUSAND = 4;
    public static final int FIVE_THOUSAND = 5;
    public static final int TWO_THOUSAND = 6;
    public static final int OWN_THOUSAND = 7;
    public static final int FIVE_HUNDRED = 8;
    public static final int TWO_HUNDRED = 9;
    public static final int HUNDRED = 10;
    public static final int FIFTY = 11;

    public static final int LEFT_SIDE_SHEET = 0;
    public static final int TOP_SIDE_SHEET = 1;
    public static final int RIGHT_SIDE_SHEET = 2;
    public static final int BOTTOM_SIDE_SHEET = 3;

    public static final List<String> listOfSteps = Arrays.asList(
            "Загрузка...",
            "Изображение загружено...",
            "Фильтрация Pyrmeanshift выполнена...",
            "Бинаризация изображения выполнена...",
            "Углы листа получены...",
            "Углы листа отсортированы...",
            "Трансформирование выполненно...",
            "Изображение обрезано...",
            "Фильтрация Pyrmeanshift выполнена...",
            "Бинаризация изображения выполнена...",
            "Точки найденны...",
            "Стрелка нарисована...",
            "Обработка завершена!"
    );
}
