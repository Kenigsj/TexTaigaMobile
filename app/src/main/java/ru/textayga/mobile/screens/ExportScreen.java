package ru.textayga.mobile.screens;

import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import ru.textayga.mobile.MainActivity;
import ru.textayga.mobile.domain.Periods;
import ru.textayga.mobile.model.DateRange;
import ru.textayga.mobile.model.ExportFormat;
import ru.textayga.mobile.model.ExportRangeMode;
import ru.textayga.mobile.ui.NavTarget;
import ru.textayga.mobile.ui.UiKit;

// тут выписки, выбираю период и формат
public class ExportScreen implements AppScreen {
    @Override
    public View render(MainActivity host) {
        UiKit.Screen screen = host.ui.screen("выписка", "Экспорт выписки", "", host.ui.iconButton("‹", v -> host.showBalance()), NavTarget.EXPORT, host);
        screen.content.addView(host.ui.infoBanner("Сформируйте файл с данными бюджета, плана и факта за выбранный период."));
        screen.content.addView(periodCard(host));
        screen.content.addView(includeCard(host));
        screen.content.addView(formatCard(host));
        screen.content.addView(host.ui.primaryButton("Сформировать файл", v -> host.startExport()), new LinearLayout.LayoutParams(-1, host.ui.dp(62)));
        return screen.root;
    }

    // карточка периода экспорта
    private View periodCard(MainActivity host) {
        LinearLayout card = host.ui.card();
        card.addView(host.ui.label("1. Период", 18, UiKit.INK, Typeface.BOLD));
        LinearLayout tabs = host.ui.row();
        tabs.setPadding(0, host.ui.dp(12), 0, host.ui.dp(12));
        addTab(host, tabs, ExportRangeMode.WEEK, "Неделя");
        addTab(host, tabs, ExportRangeMode.MONTH, "Месяц");
        addTab(host, tabs, ExportRangeMode.QUARTER, "Квартал");
        addTab(host, tabs, ExportRangeMode.YEAR, "Год");
        addTab(host, tabs, ExportRangeMode.CUSTOM, "Период");
        card.addView(tabs);
        if (host.exportRangeMode == ExportRangeMode.CUSTOM) {
            // в произвольном режиме задаем начало и конец
            LinearLayout dates = host.ui.row();
            TextView start = host.ui.fieldButton(Periods.fullDate(host.customStart), "▣");
            start.setOnClickListener(v -> host.showDatePicker("Начало периода", host.customStart, date -> {
                host.customStart = date;
                if (host.customEnd.isBefore(host.customStart)) host.customEnd = host.customStart;
                host.showExport();
            }));
            TextView end = host.ui.fieldButton(Periods.fullDate(host.customEnd), "▣");
            end.setOnClickListener(v -> host.showDatePicker("Конец периода", host.customEnd, date -> {
                host.customEnd = date;
                if (host.customStart.isAfter(host.customEnd)) host.customStart = host.customEnd;
                host.showExport();
            }));
            dates.addView(start, new LinearLayout.LayoutParams(0, host.ui.dp(56), 1));
            host.ui.gap(dates, 10, false);
            dates.addView(end, new LinearLayout.LayoutParams(0, host.ui.dp(56), 1));
            card.addView(dates);
        } else {
            // в быстрых режимах диапазон уже посчитан
            DateRange range = Periods.exportRange(host.exportRangeMode, host.customStart, host.customEnd);
            card.addView(host.ui.label(Periods.fullDate(range.start) + " - " + Periods.fullDate(range.end), 18, UiKit.INK, Typeface.NORMAL));
        }
        return card;
    }

    // галочки это что попадет в файл
    private View includeCard(MainActivity host) {
        LinearLayout card = host.ui.card();
        card.addView(host.ui.label("2. Что включить в экспорт", 18, UiKit.INK, Typeface.BOLD));
        card.addView(checkRow(host, "Плановые значения", "План доходов, расходов и перемещений", host.includePlan, () -> host.includePlan = !host.includePlan));
        card.addView(checkRow(host, "Фактические операции", "Все внесенные факты", host.includeFact, () -> host.includeFact = !host.includeFact));
        card.addView(checkRow(host, "Расчетный баланс", "Итоги по выбранному периоду", host.includeBalance, () -> host.includeBalance = !host.includeBalance));
        card.addView(checkRow(host, "Комментарии", "Комментарии к операциям и статьям", host.includeComments, () -> host.includeComments = !host.includeComments));
        return card;
    }

    // карточка выбора формата: excel, pdf или csv
    private View formatCard(MainActivity host) {
        LinearLayout card = host.ui.card();
        card.addView(host.ui.label("3. Формат файла", 18, UiKit.INK, Typeface.BOLD));
        LinearLayout formats = host.ui.row();
        addFormat(host, formats, ExportFormat.XLS, "Excel", "Таблица");
        host.ui.gap(formats, 10, false);
        addFormat(host, formats, ExportFormat.PDF, "PDF", "Для печати");
        host.ui.gap(formats, 10, false);
        addFormat(host, formats, ExportFormat.CSV, "CSV", "Текст");
        card.addView(formats);
        return card;
    }

    // вкладка периода: нажали - режим сменился
    private void addTab(MainActivity host, LinearLayout tabs, ExportRangeMode mode, String text) {
        boolean active = host.exportRangeMode == mode;
        TextView tab = host.ui.label(text, 12, active ? android.graphics.Color.WHITE : UiKit.MUTED, Typeface.NORMAL);
        tab.setGravity(android.view.Gravity.CENTER);
        tab.setSingleLine(false);
        tab.setBackground(host.ui.bg(active ? UiKit.BLUE : android.graphics.Color.rgb(240, 242, 246), 6, android.graphics.Color.TRANSPARENT, 0));
        tab.setOnClickListener(v -> {
            host.exportRangeMode = mode;
            host.showExport();
        });
        tabs.addView(tab, new LinearLayout.LayoutParams(0, host.ui.dp(46), 1));
    }

    // карточка формата файла
    private void addFormat(MainActivity host, LinearLayout parent, ExportFormat format, String title, String subtitle) {
        boolean active = host.exportFormat == format;
        LinearLayout card = host.ui.column();
        card.setGravity(android.view.Gravity.CENTER);
        card.setPadding(host.ui.dp(6), host.ui.dp(8), host.ui.dp(6), host.ui.dp(8));
        card.setBackground(host.ui.bg(android.graphics.Color.WHITE, 8, active ? UiKit.BLUE : UiKit.LINE, active ? 2 : 1));
        card.addView(host.ui.label(title, 14, UiKit.INK, Typeface.BOLD));
        card.addView(host.ui.label(subtitle, 11, UiKit.MUTED, Typeface.NORMAL));
        card.setOnClickListener(v -> {
            host.exportFormat = format;
            host.showExport();
        });
        parent.addView(card, new LinearLayout.LayoutParams(0, host.ui.dp(88), 1));
    }

    // строка настройки с галочкой
    private View checkRow(MainActivity host, String title, String subtitle, boolean checked, Runnable toggle) {
        LinearLayout row = host.ui.row();
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(12), 0, 0);
        LinearLayout textBox = host.ui.column();
        textBox.addView(host.ui.label(title, 15, UiKit.INK, Typeface.BOLD));
        textBox.addView(host.ui.label(subtitle, 12, UiKit.MUTED, Typeface.NORMAL));
        row.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(host.ui.label(checked ? "✓" : "", 26, UiKit.BLUE, Typeface.BOLD), new LinearLayout.LayoutParams(host.ui.dp(46), -2));
        row.setOnClickListener(v -> {
            toggle.run();
            host.showExport();
        });
        return row;
    }
}
