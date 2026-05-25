package ru.textayga.mobile.model;

// тип статьи, от него идёт знак суммы и цвет
public enum CategoryType {
    INCOME("Доход", "Доходы"),
    EXPENSE("Расход", "Расходы"),
    DEPOSIT("Депозит", "Депозиты"),
    LOAN("Заём", "Займы");

    public final String title;
    public final String plural;

    // русские подписи прямо в enum, чтоб не плодить switch
    CategoryType(String title, String plural) {
        this.title = title;
        this.plural = plural;
    }

    // старый transfer из базы раскладываю в новые типы аналитики
    public static CategoryType fromStorage(String raw, String name) {
        if ("TRANSFER".equals(raw)) {
            String lower = name == null ? "" : name.toLowerCase();
            return lower.contains("кредит") || lower.contains("займ") || lower.contains("заём") ? LOAN : DEPOSIT;
        }
        try {
            return CategoryType.valueOf(raw);
        } catch (Exception ignored) {
            return EXPENSE;
        }
    }
}
