package ru.textayga.mobile.screens;

import android.app.Dialog;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.UUID;

import ru.textayga.mobile.MainActivity;
import ru.textayga.mobile.domain.Money;
import ru.textayga.mobile.domain.Periods;
import ru.textayga.mobile.model.Category;
import ru.textayga.mobile.model.CategoryType;
import ru.textayga.mobile.model.PeriodKind;
import ru.textayga.mobile.ui.NavTarget;
import ru.textayga.mobile.ui.UiKit;

// настройки, тут статьи, периоды, кошельки, прочее
public class SettingsScreen implements AppScreen {
    @Override
    public View render(MainActivity host) {
        UiKit.Screen screen = host.ui.screen("Настройки", "Настройки", "Управляйте статьями, периодами и параметрами приложения", null, NavTarget.SETTINGS, host);
        LinearLayout tabs = host.ui.row();
        tabs.setPadding(0, host.ui.dp(14), 0, host.ui.dp(16));
        addTab(host, tabs, "categories", "Статьи");
        addTab(host, tabs, "periods", "Периоды");
        addTab(host, tabs, "wallets", "Кошельки");
        addTab(host, tabs, "visual", "Визуальные");
        addTab(host, tabs, "other", "Прочее");
        HorizontalScrollView tabsScroll = new HorizontalScrollView(host);
        // вкладки скроллятся, иначе на телефоне все ломается
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabsScroll.addView(tabs, new HorizontalScrollView.LayoutParams(-2, -2));
        screen.content.addView(tabsScroll);

        if ("categories".equals(host.settingsTab)) showCategories(host, screen.content);
        if ("periods".equals(host.settingsTab)) showPeriods(host, screen.content);
        if ("wallets".equals(host.settingsTab)) showWallets(host, screen.content);
        if ("visual".equals(host.settingsTab)) showVisual(host, screen.content);
        if ("other".equals(host.settingsTab)) showOther(host, screen.content);
        return screen.root;
    }

    // раздел статей (фильтр, добавить, редактировать, архив)
    private void showCategories(MainActivity host, LinearLayout content) {
        LinearLayout card = host.ui.card();
        LinearLayout head = host.ui.row();
        LinearLayout textBox = host.ui.column();
        textBox.addView(host.ui.label("Категории и статьи", 20, UiKit.INK, Typeface.BOLD));
        textBox.addView(host.ui.label("Настройте список статей доходов, расходов и перемещений", 13, UiKit.MUTED, Typeface.NORMAL));
        head.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(host.ui.primaryButton("+ Добавить", v -> showCategoryDialog(host, null)), new LinearLayout.LayoutParams(host.ui.dp(140), host.ui.dp(52)));
        card.addView(head);

        HorizontalScrollView filterScroll = new HorizontalScrollView(host);
        // фильтры скроллятся из-за длинного "перемещения"
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = host.ui.row();
        filters.setPadding(0, host.ui.dp(14), 0, host.ui.dp(12));
        filters.addView(filter(host, "Все", host.categoryFilter == null, () -> host.categoryFilter = null), new LinearLayout.LayoutParams(host.ui.dp(86), host.ui.dp(44)));
        host.ui.gap(filters, 8, false);
        filters.addView(filter(host, "Доходы", host.categoryFilter == CategoryType.INCOME, () -> host.categoryFilter = CategoryType.INCOME), new LinearLayout.LayoutParams(host.ui.dp(106), host.ui.dp(44)));
        host.ui.gap(filters, 8, false);
        filters.addView(filter(host, "Расходы", host.categoryFilter == CategoryType.EXPENSE, () -> host.categoryFilter = CategoryType.EXPENSE), new LinearLayout.LayoutParams(host.ui.dp(112), host.ui.dp(44)));
        host.ui.gap(filters, 8, false);
        filters.addView(filter(host, "Перемещения", host.categoryFilter == CategoryType.TRANSFER, () -> host.categoryFilter = CategoryType.TRANSFER), new LinearLayout.LayoutParams(host.ui.dp(142), host.ui.dp(44)));
        filterScroll.addView(filters, new HorizontalScrollView.LayoutParams(-2, -2));
        card.addView(filterScroll);

        addCategorySection(host, card, "ДОХОДЫ", CategoryType.INCOME);
        addCategorySection(host, card, "РАСХОДЫ", CategoryType.EXPENSE);
        addCategorySection(host, card, "ПЕРЕМЕЩЕНИЯ", CategoryType.TRANSFER);
        content.addView(card);
        content.addView(settingsLink(host, "Статья по умолчанию для разницы", "Используется при сверке баланса", v -> chooseDefaultDifference(host)));
        content.addView(settingsLink(host, "Архивированные статьи", "Просмотр и восстановление удаленных статей", v -> showArchived(host)));
    }

    // выбор периода (недели или месяцы)
    private void showPeriods(MainActivity host, LinearLayout content) {
        LinearLayout card = host.ui.card();
        card.addView(host.ui.label("Периоды", 20, UiKit.INK, Typeface.BOLD));
        card.addView(host.ui.label("Выберите тип периодов для плана, факта и баланса", 13, UiKit.MUTED, Typeface.NORMAL));
        LinearLayout row = host.ui.row();
        row.setPadding(0, host.ui.dp(16), 0, host.ui.dp(12));
        row.addView(filter(host, "Недели", host.repository.data().periodKind == PeriodKind.WEEK, () -> host.repository.data().periodKind = PeriodKind.WEEK), new LinearLayout.LayoutParams(0, host.ui.dp(58), 1));
        host.ui.gap(row, 10, false);
        row.addView(filter(host, "Месяцы", host.repository.data().periodKind == PeriodKind.MONTH, () -> host.repository.data().periodKind = PeriodKind.MONTH), new LinearLayout.LayoutParams(0, host.ui.dp(58), 1));
        card.addView(row);
        card.addView(infoRow(host, "Текущий период", host.repository.data().periodKind == PeriodKind.WEEK ? Periods.weekLabel(Periods.startOfWeek(LocalDate.now())) : Periods.monthName(YearMonth.now())));
        card.addView(infoRow(host, "Горизонт планирования", host.repository.data().periodKind == PeriodKind.WEEK ? "52 недели" : "12 месяцев"));
        content.addView(card);
    }

    // кошельки и стартовый баланс
    private void showWallets(MainActivity host, LinearLayout content) {
        LinearLayout card = host.ui.card();
        card.addView(host.ui.label("Кошельки", 20, UiKit.INK, Typeface.BOLD));
        card.addView(host.ui.label("Источники денег для учета фактических операций", 13, UiKit.MUTED, Typeface.NORMAL));
        card.addView(settingsLink(host, "Стартовый баланс", Money.rub(host.repository.data().startingBalance), v -> showStartingBalanceDialog(host)));
        LinearLayout head = host.ui.row();
        head.setPadding(0, host.ui.dp(14), 0, 0);
        head.addView(host.ui.label("Типы оплаты", 17, UiKit.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(host.ui.primaryButton("+ Добавить", v -> showPaymentDialog(host)), new LinearLayout.LayoutParams(host.ui.dp(128), host.ui.dp(48)));
        card.addView(head);
        for (String payment : host.repository.data().paymentTypes) {
            card.addView(walletRow(host, payment));
        }
        content.addView(card);
    }

    // настройки внешнего вида
    private void showVisual(MainActivity host, LinearLayout content) {
        LinearLayout card = host.ui.card();
        card.addView(host.ui.label("Визуальные", 20, UiKit.INK, Typeface.BOLD));
        card.addView(host.ui.label("Параметры внешнего вида приложения", 13, UiKit.MUTED, Typeface.NORMAL));
        card.addView(infoRow(host, "Стиль интерфейса", "Светлый"));
        card.addView(infoRow(host, "Акцентный цвет", "Синий"));
        card.addView(settingsLink(host, "Цвет отрицательного баланса", negativeColorName(host.repository.data().negativeBalanceColor), v -> chooseNegativeColor(host)));
        card.addView(host.ui.infoBanner("Цвет отрицательного баланса применяется в таблице бюджета, сверке и прогнозах."));
        content.addView(card);
    }

    // служебные штуки
    private void showOther(MainActivity host, LinearLayout content) {
        LinearLayout card = host.ui.card();
        card.addView(host.ui.label("Прочее", 20, UiKit.INK, Typeface.BOLD));
        card.addView(host.ui.label("Служебные параметры и данные приложения", 13, UiKit.MUTED, Typeface.NORMAL));
        card.addView(infoRow(host, "Локальное хранение", "Данные сохраняются на устройстве"));
        card.addView(infoRow(host, "Версия прототипа", "Android 1.0"));
        content.addView(card);
    }

    // рисую группу статей
    private void addCategorySection(MainActivity host, LinearLayout parent, String title, CategoryType type) {
        if (host.categoryFilter != null && host.categoryFilter != type) return;
        TextView sectionTitle = host.ui.label(title, 24, host.ui.colorFor(type), Typeface.BOLD);
        sectionTitle.setPadding(0, host.ui.dp(18), 0, host.ui.dp(6));
        parent.addView(sectionTitle);
        LinearLayout section = host.ui.column();
        section.setBackground(host.ui.bg(android.graphics.Color.WHITE, 8, UiKit.LINE, 1));
        int count = 0;
        for (Category category : host.repository.data().categories) {
            if (category.archived || category.type != type) continue;
            section.addView(categoryRow(host, category));
            count++;
        }
        if (count == 0) section.addView(host.ui.emptyText("В этом разделе пока нет статей"));
        parent.addView(section);
    }

    // строка статьи (название, описание, тип, кнопки)
    private View categoryRow(MainActivity host, Category category) {
        LinearLayout row = host.ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(host.ui.dp(12), host.ui.dp(10), host.ui.dp(8), host.ui.dp(10));
        row.setMinimumHeight(host.ui.dp(74));
        row.addView(host.ui.categoryIcon(category), new LinearLayout.LayoutParams(host.ui.dp(44), host.ui.dp(44)));
        host.ui.gap(row, 10, false);
        LinearLayout textBox = host.ui.column();
        TextView name = host.ui.label(category.name, 15, UiKit.INK, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        textBox.addView(name);
        TextView description = host.ui.label(category.description == null ? "" : category.description, 12, UiKit.MUTED, Typeface.NORMAL);
        description.setMaxLines(2);
        description.setEllipsize(TextUtils.TruncateAt.END);
        textBox.addView(description);
        TextView typeTag = host.ui.tag(category.type.title, host.ui.colorFor(category.type));
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(category.type == CategoryType.TRANSFER ? host.ui.dp(110) : host.ui.dp(76), host.ui.dp(26));
        tagLp.setMargins(0, host.ui.dp(5), 0, 0);
        textBox.addView(typeTag, tagLp);
        row.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(host.ui.actionIconButton("edit", UiKit.MUTED, v -> showCategoryDialog(host, category)), new LinearLayout.LayoutParams(host.ui.dp(38), host.ui.dp(44)));
        row.addView(host.ui.actionIconButton("delete", UiKit.RED, v -> {
            category.archived = true;
            host.repository.save();
            host.showSettings();
        }), new LinearLayout.LayoutParams(host.ui.dp(38), host.ui.dp(44)));
        return row;
    }

    // диалог добавить/изменить статью
    private void showCategoryDialog(MainActivity host, Category source) {
        Dialog dialog = host.dialogs.dialog();
        LinearLayout box = host.dialogs.dialogBox();
        box.addView(host.dialogs.title(source == null ? "Добавить статью" : "Редактировать статью", dialog));
        CategoryDraft draft = new CategoryDraft();
        // draft нужен, чтоб не менять статью до кнопки сохранить
        draft.type = source == null ? CategoryType.INCOME : source.type;
        TextView type = host.ui.fieldButton(draft.type.plural, "");
        type.setOnClickListener(v -> host.showChoiceDialog("Тип статьи", new String[]{"Доходы", "Расходы", "Перемещения"}, label -> {
            if ("Доходы".equals(label)) draft.type = CategoryType.INCOME;
            if ("Расходы".equals(label)) draft.type = CategoryType.EXPENSE;
            if ("Перемещения".equals(label)) draft.type = CategoryType.TRANSFER;
            type.setText(draft.type.plural + "  ˅");
        }));
        box.addView(host.ui.formLabel("Тип"));
        box.addView(type, new LinearLayout.LayoutParams(-1, host.ui.dp(62)));
        EditText name = host.ui.editField("", source == null ? "" : source.name, 18, false);
        box.addView(host.ui.formLabel("Название"));
        box.addView(name, new LinearLayout.LayoutParams(-1, host.ui.dp(64)));
        EditText desc = host.ui.editField("", source == null ? "" : source.description, 18, false);
        box.addView(host.ui.formLabel("Описание"));
        box.addView(desc, new LinearLayout.LayoutParams(-1, host.ui.dp(64)));
        EditText icon = host.ui.editField("", source == null ? "" : source.icon, 18, false);
        box.addView(host.ui.formLabel("Метка / эмодзи"));
        box.addView(icon, new LinearLayout.LayoutParams(-1, host.ui.dp(64)));
        EditText keywords = host.ui.editField("", source == null ? "" : source.keywords, 18, false);
        box.addView(host.ui.formLabel("Ключевые слова для импорта"));
        box.addView(keywords, new LinearLayout.LayoutParams(-1, host.ui.dp(64)));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, host.ui.dp(62));
        saveLp.setMargins(0, host.ui.dp(24), 0, 0);
        box.addView(host.ui.primaryButton("Сохранить", v -> {
            String categoryName = name.getText().toString().trim();
            if (categoryName.isEmpty()) {
                host.toast("Введите название статьи");
                return;
            }
            Category category = source == null ? new Category() : source;
            if (source == null) category.id = UUID.randomUUID().toString();
            category.type = draft.type;
            category.name = categoryName;
            category.description = desc.getText().toString().trim();
            category.icon = icon.getText().toString().trim();
            category.keywords = keywords.getText().toString().trim();
            category.archived = false;
            if (source == null) host.repository.data().categories.add(category);
            host.repository.save();
            dialog.dismiss();
            host.showSettings();
        }), saveLp);
        dialog.setContentView(host.dialogs.wrap(box));
        host.dialogs.show(dialog);
    }

    // чип фильтра в настройках
    private TextView filter(MainActivity host, String text, boolean active, Runnable action) {
        TextView chip = host.ui.chip(text, active, v -> {
            action.run();
            host.repository.save();
            host.showSettings();
        });
        chip.setTypeface(android.graphics.Typeface.DEFAULT, Typeface.BOLD);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        return chip;
    }

    // верхняя вкладка настроек
    private void addTab(MainActivity host, LinearLayout tabs, String id, String text) {
        boolean active = host.settingsTab.equals(id);
        TextView tab = host.ui.label(text, 13, active ? UiKit.BLUE : UiKit.MUTED, Typeface.NORMAL);
        tab.setGravity(Gravity.CENTER);
        tab.setSingleLine(true);
        tab.setEllipsize(TextUtils.TruncateAt.END);
        tab.setBackground(host.ui.bg(active ? android.graphics.Color.WHITE : android.graphics.Color.TRANSPARENT, 8, android.graphics.Color.TRANSPARENT, 0));
        tab.setOnClickListener(v -> {
            host.settingsTab = id;
            host.showSettings();
        });
        tabs.addView(tab, new LinearLayout.LayoutParams(host.ui.dp("Визуальные".equals(text) ? 118 : 96), host.ui.dp(72)));
    }

    // строка настройки (иконка, заголовок, подпись)
    private View infoRow(MainActivity host, String title, String subtitle) {
        LinearLayout row = host.ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
        row.setBackground(host.ui.bg(android.graphics.Color.WHITE, 8, UiKit.LINE, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, host.ui.dp(10), 0, 0);
        row.setLayoutParams(lp);
        row.addView(host.ui.circleIcon("•", UiKit.BLUE), new LinearLayout.LayoutParams(host.ui.dp(42), host.ui.dp(42)));
        host.ui.gap(row, 12, false);
        LinearLayout textBox = host.ui.column();
        textBox.addView(host.ui.label(title, 16, UiKit.INK, Typeface.BOLD));
        TextView sub = host.ui.label(subtitle, 13, UiKit.MUTED, Typeface.NORMAL);
        sub.setSingleLine(false);
        textBox.addView(sub);
        row.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    // строка типа оплаты
    private View walletRow(MainActivity host, String payment) {
        LinearLayout row = host.ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(8), host.ui.dp(12));
        row.setBackground(host.ui.bg(android.graphics.Color.WHITE, 8, UiKit.LINE, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, host.ui.dp(10), 0, 0);
        row.setLayoutParams(lp);
        row.addView(host.ui.circleIcon("▭", UiKit.BLUE), new LinearLayout.LayoutParams(host.ui.dp(42), host.ui.dp(42)));
        host.ui.gap(row, 12, false);
        row.addView(host.ui.label(payment, 16, UiKit.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
        if (!"Карта".equals(payment) && !"Наличные".equals(payment)) {
            row.addView(host.ui.actionIconButton("delete", UiKit.RED, v -> {
                host.repository.data().paymentTypes.remove(payment);
                if (payment.equals(host.factPayment)) host.factPayment = host.repository.data().paymentTypes.isEmpty() ? "Карта" : host.repository.data().paymentTypes.get(0);
                host.repository.save();
                host.showSettings();
            }), new LinearLayout.LayoutParams(host.ui.dp(42), host.ui.dp(42)));
        }
        return row;
    }

    // строка-ссылка с стрелкой справа
    private View settingsLink(MainActivity host, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout row = (LinearLayout) infoRow(host, title, subtitle);
        row.addView(host.ui.label("›", 34, UiKit.MUTED, Typeface.NORMAL));
        row.setOnClickListener(listener);
        return row;
    }

    // выбор статьи для разницы
    private void chooseDefaultDifference(MainActivity host) {
        ArrayList<String> names = new ArrayList<>();
        for (Category category : host.repository.data().categories) if (!category.archived) names.add(category.name);
        host.showChoiceDialog("Статья для разницы", names.toArray(new String[0]), label -> host.toast("Выбрано: " + label));
    }

    // диалог нового типа оплаты
    private void showPaymentDialog(MainActivity host) {
        Dialog dialog = host.dialogs.dialog();
        LinearLayout box = host.dialogs.dialogBox();
        box.addView(host.dialogs.title("Добавить тип оплаты", dialog));
        EditText name = host.ui.editField("Название", "", 18, false);
        box.addView(host.ui.formLabel("Название"));
        box.addView(name, new LinearLayout.LayoutParams(-1, host.ui.dp(64)));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, host.ui.dp(60));
        saveLp.setMargins(0, host.ui.dp(16), 0, 0);
        box.addView(host.ui.primaryButton("Сохранить", v -> {
            String payment = name.getText().toString().trim();
            if (payment.isEmpty()) {
                host.toast("Введите название типа оплаты");
                return;
            }
            if (!host.repository.data().paymentTypes.contains(payment)) host.repository.data().paymentTypes.add(payment);
            host.repository.save();
            dialog.dismiss();
            host.showSettings();
        }), saveLp);
        dialog.setContentView(host.dialogs.wrap(box));
        host.dialogs.show(dialog);
        host.showKeyboard(name);
    }

    // выбор цвета для минуса
    private void chooseNegativeColor(MainActivity host) {
        host.showChoiceDialog("Цвет отрицательного баланса", new String[]{"Красный", "Оранжевый", "Фиолетовый"}, label -> {
            if ("Красный".equals(label)) host.repository.data().negativeBalanceColor = "red";
            if ("Оранжевый".equals(label)) host.repository.data().negativeBalanceColor = "orange";
            if ("Фиолетовый".equals(label)) host.repository.data().negativeBalanceColor = "purple";
            host.repository.save();
            host.showSettings();
        });
    }

    // ключ цвета перевожу в нормальное название
    private String negativeColorName(String color) {
        if ("orange".equals(color)) return "Оранжевый";
        if ("purple".equals(color)) return "Фиолетовый";
        return "Красный";
    }

    // архив статей, можно вернуть назад
    private void showArchived(MainActivity host) {
        ArrayList<String> names = new ArrayList<>();
        for (Category category : host.repository.data().categories) if (category.archived) names.add(category.name);
        if (names.isEmpty()) {
            host.dialogs.ready("Архив пуст", "Удаленных статей пока нет.", null);
            return;
        }
        host.showChoiceDialog("Восстановить статью", names.toArray(new String[0]), label -> {
            for (Category category : host.repository.data().categories) {
                if (category.name.equals(label)) category.archived = false;
            }
            host.repository.save();
            host.showSettings();
        });
    }

    // диалог начального баланса
    private void showStartingBalanceDialog(MainActivity host) {
        Dialog dialog = host.dialogs.dialog();
        LinearLayout box = host.dialogs.dialogBox();
        box.addView(host.dialogs.title("Стартовый баланс", dialog));
        box.addView(host.ui.label("Введите сумму, с которой начинается расчет бюджета.", 14, UiKit.MUTED, Typeface.NORMAL));
        EditText amount = host.ui.editField("Сумма", Money.amount(host.repository.data().startingBalance), 22, true);
        LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(-1, host.ui.dp(66));
        amountLp.setMargins(0, host.ui.dp(16), 0, 0);
        box.addView(amount, amountLp);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, host.ui.dp(60));
        saveLp.setMargins(0, host.ui.dp(14), 0, 0);
        box.addView(host.ui.primaryButton("Сохранить", v -> {
            host.repository.data().startingBalance = Money.parse(amount.getText().toString());
            host.repository.save();
            dialog.dismiss();
            host.showSettings();
        }), saveLp);
        dialog.setContentView(host.dialogs.wrap(box));
        host.dialogs.show(dialog);
        host.showKeyboard(amount);
    }

    // временный объект для формы статьи
    private static class CategoryDraft {
        CategoryType type;
    }
}
