package ru.textayga.mobile.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import ru.textayga.mobile.model.DateRange;
import ru.textayga.mobile.model.ExportRangeMode;
import ru.textayga.mobile.model.PeriodChoice;
import ru.textayga.mobile.model.PeriodKind;
import ru.textayga.mobile.model.PlanViewMode;

// даты держу тут, чтоб недели не разъехались
public final class Periods {
    // русская локаль для месяцев и пробелов
    public static final Locale RU = new Locale("ru", "RU");
    // iso-ключ удобно хранить и сортировать
    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter DATE_FULL = DateTimeFormatter.ofPattern("d MMMM yyyy", RU);

    private Periods() {
    }

    // важно: неделя начинается с понедельника
    public static LocalDate startOfWeek(LocalDate date) {
        int daysFromMonday = date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        return date.minusDays(daysFromMonday);
    }

    // ключ факта строю по выбранной настройке периода
    public static String factKey(LocalDate date, PeriodKind kind) {
        return kind == PeriodKind.WEEK ? weekKey(startOfWeek(date)) : monthKey(YearMonth.from(date));
    }

    // ключ для таблицы: неделя, месяц или год
    public static String keyFor(LocalDate date, PlanViewMode mode) {
        if (mode == PlanViewMode.MONTH) return monthKey(YearMonth.from(date));
        if (mode == PlanViewMode.YEAR) return yearKey(date.getYear());
        return weekKey(startOfWeek(date));
    }

    // насильно двигаю дату на понедельник
    public static String weekKey(LocalDate start) {
        return "W:" + startOfWeek(start);
    }

    public static String monthKey(YearMonth month) {
        return "M:" + month;
    }

    public static String yearKey(int year) {
        return "Y:" + year;
    }

    // квартал храню отдельным ключом для бюджета
    public static String quarterKey(int year, int quarter) {
        return "Q:" + year + "-" + quarter;
    }

    // по ключу восстанавливаю даты
    public static DateRange rangeForKey(String key) {
        if (key.startsWith("W:")) {
            LocalDate start = startOfWeek(LocalDate.parse(key.substring(2), ISO));
            return new DateRange(start, start.plusDays(6));
        }
        if (key.startsWith("M:")) {
            YearMonth month = YearMonth.parse(key.substring(2));
            return new DateRange(month.atDay(1), month.atEndOfMonth());
        }
        if (key.startsWith("Y:")) {
            int year = Integer.parseInt(key.substring(2));
            return new DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        }
        if (key.startsWith("Q:")) {
            String[] parts = key.substring(2).split("-");
            int year = Integer.parseInt(parts[0]);
            int quarter = Integer.parseInt(parts[1]);
            int firstMonth = (quarter - 1) * 3 + 1;
            YearMonth start = YearMonth.of(year, firstMonth);
            YearMonth end = start.plusMonths(2);
            return new DateRange(start.atDay(1), end.atEndOfMonth());
        }
        LocalDate today = LocalDate.now();
        return new DateRange(today, today);
    }

    // нормальная подпись для кнопок и заголовков
    public static String label(String key) {
        if (key.startsWith("W:")) return weekLabel(startOfWeek(LocalDate.parse(key.substring(2), ISO)));
        if (key.startsWith("M:")) return monthName(YearMonth.parse(key.substring(2)));
        if (key.startsWith("Y:")) return key.substring(2);
        if (key.startsWith("Q:")) {
            String[] parts = key.substring(2).split("-");
            return parts[1] + " квартал " + parts[0];
        }
        return key;
    }

    // короткая подпись, чтоб влезала в таблицу
    public static String shortLabel(String key) {
        if (key.startsWith("W:")) {
            LocalDate start = startOfWeek(LocalDate.parse(key.substring(2), ISO));
            return shortDate(start) + "\n" + shortDate(start.plusDays(6));
        }
        return label(key);
    }

    // месяцы года для режима бюджета "Месяцы"
    public static List<PeriodChoice> monthsForYear(int year) {
        ArrayList<PeriodChoice> choices = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            addChoice(choices, monthKey(YearMonth.of(year, month)));
        }
        return choices;
    }

    // кварталы года для режима бюджета "Кварталы"
    public static List<PeriodChoice> quartersForYear(int year) {
        ArrayList<PeriodChoice> choices = new ArrayList<>();
        for (int quarter = 1; quarter <= 4; quarter++) {
            addChoice(choices, quarterKey(year, quarter));
        }
        return choices;
    }

    // нормально отображение недели
    public static String weekLabel(LocalDate date) {
        LocalDate start = startOfWeek(date);
        LocalDate end = start.plusDays(6);
        return start.getDayOfMonth() + "-" + end.getDayOfMonth() + " " + monthShort(end);
    }

    public static String shortDate(LocalDate date) {
        return date.getDayOfMonth() + " " + monthShort(date);
    }

    public static String fullDate(LocalDate date) {
        return date.format(DATE_FULL);
    }

    public static String monthName(YearMonth month) {
        String name = month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL", RU));
        return name.substring(0, 1).toUpperCase(RU) + name.substring(1);
    }

    public static String monthYearLabel(YearMonth month) {
        return monthName(month) + " " + month.getYear();
    }

    public static String monthShort(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("MMM", RU)).replace(".", "");
    }

    // в факте можно выбрать прошлое и текущую неделю
    public static List<PeriodChoice> factChoices(PeriodKind kind) {
        ArrayList<PeriodChoice> choices = new ArrayList<>();
        LocalDate today = LocalDate.now();
        if (kind == PeriodKind.WEEK) {
            LocalDate date = startOfWeek(today).minusWeeks(52);
            LocalDate current = startOfWeek(today);
            while (!date.isAfter(current)) {
                addChoice(choices, weekKey(date));
                date = date.plusWeeks(1);
            }
        } else {
            YearMonth month = YearMonth.from(today).minusMonths(24);
            YearMonth current = YearMonth.from(today);
            while (!month.isAfter(current)) {
                addChoice(choices, monthKey(month));
                month = month.plusMonths(1);
            }
        }
        choices.sort(Comparator.comparing((PeriodChoice p) -> p.start).reversed());
        return choices;
    }

    // в плане нужны будущие периоды тоже
    public static List<PeriodChoice> planChoices(PlanViewMode mode) {
        ArrayList<PeriodChoice> choices = new ArrayList<>();
        LocalDate today = LocalDate.now();
        if (mode == PlanViewMode.WEEK) {
            LocalDate date = startOfWeek(today).minusWeeks(52);
            LocalDate end = startOfWeek(today).plusWeeks(52);
            while (!date.isAfter(end)) {
                addChoice(choices, weekKey(date));
                date = date.plusWeeks(1);
            }
        } else if (mode == PlanViewMode.MONTH) {
            YearMonth month = YearMonth.from(today).minusMonths(24);
            YearMonth end = YearMonth.from(today).plusMonths(12);
            while (!month.isAfter(end)) {
                addChoice(choices, monthKey(month));
                month = month.plusMonths(1);
            }
        } else if (mode == PlanViewMode.YEAR) {
            for (int year = today.getYear() - 5; year <= today.getYear() + 3; year++) addChoice(choices, yearKey(year));
        }
        choices.sort(Comparator.comparing((PeriodChoice p) -> p.start).reversed());
        return choices;
    }

    // месяцы для главной таблицы бюджета
    public static List<PeriodChoice> monthChoices(int monthsBack, int monthsAhead) {
        ArrayList<PeriodChoice> choices = new ArrayList<>();
        YearMonth month = YearMonth.from(LocalDate.now()).minusMonths(monthsBack);
        YearMonth end = YearMonth.from(LocalDate.now()).plusMonths(monthsAhead);
        while (!month.isAfter(end)) {
            DateRange range = rangeForKey(monthKey(month));
            String label = monthYearLabel(month);
            choices.add(new PeriodChoice(monthKey(month), label, label, range.start, range.end));
            month = month.plusMonths(1);
        }
        choices.sort(Comparator.comparing((PeriodChoice p) -> p.start).reversed());
        return choices;
    }

    // список недель между датами, на нем держатся расчеты
    public static List<PeriodChoice> weeksBetween(LocalDate from, LocalDate to) {
        ArrayList<PeriodChoice> choices = new ArrayList<>();
        LocalDate date = startOfWeek(from);
        LocalDate end = startOfWeek(to);
        while (!date.isAfter(end)) {
            addChoice(choices, weekKey(date));
            date = date.plusWeeks(1);
        }
        return choices;
    }

    // месяц раскладываю на недели
    public static List<PeriodChoice> weeksForMonth(YearMonth month) {
        ArrayList<PeriodChoice> choices = new ArrayList<>();
        LocalDate date = startOfWeek(month.atDay(1));
        LocalDate end = startOfWeek(month.atEndOfMonth());
        while (!date.isAfter(end)) {
            addChoice(choices, weekKey(date));
            date = date.plusWeeks(1);
        }
        return choices;
    }

    // прогноз от текущей недели вперед
    public static List<PeriodChoice> forecastWeeks(int weeksAhead) {
        ArrayList<PeriodChoice> choices = new ArrayList<>();
        LocalDate date = startOfWeek(LocalDate.now());
        for (int i = 0; i < weeksAhead; i++) {
            addChoice(choices, weekKey(date));
            date = date.plusWeeks(1);
        }
        return choices;
    }

    // "все время" начинаю с первого факта
    public static List<PeriodChoice> allTimeWeeks(LocalDate firstDate) {
        ArrayList<PeriodChoice> choices = new ArrayList<>();
        LocalDate date = startOfWeek(firstDate);
        LocalDate current = startOfWeek(LocalDate.now());
        while (!date.isAfter(current)) {
            addChoice(choices, weekKey(date));
            date = date.plusWeeks(1);
        }
        return choices;
    }

    // диапазоны экспорта считаю назад от сегодня
    public static DateRange exportRange(ExportRangeMode mode, LocalDate customStart, LocalDate customEnd) {
        LocalDate today = LocalDate.now();
        if (mode == ExportRangeMode.WEEK) return new DateRange(today.minusDays(6), today);
        if (mode == ExportRangeMode.MONTH) return new DateRange(today.minusMonths(1).plusDays(1), today);
        if (mode == ExportRangeMode.QUARTER) return new DateRange(today.minusMonths(3).plusDays(1), today);
        if (mode == ExportRangeMode.YEAR) return new DateRange(today.minusYears(1).plusDays(1), today);
        return new DateRange(customStart, customEnd);
    }

    // чтобы не копировать создание periodchoice
    private static void addChoice(List<PeriodChoice> choices, String key) {
        DateRange range = rangeForKey(key);
        choices.add(new PeriodChoice(key, label(key), shortLabel(key), range.start, range.end));
    }
}
