package ru.textayga.mobile.domain;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

// вся мелочь с суммами лежит тут
public final class Money {
    // русская локаль для месяцев и пробелов
    private static final Locale RU = new Locale("ru", "RU");

    private Money() {
    }

    // 12500 превращаю в 12 500
    public static String amount(double value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(RU);
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0", symbols).format(Math.round(value));
    }

    // рубль добавляю отдельно
    public static String rub(double value) {
        return amount(value) + " ₽";
    }

    // дельта уже с + или -
    public static String delta(double value) {
        return (value >= 0 ? "+" : "-") + " " + rub(Math.abs(value));
    }

    // читаю сумму из поля и выкидываю лишнее
    public static double parse(String value) {
        if (value == null) return 0;
        String cleaned = value
                .replace('\u00A0', ' ')
                .replace(" ", "")
                .replace("₽", "")
                .replaceAll("[^0-9,\\.\\-+]", "")
                .trim();
        if (cleaned.isEmpty() || "—".equals(cleaned)) return 0;
        int lastComma = cleaned.lastIndexOf(',');
        int lastDot = cleaned.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            // если есть точка и запятая, пытаюсь угадать десятичный разделитель
            if (lastComma > lastDot) cleaned = cleaned.replace(".", "").replace(",", ".");
            else cleaned = cleaned.replace(",", "");
        } else if (lastComma >= 0) {
            cleaned = cleaned.replace(",", ".");
        }
        if (cleaned.indexOf('.') != cleaned.lastIndexOf('.')) {
            int last = cleaned.lastIndexOf('.');
            cleaned = cleaned.substring(0, last).replace(".", "") + cleaned.substring(last);
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException exception) {
            // если ввод кривой, просто возвращаю 0
            return 0;
        }
    }
}
