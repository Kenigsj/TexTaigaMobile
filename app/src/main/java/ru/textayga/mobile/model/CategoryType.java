package ru.textayga.mobile.model;

// тип статьи, от него идёт знак суммы и цвет
public enum CategoryType {
    INCOME("Доход", "Доходы"),
    EXPENSE("Расход", "Расходы"),
    TRANSFER("Перемещение", "Перемещения");

    public final String title;
    public final String plural;

    // русские подписи прямо в enum, чтоб не плодить switch
    CategoryType(String title, String plural) {
        this.title = title;
        this.plural = plural;
    }
}
