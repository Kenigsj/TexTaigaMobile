package ru.textayga.mobile.model;

// готовый расчет по одному периоду, чтоб экрану было проще
public class PeriodBalance {
    public String periodKey;
    public String label;
    public DateRange range;
    public double openingBalance;
    public double planIncome;
    public double planExpense;
    public double factIncome;
    public double factExpense;
    public double projectedIncome;
    public double projectedExpense;
    public double closingBalance;

    // изменение за период: эффективные доходы минус эффективные расходы
    public double projectedDelta() {
        return projectedIncome - projectedExpense;
    }
}
