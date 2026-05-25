package ru.textayga.mobile.model;

// режим таблицы бюджета: недели, месяцы или кварталы
public enum BalanceViewMode {
    WEEK("Недели"),
    MONTH("Месяцы"),
    QUARTER("Кварталы");

    public final String title;

    BalanceViewMode(String title) {
        this.title = title;
    }
}
