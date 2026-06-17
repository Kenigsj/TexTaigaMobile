package ru.textayga.mobile.screens;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
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
import ru.textayga.mobile.model.BalanceViewMode;
import ru.textayga.mobile.model.DateRange;
import ru.textayga.mobile.model.FactAction;
import ru.textayga.mobile.model.FactEntry;
import ru.textayga.mobile.model.PeriodBalance;
import ru.textayga.mobile.model.PeriodChoice;
import ru.textayga.mobile.ui.NavTarget;
import ru.textayga.mobile.ui.UiKit;

// главный бюджет (таблица и детали недели)
public class BalanceScreen implements AppScreen {
    @Override
    public View render(MainActivity host) {
        // старый фильтр статей в бюджете больше не нужен
        host.balanceFilter = null;
        // справа в шапке настройки и фильтр
        LinearLayout right = host.ui.row();
        right.setGravity(Gravity.CENTER);
        right.addView(host.ui.iconButton("⚙", v -> host.showSettings()), new LinearLayout.LayoutParams(host.ui.dp(42), host.ui.dp(42)));
        right.addView(host.ui.iconButton(host.balanceFilter == null ? "▽" : "●", v -> showBalanceFilter(host)), new LinearLayout.LayoutParams(host.ui.dp(42), host.ui.dp(42)));

        UiKit.Screen screen = host.ui.screen("бюджет", "Бюджет", "", right, NavTarget.BALANCE, host);
        screen.content.addView(monthButton(host));
        screen.content.addView(balanceOverview(host));
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
        actions.addView(host.ui.outlineButton("Сверка", v -> openCurrentReconcile(host)), new LinearLayout.LayoutParams(0, host.ui.dp(58), 1));
        return actions;
    }

    // кнопка месяца для таблицы
    private View monthButton(MainActivity host) {
        LinearLayout row = host.ui.row();
        row.setPadding(0, host.ui.dp(6), 0, host.ui.dp(10));
        YearMonth monthValue = selectedMonth(host);
        TextView month = host.ui.fieldButton(host.balanceViewMode == BalanceViewMode.WEEK ? Periods.monthYearLabel(monthValue) : String.valueOf(monthValue.getYear()), "▣");
        month.setOnClickListener(v -> {
            if (host.balanceViewMode == BalanceViewMode.WEEK) {
                host.showPeriodPicker("Выберите месяц", Periods.monthChoices(36, 12), host.balancePeriodKey, choice -> {
                    host.balancePeriodKey = choice.key;
                    host.showBalance();
                });
            } else {
                showYearPicker(host, monthValue.getYear());
            }
        });
        row.addView(month, new LinearLayout.LayoutParams(host.ui.dp(150), host.ui.dp(48)));
        return row;
    }

    // на главном показываю недели месяца
    private List<PeriodChoice> visiblePeriods(MainActivity host) {
        YearMonth selected = selectedMonth(host);
        if (host.balanceViewMode == BalanceViewMode.MONTH) return Periods.monthsForYear(selected.getYear());
        if (host.balanceViewMode == BalanceViewMode.QUARTER) return Periods.quartersForYear(selected.getYear());
        return Periods.weeksForMonth(selected);
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
        host.showChoiceDialog("Вид таблицы", new String[]{"Недели", "Месяцы", "Кварталы"}, label -> {
            if ("Недели".equals(label)) host.balanceViewMode = BalanceViewMode.WEEK;
            if ("Месяцы".equals(label)) host.balanceViewMode = BalanceViewMode.MONTH;
            if ("Кварталы".equals(label)) host.balanceViewMode = BalanceViewMode.QUARTER;
            host.showBalance();
        });
    }

    // год выбираю обычным списком, чтобы не городить отдельный экран
    private void showYearPicker(MainActivity host, int currentYear) {
        String[] years = new String[7];
        for (int i = 0; i < years.length; i++) years[i] = String.valueOf(currentYear - 3 + i);
        host.showChoiceDialog("Выберите год", years, label -> {
            int year = Integer.parseInt(label);
            host.balancePeriodKey = Periods.monthKey(YearMonth.of(year, selectedMonth(host).getMonth()));
            host.showBalance();
        });
    }

    // три главных показателя из новой аналитики
    private View balanceOverview(MainActivity host) {
        LocalDate today = LocalDate.now();
        LinearLayout row = host.ui.row();
        row.setPadding(0, host.ui.dp(2), 0, host.ui.dp(2));
        row.addView(metricCard(host, "Доступный баланс", Money.rub(host.calculator.balanceUpTo(today)), UiKit.BLUE, v -> openCurrentPeriodDetail(host)), new LinearLayout.LayoutParams(0, host.ui.dp(92), 1));
        host.ui.gap(row, 10, false);
        row.addView(metricCard(host, "Накопления", Money.rub(host.calculator.savingsUpTo(today)), UiKit.GREEN, v -> showTypeDetails(host, CategoryType.DEPOSIT, "Накопления")), new LinearLayout.LayoutParams(0, host.ui.dp(92), 1));
        host.ui.gap(row, 10, false);
        row.addView(metricCard(host, "Задолженность", Money.rub(host.calculator.debtUpTo(today)), UiKit.RED, v -> showTypeDetails(host, CategoryType.LOAN, "Задолженность")), new LinearLayout.LayoutParams(0, host.ui.dp(92), 1));
        return row;
    }

    // маленькая карточка в верхней сводке
    private View metricCard(MainActivity host, String title, String value, int color, View.OnClickListener listener) {
        LinearLayout card = host.ui.column();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(host.ui.dp(10), 0, host.ui.dp(10), 0);
        card.setBackground(host.ui.bg(Color.WHITE, 8, UiKit.LINE, 1));
        TextView titleView = host.ui.label(title, 11, UiKit.MUTED, Typeface.NORMAL);
        titleView.setSingleLine(false);
        card.addView(titleView);
        TextView valueView = host.ui.label(value, 15, color, Typeface.BOLD);
        valueView.setSingleLine(false);
        card.addView(valueView);
        card.setOnClickListener(listener);
        return card;
    }

    // раскрываю накопления или долги простым списком
    private void showTypeDetails(MainActivity host, CategoryType type, String title) {
        StringBuilder body = new StringBuilder();
        for (Category category : host.repository.data().categories) {
            if (category.archived || category.type != type) continue;
            double amount = categoryState(host, category);
            if (amount <= 0) continue;
            if (body.length() > 0) body.append("\n");
            body.append(category.name).append(": ").append(Money.rub(amount));
        }
        host.dialogs.ready(title, body.length() == 0 ? "Активных статей пока нет." : body.toString(), null);
    }

    // текущее состояние одной депозитной или долговой статьи
    private double categoryState(MainActivity host, Category category) {
        double amount = category.initialAmount;
        for (FactEntry fact : host.repository.data().facts) {
            if (!fact.categoryId.equals(category.id)) continue;
            if (category.type == CategoryType.DEPOSIT && fact.action == FactAction.DEPOSIT_ADD) amount += fact.amount;
            if (category.type == CategoryType.DEPOSIT && fact.action == FactAction.DEPOSIT_WITHDRAW) amount -= fact.amount;
            if (category.type == CategoryType.LOAN && fact.action == FactAction.LOAN_RECEIVE) amount += fact.amount;
            if (category.type == CategoryType.LOAN && fact.action == FactAction.LOAN_REPAY) amount -= fact.amount;
        }
        return Math.max(0, amount);
    }

    // таблицу собираю из двух частей, слева закрепленная "Статья", справа прокрутка по периодам
    private View balanceTable(MainActivity host, List<PeriodChoice> periods) {
        // чуть ужал таблицу, чтобы на телефоне помещалось больше строк сразу
        int firstColumnWidth = 142;
        int periodWidth = 90;
        int headerHeight = 58;
        int sectionHeight = 38;
        int rowHeight = 46;

        LinearLayout tableFrame = new LinearLayout(host);
        tableFrame.setOrientation(LinearLayout.HORIZONTAL);
        tableFrame.setBackground(host.ui.bg(Color.WHITE, 8, UiKit.LINE, 1));
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(-1, -2);
        frameLp.setMargins(0, host.ui.dp(12), 0, 0);
        tableFrame.setLayoutParams(frameLp);

        TableLayout fixedTable = new TableLayout(host);
        TableLayout scrollTable = new TableLayout(host);
        Map<String, PeriodBalance> balances = balanceMap(host, periods);

        TableRow fixedHeader = tableRow(host);
        fixedHeader.addView(cell(host, "Статья", UiKit.INK, Typeface.BOLD, true, firstColumnWidth, headerHeight, null));
        fixedTable.addView(fixedHeader);

        TableRow scrollHeader = tableRow(host);
        for (PeriodChoice period : periods) {
            scrollHeader.addView(cell(host, period.shortLabel, UiKit.INK, Typeface.BOLD, false, periodWidth, headerHeight, v -> showPeriodDetail(host, period.key)));
        }
        scrollTable.addView(scrollHeader);

        addSection(host, fixedTable, scrollTable, "ДОХОДЫ", CategoryType.INCOME, periods, firstColumnWidth, periodWidth, sectionHeight, rowHeight);
        // баланс ставлю сразу после доходов, так удобнее читать таблицу сверху вниз
        addTotalRow(host, fixedTable, scrollTable, periods, balances, firstColumnWidth, periodWidth, rowHeight);
        addSection(host, fixedTable, scrollTable, "РАСХОДЫ", CategoryType.EXPENSE, periods, firstColumnWidth, periodWidth, sectionHeight, rowHeight);
        addSection(host, fixedTable, scrollTable, "ДЕПОЗИТЫ", CategoryType.DEPOSIT, periods, firstColumnWidth, periodWidth, sectionHeight, rowHeight);
        addSection(host, fixedTable, scrollTable, "ЗАЙМЫ", CategoryType.LOAN, periods, firstColumnWidth, periodWidth, sectionHeight, rowHeight);

        HorizontalScrollView horizontal = new HorizontalScrollView(host);
        horizontal.setFillViewport(true);
        horizontal.setHorizontalScrollBarEnabled(true);
        horizontal.addView(scrollTable, new HorizontalScrollView.LayoutParams(-2, -2));

        tableFrame.addView(fixedTable, new LinearLayout.LayoutParams(host.ui.dp(firstColumnWidth), -2));
        tableFrame.addView(horizontal, new LinearLayout.LayoutParams(0, -2, 1));
        return tableFrame;
    }

    // секция доходов/расходов/перемещений
    private void addSection(MainActivity host, TableLayout fixedTable, TableLayout scrollTable, String title, CategoryType type, List<PeriodChoice> periods, int firstColumnWidth, int periodWidth, int sectionHeight, int rowHeight) {
        if (host.balanceFilter != null && host.balanceFilter != type) return;
        boolean collapsible = type == CategoryType.DEPOSIT || type == CategoryType.LOAN;
        boolean expanded = sectionExpanded(host, type);
        TableRow fixedSection = tableRow(host);
        // у депозитов и займов строка заголовка сама сворачивает список
        fixedSection.addView(cell(host, sectionTitle(title, collapsible, expanded), host.ui.colorFor(type), Typeface.BOLD, true, firstColumnWidth, sectionHeight, collapsible ? v -> toggleSection(host, type) : null));
        fixedTable.addView(fixedSection);

        TableRow scrollSection = tableRow(host);
        for (PeriodChoice period : periods) scrollSection.addView(cell(host, "", host.ui.colorFor(type), Typeface.BOLD, false, periodWidth, sectionHeight, null));
        scrollTable.addView(scrollSection);
        if (collapsible && !expanded) return;

        for (Category category : host.repository.data().categories) {
            // архив не показываю, но старые факты могут остаться
            if (category.archived || category.type != type) continue;
            TableRow fixedRow = tableRow(host);
            fixedRow.addView(cell(host, category.name, UiKit.INK, Typeface.BOLD, true, firstColumnWidth, rowHeight, null));
            fixedTable.addView(fixedRow);

            TableRow scrollRow = tableRow(host);
            for (PeriodChoice period : periods) {
                scrollRow.addView(amountCell(host, category, period, periodWidth, rowHeight));
            }
            scrollTable.addView(scrollRow);
        }

        // спецоперации показываю в доходах/расходах, но баланс их уже считает отдельно
        if (type == CategoryType.INCOME) {
            addSyntheticActionRow(host, fixedTable, scrollTable, "Взятие займа", CategoryType.LOAN, FactAction.LOAN_RECEIVE, periods, firstColumnWidth, periodWidth, rowHeight, UiKit.GREEN);
            addSyntheticActionRow(host, fixedTable, scrollTable, "Снятие с депозита", CategoryType.DEPOSIT, FactAction.DEPOSIT_WITHDRAW, periods, firstColumnWidth, periodWidth, rowHeight, UiKit.GREEN);
        }
        if (type == CategoryType.EXPENSE) {
            addSyntheticActionRow(host, fixedTable, scrollTable, "Пополнение депозита", CategoryType.DEPOSIT, FactAction.DEPOSIT_ADD, periods, firstColumnWidth, periodWidth, rowHeight, UiKit.RED);
            addSyntheticActionRow(host, fixedTable, scrollTable, "Погашение кредита", CategoryType.LOAN, FactAction.LOAN_REPAY, periods, firstColumnWidth, periodWidth, rowHeight, UiKit.RED);
        }
    }

    // это не настоящая статья, а строка-пояснение, чтобы было видно куда попал депозит или займ
    private void addSyntheticActionRow(MainActivity host, TableLayout fixedTable, TableLayout scrollTable, String title, CategoryType sourceType, FactAction action, List<PeriodChoice> periods, int firstColumnWidth, int periodWidth, int rowHeight, int color) {
        if (!hasSyntheticActionValue(host, sourceType, action, periods)) return;
        TableRow fixedRow = tableRow(host);
        fixedRow.addView(cell(host, title, UiKit.INK, Typeface.BOLD, true, firstColumnWidth, rowHeight, null));
        fixedTable.addView(fixedRow);

        TableRow scrollRow = tableRow(host);
        for (PeriodChoice period : periods) {
            DateRange range = period.range();
            double amount = syntheticActionAmount(host, sourceType, action, range);
            boolean hasFact = syntheticActionFactAmount(host, sourceType, action, range) > 0;
            scrollRow.addView(cell(host, amount > 0 ? Money.amount(amount) : "—", hasFact ? color : UiKit.MUTED, Typeface.BOLD, false, periodWidth, rowHeight, v -> showPeriodDetail(host, period.key)));
        }
        scrollTable.addView(scrollRow);
    }

    // перед добавлением строки проверяю все периоды, иначе будет куча пустых строк
    private boolean hasSyntheticActionValue(MainActivity host, CategoryType sourceType, FactAction action, List<PeriodChoice> periods) {
        for (PeriodChoice period : periods) {
            if (syntheticActionAmount(host, sourceType, action, period.range()) > 0) return true;
        }
        return false;
    }

    // тут собираю факт плюс план для тех спецстатей, где план означает будущий расход
    private double syntheticActionAmount(MainActivity host, CategoryType sourceType, FactAction action, DateRange range) {
        double sum = 0;
        for (Category category : host.repository.data().categories) {
            if (category.archived || category.type != sourceType) continue;
            double fact = host.calculator.factActionForRange(category.id, action, range);
            double plan = syntheticPlanPart(host, category, action, range);
            sum += fact + plan;
        }
        return sum;
    }

    // план для депозита это пополнение, а для займа это погашение
    private double syntheticPlanPart(MainActivity host, Category category, FactAction action, DateRange range) {
        if (action != FactAction.DEPOSIT_ADD && action != FactAction.LOAN_RECEIVE && action != FactAction.LOAN_REPAY) return 0;
        // план заменяю только фактом такого же действия, чтобы взятие займа не гасило план погашения
        boolean hasSameFact = host.calculator.factActionForRange(category.id, action, range) > 0;
        return hasSameFact ? 0 : host.calculator.planForRange(category.id, range, action);
    }

    // отдельно достаю только факт, чтобы серый план не красить как реальную операцию
    private double syntheticActionFactAmount(MainActivity host, CategoryType sourceType, FactAction action, DateRange range) {
        double sum = 0;
        for (Category category : host.repository.data().categories) {
            if (category.archived || category.type != sourceType) continue;
            sum += host.calculator.factActionForRange(category.id, action, range);
        }
        return sum;
    }

    // состояние сворачивания держу в activity, чтобы поворот экрана не сбивал таблицу внутри экрана
    private boolean sectionExpanded(MainActivity host, CategoryType type) {
        if (type == CategoryType.DEPOSIT) return host.balanceDepositsExpanded;
        if (type == CategoryType.LOAN) return host.balanceLoansExpanded;
        return true;
    }

    // тап по заголовку депозитов/займов меняет только эту секцию
    private void toggleSection(MainActivity host, CategoryType type) {
        if (type == CategoryType.DEPOSIT) host.balanceDepositsExpanded = !host.balanceDepositsExpanded;
        if (type == CategoryType.LOAN) host.balanceLoansExpanded = !host.balanceLoansExpanded;
        host.showBalance();
    }

    // стрелку ставлю текстом, чтобы не тащить отдельную маленькую иконку
    private String sectionTitle(String title, boolean collapsible, boolean expanded) {
        if (!collapsible) return title;
        return title + (expanded ? "  ˄" : "  ˅");
    }

    // ячейка денег, факт цветной, план серый
    private View amountCell(MainActivity host, Category category, PeriodChoice period, int periodWidth, int rowHeight) {
        DateRange range = period.range();
        double fact = host.calculator.factNetForRange(category.id, range);
        double plan = host.calculator.planForRange(category.id, range);
        boolean hasFact = host.calculator.hasFactForRange(category.id, range);
        if (category.type == CategoryType.DEPOSIT || category.type == CategoryType.LOAN) {
            // депозит и кредит тут показываю как остаток статьи на конец периода
            double state = host.calculator.projectedCategoryStateUpTo(category.id, range.end);
            double before = host.calculator.projectedCategoryStateUpTo(category.id, range.start.minusDays(1));
            if (state > 0) {
                // если состояние появилось только из плана, крашу серым, факт уже будет цветной
                double realState = host.calculator.categoryStateUpTo(category.id, range.end);
                int stateColor = realState > 0 ? host.ui.colorFor(category.type) : UiKit.MUTED;
                return cell(host, Money.amount(state), stateColor, Typeface.BOLD, false, periodWidth, rowHeight, v -> showPeriodDetail(host, period.key, category.id));
            }
            if (category.type == CategoryType.LOAN && !hasFact && before <= 0) {
                return cell(host, "—", UiKit.MUTED, Typeface.BOLD, false, periodWidth, rowHeight, v -> showPeriodDetail(host, period.key, category.id));
            }
        }
        double value = hasFact ? fact : plan;
        int color = hasFact ? host.ui.colorFor(category.type) : UiKit.MUTED;
        String text = amountText(category, value, hasFact);
        return cell(host, text, color, Typeface.BOLD, false, periodWidth, rowHeight, v -> showPeriodDetail(host, period.key, category.id));
    }

    // в обычных статьях знак не нужен, а у депозитов/займов без знака непонятно куда ушли деньги
    private String amountText(Category category, double value, boolean fact) {
        if (Math.abs(value) < 0.01) return fact ? "0" : "—";
        if (fact && category.type == CategoryType.DEPOSIT) {
            return (value >= 0 ? "+" : "-") + Money.amount(Math.abs(value));
        }
        if (fact && category.type == CategoryType.LOAN) return Money.amount(Math.abs(value));
        return Money.amount(value);
    }

    // строка таблицы с итоговым балансом по каждому периоду
    private void addTotalRow(MainActivity host, TableLayout fixedTable, TableLayout scrollTable, List<PeriodChoice> periods, Map<String, PeriodBalance> balances, int firstColumnWidth, int periodWidth, int rowHeight) {
        TableRow fixedTotals = tableRow(host);
        fixedTotals.setBackgroundColor(Color.rgb(234, 252, 242));
        fixedTotals.addView(cell(host, "БАЛАНС", UiKit.GREEN, Typeface.BOLD, true, firstColumnWidth, rowHeight, null));
        fixedTable.addView(fixedTotals);

        TableRow scrollTotals = tableRow(host);
        scrollTotals.setBackgroundColor(Color.rgb(234, 252, 242));
        for (PeriodChoice period : periods) {
            PeriodBalance balance = balances.get(period.key);
            double amount = balance == null ? 0 : balance.closingBalance;
            scrollTotals.addView(cell(host, Money.amount(amount), amount < 0 ? host.ui.negativeColor(host.repository.data().negativeBalanceColor) : UiKit.GREEN, Typeface.BOLD, false, periodWidth, rowHeight, v -> showPeriodDetail(host, period.key)));
        }
        scrollTable.addView(scrollTotals);
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
        showPeriodDetail(host, periodKey, null);
    }

    // если пришли из конкретной ячейки, запоминаю статью для кнопки "внести факт"
    private void showPeriodDetail(MainActivity host, String periodKey, String selectedCategoryId) {
        DateRange range = Periods.rangeForKey(periodKey);
        PeriodChoice period = new PeriodChoice(periodKey, Periods.label(periodKey), Periods.shortLabel(periodKey), range.start, range.end);
        PeriodBalance balance = balanceFor(host, period);

        UiKit.Screen screen = host.ui.screen("бюджет", "Бюджет", period.label, host.ui.iconButton("‹", v -> host.showBalance()), NavTarget.BALANCE, host);
        LinearLayout content = screen.content;

        LinearLayout balanceCard = host.ui.card();
        balanceCard.addView(host.ui.label("Текущий баланс", 12, UiKit.MUTED, Typeface.NORMAL));
        balanceCard.addView(host.ui.label(Money.rub(balance.closingBalance), 32, UiKit.INK, Typeface.BOLD));
        balanceCard.addView(host.ui.label(Money.delta(balance.projectedDelta()) + " за период", 13, balance.projectedDelta() >= 0 ? UiKit.GREEN : host.ui.negativeColor(host.repository.data().negativeBalanceColor), Typeface.NORMAL));
        content.addView(balanceCard);

        LinearLayout stats = host.ui.row();
        stats.setPadding(0, host.ui.dp(12), 0, host.ui.dp(12));
        // в верхних карточках тоже различаю серый план и цветной факт
        double visibleIncome = balance.projectedIncome + balance.projectedLoanReceive;
        // в карточке доходов показываю заем как поступление, сам баланс его отдельно уже считает
        stats.addView(statCard(host, "Доходы", Money.rub(visibleIncome), balance.factIncome > 0 || balance.factLoanReceive > 0 ? UiKit.GREEN : UiKit.MUTED), new LinearLayout.LayoutParams(0, host.ui.dp(86), 1));
        host.ui.gap(stats, 10, false);
        // баланс ставлю посередине, так глазами проще сравнить его с доходами и расходами
        stats.addView(statCard(host, "Баланс", Money.rub(balance.closingBalance), UiKit.BLUE), new LinearLayout.LayoutParams(0, host.ui.dp(86), 1));
        host.ui.gap(stats, 10, false);
        stats.addView(statCard(host, "Расходы", Money.rub(balance.projectedExpense), balance.factExpense > 0 ? UiKit.RED : UiKit.MUTED), new LinearLayout.LayoutParams(0, host.ui.dp(86), 1));
        content.addView(stats);
        LinearLayout extendedStats = host.ui.row();
        extendedStats.setPadding(0, 0, 0, host.ui.dp(12));
        extendedStats.addView(statCard(host, "Депозиты", Money.rub(balance.projectedDepositAdd - balance.projectedDepositWithdraw), balance.factDepositAdd > 0 || balance.factDepositWithdraw > 0 ? UiKit.BLUE : UiKit.MUTED), new LinearLayout.LayoutParams(0, host.ui.dp(86), 1));
        host.ui.gap(extendedStats, 10, false);
        extendedStats.addView(statCard(host, "Займы", Money.rub(balance.projectedLoanReceive - balance.projectedLoanRepay), balance.factLoanReceive > 0 || balance.factLoanRepay > 0 ? UiKit.PURPLE : UiKit.MUTED), new LinearLayout.LayoutParams(0, host.ui.dp(86), 1));
        host.ui.gap(extendedStats, 10, false);
        extendedStats.addView(statCard(host, "Итог периода", Money.rub(balance.projectedDelta()), balance.projectedDelta() >= 0 ? UiKit.GREEN : host.ui.negativeColor(host.repository.data().negativeBalanceColor)), new LinearLayout.LayoutParams(0, host.ui.dp(86), 1));
        content.addView(extendedStats);

        LinearLayout list = host.ui.card();
        list.addView(host.ui.label("Детализация периода", 18, UiKit.INK, Typeface.BOLD));
        int rows = 0;
        // раскладываю период по разделам из аналитики, а не одним общим списком
        rows += addDetailSection(host, list, "Доходы", CategoryType.INCOME, range);
        rows += addDetailSection(host, list, "Расходы", CategoryType.EXPENSE, range);
        rows += addDetailSection(host, list, "Депозиты", CategoryType.DEPOSIT, range);
        rows += addDetailSection(host, list, "Займы", CategoryType.LOAN, range);
        if (rows == 0) list.addView(host.ui.emptyText("В этом периоде пока нет плана и факта"));
        content.addView(list);

        if (balance.closingBalance < 0) {
            content.addView(host.ui.warningCard("Возможен кассовый разрыв", period.label + " баланс может уйти в минус."));
        }

        LinearLayout actions = host.ui.row();
        actions.setPadding(0, host.ui.dp(14), 0, 0);
        actions.addView(host.ui.primaryButton("+ Внести факт", v -> openFactForPeriod(host, periodKey, selectedCategoryId)), new LinearLayout.LayoutParams(0, host.ui.dp(58), 1));
        host.ui.gap(actions, 12, false);
        actions.addView(host.ui.outlineButton("Сверка", v -> showReconcile(host, periodKey)), new LinearLayout.LayoutParams(0, host.ui.dp(58), 1));
        content.addView(actions);

        host.showTemporaryScreen(NavTarget.BALANCE, screen.root);
    }

    // считаю баланс выбранного периода с учетом предыдущих периодов
    private PeriodBalance balanceFor(MainActivity host, PeriodChoice period) {
        // детализация может быть неделей, месяцем или кварталом
        return host.calculator.balanceFor(period, detailContext(period));
    }

    // беру соседние периоды, чтобы отдельная карточка считалась как та же строка в таблице
    private List<PeriodChoice> detailContext(PeriodChoice period) {
        if (period.key.startsWith("W:")) return Periods.weeksForMonth(YearMonth.from(period.start));
        if (period.key.startsWith("M:")) return Periods.monthsForYear(period.start.getYear());
        if (period.key.startsWith("Q:")) return Periods.quartersForYear(period.start.getYear());
        return java.util.Collections.singletonList(period);
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

    // секция детализации: план и факт рядом, чтобы было понятно что заменяет что
    private int addDetailSection(MainActivity host, LinearLayout parent, String title, CategoryType type, DateRange range) {
        int rows = 0;
        LinearLayout rowsBox = host.ui.column();
        for (Category category : host.repository.data().categories) {
            if (category.archived || category.type != type) continue;
            double plan = host.calculator.planForRange(category.id, range);
            double fact = host.calculator.factForRange(category.id, range);
            // если кредит уже закрыт, старый повторяющийся план дальше не показываю
            if (type == CategoryType.LOAN && fact <= 0 && plan > 0 && host.calculator.projectedCategoryStateUpTo(category.id, range.start.minusDays(1)) <= 0) continue;
            if (plan <= 0 && fact <= 0) continue;
            rowsBox.addView(categoryRow(host, category, plan, fact, range));
            rows++;
        }
        if (type == CategoryType.INCOME) {
            double loanReceive = host.calculator.factActionForRange(CategoryType.LOAN, FactAction.LOAN_RECEIVE, range);
            if (loanReceive > 0) {
                // взятие займа показываю доходной строкой, хотя в базе это все равно статья займа
                rowsBox.addView(syntheticDetailRow(host, "Взятие займа", "Факт: " + Money.rub(loanReceive), Money.rub(loanReceive), UiKit.GREEN));
                rows++;
            }
        }
        if (rows == 0) return 0;
        // пустые разделы в деталях не вывожу, иначе экран превращается в список "нет записей"
        TextView section = host.ui.label(title, 16, host.ui.colorFor(type), Typeface.BOLD);
        section.setPadding(0, host.ui.dp(14), 0, host.ui.dp(4));
        parent.addView(section);
        parent.addView(rowsBox);
        return rows;
    }

    // строка для служебного дохода/расхода, которому не нужна отдельная категория в настройках
    private View syntheticDetailRow(MainActivity host, String title, String subtitle, String amount, int color) {
        LinearLayout row = host.ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(10), 0, 0);
        row.addView(host.ui.circleIcon("₽", color), new LinearLayout.LayoutParams(host.ui.dp(36), host.ui.dp(36)));
        host.ui.gap(row, 10, false);
        LinearLayout texts = host.ui.column();
        texts.addView(host.ui.label(title, 15, UiKit.INK, Typeface.BOLD));
        texts.addView(host.ui.label(subtitle, 12, color, Typeface.NORMAL));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(host.ui.label(amount, 15, UiKit.INK, Typeface.BOLD));
        host.ui.gap(row, 10, false);
        row.addView(host.ui.tag("факт", UiKit.BLUE), new LinearLayout.LayoutParams(host.ui.dp(58), host.ui.dp(30)));
        return row;
    }

    // строка статьи в деталях периода
    private View categoryRow(MainActivity host, Category category, double plan, double fact, DateRange range) {
        LinearLayout row = host.ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(10), 0, 0);
        row.addView(host.ui.categoryIcon(category), new LinearLayout.LayoutParams(host.ui.dp(36), host.ui.dp(36)));
        host.ui.gap(row, 10, false);
        LinearLayout texts = host.ui.column();
        texts.addView(host.ui.label(category.name, 15, UiKit.INK, Typeface.BOLD));
        texts.addView(host.ui.label("План: " + (plan > 0 ? Money.rub(plan) : "—"), 12, UiKit.MUTED, Typeface.NORMAL));
        texts.addView(host.ui.label("Факт: " + factText(host, category, fact, range), 12, fact > 0 ? host.ui.colorFor(category.type) : UiKit.MUTED, Typeface.NORMAL));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        // плановую сумму справа специально глушу, чтобы не путалась с фактом
        TextView detailAmount = host.ui.label(detailAmountText(host, category, plan, fact, range), 15, fact > 0 ? UiKit.INK : UiKit.MUTED, Typeface.BOLD);
        if (fact <= 0) detailAmount.setAlpha(0.72f);
        row.addView(detailAmount);
        host.ui.gap(row, 10, false);
        row.addView(host.ui.tag(fact > 0 ? "факт" : "план", fact > 0 ? UiKit.BLUE : UiKit.MUTED), new LinearLayout.LayoutParams(host.ui.dp(58), host.ui.dp(30)));
        return row;
    }

    // справа в деталях показываю итог по статье, у спецстатей с нормальным знаком
    private String detailAmountText(MainActivity host, Category category, double plan, double fact, DateRange range) {
        if (fact > 0 && (category.type == CategoryType.DEPOSIT || category.type == CategoryType.LOAN)) {
            double net = host.calculator.factNetForRange(category.id, range);
            // у кредита плюс справа мешает, тут нужно просто число операции
            if (category.type == CategoryType.LOAN) return Money.rub(Math.abs(net));
            return Money.delta(net);
        }
        if (fact > 0) return Money.rub(fact);
        return plan > 0 ? Money.rub(plan) : "—";
    }

    // для депозитов и займов в факте важен не только размер, но и действие
    private String factText(MainActivity host, Category category, double fact, DateRange range) {
        if (fact <= 0) return "—";
        if (category.type == CategoryType.DEPOSIT) {
            double added = host.calculator.factActionForRange(category.id, FactAction.DEPOSIT_ADD, range);
            double withdrawn = host.calculator.factActionForRange(category.id, FactAction.DEPOSIT_WITHDRAW, range);
            return "+" + Money.rub(added) + " / -" + Money.rub(withdrawn);
        }
        if (category.type == CategoryType.LOAN) {
            double received = host.calculator.factActionForRange(category.id, FactAction.LOAN_RECEIVE, range);
            double repaid = host.calculator.factActionForRange(category.id, FactAction.LOAN_REPAY, range);
            // по займу показываю две суммы без плюса, чтобы карточка не выглядела как доходная строка
            return Money.rub(received) + " / " + Money.rub(repaid);
        }
        return Money.rub(fact);
    }

    // сверка (вводим реальный остаток, считаем разницу)
    private void showReconcile(MainActivity host, String periodKey) {
        DateRange range = Periods.rangeForKey(periodKey);
        PeriodChoice period = new PeriodChoice(periodKey, Periods.label(periodKey), Periods.shortLabel(periodKey), range.start, range.end);
        double calculated = balanceFor(host, period).closingBalance;
        boolean hasRealInput = host.reconcileRealDraft != null && !host.reconcileRealDraft.trim().isEmpty();
        // если поле пустое, не подставляю расчетный баланс, иначе минусовой баланс выглядел как "все ок"
        double real = hasRealInput ? Money.parse(host.reconcileRealDraft) : 0;
        double diff = real - calculated;
        UiKit.Screen screen = host.ui.screen("бюджет", "Сверка", Periods.label(periodKey), host.ui.iconButton("‹", v -> showPeriodDetail(host, periodKey)), NavTarget.BALANCE, host);
        LinearLayout card = host.ui.card();
        card.addView(host.ui.label("Баланс периода", 18, UiKit.INK, Typeface.BOLD));
        card.addView(summaryRow(host, "Расчетный баланс", Money.rub(calculated), UiKit.INK));
        EditText realInput = host.ui.editField("", hasRealInput ? host.reconcileRealDraft : "", 18, false);
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
        boolean hasDifference = Math.abs(diff) >= 0.01;
        String reconcileTitle = hasDifference ? "Обнаружено расхождение" : "Расхождений нет";
        String reconcileText = hasDifference ? "Разница считается как реальный остаток минус расчетный баланс." : "Реальный остаток совпадает с расчетным балансом.";
        screen.content.addView(host.ui.warningCard(reconcileTitle, reconcileText));
        LinearLayout actions = host.ui.row();
        actions.setPadding(0, host.ui.dp(12), 0, 0);
        actions.addView(host.ui.outlineButton("Исправить вручную", v -> openFactForPeriod(host, periodKey, null)), new LinearLayout.LayoutParams(0, host.ui.dp(60), 1));
        host.ui.gap(actions, 10, false);
        actions.addView(host.ui.primaryButton("Учесть разницу", v -> applyReconcileCorrection(host, periodKey, calculated)), new LinearLayout.LayoutParams(0, host.ui.dp(60), 1));
        screen.content.addView(actions);
        host.showTemporaryScreen(NavTarget.BALANCE, screen.root);
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
        if (host.reconcileRealDraft == null || host.reconcileRealDraft.trim().isEmpty()) {
            host.toast("Введите реальный остаток");
            return;
        }
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
    private void openFactForPeriod(MainActivity host, String periodKey, String categoryId) {
        host.setFactPeriod(periodKey);
        // если детализация открыта из ячейки статьи, в факте сразу ставлю эту статью
        if (categoryId != null && host.repository.findCategory(categoryId) != null) {
            host.factCategoryId = categoryId;
            host.syncFactAction();
        }
        host.showFact();
    }

    // для кнопки "+ внести факт" беру текущий период из настроек
    private void openCurrentFact(MainActivity host) {
        host.setFactDate(LocalDate.now());
        host.showFact();
    }

    // кнопка сверки на главном работает с текущим видимым периодом
    private void openCurrentReconcile(MainActivity host) {
        LocalDate today = LocalDate.now();
        if (host.balanceViewMode == BalanceViewMode.MONTH) {
            showReconcile(host, Periods.monthKey(YearMonth.from(today)));
            return;
        }
        if (host.balanceViewMode == BalanceViewMode.QUARTER) {
            int quarter = ((today.getMonthValue() - 1) / 3) + 1;
            showReconcile(host, Periods.quarterKey(today.getYear(), quarter));
            return;
        }
        showReconcile(host, Periods.weekKey(today));
    }

    // карточка доступного баланса ведет в детализацию текущего периода
    private void openCurrentPeriodDetail(MainActivity host) {
        LocalDate today = LocalDate.now();
        if (host.balanceViewMode == BalanceViewMode.MONTH) {
            showPeriodDetail(host, Periods.monthKey(YearMonth.from(today)));
            return;
        }
        if (host.balanceViewMode == BalanceViewMode.QUARTER) {
            int quarter = ((today.getMonthValue() - 1) / 3) + 1;
            showPeriodDetail(host, Periods.quarterKey(today.getYear(), quarter));
            return;
        }
        showPeriodDetail(host, Periods.weekKey(today));
    }

    // если статьи для разницы нет - создаю
    private Category correctionCategory(MainActivity host, CategoryType type) {
        Category selected = host.repository.findCategory(host.repository.data().defaultDifferenceCategoryId);
        // пользователь мог выбрать свою статью для сверки, но тип все равно должен совпасть
        if (selected != null && !selected.archived && selected.type == type) return selected;
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
    private TextView cell(MainActivity host, String text, int color, int style, boolean left, int widthDp, int heightDp, View.OnClickListener listener) {
        TextView cell = host.ui.label(text, 12, color, style);
        cell.setGravity(left ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        cell.setSingleLine(false);
        // длинные статьи не должны раздувать строку, пусть лучше аккуратно обрежутся
        cell.setMaxLines(2);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        cell.setIncludeFontPadding(false);
        cell.setPadding(host.ui.dp(7), host.ui.dp(5), host.ui.dp(7), host.ui.dp(5));
        TableRow.LayoutParams params = new TableRow.LayoutParams(host.ui.dp(widthDp), host.ui.dp(heightDp));
        cell.setLayoutParams(params);
        cell.setMinWidth(host.ui.dp(widthDp));
        cell.setMinHeight(host.ui.dp(heightDp));
        cell.setBackground(host.ui.bg(Color.WHITE, 0, UiKit.LINE, 1));
        if (listener != null) cell.setOnClickListener(listener);
        return cell;
    }
}
