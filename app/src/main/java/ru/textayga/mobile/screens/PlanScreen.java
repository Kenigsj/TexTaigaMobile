package ru.textayga.mobile.screens;

import android.app.Dialog;
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

import ru.textayga.mobile.MainActivity;
import ru.textayga.mobile.domain.Money;
import ru.textayga.mobile.domain.Periods;
import ru.textayga.mobile.model.Category;
import ru.textayga.mobile.model.CategoryType;
import ru.textayga.mobile.model.DateRange;
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
        int firstColumnWidth = 154;
        int periodWidth = 112;
        int headerHeight = 68;
        int sectionHeight = 48;
        // строки делаю выше, потому что длинные статьи переносятся и иначе низ букв режется
        int rowHeight = 68;

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

        addSection(host, fixedTable, scrollTable, "ДОХОДЫ", CategoryType.INCOME, periods, firstColumnWidth, periodWidth, sectionHeight, rowHeight);
        addSection(host, fixedTable, scrollTable, "РАСХОДЫ", CategoryType.EXPENSE, periods, firstColumnWidth, periodWidth, sectionHeight, rowHeight);
        addSection(host, fixedTable, scrollTable, "ДЕПОЗИТЫ", CategoryType.DEPOSIT, periods, firstColumnWidth, periodWidth, sectionHeight, rowHeight);
        addSection(host, fixedTable, scrollTable, "ЗАЙМЫ", CategoryType.LOAN, periods, firstColumnWidth, periodWidth, sectionHeight, rowHeight);

        // лишние строки "план доходы/факт расходы" убрал, в плане нужен только ввод статей и итог
        Map<String, PeriodBalance> balances = balanceMap(host, periods);

        TableRow fixedTotals = tableRow(host);
        // фон всей строки не ставлю, иначе справа от таблицы появляется лишний зелёный кусок
        fixedTotals.addView(cell(host, "ИТОГОВЫЙ БАЛАНС", UiKit.GREEN, Typeface.BOLD, true, firstColumnWidth, rowHeight));
        fixedTable.addView(fixedTotals);

        TableRow scrollTotals = tableRow(host);
        // тут тоже без фона строки, красим только сами ячейки
        for (PeriodChoice period : periods) {
            PeriodBalance periodBalance = balances.get(period.key);
            double amount = periodBalance == null ? 0 : periodBalance.closingBalance;
            scrollTotals.addView(cell(host, Money.amount(amount), amount >= 0 ? UiKit.GREEN : host.ui.negativeColor(host.repository.data().negativeBalanceColor), Typeface.BOLD, false, periodWidth, rowHeight));
        }
        scrollTable.addView(scrollTotals);

        HorizontalScrollView horizontalScroll = new HorizontalScrollView(host);
        horizontalScroll.setFillViewport(true);
        horizontalScroll.setHorizontalScrollBarEnabled(true);
        horizontalScroll.addView(scrollTable, new HorizontalScrollView.LayoutParams(-2, -2));

        tableFrame.addView(fixedTable, new LinearLayout.LayoutParams(host.ui.dp(firstColumnWidth), -2));
        tableFrame.addView(horizontalScroll, new LinearLayout.LayoutParams(0, -2, 1));
        return tableFrame;
    }

    // баланс снизу, чтоб сразу видеть итог
    private Map<String, PeriodBalance> balanceMap(MainActivity host, List<PeriodChoice> periods) {
        HashMap<String, PeriodBalance> map = new HashMap<>();
        for (PeriodBalance balance : host.calculator.balanceSeries(periods)) {
            map.put(balance.periodKey, balance);
        }
        return map;
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
                double amount = host.calculator.planAmount(category.id, period.key);
                // тап по ячейке - ввод суммы и коммента
                TextView value = cell(host, amount > 0 ? Money.amount(amount) : "—", host.ui.colorFor(type), Typeface.BOLD, false, periodWidth, rowHeight);
                value.setOnClickListener(v -> editAmount(host, category, period));
                scrollRow.addView(value);
            }
            scrollTable.addView(scrollRow);
        }
    }

    // неделя/месяц/год показывают выбранное, все время раскрывает недели
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
        Dialog dialog = host.dialogs.dialog();
        LinearLayout box = host.dialogs.dialogBox();
        box.addView(host.dialogs.title(category.name, dialog));
        box.addView(host.ui.label(period.label, 14, UiKit.MUTED, Typeface.NORMAL));
        EditText amount = host.ui.editField("Сумма", host.calculator.planAmount(category.id, period.key) > 0 ? Money.amount(host.calculator.planAmount(category.id, period.key)) : "", 22, true);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(-1, host.ui.dp(66));
        amountLp.setMargins(0, host.ui.dp(16), 0, 0);
        box.addView(amount, amountLp);
        EditText comment = host.ui.editField("Комментарий", host.calculator.planComment(category.id, period.key), 16, false);
        LinearLayout.LayoutParams commentLp = new LinearLayout.LayoutParams(-1, host.ui.dp(80));
        commentLp.setMargins(0, host.ui.dp(12), 0, 0);
        box.addView(comment, commentLp);
        LinearLayout actions = host.ui.row();
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, host.ui.dp(60));
        actionsLp.setMargins(0, host.ui.dp(14), 0, 0);
        actions.addView(host.ui.outlineButton("На все периоды", v -> {
            // быстрое повторение платежа от выбранного периода
            fillAllPeriods(host, category, period, Money.parse(amount.getText().toString()), comment.getText().toString());
            dialog.dismiss();
            host.showPlan();
        }), new LinearLayout.LayoutParams(0, host.ui.dp(60), 1));
        host.ui.gap(actions, 10, false);
        actions.addView(host.ui.primaryButton("Сохранить", v -> {
            host.calculator.setPlanAmount(category.id, period.key, Money.parse(amount.getText().toString()), comment.getText().toString());
            host.repository.save();
            dialog.dismiss();
            host.showPlan();
        }), new LinearLayout.LayoutParams(0, host.ui.dp(60), 1));
        box.addView(actions, actionsLp);
        dialog.setContentView(host.dialogs.wrap(box));
        host.dialogs.show(dialog);
        host.showKeyboard(amount);
    }

    // копирую сумму в будущие периоды
    private void fillAllPeriods(MainActivity host, Category category, PeriodChoice selected, double amount, String comment) {
        // если открыт месяц с неделями, повторяю только внутри видимой месячной сетки
        List<PeriodChoice> periods = host.planMode == PlanViewMode.WEEK ? Periods.weeksForMonth(selectedPlanMonth(host)) : host.planMode == PlanViewMode.ALL ? Periods.weeksBetween(host.calculator.firstFactDate(), LocalDate.now().plusWeeks(52)) : Periods.planChoices(host.planMode);
        for (PeriodChoice period : periods) {
            if (!period.start.isBefore(selected.start)) {
                host.calculator.setPlanAmount(category.id, period.key, amount, comment);
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
        TextView cell = host.ui.label(text, 15, color, style);
        cell.setGravity(left ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        cell.setSingleLine(false);
        cell.setMaxLines(3);
        cell.setPadding(host.ui.dp(10), host.ui.dp(10), host.ui.dp(10), host.ui.dp(10));
        cell.setLayoutParams(new TableRow.LayoutParams(host.ui.dp(widthDp), host.ui.dp(heightDp)));
        cell.setMinWidth(host.ui.dp(widthDp));
        cell.setMinHeight(host.ui.dp(heightDp));
        cell.setBackground(host.ui.bg(android.graphics.Color.WHITE, 0, UiKit.LINE, 1));
        return cell;
    }
}
