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
    public double planDeposit;
    public double planLoan;
    public double factDepositAdd;
    public double factDepositWithdraw;
    public double factLoanReceive;
    public double factLoanRepay;
    public double projectedIncome;
    public double projectedExpense;
    public double projectedDepositAdd;
    public double projectedDepositWithdraw;
    public double projectedLoanReceive;
    public double projectedLoanRepay;
    public double openingSavings;
    public double closingSavings;
    public double openingDebt;
    public double closingDebt;
    public double closingBalance;

    // изменение за период: эффективные доходы минус эффективные расходы
    public double projectedDelta() {
        return projectedIncome - projectedExpense - projectedDepositAdd + projectedDepositWithdraw + projectedLoanReceive - projectedLoanRepay;
    }
}
