package ru.textayga.mobile.screens;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import ru.textayga.mobile.MainActivity;
import ru.textayga.mobile.domain.Periods;
import ru.textayga.mobile.model.Category;
import ru.textayga.mobile.model.CategoryType;
import ru.textayga.mobile.ui.NavTarget;
import ru.textayga.mobile.ui.UiKit;

// факт, тут записываю реальную операцию
public class FactScreen implements AppScreen {
    @Override
    public View render(MainActivity host) {
        // перед отрисовкой чиню выбранные значения
        host.ensureSelections();
        Category category = host.repository.findCategory(host.factCategoryId);
        UiKit.Screen screen = host.ui.screen("факт", "Факт", "Внесение фактической операции", host.ui.iconButton("☼", v -> host.showSettings()), NavTarget.FACT, host);
        LinearLayout form = host.ui.card();

        form.addView(selector(host, "Период", Periods.label(host.factPeriodKey), "▣", v -> {
            // период берем из списка недель/месяцев
            host.showPeriodPicker("Выберите период", Periods.factChoices(host.repository.data().periodKind), host.factPeriodKey, choice -> {
                host.factPeriodKey = choice.key;
                host.showFact();
            });
        }));
        form.addView(selector(host, "Статья", category == null ? "Выберите статью" : category.name, "◘", v -> host.showCategoryPicker()));

        EditText amountInput = host.ui.editField("Сумма", host.factAmountDraft, 30, true);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                // если печатать руками, выражение калькулятора сбрасываю
                host.factAmountDraft = editable.toString();
                host.factExpressionDraft = "";
            }
        });
        form.addView(amountInput, new LinearLayout.LayoutParams(-1, host.ui.dp(96)));

        android.widget.TextView calcHint = host.ui.label(host.factExpressionDraft == null || host.factExpressionDraft.isEmpty() ? "1000 + 500 + 300" : host.factExpressionDraft.replace(".", ","), 16, UiKit.MUTED, Typeface.NORMAL);
        calcHint.setPadding(0, host.ui.dp(10), 0, host.ui.dp(8));
        form.addView(calcHint);
        // калькулятор для чеков из нескольких сумм
        addCalculator(host, form);

        android.widget.TextView paymentLabel = host.ui.label("Тип оплаты", 13, UiKit.MUTED, Typeface.NORMAL);
        paymentLabel.setPadding(0, host.ui.dp(10), 0, host.ui.dp(6));
        form.addView(paymentLabel);
        HorizontalScrollView paymentScroll = new HorizontalScrollView(host);
        paymentScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout payments = host.ui.row();
        for (int i = 0; i < host.repository.data().paymentTypes.size(); i++) {
            String payment = host.repository.data().paymentTypes.get(i);
            // тип оплаты это строка из настроек кошельков
            payments.addView(host.ui.chip("▰  " + payment, payment.equals(host.factPayment), v -> {
                host.factPayment = ((android.widget.TextView) v).getText().toString().replace("▰", "").trim();
                host.showFact();
            }), new LinearLayout.LayoutParams(host.ui.dp(142), host.ui.dp(56)));
            if (i < host.repository.data().paymentTypes.size() - 1) host.ui.gap(payments, 10, false);
        }
        paymentScroll.addView(payments, new HorizontalScrollView.LayoutParams(-2, -2));
        form.addView(paymentScroll);

        EditText comment = host.ui.editField("Комментарий", host.factCommentDraft, 16, false);
        comment.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                host.factCommentDraft = editable.toString();
            }
        });
        LinearLayout.LayoutParams commentLp = new LinearLayout.LayoutParams(-1, host.ui.dp(88));
        commentLp.setMargins(0, host.ui.dp(12), 0, 0);
        form.addView(comment, commentLp);

        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, host.ui.dp(62));
        saveLp.setMargins(0, host.ui.dp(12), 0, 0);
        form.addView(host.ui.primaryButton("Сохранить", v -> host.saveFact()), saveLp);
        screen.content.addView(form);
        screen.content.addView(host.ui.infoBanner("После сохранения баланс пересчитается автоматически"));
        return screen.root;
    }

    // одинаковая строка выбора для периода и статьи
    private View selector(MainActivity host, String caption, String value, String icon, View.OnClickListener listener) {
        LinearLayout row = host.ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(host.ui.dp(14), host.ui.dp(10), host.ui.dp(14), host.ui.dp(10));
        row.setBackground(host.ui.bg(android.graphics.Color.WHITE, 8, UiKit.LINE, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, host.ui.dp(82));
        lp.setMargins(0, 0, 0, host.ui.dp(10));
        row.setLayoutParams(lp);
        row.addView(host.ui.circleIcon(icon, UiKit.BLUE), new LinearLayout.LayoutParams(host.ui.dp(44), host.ui.dp(44)));
        host.ui.gap(row, 12, false);
        LinearLayout texts = host.ui.column();
        texts.addView(host.ui.label(caption, 13, UiKit.MUTED, Typeface.NORMAL));
        texts.addView(host.ui.label(value, 17, UiKit.INK, Typeface.NORMAL));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(host.ui.label("›", 36, UiKit.INK, Typeface.NORMAL));
        row.setOnClickListener(listener);
        return row;
    }

    // рисую четыре строки кнопок калькулятора
    private void addCalculator(MainActivity host, LinearLayout form) {
        String[][] keys = {{"7", "8", "9", "⌫"}, {"4", "5", "6", "×"}, {"1", "2", "3", "−"}, {"0", "00", ",", "+"}};
        for (String[] rowKeys : keys) {
            LinearLayout row = host.ui.row();
            row.setPadding(0, 0, 0, host.ui.dp(8));
            for (int i = 0; i < rowKeys.length; i++) {
                android.widget.TextView button = host.ui.calcButton(rowKeys[i]);
                button.setOnClickListener(v -> host.appendAmount(((android.widget.TextView) v).getText().toString()));
                row.addView(button, new LinearLayout.LayoutParams(0, host.ui.dp(54), 1));
                if (i < rowKeys.length - 1) host.ui.gap(row, 8, false);
            }
            form.addView(row);
        }
    }
}
