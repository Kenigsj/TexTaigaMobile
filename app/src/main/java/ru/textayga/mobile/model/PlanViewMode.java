package ru.textayga.mobile.model;

// режимы таблицы плана
public enum PlanViewMode {
    WEEK("W:", "Недели"),
    MONTH("M:", "Месяцы"),
    YEAR("Y:", "Годы"),
    ALL("*", "Всё время");

    public final String prefix;
    public final String title;

    // prefix помогает быстро понять, подходит ли выбранный periodkey к текущему режиму
    PlanViewMode(String prefix, String title) {
        this.prefix = prefix;
        this.title = title;
    }
}
