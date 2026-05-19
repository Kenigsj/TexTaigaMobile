package ru.textayga.mobile.screens;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ru.textayga.mobile.MainActivity;
import ru.textayga.mobile.domain.Money;
import ru.textayga.mobile.domain.Periods;
import ru.textayga.mobile.model.Category;
import ru.textayga.mobile.model.CategoryType;
import ru.textayga.mobile.model.DateRange;
import ru.textayga.mobile.model.FactEntry;
import ru.textayga.mobile.model.PeriodBalance;
import ru.textayga.mobile.model.PeriodChoice;
import ru.textayga.mobile.ui.NavTarget;
import ru.textayga.mobile.ui.UiKit;

// главный бюджет (таблица и детали недели)
public class BalanceScreen implements AppScreen {
    @Override
    public View render(MainActivity host) {
        // справа в шапке настройки и фильтр
        LinearLayout right = host.ui.row();
        right.setGravity(Gravity.CENTER);
        right.addView(host.ui.iconButton("⚙", v -> host.showSettings()), new LinearLayout.LayoutParams(host.ui.dp(42), host.ui.dp(42)));
        right.addView(host.ui.iconButton(host.balanceFilter == null ? "▽" : "●", v -> showBalanceFilter(host)), new LinearLayout.LayoutParams(host.ui.dp(42), host.ui.dp(42)));

        UiKit.Screen screen = host.ui.screen("бюджет", "Бюджет", "", right, NavTarget.BALANCE, host);
        screen.content.addView(monthButton(host));
        screen.content.addView(host.ui.infoBanner("Нажмите на ячейку, чтобы увидеть детали и сверить баланс"));
        // беру недели выбранного месяца, а не просто вперед
        List<PeriodChoice> periods = visiblePeriods(host);
        screen.content.addView(balanceTable(host, periods));
        screen.content.addView(legend(host));
        screen.content.addView(summaryCard(host, periods));
        screen.content.addView(mainActions(host));
        screen.content.addView(host.ui.infoBanner("Баланс рассчитывается на основе плана и внесенных фактов. Если факт есть, он заменяет план по этой статье и периоду."));
        return screen.root;
    }

    // быстрые кнопки под таблицей
    private View mainActions(MainActivity host) {
        LinearLayout actions = host.ui.row();
        actions.setPadding(0, host.ui.dp(14), 0, 0);
        actions.addView(host.ui.primaryButton("+ Внести факт", v -> openCurrentFact(host)), new LinearLayout.LayoutParams(0, host.ui.dp(58), 1));
        host.ui.gap(actions, 12, false);
        actions.addView(host.ui.outlineButton("Планировать", v -> host.showPlan()), new LinearLayout.LayoutParams(0, host.ui.dp(58), 1));
        return actions;
    }

    // кнопка месяца для таблицы
    private View monthButton(MainActivity host) {
        LinearLayout row = host.ui.row();
        row.setPadding(0, host.ui.dp(6), 0, host.ui.dp(10));
        YearMonth monthValue = selectedMonth(host);
        TextView month = host.ui.fieldButton(Periods.monthYearLabel(monthValue), "▣");
        month.setOnClickListener(v -> host.showPeriodPicker("Выберите месяц", Periods.monthChoices(36, 12), host.balancePeriodKey, choice -> {
            host.balancePeriodKey = choice.key;
            host.showBalance();
        }));
        row.addView(month, new LinearLayout.LayoutParams(host.ui.dp(150), host.ui.dp(48)));
        return row;
    }

    // на главном показываю недели месяца
    private List<PeriodChoice> visiblePeriods(MainActivity host) {
        return Periods.weeksForMonth(selectedMonth(host));
    }

    // если по какой-то причине месяц не выбран, берется текущий
    private YearMonth selectedMonth(MainActivity host) {
        if (host.balancePeriodKey != null && host.balancePeriodKey.startsWith("M:")) {
            return YearMonth.parse(host.balancePeriodKey.substring(2));
        }
        return YearMonth.now();
    }

    // фильтр прячет строки, но баланс считаю полностью
    private void showBalanceFilter(MainActivity host) {
        host.showChoiceDialog("Фильтр баланса", new String[]{"Все статьи", "Доходы", "Расходы", "Перемещения"}, label -> {
            if ("Доходы".equals(label)) host.balanceFilter = CategoryType.INCOME;
            else if ("Расходы".equals(label)) host.balanceFilter = CategoryType.EXPENSE;
            else if ("Перемещения".equals(label)) host.balanceFilter = CategoryType.TRANSFER;
            else host.balanceFilter = null;
            host.showBalance();
        });
    }

    // таблицу собираю руками через tablelayout
    private View balanceTable(MainActivity host, List<PeriodChoice> periods) {
        HorizontalScrollView horizontal = new HorizontalScrollView(host);
        horizontal.setFillViewport(true);
        horizontal.setPadding(0, host.ui.dp(12), 0, 0);

        TableLayout table = new TableLayout(host);
        table.setBackground(host.ui.bg(Color.WHITE, 8, UiKit.LINE, 1));
        Map<String, PeriodBalance> balances = balanceMap(host, periods);

        TableRow header = tableRow(host);
        header.addView(cell(host, "Статья", UiKit.INK, Typeface.BOLD, true, 126, null));
        for (PeriodChoice period : periods) {
            header.addView(cell(host, period.shortLabel, UiKit.INK, Typeface.BOLD, false, 92, v -> showPeriodDetail(host, period.key)));
        }
        table.addView(header);

        addSection(host, table, "ДОХОДЫ", CategoryType.INCOME, periods);
        addSection(host, table, "РАСХОДЫ", CategoryType.EXPENSE, periods);
        addSection(host, table, "ПЕРЕМЕЩЕНИЯ", CategoryType.TRANSFER, periods);
        addTotalRow(host, table, periods, balances);

        horizontal.addView(table, new HorizontalScrollView.LayoutParams(-2, -2));
        return horizontal;
    }

    // секция доходов/расходов/перемещений
    private void addSection(MainActivity host, TableLayout table, String title, CategoryType type, List<PeriodChoice> periods) {
        if (host.balanceFilter != null && host.balanceFilter != type) return;
        TableRow section = tableRow(host);
        section.addView(cell(host, title, host.ui.colorFor(type), Typeface.BOLD, true, 126, null));
        for (PeriodChoice period : periods) section.addView(cell(host, "", host.ui.colorFor(type), Typeface.BOLD, false, 92, v -> showPeriodDetail(host, period.key)));
        table.addView(section);

        for (Category category : host.repository.data().categories) {
            // архив не показываю, но старые факты могут остаться
            if (category.archived || category.type != type) continue;
            TableRow row = tableRow(host);
            row.addView(cell(host, category.name, UiKit.INK, Typeface.BOLD, true, 126, null));
            for (PeriodChoice period : periods) {
                row.addView(amountCell(host, category, period));
            }
            table.addView(row);
        }
    }

    // ячейка денег: факт цветной, план серый
    private View amountCell(MainActivity host, Category category, PeriodChoice period) {
        DateRange range = period.range();
        double fact = host.calculator.factForRange(category.id, range);
        double plan = host.calculator.planForRange(category.id, range);
        boolean hasFact = fact > 0;
        double value = hasFact ? fact : plan;
        int color = hasFact ? host.ui.colorFor(category.type) : UiKit.MUTED;
        String text = value > 0 ? Money.amount(value) : "—";
        return cell(host, text, color, Typeface.BOLD, false, 92, v -> showPeriodDetail(host, period.key));
    }

    // нижняя строка таблицы с итоговым балансом по каждому периоду
    private void addTotalRow(MainActivity host, TableLayout table, List<PeriodChoice> periods, Map<String, PeriodBalance> balances) {
        TableRow totals = tableRow(host);
        totals.setBackgroundColor(Color.rgb(234, 252, 242));
        totals.addView(cell(host, "ИТОГОВЫЙ БАЛАНС", UiKit.GREEN, Typeface.BOLD, true, 126, null));
        for (PeriodChoice period : periods) {
            PeriodBalance balance = balances.get(period.key);
            double amount = balance == null ? 0 : balance.closingBalance;
            totals.addView(cell(host, Money.amount(amount), amount < 0 ? host.ui.negativeColor(host.repository.data().negativeBalanceColor) : UiKit.GREEN, Typeface.BOLD, false, 92, v -> showPeriodDetail(host, period.key)));
        }
        table.addView(totals);
    }

    // делаю map, чтоб быстро брать баланс по периоду
    private Map<String, PeriodBalance> balanceMap(MainActivity host, List<PeriodChoice> periods) {
        HashMap<String, PeriodBalance> result = new HashMap<>();
        for (PeriodBalance balance : host.calculator.balanceSeries(periods)) result.put(balance.periodKey, balance);
        return result;
    }

    // легенда
    private View legend(MainActivity host) {
        LinearLayout legend = host.ui.row();
        legend.setGravity(Gravity.CENTER);
        legend.setPadding(host.ui.dp(10), host.ui.dp(10), host.ui.dp(10), host.ui.dp(10));
        legend.setBackground(host.ui.bg(Color.WHITE, 8, UiKit.LINE, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, host.ui.dp(12), 0, 0);
        legend.setLayoutParams(lp);
        legend.addView(host.ui.label("● Факт", 12, UiKit.BLUE, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        legend.addView(host.ui.label("● План", 12, UiKit.MUTED, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        legend.addView(host.ui.label("● Минус", 12, host.ui.negativeColor(host.repository.data().negativeBalanceColor), Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        return legend;
    }

    // сводная карточка под таблицей
    private View summaryCard(MainActivity host, List<PeriodChoice> periods) {
        Map<String, PeriodBalance> balances = balanceMap(host, periods);
        double endBalance = 0;
        double minBalance = Double.MAX_VALUE;
        for (PeriodChoice period : periods) {
            PeriodBalance balance = balances.get(period.key);
            if (balance == null) continue;
            endBalance = balance.closingBalance;
            minBalance = Math.min(minBalance, balance.closingBalance);
        }
        if (minBalance == Double.MAX_VALUE) minBalance = 0;
        LinearLayout card = host.ui.card();
        LinearLayout row = host.ui.row();
        row.addView(summaryColumn(host, "Расчетный остаток на конец периода", Money.rub(endBalance), endBalance < 0 ? host.ui.negativeColor(host.repository.data().negativeBalanceColor) : UiKit.GREEN), new LinearLayout.LayoutParams(0, -2, 1));
        host.ui.gap(row, 14, false);
        row.addView(summaryColumn(host, "Минимальный баланс", Money.rub(minBalance), minBalance < 0 ? host.ui.negativeColor(host.repository.data().negativeBalanceColor) : UiKit.GREEN), new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row);
        return card;
    }

    private View summaryColumn(MainActivity host, String title, String value, int color) {
        LinearLayout column = host.ui.column();
        column.addView(host.ui.label(title, 11, UiKit.MUTED, Typeface.NORMAL));
        column.addView(host.ui.label(value, 18, color, Typeface.NORMAL));
        return column;
    }

    // детали недели открываются по тапу на ячейку
    private void showPeriodDetail(MainActivity host, String periodKey) {
        DateRange range = Periods.rangeForKey(periodKey);
        PeriodChoice period = new PeriodChoice(periodKey, Periods.label(periodKey), Periods.shortLabel(periodKey), range.start, range.end);
        PeriodBalance balance = balanceFor(host, period);

        UiKit.Screen screen = host.ui.screen("бюджет", "Бюджет", period.label, host.ui.iconButton("‹", v -> host.showBalance()), NavTarget.BALANCE, host);
        LinearLayout content = screen.content;

        LinearLayout balanceCard = host.ui.card();
        balanceCard.addView(host.ui.label("Текущий баланс", 12, UiKit.MUTED, Typeface.NORMAL));
        balanceCard.addView(host.ui.label(Money.rub(balance.closingBalance), 32, UiKit.INK, Typeface.BOLD));
        balanceCard.addView(host.ui.label(Money.delta(balance.projectedDelta()) + " за неделю", 13, balance.projectedDelta() >= 0 ? UiKit.GREEN : host.ui.negativeColor(host.repository.data().negativeBalanceColor), Typeface.NORMAL));
        content.addView(balanceCard);

        LinearLayout stats = host.ui.row();
        stats.setPadding(0, host.ui.dp(12), 0, host.ui.dp(12));
        stats.addView(statCard(host, "Доходы", Money.rub(balance.projectedIncome), UiKit.GREEN), new LinearLayout.LayoutParams(0, host.ui.dp(86), 1));
        host.ui.gap(stats, 10, false);
        stats.addView(statCard(host, "Расходы", Money.rub(balance.projectedExpense), UiKit.RED), new LinearLayout.LayoutParams(0, host.ui.dp(86), 1));
        host.ui.gap(stats, 10, false);
        stats.addView(statCard(host, "Остаток", Money.rub(balance.closingBalance), UiKit.BLUE), new LinearLayout.LayoutParams(0, host.ui.dp(86), 1));
        content.addView(stats);

        LinearLayout list = host.ui.card();
        list.addView(host.ui.label("Статьи за неделю", 18, UiKit.INK, Typeface.BOLD));
        int rows = 0;
        for (Category category : host.repository.data().categories) {
            if (category.archived || category.type == CategoryType.TRANSFER) continue;
            double fact = host.calculator.factForRange(category.id, range);
            double plan = host.calculator.planForRange(category.id, range);
            if (fact <= 0 && plan <= 0) continue;
            list.addView(categoryRow(host, category, fact > 0 ? fact : plan, fact > 0));
            rows++;
        }
        if (rows == 0) list.addView(host.ui.emptyText("В этом периоде пока нет плана и факта"));
        content.addView(list);

        if (balance.closingBalance < 0) {
            content.addView(host.ui.warningCard("Возможен кассовый разрыв", period.label + " баланс может уйти в минус."));
        }

        LinearLayout actions = host.ui.row();
        actions.setPadding(0, host.ui.dp(14), 0, 0);
        actions.addView(host.ui.primaryButton("+ Внести факт", v -> openFactForPeriod(host, periodKey)), new LinearLayout.LayoutParams(0, host.ui.dp(58), 1));
        host.ui.gap(actions, 12, false);
        actions.addView(host.ui.outlineButton("Сверка", v -> showReconcile(host, periodKey)), new LinearLayout.LayoutParams(0, host.ui.dp(58), 1));
        content.addView(actions);

        host.setContentView(screen.root);
    }

    // считаю баланс выбранного периода с учетом предыдущих периодов
    private PeriodBalance balanceFor(MainActivity host, PeriodChoice period) {
        List<PeriodChoice> periods = Periods.weeksBetween(LocalDate.now(), period.end);
        for (PeriodBalance balance : host.calculator.balanceSeries(periods)) {
            if (balance.periodKey.equals(period.key)) return balance;
        }
        return host.calculator.periodBalance(period, host.calculator.balanceUpTo(period.start.minusDays(1)));
    }

    // карточка со статистикой доходов, расходов или остатка
    private LinearLayout statCard(MainActivity host, String caption, String value, int color) {
        LinearLayout card = host.ui.column();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(host.ui.dp(12), host.ui.dp(10), host.ui.dp(12), host.ui.dp(10));
        card.setBackground(host.ui.bg(Color.WHITE, 8, UiKit.LINE, 1));
        card.addView(host.ui.label(caption, 11, UiKit.MUTED, Typeface.NORMAL));
        card.addView(host.ui.label(value, 17, color, Typeface.BOLD));
        return card;
    }

    // строка статьи в деталях недели
    private View categoryRow(MainActivity host, Category category, double amount, boolean fact) {
        LinearLayout row = host.ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(10), 0, 0);
        row.addView(host.ui.categoryIcon(category), new LinearLayout.LayoutParams(host.ui.dp(36), host.ui.dp(36)));
        host.ui.gap(row, 10, false);
        row.addView(host.ui.label(category.name, 15, UiKit.INK, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(host.ui.label(Money.rub(amount), 16, fact ? UiKit.INK : UiKit.MUTED, Typeface.NORMAL));
        host.ui.gap(row, 10, false);
        row.addView(host.ui.tag(fact ? "факт" : "план", fact ? UiKit.BLUE : UiKit.MUTED), new LinearLayout.LayoutParams(host.ui.dp(58), host.ui.dp(30)));
        return row;
    }

    // сверка (вводим реальный остаток, считаем разницу)
    private void showReconcile(MainActivity host, String periodKey) {
        DateRange range = Periods.rangeForKey(periodKey);
        PeriodChoice period = new PeriodChoice(periodKey, Periods.label(periodKey), Periods.shortLabel(periodKey), range.start, range.end);
        double calculated = balanceFor(host, period).closingBalance;
        if (host.reconcileRealDraft == null || host.reconcileRealDraft.isEmpty()) {
            host.reconcileRealDraft = Money.amount(calculated);
        }
        double real = Money.parse(host.reconcileRealDraft);
        double diff = real - calculated;
        UiKit.Screen screen = host.ui.screen("бюджет", "Сверка", Periods.label(periodKey), host.ui.iconButton("‹", v -> showPeriodDetail(host, periodKey)), NavTarget.BALANCE, host);
        LinearLayout card = host.ui.card();
        card.addView(host.ui.label("Баланс периода", 18, UiKit.INK, Typeface.BOLD));
        card.addView(summaryRow(host, "Расчетный баланс", Money.rub(calculated), UiKit.INK));
        EditText realInput = host.ui.editField("", host.reconcileRealDraft, 18, false);
        realInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        card.addView(inputRow(host, "Реальный остаток", realInput));
        TextView difference = host.ui.label(Money.rub(diff), 20, diff < 0 ? host.ui.negativeColor(host.repository.data().negativeBalanceColor) : UiKit.GREEN, Typeface.BOLD);
        card.addView(summaryRow(host, "Разница", difference));
        realInput.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                host.reconcileRealDraft = editable.toString();
                double value = Money.parse(host.reconcileRealDraft);
                double updatedDiff = value - calculated;
                difference.setText(Money.rub(updatedDiff));
                difference.setTextColor(updatedDiff < 0 ? host.ui.negativeColor(host.repository.data().negativeBalanceColor) : UiKit.GREEN);
            }
        });
        screen.content.addView(card);
        screen.content.addView(host.ui.warningCard(diff == 0 ? "Расхождений нет" : "Обнаружено расхождение", diff == 0 ? "Реальный остаток совпадает с расчетным балансом." : "Разница считается как реальный остаток минус расчетный баланс."));
        LinearLayout actions = host.ui.row();
        actions.setPadding(0, host.ui.dp(12), 0, 0);
        actions.addView(host.ui.outlineButton("Исправить вручную", v -> openFactForPeriod(host, periodKey)), new LinearLayout.LayoutParams(0, host.ui.dp(60), 1));
        host.ui.gap(actions, 10, false);
        actions.addView(host.ui.primaryButton("Учесть разницу", v -> applyReconcileCorrection(host, periodKey, calculated)), new LinearLayout.LayoutParams(0, host.ui.dp(60), 1));
        screen.content.addView(actions);
        host.setContentView(screen.root);
    }

    // строка "название - значение" для карточки сверки
    private View summaryRow(MainActivity host, String left, String right, int color) {
        return summaryRow(host, left, host.ui.label(right, 20, color, Typeface.BOLD));
    }

    // то же самое, но справа может быть не текст, а поле ввода
    private View summaryRow(MainActivity host, String left, View right) {
        LinearLayout row = host.ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(10), 0, host.ui.dp(10));
        row.addView(host.ui.label(left, 16, UiKit.INK, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(right);
        return row;
    }

    // ряд с edittext для реального остатка
    private View inputRow(MainActivity host, String left, EditText input) {
        LinearLayout row = host.ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(10), 0, host.ui.dp(10));
        row.addView(host.ui.label(left, 16, UiKit.INK, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(input, new LinearLayout.LayoutParams(host.ui.dp(150), host.ui.dp(52)));
        return row;
    }

    // быстрая корректировка, факт на разницу
    private void applyReconcileCorrection(MainActivity host, String periodKey, double calculated) {
        double real = Money.parse(host.reconcileRealDraft);
        double difference = real - calculated;
        if (Math.abs(difference) < 0.01) {
            host.toast("Расхождений нет");
            return;
        }
        CategoryType type = difference < 0 ? CategoryType.EXPENSE : CategoryType.INCOME;
        Category category = correctionCategory(host, type);
        DateRange range = Periods.rangeForKey(periodKey);
        LocalDate today = LocalDate.now();
        FactEntry fact = new FactEntry();
        fact.id = UUID.randomUUID().toString();
        fact.categoryId = category.id;
        fact.categoryType = type;
        fact.periodKey = periodKey;
        fact.date = range.contains(today) ? today.toString() : range.start.toString();
        fact.amount = Math.abs(difference);
        fact.payment = "Корректировка";
        fact.comment = "Быстрая корректировка сверки баланса";
        host.repository.data().facts.add(fact);
        host.repository.save();
        host.reconcileRealDraft = "";
        host.dialogs.ready("Разница учтена", "Создана корректирующая операция на сумму " + Money.rub(Math.abs(difference)) + ".", host::showBalance);
    }

    // открываю вкладку факта сразу с нужным периодом
    private void openFactForPeriod(MainActivity host, String periodKey) {
        host.factPeriodKey = periodKey;
        host.showFact();
    }

    // для кнопки "+ внести факт" беру текущий период из настроек
    private void openCurrentFact(MainActivity host) {
        host.factPeriodKey = Periods.factKey(LocalDate.now(), host.repository.data().periodKind);
        host.showFact();
    }

    // если статьи для разницы нет - создаю
    private Category correctionCategory(MainActivity host, CategoryType type) {
        String name = type == CategoryType.EXPENSE ? "Неучтенные операции" : "Неучтенные поступления";
        for (Category category : host.repository.data().categories) {
            if (!category.archived && category.type == type && name.equals(category.name)) return category;
        }
        Category category = new Category();
        category.id = UUID.randomUUID().toString();
        category.name = name;
        category.description = "Корректировка сверки";
        category.keywords = "корректировка";
        category.type = type;
        category.archived = false;
        host.repository.data().categories.add(category);
        return category;
    }

    // маленький помощник для создания строки таблицы
    private TableRow tableRow(MainActivity host) {
        TableRow row = new TableRow(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    // стиль ячейки, чтоб таблица была одинаковая
    private TextView cell(MainActivity host, String text, int color, int style, boolean left, int widthDp, View.OnClickListener listener) {
        TextView cell = host.ui.label(text, 13, color, style);
        cell.setGravity(left ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        cell.setSingleLine(false);
        cell.setPadding(host.ui.dp(8), host.ui.dp(9), host.ui.dp(8), host.ui.dp(9));
        cell.setMinWidth(host.ui.dp(widthDp));
        cell.setMinHeight(host.ui.dp(46));
        cell.setBackground(host.ui.bg(Color.WHITE, 0, UiKit.LINE, 1));
        if (listener != null) cell.setOnClickListener(listener);
        return cell;
    }
}
