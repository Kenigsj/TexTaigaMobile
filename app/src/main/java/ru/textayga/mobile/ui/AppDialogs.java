package ru.textayga.mobile.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import ru.textayga.mobile.model.PeriodChoice;

// дизайн диалогов
public class AppDialogs {
    private final Activity activity;
    private final UiKit ui;

    public AppDialogs(Activity activity, UiKit ui) {
        this.activity = activity;
        this.ui = ui;
    }

    // создаю пустой dialog без стандартного заголовка android
    public Dialog dialog() {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    // каркас диалога (фон, отступы, все сверху вниз)
    public LinearLayout dialogBox() {
        LinearLayout box = ui.column();
        box.setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(18));
        box.setBackgroundColor(UiKit.BG);
        return box;
    }

    // scrollview нужен, чтоб диалог влез на маленький экран
    public ScrollView wrap(LinearLayout content) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(UiKit.BG);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    // заголовок диалога и крестик
    public LinearLayout title(String title, Dialog dialog) {
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(ui.label(title, 24, UiKit.INK, android.graphics.Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(ui.iconButton("×", v -> dialog.dismiss()), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        return row;
    }

    // показываю диалог почти на всю ширину
    public void show(Dialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
    }

    // диалог для готово/ошибки
    public void ready(String title, String body, Runnable onDone) {
        Dialog dialog = dialog();
        LinearLayout box = dialogBox();
        box.addView(title(title, dialog));
        TextView bodyView = ui.label(body == null ? "" : body, 16, Color.rgb(65, 70, 95), android.graphics.Typeface.NORMAL);
        bodyView.setPadding(0, ui.dp(10), 0, ui.dp(18));
        bodyView.setSingleLine(false);
        box.addView(bodyView);
        box.addView(ui.primaryButton("Готово", v -> {
            dialog.dismiss();
            if (onDone != null) onDone.run();
        }), new LinearLayout.LayoutParams(-1, ui.dp(58)));
        dialog.setContentView(wrap(box));
        show(dialog);
    }

    // простой список вариантов выбора
    public void choices(String title, String[] labels, ChoiceCallback callback) {
        Dialog dialog = dialog();
        LinearLayout box = dialogBox();
        box.addView(title(title, dialog));
        if (labels.length == 0) box.addView(ui.emptyText("Нет доступных вариантов"));
        for (String item : labels) {
            TextView row = ui.fieldButton(item, "");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ui.dp(56));
            lp.setMargins(0, ui.dp(8), 0, 0);
            box.addView(row, lp);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onChoice(((TextView) v).getText().toString().replace("  ˅", "").trim());
            });
        }
        dialog.setContentView(wrap(box));
        show(dialog);
    }

    // список периодов с подсветкой выбранного
    public void periods(String title, List<PeriodChoice> choices, String selected, PeriodCallback callback) {
        Dialog dialog = dialog();
        LinearLayout box = dialogBox();
        box.addView(title(title, dialog));
        for (PeriodChoice choice : choices) {
            boolean active = choice.key.equals(selected);
            TextView row = ui.label(choice.label, 17, active ? UiKit.BLUE : UiKit.INK, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(ui.dp(14), 0, ui.dp(14), 0);
            row.setBackground(ui.bg(active ? UiKit.SOFT_BLUE : Color.WHITE, 8, active ? UiKit.BLUE : UiKit.LINE, active ? 2 : 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ui.dp(56));
            lp.setMargins(0, ui.dp(8), 0, 0);
            box.addView(row, lp);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onChoice(choice);
            });
        }
        dialog.setContentView(wrap(box));
        show(dialog);
    }

    public interface ChoiceCallback {
        void onChoice(String label);
    }

    public interface PeriodCallback {
        void onChoice(PeriodChoice choice);
    }
}
