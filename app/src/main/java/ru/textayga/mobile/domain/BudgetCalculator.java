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
import ru.textayga.mobile.model.FactAction;
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
            balance += availableDelta(fact);
        }
        return balance;
    }

    // накопления считаю отдельно, потому что это не обычный доход
    public double savingsUpTo(LocalDate date) {
        double savings = 0;
        for (Category category : repository.data().categories) {
            if (!category.archived && category.type == CategoryType.DEPOSIT) savings += category.initialAmount;
        }
        for (FactEntry fact : repository.data().facts) {
            LocalDate factDate = LocalDate.parse(fact.date, Periods.ISO);
            if (factDate.isAfter(date) || fact.categoryType != CategoryType.DEPOSIT) continue;
            if (fact.action == FactAction.DEPOSIT_ADD) savings += fact.amount;
            if (fact.action == FactAction.DEPOSIT_WITHDRAW) savings -= fact.amount;
        }
        return Math.max(0, savings);
    }

    // задолженность тоже отдельная цифра, а не часть доступного баланса
    public double debtUpTo(LocalDate date) {
        double debt = 0;
        for (Category category : repository.data().categories) {
            if (!category.archived && category.type == CategoryType.LOAN) debt += category.initialAmount;
        }
        for (FactEntry fact : repository.data().facts) {
            LocalDate factDate = LocalDate.parse(fact.date, Periods.ISO);
            if (factDate.isAfter(date) || fact.categoryType != CategoryType.LOAN) continue;
            if (fact.action == FactAction.LOAN_RECEIVE) debt += fact.amount;
            if (fact.action == FactAction.LOAN_REPAY) debt -= fact.amount;
        }
        return Math.max(0, debt);
    }

    // состояние одной статьи на конец даты, для депозита нужно тянуть сумму дальше по таблице
    public double categoryStateUpTo(String categoryId, LocalDate date) {
        Category category = repository.findCategory(categoryId);
        if (category == null) return 0;
        double amount = category.initialAmount;
        for (FactEntry fact : repository.data().facts) {
            if (!fact.categoryId.equals(categoryId)) continue;
            LocalDate factDate = LocalDate.parse(fact.date, Periods.ISO);
            if (factDate.isAfter(date)) continue;
            if (category.type == CategoryType.DEPOSIT && fact.action == FactAction.DEPOSIT_ADD) amount += fact.amount;
            if (category.type == CategoryType.DEPOSIT && fact.action == FactAction.DEPOSIT_WITHDRAW) amount -= fact.amount;
            if (category.type == CategoryType.LOAN && fact.action == FactAction.LOAN_RECEIVE) amount += fact.amount;
            if (category.type == CategoryType.LOAN && fact.action == FactAction.LOAN_REPAY) amount -= fact.amount;
        }
        return Math.max(0, amount);
    }

    // состояние статьи с учетом будущего плана, чтобы кредит тянулся как депозит
    public double projectedCategoryStateUpTo(String categoryId, LocalDate date) {
        Category category = repository.findCategory(categoryId);
        if (category == null) return 0;
        if (category.type != CategoryType.DEPOSIT && category.type != CategoryType.LOAN) return categoryStateUpTo(categoryId, date);
        LocalDate today = LocalDate.now();
        if (!date.isAfter(today)) return categoryStateUpTo(categoryId, date);
        double amount = categoryStateUpTo(categoryId, Periods.startOfWeek(today).minusDays(1));
        for (PeriodChoice period : Periods.weeksBetween(today, date)) {
            DateRange range = period.range();
            if (category.type == CategoryType.DEPOSIT) {
                amount = projectedDepositState(categoryId, amount, range);
            } else {
                amount = projectedLoanState(categoryId, amount, range);
            }
            if (amount < 0) amount = 0;
        }
        return Math.max(0, amount);
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

    // для депозита и займа показываю не сумму всех действий, а чистое движение
    public double factNetForRange(String categoryId, DateRange range) {
        Category category = repository.findCategory(categoryId);
        if (category == null) return factForRange(categoryId, range);
        if (category.type == CategoryType.DEPOSIT) {
            return factByActionForCategory(categoryId, FactAction.DEPOSIT_ADD, range) - factByActionForCategory(categoryId, FactAction.DEPOSIT_WITHDRAW, range);
        }
        if (category.type == CategoryType.LOAN) {
            return factByActionForCategory(categoryId, FactAction.LOAN_RECEIVE, range) - factByActionForCategory(categoryId, FactAction.LOAN_REPAY, range);
        }
        return factForRange(categoryId, range);
    }

    // для детализации надо отдельно показать пополнение/снятие и получение/погашение
    public double factActionForRange(String categoryId, FactAction action, DateRange range) {
        return factByActionForCategory(categoryId, action, range);
    }

    // баланс периода = старт + доходы - расходы
    public PeriodBalance periodBalance(PeriodChoice period, double openingBalance) {
        return periodBalance(period, openingBalance, savingsUpTo(period.start.minusDays(1)), debtUpTo(period.start.minusDays(1)));
    }

    // тут уже считаю новый баланс с депозитами и займами
    public PeriodBalance periodBalance(PeriodChoice period, double openingBalance, double openingSavings, double openingDebt) {
        PeriodBalance balance = new PeriodBalance();
        // сразу кладу служебные поля, чтоб экран не считал сам
        balance.periodKey = period.key;
        balance.label = period.label;
        balance.range = period.range();
        balance.openingBalance = openingBalance;
        balance.openingSavings = openingSavings;
        balance.openingDebt = openingDebt;
        balance.planIncome = planForRange(CategoryType.INCOME, balance.range);
        balance.planExpense = planForRange(CategoryType.EXPENSE, balance.range);
        balance.planDeposit = planForRange(CategoryType.DEPOSIT, balance.range);
        balance.planLoan = planForRange(CategoryType.LOAN, balance.range);
        balance.factIncome = factForRange(CategoryType.INCOME, balance.range);
        balance.factExpense = factForRange(CategoryType.EXPENSE, balance.range);
        balance.factDepositAdd = factActionForRange(CategoryType.DEPOSIT, FactAction.DEPOSIT_ADD, balance.range);
        balance.factDepositWithdraw = factActionForRange(CategoryType.DEPOSIT, FactAction.DEPOSIT_WITHDRAW, balance.range);
        balance.factLoanReceive = factActionForRange(CategoryType.LOAN, FactAction.LOAN_RECEIVE, balance.range);
        balance.factLoanRepay = factActionForRange(CategoryType.LOAN, FactAction.LOAN_REPAY, balance.range);
        for (Category category : repository.data().categories) {
            if (category.archived) continue;
            applyEffective(category, balance);
        }
        balance.closingBalance = openingBalance + balance.projectedDelta();
        balance.closingSavings = Math.max(0, openingSavings + balance.projectedDepositAdd - balance.projectedDepositWithdraw);
        balance.closingDebt = Math.max(0, openingDebt + balance.projectedLoanReceive - balance.projectedLoanRepay);
        return balance;
    }

    // один период достаю из такой же цепочки, как в таблице, чтобы сверка не спорила с бюджетом
    public PeriodBalance balanceFor(PeriodChoice period, List<PeriodChoice> context) {
        for (PeriodBalance balance : balanceSeries(context)) {
            if (period.key.equals(balance.periodKey)) return balance;
        }
        ProjectedState state = projectedStateBefore(period.start);
        return periodBalance(period, state.available, state.savings, state.debt);
    }

    // цепочка периодов: следующий стартует с прошлого остатка
    public List<PeriodBalance> balanceSeries(List<PeriodChoice> periods) {
        ArrayList<PeriodChoice> sorted = new ArrayList<>(periods);
        // без сортировки баланс будет считаться криво
        sorted.sort(Comparator.comparing(choice -> choice.start));
        ArrayList<PeriodBalance> result = new ArrayList<>();
        // если период далеко в будущем, стартую уже от прогноза, а не только от фактов
        ProjectedState state = sorted.isEmpty() ? new ProjectedState() : projectedStateBefore(sorted.get(0).start);
        double opening = state.available;
        double savings = state.savings;
        double debt = state.debt;
        for (PeriodChoice period : sorted) {
            PeriodBalance balance = periodBalance(period, opening, savings, debt);
            result.add(balance);
            opening = balance.closingBalance;
            savings = balance.closingSavings;
            debt = balance.closingDebt;
        }
        return result;
    }

    // считаю состояние прямо перед периодом, чтобы будущий месяц не начинался с нуля
    private ProjectedState projectedStateBefore(LocalDate periodStart) {
        LocalDate target = periodStart.minusDays(1);
        ProjectedState state = new ProjectedState();
        if (target.isBefore(LocalDate.now())) {
            state.available = balanceUpTo(target);
            state.savings = savingsUpTo(target);
            state.debt = debtUpTo(target);
            return state;
        }
        LocalDate currentWeek = Periods.startOfWeek(LocalDate.now());
        state.available = balanceUpTo(currentWeek.minusDays(1));
        state.savings = savingsUpTo(currentWeek.minusDays(1));
        state.debt = debtUpTo(currentWeek.minusDays(1));
        for (PeriodChoice period : Periods.weeksBetween(currentWeek, target)) {
            PeriodBalance balance = periodBalance(period, state.available, state.savings, state.debt);
            state.available = balance.closingBalance;
            state.savings = balance.closingSavings;
            state.debt = balance.closingDebt;
        }
        return state;
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

    // одно место, где факт превращается в плюс или минус доступного баланса
    private double availableDelta(FactEntry fact) {
        if (fact.categoryType == CategoryType.INCOME) return fact.amount;
        if (fact.categoryType == CategoryType.EXPENSE) return -fact.amount;
        if (fact.categoryType == CategoryType.DEPOSIT && fact.action == FactAction.DEPOSIT_ADD) return -fact.amount;
        if (fact.categoryType == CategoryType.DEPOSIT && fact.action == FactAction.DEPOSIT_WITHDRAW) return fact.amount;
        if (fact.categoryType == CategoryType.LOAN && fact.action == FactAction.LOAN_RECEIVE) return fact.amount;
        if (fact.categoryType == CategoryType.LOAN && fact.action == FactAction.LOAN_REPAY) return -fact.amount;
        return 0;
    }

    // сумма фактов по конкретному действию, например только снятия депозита
    // для служебных строк доходов/расходов беру факт сразу по типу и действию
    public double factActionForRange(CategoryType type, FactAction action, DateRange range) {
        double sum = 0;
        for (FactEntry fact : repository.data().facts) {
            if (fact.categoryType != type || fact.action != action) continue;
            LocalDate factDate = LocalDate.parse(fact.date, Periods.ISO);
            if (range.contains(factDate)) sum += fact.amount;
        }
        return sum;
    }

    // главное правило аналитика: факт есть - берем факт, факта нет - берем план
    private void applyEffective(Category category, PeriodBalance balance) {
        if (category.type == CategoryType.INCOME) {
            balance.projectedIncome += effectiveForRange(category.id, balance.range);
        } else if (category.type == CategoryType.EXPENSE) {
            balance.projectedExpense += effectiveForRange(category.id, balance.range);
        } else if (category.type == CategoryType.DEPOSIT) {
            double added = factByActionForCategory(category.id, FactAction.DEPOSIT_ADD, balance.range);
            double withdrawn = factByActionForCategory(category.id, FactAction.DEPOSIT_WITHDRAW, balance.range);
            if (added > 0 || withdrawn > 0) {
                balance.projectedDepositAdd += added;
                balance.projectedDepositWithdraw += withdrawn;
            } else {
                balance.projectedDepositAdd += planForRange(category.id, balance.range);
            }
        } else if (category.type == CategoryType.LOAN) {
            double received = factByActionForCategory(category.id, FactAction.LOAN_RECEIVE, balance.range);
            double repaid = factByActionForCategory(category.id, FactAction.LOAN_REPAY, balance.range);
            if (received > 0 || repaid > 0) {
                balance.projectedLoanReceive += received;
                balance.projectedLoanRepay += repaid;
            } else {
                balance.projectedLoanRepay += planForRange(category.id, balance.range);
            }
        }
    }

    // по id и действию достаю только нужный кусок факта
    // для копилки план значит пополнение, но факт в периоде все равно главнее
    private double projectedDepositState(String categoryId, double amount, DateRange range) {
        double added = factByActionForCategory(categoryId, FactAction.DEPOSIT_ADD, range);
        double withdrawn = factByActionForCategory(categoryId, FactAction.DEPOSIT_WITHDRAW, range);
        if (added > 0 || withdrawn > 0) return amount + added - withdrawn;
        return amount + planForRange(categoryId, range);
    }

    // для кредита план трактую как погашение, потому что получение займа вносится фактом
    private double projectedLoanState(String categoryId, double amount, DateRange range) {
        double received = factByActionForCategory(categoryId, FactAction.LOAN_RECEIVE, range);
        double repaid = factByActionForCategory(categoryId, FactAction.LOAN_REPAY, range);
        if (received > 0 || repaid > 0) return amount + received - repaid;
        return amount - planForRange(categoryId, range);
    }

    private double factByActionForCategory(String categoryId, FactAction action, DateRange range) {
        double sum = 0;
        for (FactEntry fact : repository.data().facts) {
            if (!fact.categoryId.equals(categoryId) || fact.action != action) continue;
            LocalDate factDate = LocalDate.parse(fact.date, Periods.ISO);
            if (range.contains(factDate)) sum += fact.amount;
        }
        return sum;
    }

    // маленький контейнер, чтоб не возвращать три числа отдельными методами
    private static class ProjectedState {
        double available;
        double savings;
        double debt;
    }
}
