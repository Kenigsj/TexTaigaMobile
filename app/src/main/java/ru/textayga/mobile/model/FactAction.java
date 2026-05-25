package ru.textayga.mobile.model;

// действие факта нужно только для депозитов и займов
public enum FactAction {
    NONE("Обычная операция"),
    DEPOSIT_ADD("Пополнить"),
    DEPOSIT_WITHDRAW("Снять"),
    LOAN_RECEIVE("Получить заём"),
    LOAN_REPAY("Погасить заём");

    public final String title;

    FactAction(String title) {
        this.title = title;
    }

    // если в старой базе действия нет, ставлю нормальное по типу статьи
    public static FactAction defaultFor(CategoryType type) {
        if (type == CategoryType.DEPOSIT) return DEPOSIT_ADD;
        // у кредита первым действием логичнее получить займ, а погашение пользователь выберет сам
        if (type == CategoryType.LOAN) return LOAN_RECEIVE;
        return NONE;
    }

    // json может быть старый или битый, поэтому читаю спокойно
    public static FactAction fromStorage(String raw, CategoryType type) {
        try {
            return FactAction.valueOf(raw);
        } catch (Exception ignored) {
            return defaultFor(type);
        }
    }
}
