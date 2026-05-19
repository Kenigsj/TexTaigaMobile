package ru.textayga.mobile.model;

import java.time.LocalDate;

// диапазон дат, границы входят
public class DateRange {
    public final LocalDate start;
    public final LocalDate end;

    public DateRange(LocalDate start, LocalDate end) {
        this.start = start;
        this.end = end;
    }

    // смотрю, попала дата в диапазон или нет
    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    // тут смотрю пересекаются ли месяц и неделя
    public boolean intersects(DateRange other) {
        return !end.isBefore(other.start) && !other.end.isBefore(start);
    }
}
