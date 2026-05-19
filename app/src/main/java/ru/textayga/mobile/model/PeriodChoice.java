package ru.textayga.mobile.model;

import java.time.LocalDate;

// один вариант периода для списка или таблицы
public class PeriodChoice {
    public final String key;
    public final String label;
    public final String shortLabel;
    public final LocalDate start;
    public final LocalDate end;

    public PeriodChoice(String key, String label, String shortLabel, LocalDate start, LocalDate end) {
        this.key = key;
        this.label = label;
        this.shortLabel = shortLabel;
        this.start = start;
        this.end = end;
    }

    // иногда удобнее передать период как daterange
    public DateRange range() {
        return new DateRange(start, end);
    }
}
