package ru.textayga.mobile.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ru.textayga.mobile.data.BudgetRepository;
import ru.textayga.mobile.model.Category;
import ru.textayga.mobile.model.CategoryType;
import ru.textayga.mobile.model.DateRange;
import ru.textayga.mobile.model.FactEntry;
import ru.textayga.mobile.model.PeriodBalance;
import ru.textayga.mobile.model.PeriodChoice;
import ru.textayga.mobile.model.PlanEntry;

// вся математика по бюджету тут
public class BudgetCalculator {
    // калькулятор берет планы/факты/статьи из репозитория
    private final BudgetRepository repository;

    // репозиторий приходит снаружи, значит расчеты всегда по текущей базе
    public BudgetCalculator(BudgetRepository repository) {
        this.repository = repository;
    }

    // сумма фактов за период и тип статьи
    public double factSum(String periodKey, CategoryType type) {
        double sum = 0;
        for (FactEntry fact : repository.data().facts) {
            if (fact.periodKey.equals(periodKey) && fact.categoryType == type) sum += fact.amount;
        }
        return sum;
    }

    // то же самое, но по одной статье
    public double factSum(String periodKey, String categoryId) {
        double sum = 0;
        for (FactEntry fact : repository.data().facts) {
            if (fact.periodKey.equals(periodKey) && fact.categoryId.equals(categoryId)) sum += fact.amount;
        }
        return sum;
    }

    // факты считаю по датам, а не по строковому ключу
    public double factForRange(String categoryId, DateRange range) {
        double sum = 0;
        for (FactEntry fact : repository.data().facts) {
            // важно брать дату операции, иначе факт размажется
            if (fact.categoryId.equals(categoryId) && range.contains(LocalDate.parse(fact.date, Periods.ISO))) sum += fact.amount;
        }
        return sum;
    }

    // версия по типу статьи, для итогов таблицы
    public double factForRange(CategoryType type, DateRange range) {
        double sum = 0;
        for (FactEntry fact : repository.data().facts) {
            if (fact.categoryType != type) continue;
            LocalDate factDate = LocalDate.parse(fact.date, Periods.ISO);
            if (range.contains(factDate)) sum += fact.amount;
        }
        return sum;
    }

    // баланс до даты: старт + доходы - расходы
    public double balanceUpTo(LocalDate date) {
        double balance = repository.data().startingBalance;
        for (FactEntry fact : repository.data().facts) {
            LocalDate factDate = LocalDate.parse(fact.date, Periods.ISO);
            if (factDate.isAfter(date)) continue;
            if (fact.categoryType == CategoryType.INCOME) balance += fact.amount;
            if (fact.categoryType == CategoryType.EXPENSE) balance -= fact.amount;
        }
        return balance;
    }

    // план в ячейке: статья + период
    public double planAmount(String categoryId, String periodKey) {
        for (PlanEntry plan : repository.data().plans) {
            if (plan.categoryId.equals(categoryId) && plan.periodKey.equals(periodKey)) return plan.amount;
        }
        return 0;
    }

    // коммент плана хранится по тому же ключу
    public String planComment(String categoryId, String periodKey) {
        for (PlanEntry plan : repository.data().plans) {
            if (plan.categoryId.equals(categoryId) && plan.periodKey.equals(periodKey)) return plan.comment;
        }
        return "";
    }

    // сумма планов по типу, для простых сводок
    public double plannedSum(String periodKey, CategoryType type) {
        double sum = 0;
        for (PlanEntry plan : repository.data().plans) {
            Category category = repository.findCategory(plan.categoryId);
            if (category != null && category.type == type && plan.periodKey.equals(periodKey)) sum += plan.amount;
        }
        return sum;
    }

    // если план пересекает период частично, считаю долю дней
    public double planForRange(String categoryId, DateRange range) {
        double sum = 0;
        for (PlanEntry plan : repository.data().plans) {
            if (plan.categoryId.equals(categoryId)) sum += proratedPlanAmount(plan, range);
        }
        return sum;
    }

    // такая же сумма планов, но сразу по типу статьи
    public double planForRange(CategoryType type, DateRange range) {
        double sum = 0;
        for (PlanEntry plan : repository.data().plans) {
            Category category = repository.findCategory(plan.categoryId);
            if (category == null || category.type != type) continue;
            sum += proratedPlanAmount(plan, range);
        }
        return sum;
    }

    // главное правило: факт заменяет план
    public double effectiveForRange(String categoryId, DateRange range) {
        double fact = factForRange(categoryId, range);
        if (fact > 0) return fact;
        return planForRange(categoryId, range);
    }

    // для ui: понять, серый план или цветной факт
    public boolean hasFactForRange(String categoryId, DateRange range) {
        return factForRange(categoryId, range) > 0;
    }

    // баланс периода = старт + доходы - расходы
    public PeriodBalance periodBalance(PeriodChoice period, double openingBalance) {
        PeriodBalance balance = new PeriodBalance();
        // сразу кладу служебные поля, чтоб экран не считал сам
        balance.periodKey = period.key;
        balance.label = period.label;
        balance.range = period.range();
        balance.openingBalance = openingBalance;
        balance.planIncome = planForRange(CategoryType.INCOME, balance.range);
        balance.planExpense = planForRange(CategoryType.EXPENSE, balance.range);
        balance.factIncome = factForRange(CategoryType.INCOME, balance.range);
        balance.factExpense = factForRange(CategoryType.EXPENSE, balance.range);
        for (Category category : repository.data().categories) {
            // перемещения не доход/расход, это просто перекладка
            if (category.archived || category.type == CategoryType.TRANSFER) continue;
            double effective = effectiveForRange(category.id, balance.range);
            if (category.type == CategoryType.INCOME) balance.projectedIncome += effective;
            if (category.type == CategoryType.EXPENSE) balance.projectedExpense += effective;
        }
        balance.closingBalance = openingBalance + balance.projectedIncome - balance.projectedExpense;
        return balance;
    }

    // цепочка периодов: следующий стартует с прошлого остатка
    public List<PeriodBalance> balanceSeries(List<PeriodChoice> periods) {
        ArrayList<PeriodChoice> sorted = new ArrayList<>(periods);
        // без сортировки баланс будет считаться криво
        sorted.sort(Comparator.comparing(choice -> choice.start));
        ArrayList<PeriodBalance> result = new ArrayList<>();
        double opening = sorted.isEmpty() ? 0 : balanceUpTo(sorted.get(0).start.minusDays(1));
        for (PeriodChoice period : sorted) {
            PeriodBalance balance = periodBalance(period, opening);
            result.add(balance);
            opening = balance.closingBalance;
        }
        return result;
    }

    // прогноз: прошлое по фактам, будущее по плану/факту
    public double projectedBalanceAt(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (!date.isAfter(today)) return balanceUpTo(date);
        List<PeriodChoice> periods = Periods.weeksBetween(today, date);
        double result = balanceUpTo(Periods.startOfWeek(today).minusDays(1));
        for (PeriodBalance balance : balanceSeries(periods)) {
            result = balance.closingBalance;
            if (!balance.range.end.isBefore(date)) break;
        }
        return result;
    }

    // сохраняю план ячейки, старый дубль убираю
    public void setPlanAmount(String categoryId, String periodKey, double amount, String comment) {
        for (int i = repository.data().plans.size() - 1; i >= 0; i--) {
            PlanEntry plan = repository.data().plans.get(i);
            if (plan.categoryId.equals(categoryId) && plan.periodKey.equals(periodKey)) {
                repository.data().plans.remove(i);
            }
        }
        if (amount > 0) {
            // нулевой план не храню, меньше мусора в базе
            PlanEntry plan = new PlanEntry();
            plan.categoryId = categoryId;
            plan.periodKey = periodKey;
            plan.amount = amount;
            plan.comment = comment == null ? "" : comment;
            repository.data().plans.add(plan);
        }
    }

    // для "все время" надо знать первый факт
    public LocalDate firstFactDate() {
        LocalDate first = LocalDate.now();
        boolean found = false;
        for (FactEntry fact : repository.data().facts) {
            LocalDate date = LocalDate.parse(fact.date, Periods.ISO);
            if (!found || date.isBefore(first)) first = date;
            found = true;
        }
        return first;
    }

    // месячный план на неделю режу по дням
    private double proratedPlanAmount(PlanEntry plan, DateRange targetRange) {
        DateRange planRange = Periods.rangeForKey(plan.periodKey);
        if (!targetRange.intersects(planRange)) return 0;
        // ищу пересечение двух дат
        LocalDate start = targetRange.start.isAfter(planRange.start) ? targetRange.start : planRange.start;
        LocalDate end = targetRange.end.isBefore(planRange.end) ? targetRange.end : planRange.end;
        long overlapDays = ChronoUnit.DAYS.between(start, end) + 1;
        long planDays = ChronoUnit.DAYS.between(planRange.start, planRange.end) + 1;
        if (overlapDays <= 0 || planDays <= 0) return 0;
        return plan.amount * overlapDays / planDays;
    }
}
