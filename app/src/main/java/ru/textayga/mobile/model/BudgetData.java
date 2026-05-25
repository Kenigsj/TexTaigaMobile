package ru.textayga.mobile.model;

import java.util.ArrayList;

// тут лежит вся база, которую сохраняю
public class BudgetData {
    // список статей это доходы, расходы, перемещения
    public final ArrayList<Category> categories = new ArrayList<>();
    // плановые суммы по статьям и периодам
    public final ArrayList<PlanEntry> plans = new ArrayList<>();
    // факты, которые внесли во вкладке "факт"
    public final ArrayList<FactEntry> facts = new ArrayList<>();
    // типы оплаты это карта, наличные, депозит и свои варианты
    public final ArrayList<String> paymentTypes = new ArrayList<>();
    // как приложение группирует факты по умолчанию
    public PeriodKind periodKind = PeriodKind.WEEK;
    // стартовый остаток, с которого начинается расчет баланса
    public double startingBalance = 0;
    // цвет минуса храню строкой, так проще сохранять
    public String negativeBalanceColor = "red";
    // выбранная статья для быстрой сверки, если пользователь ее задал
    public String defaultDifferenceCategoryId = "";
}
