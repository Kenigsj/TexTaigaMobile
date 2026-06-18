package ru.textayga.mobile.screens;

import android.app.Dialog;
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

import ru.textayga.mobile.MainActivity;
import ru.textayga.mobile.domain.Money;
import ru.textayga.mobile.domain.Periods;
import ru.textayga.mobile.model.Category;
import ru.textayga.mobile.model.CategoryType;
import ru.textayga.mobile.model.DateRange;
import ru.textayga.mobile.model.FactAction;
import ru.textayga.mobile.model.PeriodBalance;
import ru.textayga.mobile.model.PeriodChoice;
import ru.textayga.mobile.model.PlanViewMode;
import ru.textayga.mobile.ui.NavTarget;
import ru.textayga.mobile.ui.UiKit;

// тут план, сюда вбиваем ожидаемые суммы
public class PlanScreen implements AppScreen {
    @Override
    public View render(MainActivity host) {
        // после смены режима старый ключ может не подойти
        if (host.planMode == PlanViewMode.WEEK && !isMonthKey(host.planPeriodKey)) {
            // в режиме недель теперь выбираю месяц, а внутри уже показываю его недели
            host.planPeriodKey = Periods.monthKey(YearMonth.now());
        } else if (host.planMode != PlanViewMode.ALL && host.planMode != PlanViewMode.WEEK && !host.planPeriodKey.startsWith(host.planMode.prefix)) {
            host.planPeriodKey = Periods.keyFor(LocalDate.now(), host.planMode);
        }
        UiKit.Screen screen = host.ui.screen("план", "План", "", host.ui.iconButton("▽", v -> showFilter(host)), NavTarget.PLAN, host);
        // верхние кнопки: период и режим
        LinearLayout controls = host.ui.row();
        controls.setPadding(0, host.ui.dp(8), 0, host.ui.dp(10));
        TextView periodButton = host.ui.fieldButton(planPeriodTitle(host), "▣");
        periodButton.setOnClickListener(v -> {
            if (host.planMode == PlanViewMode.ALL) {
                host.toast("Для режима «Всё время» период не выбирается");
            } else if (host.planMode == PlanViewMode.WEEK) {
                // недельный план выбирается через месяц, чтобы таблица была как в бюджете
                host.showPeriodPicker("Выберите месяц", Periods.monthChoices(36, 12), Periods.monthKey(selectedPlanMonth(host)), choice -> {
                    host.planPeriodKey = choice.key;
                    host.showPlan();
                });
            } else {
                host.showPeriodPicker("Выберите период", Periods.planChoices(host.planMode), host.planPeriodKey, choice -> {
                    host.planPeriodKey = choice.key;
                    host.showPlan();
                });
            }
        });
        TextView modeButton = host.ui.fieldButton(host.planMode.title, "☰");
        modeButton.setOnClickListener(v -> host.showChoiceDialog("Отображать", new String[]{"Недели", "Месяцы", "Годы", "Всё время"}, label -> {
            if ("Недели".equals(label)) host.planMode = PlanViewMode.WEEK;
            if ("Месяцы".equals(label)) host.planMode = PlanViewMode.MONTH;
            if ("Годы".equals(label)) host.planMode = PlanViewMode.YEAR;
            if ("Всё время".equals(label)) host.planMode = PlanViewMode.ALL;
            // для недель слева нужен месяц, для остальных режимов оставляю старую механику
            host.planPeriodKey = host.planMode == PlanViewMode.WEEK ? Periods.monthKey(YearMonth.now()) : Periods.keyFor(LocalDate.now(), host.planMode == PlanViewMode.ALL ? PlanViewMode.WEEK : host.planMode);
            host.showPlan();
        }));
        controls.addView(periodButton, new LinearLayout.LayoutParams(0, host.ui.dp(58), 1));
        host.ui.gap(controls, 12, false);
        controls.addView(modeButton, new LinearLayout.LayoutParams(0, host.ui.dp(58), 1));
        screen.content.addView(controls);
        screen.content.addView(host.ui.infoBanner("Нажмите на ячейку, чтобы ввести плановую сумму"));
        screen.content.addView(planTable(host));
        screen.content.addView(legend(host));
        return screen.root;
    }

    // таблица плана, первый столбец стоит на месте, периоды ездят отдельно
    private View planTable(MainActivity host) {
        // делаю таблицу ниже, а длинные названия ниже обрежу многоточием
        int firstColumnWidth = 146;
        int periodWidth = 102;
        int headerHeight = 58;
        int sectionHeight = 38;
        // строки делаю выше, потому что длинные статьи переносятся и иначе низ букв режется
        int rowHeight = 52;

        LinearLayout tableFrame = new LinearLayout(host);
        tableFrame.setOrientation(LinearLayout.HORIZONTAL);
        tableFrame.setBackground(host.ui.bg(android.graphics.Color.WHITE, 8, UiKit.LINE, 1));
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(-1, -2);
        frameLp.setMargins(0, host.ui.dp(14), 0, 0);
        tableFrame.setLayoutParams(frameLp);

        TableLayout fixedTable = new TableLayout(host);
        TableLayout scrollTable = new TableLayout(host);
        List<PeriodChoice> periods = visiblePeriods(host);

        TableRow fixedHeader = tableRow(host);
        fixedHeader.addView(cell(host, "Статья", UiKit.INK, Typeface.BOLD, true, firstColumnWidth, headerHeight));
        fixedTable.addView(fixedHeader);

        TableRow scrollHeader = tableRow(host);
        for (PeriodChoice period : periods) scrollHeader.addView(cell(host, period.shortLabel, UiKit.INK, Typeface.BOLD, false, periodWidth, headerHeight));
        scrollTable.addView(scrollHeader);

        Map<String, PeriodBalance> balances = balanceMap(host, periods);
        addSection(host, fixedTable, scrollTable, "ДОХОДЫ", CategoryType.INCOME, periods, firstColumnWidth, periodWidth, sectionHeight, rowHeight);
        // баланс в плане тоже держу между доходами и расходами, чтобы было как в бюджете
        addBalanceRow(host, fixedTable, scrollTable, periods, balances, firstColumnWidth, periodWidth, rowHeight);
        addSection(host, fixedTable, scrollTable, "РАСХОДЫ", CategoryType.EXPENSE, periods, firstColumnWidth, periodWidth, sectionHeight, rowHeight);
        addSection(host, fixedTable, scrollTable, "ДЕПОЗИТЫ", CategoryType.DEPOSIT, periods, firstColumnWidth, periodWidth, sectionHeight, rowHeight);
        addSection(host, fixedTable, scrollTable, "ЗАЙМЫ", CategoryType.LOAN, periods, firstColumnWidth, periodWidth, sectionHeight, rowHeight);

        HorizontalScrollView horizontalScroll = new HorizontalScrollView(host);
        horizontalScroll.setFillViewport(true);
        horizontalScroll.setHorizontalScrollBarEnabled(true);
        horizontalScroll.addView(scrollTable, new HorizontalScrollView.LayoutParams(-2, -2));

        tableFrame.addView(fixedTable, new LinearLayout.LayoutParams(host.ui.dp(firstColumnWidth), -2));
        tableFrame.addView(horizontalScroll, new LinearLayout.LayoutParams(0, -2, 1));
        return tableFrame;
    }

    // баланс по периодам, отдельно методом, чтобы можно было вставить его в нужное место
    private Map<String, PeriodBalance> balanceMap(MainActivity host, List<PeriodChoice> periods) {
        HashMap<String, PeriodBalance> map = new HashMap<>();
        for (PeriodBalance balance : host.calculator.balanceSeries(periods)) {
            map.put(balance.periodKey, balance);
        }
        return map;
    }

    // сама строка баланса в таблице плана
    private void addBalanceRow(MainActivity host, TableLayout fixedTable, TableLayout scrollTable, List<PeriodChoice> periods, Map<String, PeriodBalance> balances, int firstColumnWidth, int periodWidth, int rowHeight) {
        TableRow fixedTotals = tableRow(host);
        fixedTotals.addView(cell(host, "БАЛАНС", UiKit.GREEN, Typeface.BOLD, true, firstColumnWidth, rowHeight));
        fixedTable.addView(fixedTotals);

        TableRow scrollTotals = tableRow(host);
        for (PeriodChoice period : periods) {
            PeriodBalance periodBalance = balances.get(period.key);
            double amount = periodBalance == null ? 0 : periodBalance.closingBalance;
            scrollTotals.addView(cell(host, Money.amount(amount), amount >= 0 ? UiKit.GREEN : host.ui.negativeColor(host.repository.data().negativeBalanceColor), Typeface.BOLD, false, periodWidth, rowHeight));
        }
        scrollTable.addView(scrollTotals);
    }

    // секция таблицы по типу статьи
    private void addSection(MainActivity host, TableLayout fixedTable, TableLayout scrollTable, String title, CategoryType type, List<PeriodChoice> periods, int firstColumnWidth, int periodWidth, int sectionHeight, int rowHeight) {
        if (host.planFilter != null && host.planFilter != type) return;
        TableRow fixedSection = tableRow(host);
        fixedSection.addView(cell(host, title, host.ui.colorFor(type), Typeface.BOLD, true, firstColumnWidth, sectionHeight));
        fixedTable.addView(fixedSection);

        TableRow scrollSection = tableRow(host);
        for (int i = 0; i < periods.size(); i++) scrollSection.addView(cell(host, "", host.ui.colorFor(type), Typeface.BOLD, false, periodWidth, sectionHeight));
        scrollTable.addView(scrollSection);

        for (Category category : host.repository.data().categories) {
            if (category.archived || category.type != type) continue;
            TableRow fixedRow = tableRow(host);
            fixedRow.addView(cell(host, category.name, UiKit.INK, Typeface.BOLD, true, firstColumnWidth, rowHeight));
            fixedTable.addView(fixedRow);

            TableRow scrollRow = tableRow(host);
            for (PeriodChoice period : periods) {
                FactAction action = planActionFor(category);
                // в плане показываю именно введенную сумму ячейки, а не общий остаток долга
                double amount = host.calculator.planAmount(category.id, period.key, action);
                TextView value = cell(host, amount > 0 ? Money.amount(amount) : "—", host.ui.colorFor(type), Typeface.BOLD, false, periodWidth, rowHeight);
                // клик по займу теперь открывает получение займа, погашение ниже отдельной строкой
                value.setOnClickListener(v -> editAmount(host, category, period, action));
                scrollRow.addView(value);
            }
            scrollTable.addView(scrollRow);
        }
        if (type == CategoryType.EXPENSE) {
            // кредиты планирую через расходную строку, но храню сумму в самой статье займа
            addLoanRepaymentRows(host, fixedTable, scrollTable, periods, firstColumnWidth, periodWidth, rowHeight);
        }
    }

    // отдельные строки погашения кредита, чтобы в плане это было видно именно в расходах
    private void addLoanRepaymentRows(MainActivity host, TableLayout fixedTable, TableLayout scrollTable, List<PeriodChoice> periods, int firstColumnWidth, int periodWidth, int rowHeight) {
        for (Category category : host.repository.data().categories) {
            if (category.archived || category.type != CategoryType.LOAN) continue;
            TableRow fixedRow = tableRow(host);
            fixedRow.addView(cell(host, loanRepaymentTitle(category), UiKit.INK, Typeface.BOLD, true, firstColumnWidth, rowHeight));
            fixedTable.addView(fixedRow);

            TableRow scrollRow = tableRow(host);
            for (PeriodChoice period : periods) {
                double amount = host.calculator.planAmount(category.id, period.key, FactAction.LOAN_REPAY);
                TextView value = cell(host, amount > 0 ? Money.amount(amount) : "—", UiKit.RED, Typeface.BOLD, false, periodWidth, rowHeight);
                value.setOnClickListener(v -> editAmount(host, category, period, FactAction.LOAN_REPAY));
                scrollRow.addView(value);
            }
            scrollTable.addView(scrollRow);
        }
    }

    // для стандартного "Кредит" пишу нормально, а не "Погашение Кредит"
    private String loanRepaymentTitle(Category category) {
        if ("Кредит".equalsIgnoreCase(category.name.trim())) return "Погашение кредита";
        return "Погашение " + category.name;
    }

    // неделя/месяц/год показывают выбранное, все время раскрывает недели
    // обычный план без action, а у депозита/займа сразу понятно что за движение
    private FactAction planActionFor(Category category) {
        if (category.type == CategoryType.DEPOSIT) return FactAction.DEPOSIT_ADD;
        if (category.type == CategoryType.LOAN) return FactAction.LOAN_RECEIVE;
        return null;
    }

    private List<PeriodChoice> visiblePeriods(MainActivity host) {
        if (host.planMode == PlanViewMode.WEEK) return Periods.weeksForMonth(selectedPlanMonth(host));
        if (host.planMode == PlanViewMode.ALL) return Periods.weeksBetween(host.calculator.firstFactDate(), LocalDate.now().plusWeeks(52));
        DateRange selected = Periods.rangeForKey(host.planPeriodKey);
        java.util.ArrayList<PeriodChoice> result = new java.util.ArrayList<>();
        for (PeriodChoice period : Periods.planChoices(host.planMode)) {
            // план нельзя резать текущей неделей, поэтому беру выбранный период и всё будущее
            if (!period.start.isBefore(selected.start)) result.add(period);
        }
        result.sort(java.util.Comparator.comparing(period -> period.start));
        return result;
    }

    // название левой кнопки, чтобы не показывать неделю там, где теперь выбирается месяц
    private String planPeriodTitle(MainActivity host) {
        if (host.planMode == PlanViewMode.ALL) return "Всё время";
        if (host.planMode == PlanViewMode.WEEK) return Periods.monthYearLabel(selectedPlanMonth(host));
        return Periods.label(host.planPeriodKey);
    }

    // в planPeriodKey для недель храню месяц, поэтому достаю его отдельно
    private YearMonth selectedPlanMonth(MainActivity host) {
        if (isMonthKey(host.planPeriodKey)) return YearMonth.parse(host.planPeriodKey.substring(2));
        return YearMonth.now();
    }

    // маленькая проверка ключа, чтобы не словить parse на старом W:
    private boolean isMonthKey(String key) {
        return key != null && key.startsWith("M:");
    }

    // диалог одной ячейки плана
    private void editAmount(MainActivity host, Category category, PeriodChoice period) {
        editAmount(host, category, period, planActionFor(category));
    }

    // action нужен для кредита, чтобы "тест" не сохранялся как "Погашение тест"
    private void editAmount(MainActivity host, Category category, PeriodChoice period, FactAction action) {
        Dialog dialog = host.dialogs.dialog();
        LinearLayout box = host.dialogs.dialogBox();
        // заголовок зависит от действия: получение кредита или его погашение
        String title = planDialogTitle(category, action);
        box.addView(host.dialogs.title(title, dialog));
        box.addView(host.ui.label(period.label, 14, UiKit.MUTED, Typeface.NORMAL));
        EditText amount = host.ui.editField("Сумма", host.calculator.planAmount(category.id, period.key, action) > 0 ? Money.amount(host.calculator.planAmount(category.id, period.key, action)) : "", 22, true);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(-1, host.ui.dp(66));
        amountLp.setMargins(0, host.ui.dp(16), 0, 0);
        box.addView(amount, amountLp);
        final EditText[] weeklyRepayment = new EditText[1];
        if (action == FactAction.LOAN_RECEIVE) {
            // это поле только для кредита: сразу раскидываю погашение по неделям
            weeklyRepayment[0] = host.ui.editField("Еженедельное погашение", "", 18, true);
            weeklyRepayment[0].setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            LinearLayout.LayoutParams weeklyLp = new LinearLayout.LayoutParams(-1, host.ui.dp(66));
            weeklyLp.setMargins(0, host.ui.dp(12), 0, 0);
            box.addView(weeklyRepayment[0], weeklyLp);
        }
        EditText comment = host.ui.editField("Комментарий", host.calculator.planComment(category.id, period.key, action), 16, false);
        LinearLayout.LayoutParams commentLp = new LinearLayout.LayoutParams(-1, host.ui.dp(80));
        commentLp.setMargins(0, host.ui.dp(12), 0, 0);
        box.addView(comment, commentLp);
        LinearLayout actions = host.ui.row();
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, host.ui.dp(60));
        actionsLp.setMargins(0, host.ui.dp(14), 0, 0);
        boolean oneTimeLoanReceive = action == FactAction.LOAN_RECEIVE;
        if (!oneTimeLoanReceive) {
            actions.addView(host.ui.outlineButton("На все периоды", v -> {
                // получение займа сюда не пускаю, это одно событие, а не еженедельный доход
                fillAllPeriods(host, category, period, Money.parse(amount.getText().toString()), comment.getText().toString(), action);
                dialog.dismiss();
                host.showPlan();
            }), new LinearLayout.LayoutParams(0, host.ui.dp(60), 1));
            host.ui.gap(actions, 10, false);
        }
        actions.addView(host.ui.primaryButton("Сохранить", v -> {
            double planAmount = Money.parse(amount.getText().toString());
            host.calculator.setPlanAmount(category.id, period.key, planAmount, comment.getText().toString(), action);
            if (action == FactAction.LOAN_RECEIVE && weeklyRepayment[0] != null) {
                // старые повторы получения кредита дальше стираю, сам долг потом тянется уже расчетом
                clearFutureLoanReceives(host, category, period);
                // если поле пустое, метод просто ничего не раскидает
                scheduleLoanRepayments(host, category, period, planAmount, Money.parse(weeklyRepayment[0].getText().toString()), comment.getText().toString());
            }
            host.repository.save();
            dialog.dismiss();
            host.showPlan();
        }), new LinearLayout.LayoutParams(oneTimeLoanReceive ? -1 : 0, host.ui.dp(60), oneTimeLoanReceive ? 0 : 1));
        box.addView(actions, actionsLp);
        dialog.setContentView(host.dialogs.wrap(box));
        host.dialogs.show(dialog);
        host.showKeyboard(amount);
    }

    // автоматом ставлю погашение кредита по неделям, последний платеж режу по остатку
    private void scheduleLoanRepayments(MainActivity host, Category category, PeriodChoice selected, double loanAmount, double weeklyAmount, String comment) {
        if (weeklyAmount <= 0) return;
        // график из этого окна считаю только для введенной суммы, старый долг сюда второй раз не подмешиваю
        double remaining = loanAmount;
        for (PeriodChoice period : Periods.weeksBetween(selected.start, selected.start.plusWeeks(52))) {
            double payment = remaining > 0 ? Math.min(weeklyAmount, remaining) : 0;
            // когда кредит уже закрыт, на будущих неделях стираю старый план погашения
            host.calculator.setPlanAmount(category.id, period.key, payment, comment, FactAction.LOAN_REPAY);
            remaining -= payment;
        }
    }

    // получение займа не должно повторяться дальше, иначе долг и баланс начинают расти каждую неделю
    private void clearFutureLoanReceives(MainActivity host, Category category, PeriodChoice selected) {
        for (PeriodChoice period : Periods.weeksBetween(selected.start.plusWeeks(1), selected.start.plusWeeks(52))) {
            host.calculator.setPlanAmount(category.id, period.key, 0, "", FactAction.LOAN_RECEIVE);
        }
    }

    // заголовок диалога подсказывает, что именно планируем по кредиту
    private String planDialogTitle(Category category, FactAction action) {
        if (action == FactAction.LOAN_REPAY) return loanRepaymentTitle(category);
        if (action == FactAction.LOAN_RECEIVE) return "Получение " + category.name;
        return category.name;
    }

    // копирую сумму в будущие периоды
    private void fillAllPeriods(MainActivity host, Category category, PeriodChoice selected, double amount, String comment) {
        fillAllPeriods(host, category, selected, amount, comment, planActionFor(category));
    }

    // тут тоже передаю action, иначе "на все периоды" опять смешает получение и погашение
    private void fillAllPeriods(MainActivity host, Category category, PeriodChoice selected, double amount, String comment, FactAction action) {
        if (action == FactAction.LOAN_RECEIVE) {
            // на всякий случай страхуюсь: получение кредита сохраняю только в выбранную неделю
            host.calculator.setPlanAmount(category.id, selected.key, amount, comment, action);
            clearFutureLoanReceives(host, category, selected);
            host.repository.save();
            return;
        }
        // если открыт месяц с неделями, повторяю только внутри видимой месячной сетки
        List<PeriodChoice> periods = host.planMode == PlanViewMode.WEEK ? Periods.weeksForMonth(selectedPlanMonth(host)) : host.planMode == PlanViewMode.ALL ? Periods.weeksBetween(host.calculator.firstFactDate(), LocalDate.now().plusWeeks(52)) : Periods.planChoices(host.planMode);
        for (PeriodChoice period : periods) {
            if (!period.start.isBefore(selected.start)) {
                host.calculator.setPlanAmount(category.id, period.key, amount, comment, action);
            }
        }
        host.repository.save();
    }

    // фильтр таблицы плана по типу статей
    private void showFilter(MainActivity host) {
        host.showChoiceDialog("Фильтр таблицы", new String[]{"Показать все статьи", "Только доходы", "Только расходы", "Только депозиты", "Только займы"}, label -> {
            if ("Показать все статьи".equals(label)) host.planFilter = null;
            if ("Только доходы".equals(label)) host.planFilter = CategoryType.INCOME;
            if ("Только расходы".equals(label)) host.planFilter = CategoryType.EXPENSE;
            if ("Только депозиты".equals(label)) host.planFilter = CategoryType.DEPOSIT;
            if ("Только займы".equals(label)) host.planFilter = CategoryType.LOAN;
            host.showPlan();
        });
    }

    // легенда цветов под таблицей
    private View legend(MainActivity host) {
        LinearLayout legend = host.ui.row();
        legend.setGravity(Gravity.CENTER);
        legend.setPadding(host.ui.dp(12), host.ui.dp(10), host.ui.dp(12), host.ui.dp(10));
        legend.setBackground(host.ui.bg(android.graphics.Color.WHITE, 8, UiKit.LINE, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, host.ui.dp(12), 0, 0);
        legend.setLayoutParams(lp);
        legend.addView(host.ui.label("● Доходы", 13, UiKit.GREEN, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        legend.addView(host.ui.label("● Расходы", 13, UiKit.RED, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        legend.addView(host.ui.label("● Депозиты", 13, UiKit.BLUE, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        legend.addView(host.ui.label("● Займы", 13, UiKit.PURPLE, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        return legend;
    }

    // маленький помощник для строки tablelayout
    private TableRow tableRow(MainActivity host) {
        TableRow row = new TableRow(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    // стиль ячеек плана, чтоб не разъезжались
    private TextView cell(MainActivity host, String text, int color, int style, boolean left, int widthDp, int heightDp) {
        TextView cell = host.ui.label(text, 13, color, style);
        cell.setGravity(left ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        cell.setSingleLine(false);
        // так строки не становятся огромными из-за "Коммунальные платежи"
        cell.setMaxLines(2);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        cell.setIncludeFontPadding(false);
        cell.setPadding(host.ui.dp(8), host.ui.dp(5), host.ui.dp(8), host.ui.dp(5));
        cell.setLayoutParams(new TableRow.LayoutParams(host.ui.dp(widthDp), host.ui.dp(heightDp)));
        cell.setMinWidth(host.ui.dp(widthDp));
        cell.setMinHeight(host.ui.dp(heightDp));
        cell.setBackground(host.ui.bg(android.graphics.Color.WHITE, 0, UiKit.LINE, 1));
        return cell;
    }
}
