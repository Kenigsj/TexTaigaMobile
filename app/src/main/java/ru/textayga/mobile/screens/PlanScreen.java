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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.textayga.mobile.MainActivity;
import ru.textayga.mobile.domain.Money;
import ru.textayga.mobile.domain.Periods;
import ru.textayga.mobile.model.Category;
import ru.textayga.mobile.model.CategoryType;
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
        if (host.planMode != PlanViewMode.ALL && !host.planPeriodKey.startsWith(host.planMode.prefix)) {
            host.planPeriodKey = Periods.keyFor(LocalDate.now(), host.planMode);
        }
        UiKit.Screen screen = host.ui.screen("план", "План", "", host.ui.iconButton("▽", v -> showFilter(host)), NavTarget.PLAN, host);
        // верхние кнопки: период и режим
        LinearLayout controls = host.ui.row();
        controls.setPadding(0, host.ui.dp(8), 0, host.ui.dp(10));
        TextView periodButton = host.ui.fieldButton(host.planMode == PlanViewMode.ALL ? "Всё время" : Periods.label(host.planPeriodKey), "▣");
        periodButton.setOnClickListener(v -> {
            if (host.planMode == PlanViewMode.ALL) {
                host.toast("Для режима «Всё время» период не выбирается");
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
            host.planPeriodKey = Periods.keyFor(LocalDate.now(), host.planMode == PlanViewMode.ALL ? PlanViewMode.WEEK : host.planMode);
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

    // таблица плана: строки статьи, столбцы периоды
    private View planTable(MainActivity host) {
        HorizontalScrollView horizontalScroll = new HorizontalScrollView(host);
        horizontalScroll.setFillViewport(true);
        horizontalScroll.setPadding(0, host.ui.dp(14), 0, 0);
        TableLayout table = new TableLayout(host);
        table.setBackground(host.ui.bg(android.graphics.Color.WHITE, 8, UiKit.LINE, 1));
        List<PeriodChoice> periods = visiblePeriods(host);

        TableRow header = tableRow(host);
        header.addView(cell(host, "Статья", UiKit.INK, Typeface.BOLD, true, 136));
        for (PeriodChoice period : periods) header.addView(cell(host, period.shortLabel, UiKit.INK, Typeface.BOLD, false, 110));
        table.addView(header);

        addSection(host, table, "ДОХОДЫ", CategoryType.INCOME, periods);
        addSection(host, table, "РАСХОДЫ", CategoryType.EXPENSE, periods);
        addSection(host, table, "ПЕРЕМЕЩЕНИЯ", CategoryType.TRANSFER, periods);

        Map<String, PeriodBalance> balances = balanceMap(host, periods);
        addBalanceRow(host, table, "ПЛАН ДОХОДЫ", periods, balances, UiKit.GREEN, balance -> balance.planIncome);
        addBalanceRow(host, table, "ПЛАН РАСХОДЫ", periods, balances, UiKit.RED, balance -> balance.planExpense);
        addBalanceRow(host, table, "ФАКТ ДОХОДЫ", periods, balances, UiKit.GREEN, balance -> balance.factIncome);
        addBalanceRow(host, table, "ФАКТ РАСХОДЫ", periods, balances, UiKit.RED, balance -> balance.factExpense);
        TableRow totals = tableRow(host);
        totals.setBackgroundColor(android.graphics.Color.rgb(234, 252, 242));
        totals.addView(cell(host, "ИТОГОВЫЙ БАЛАНС", UiKit.GREEN, Typeface.BOLD, true, 136));
        for (PeriodChoice period : periods) {
            PeriodBalance periodBalance = balances.get(period.key);
            double amount = periodBalance == null ? 0 : periodBalance.closingBalance;
            totals.addView(cell(host, Money.amount(amount), amount >= 0 ? UiKit.GREEN : host.ui.negativeColor(host.repository.data().negativeBalanceColor), Typeface.BOLD, false, 110));
        }
        table.addView(totals);
        horizontalScroll.addView(table, new HorizontalScrollView.LayoutParams(-2, -2));
        return horizontalScroll;
    }

    // баланс снизу, чтоб сразу видеть итог
    private Map<String, PeriodBalance> balanceMap(MainActivity host, List<PeriodChoice> periods) {
        HashMap<String, PeriodBalance> map = new HashMap<>();
        for (PeriodBalance balance : host.calculator.balanceSeries(periods)) {
            map.put(balance.periodKey, balance);
        }
        return map;
    }

    // строка сводки: доходы, расходы и т.д
    private void addBalanceRow(MainActivity host, TableLayout table, String title, List<PeriodChoice> periods, Map<String, PeriodBalance> balances, int color, BalanceValue value) {
        TableRow row = tableRow(host);
        row.addView(cell(host, title, color, Typeface.BOLD, true, 136));
        for (PeriodChoice period : periods) {
            PeriodBalance balance = balances.get(period.key);
            double amount = balance == null ? 0 : value.amount(balance);
            row.addView(cell(host, amount == 0 ? "—" : Money.amount(amount), color, Typeface.BOLD, false, 110));
        }
        table.addView(row);
    }

    // секция таблицы по типу статьи
    private void addSection(MainActivity host, TableLayout table, String title, CategoryType type, List<PeriodChoice> periods) {
        if (host.planFilter != null && host.planFilter != type) return;
        TableRow section = tableRow(host);
        section.addView(cell(host, title, host.ui.colorFor(type), Typeface.BOLD, true, 136));
        for (int i = 0; i < periods.size(); i++) section.addView(cell(host, "", host.ui.colorFor(type), Typeface.BOLD, false, 110));
        table.addView(section);
        for (Category category : host.repository.data().categories) {
            if (category.archived || category.type != type) continue;
            TableRow row = tableRow(host);
            row.addView(cell(host, category.name, UiKit.INK, Typeface.BOLD, true, 136));
            for (PeriodChoice period : periods) {
                double amount = host.calculator.planAmount(category.id, period.key);
                // тап по ячейке - ввод суммы и коммента
                TextView value = cell(host, amount > 0 ? Money.amount(amount) : "—", host.ui.colorFor(type), Typeface.BOLD, false, 110);
                value.setOnClickListener(v -> editAmount(host, category, period));
                row.addView(value);
            }
            table.addView(row);
        }
    }

    // неделя/месяц/год показывают выбранное, все время раскрывает недели
    private List<PeriodChoice> visiblePeriods(MainActivity host) {
        if (host.planMode == PlanViewMode.ALL) return Periods.allTimeWeeks(host.calculator.firstFactDate());
        return java.util.Collections.singletonList(new PeriodChoice(
                host.planPeriodKey,
                Periods.label(host.planPeriodKey),
                Periods.shortLabel(host.planPeriodKey),
                Periods.rangeForKey(host.planPeriodKey).start,
                Periods.rangeForKey(host.planPeriodKey).end
        ));
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
        List<PeriodChoice> periods = host.planMode == PlanViewMode.ALL ? Periods.allTimeWeeks(host.calculator.firstFactDate()) : Periods.planChoices(host.planMode);
        for (PeriodChoice period : periods) {
            if (!period.start.isBefore(selected.start)) {
                host.calculator.setPlanAmount(category.id, period.key, amount, comment);
            }
        }
        host.repository.save();
    }

    // фильтр таблицы плана по типу статей
    private void showFilter(MainActivity host) {
        host.showChoiceDialog("Фильтр таблицы", new String[]{"Показать все статьи", "Только доходы", "Только расходы", "Только перемещения"}, label -> {
            if ("Показать все статьи".equals(label)) host.planFilter = null;
            if ("Только доходы".equals(label)) host.planFilter = CategoryType.INCOME;
            if ("Только расходы".equals(label)) host.planFilter = CategoryType.EXPENSE;
            if ("Только перемещения".equals(label)) host.planFilter = CategoryType.TRANSFER;
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
        legend.addView(host.ui.label("● Перемещения", 13, UiKit.BLUE, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        return legend;
    }

    // маленький помощник для строки tablelayout
    private TableRow tableRow(MainActivity host) {
        TableRow row = new TableRow(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    // стиль ячеек плана, чтоб не разъезжались
    private TextView cell(MainActivity host, String text, int color, int style, boolean left, int widthDp) {
        TextView cell = host.ui.label(text, 15, color, style);
        cell.setGravity(left ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        cell.setSingleLine(false);
        cell.setPadding(host.ui.dp(10), host.ui.dp(10), host.ui.dp(10), host.ui.dp(10));
        cell.setMinWidth(host.ui.dp(widthDp));
        cell.setBackground(host.ui.bg(android.graphics.Color.WHITE, 0, UiKit.LINE, 1));
        return cell;
    }

    // маленький интерфейс, чтоб доставать разные поля periodbalance
    private interface BalanceValue {
        double amount(PeriodBalance balance);
    }
}
