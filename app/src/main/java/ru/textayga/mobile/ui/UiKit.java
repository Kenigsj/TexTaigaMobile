package ru.textayga.mobile.ui;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import ru.textayga.mobile.model.Category;
import ru.textayga.mobile.model.CategoryType;

// свой ui-kit, чтоб не копипастить стили
public class UiKit {
    // цвета приложения
    public static final int BG = Color.rgb(245, 245, 247);
    public static final int CARD = Color.WHITE;
    public static final int INK = Color.rgb(7, 18, 47);
    public static final int MUTED = Color.rgb(125, 135, 160);
    public static final int LINE = Color.rgb(220, 225, 235);
    public static final int BLUE = Color.rgb(37, 99, 235);
    public static final int GREEN = Color.rgb(18, 184, 112);
    public static final int RED = Color.rgb(255, 45, 58);
    public static final int SOFT_BLUE = Color.rgb(237, 245, 255);

    private final Activity activity;

    // activity нужна для view и dp
    public UiKit(Activity activity) {
        this.activity = activity;
    }

    // каркас экрана: заголовок, скролл, нижнее меню
    public Screen screen(String eyebrow, String title, String subtitle, View rightAction, NavTarget active, NavListener listener) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(20));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        if (eyebrow != null) content.addView(label(eyebrow, 18, Color.rgb(205, 208, 214), Typeface.BOLD));

        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, dp(10), 0, dp(2));
        LinearLayout titleBox = column();
        titleBox.addView(label(title, 28, INK, Typeface.BOLD));
        if (subtitle != null && !subtitle.isEmpty()) titleBox.addView(label(subtitle, 14, MUTED, Typeface.NORMAL));
        titleRow.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        if (rightAction != null) titleRow.addView(rightAction);
        content.addView(titleRow);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(bottomNav(active, listener), new LinearLayout.LayoutParams(-1, dp(82)));
        return new Screen(root, content);
    }

    // нижнее меню приложения
    public LinearLayout bottomNav(NavTarget active, NavListener listener) {
        LinearLayout nav = row();
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        nav.setBackgroundColor(Color.WHITE);
        nav.addView(navItem("Бюджет", NavTarget.BALANCE, active, listener), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(navItem("План", NavTarget.PLAN, active, listener), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(navItem("Факт", NavTarget.FACT, active, listener), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(navItem("Выписки", NavTarget.EXPORT, active, listener), new LinearLayout.LayoutParams(0, -1, 1));
        return nav;
    }

    // карточка как на макетах
    public LinearLayout card() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(bg(CARD, 10, LINE, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        card.setLayoutParams(lp);
        return card;
    }

    public LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    public LinearLayout column() {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    // обычный текст, вынес чтоб не настраивать каждый раз
    public TextView label(String text, int sp, int color, int style) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(true);
        view.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        view.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        return view;
    }

    // подпись над полем формы
    public TextView formLabel(String text) {
        TextView view = label(text, 14, MUTED, Typeface.NORMAL);
        view.setPadding(0, dp(14), 0, dp(6));
        return view;
    }

    // главная синяя кнопка
    public TextView primaryButton(String text, View.OnClickListener listener) {
        TextView button = label(text, 17, Color.WHITE, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setSingleLine(true);
        button.setAutoSizeTextTypeUniformWithConfiguration(12, 17, 1, TypedValue.COMPLEX_UNIT_SP);
        button.setBackground(bg(BLUE, 8, BLUE, 1));
        button.setOnClickListener(listener);
        return button;
    }

    // вторичная кнопка с рамкой
    public TextView outlineButton(String text, View.OnClickListener listener) {
        TextView button = label(text, 17, BLUE, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setSingleLine(true);
        button.setAutoSizeTextTypeUniformWithConfiguration(12, 17, 1, TypedValue.COMPLEX_UNIT_SP);
        button.setBackground(bg(Color.WHITE, 8, BLUE, 2));
        button.setOnClickListener(listener);
        return button;
    }

    // кнопка-иконка для заголовка
    public TextView iconButton(String text, View.OnClickListener listener) {
        TextView button = label(text, 28, INK, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setOnClickListener(listener);
        return button;
    }

    // кнопка выбора значения, например месяц или период
    public TextView fieldButton(String text, String icon) {
        TextView button = label((icon == null || icon.isEmpty() ? "" : icon + "  ") + text + "  ˅", 16, INK, Typeface.NORMAL);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setSingleLine(false);
        button.setBackground(bg(Color.WHITE, 8, LINE, 1));
        return button;
    }

    // маленький чип-переключатель
    public TextView chip(String text, boolean active, View.OnClickListener listener) {
        TextView chip = label(text, 16, active ? BLUE : INK, Typeface.NORMAL);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(false);
        chip.setPadding(dp(8), 0, dp(8), 0);
        chip.setBackground(bg(Color.WHITE, 4, active ? BLUE : LINE, active ? 2 : 1));
        chip.setOnClickListener(listener);
        return chip;
    }

    // кнопка калькулятора во вкладке "факт"
    public TextView calcButton(String text) {
        TextView button = label(text, 27, Color.rgb(125, 135, 150), Typeface.NORMAL);
        button.setGravity(Gravity.CENTER);
        button.setBackground(bg(Color.WHITE, 7, LINE, 1));
        return button;
    }

    // один стиль для полей ввода
    public EditText editField(String hint, String text, int sp, boolean bold) {
        EditText input = new EditText(activity);
        input.setText(text == null ? "" : text);
        input.setHint(hint);
        input.setTextSize(sp);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        input.setPadding(dp(16), 0, dp(16), 0);
        input.setSingleLine(false);
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        input.setBackground(bg(Color.WHITE, 8, LINE, 1));
        return input;
    }

    public TextView circleIcon(String text, int color) {
        TextView icon = label(text, 18, Color.WHITE, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(bg(color, 40, color, 1));
        return icon;
    }

    public View categoryIcon(CategoryType type) {
        return new CategoryIconView(activity, type, colorFor(type));
    }

    public View categoryIcon(Category category) {
        if (category != null && category.icon != null && !category.icon.trim().isEmpty()) {
            TextView icon = label(category.icon.trim(), 18, Color.WHITE, Typeface.BOLD);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(bg(colorFor(category.type), 40, colorFor(category.type), 1));
            return icon;
        }
        return categoryIcon(category == null ? CategoryType.EXPENSE : category.type);
    }

    // цвет отрицательного баланса можно выбрать в настройках
    public int negativeColor(String key) {
        if ("orange".equals(key)) return Color.rgb(245, 124, 0);
        if ("purple".equals(key)) return Color.rgb(124, 58, 237);
        return RED;
    }

    public View actionIconButton(String kind, int color, View.OnClickListener listener) {
        LineActionIconView icon = new LineActionIconView(activity, kind, color);
        icon.setOnClickListener(listener);
        return icon;
    }

    public TextView tag(String text, int color) {
        TextView tag = label(text, 11, color, Typeface.NORMAL);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(dp(4), 0, dp(4), 0);
        tag.setSingleLine(false);
        tag.setBackground(bg(Color.TRANSPARENT, 4, color, 1));
        return tag;
    }

    public TextView emptyText(String text) {
        TextView empty = label(text, 15, MUTED, Typeface.NORMAL);
        empty.setPadding(0, dp(18), 0, dp(18));
        return empty;
    }

    // синяя инфо-плашка
    public View infoBanner(String text) {
        TextView banner = label("ⓘ  " + text, 15, Color.rgb(115, 130, 175), Typeface.NORMAL);
        banner.setPadding(dp(12), dp(10), dp(12), dp(10));
        banner.setSingleLine(false);
        banner.setBackground(bg(SOFT_BLUE, 6, Color.rgb(218, 232, 255), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        banner.setLayoutParams(lp);
        return banner;
    }

    // красная плашка для предупреждений
    public View warningCard(String title, String body) {
        LinearLayout card = row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(bg(Color.rgb(255, 246, 246), 8, Color.rgb(255, 200, 205), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        card.setLayoutParams(lp);
        card.addView(label("!", 26, RED, Typeface.BOLD), new LinearLayout.LayoutParams(dp(46), -2));
        LinearLayout textBox = column();
        textBox.addView(label(title, 16, RED, Typeface.BOLD));
        TextView bodyView = label(body, 13, Color.rgb(65, 70, 95), Typeface.NORMAL);
        bodyView.setSingleLine(false);
        textBox.addView(bodyView);
        card.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1));
        return card;
    }

    // фон с радиусом и рамкой
    public GradientDrawable bg(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    // добавляю пробел между элементами linearlayout
    public void gap(LinearLayout row, int dp, boolean vertical) {
        Space space = new Space(activity);
        if (vertical) row.addView(space, new LinearLayout.LayoutParams(1, dp(dp)));
        else row.addView(space, new LinearLayout.LayoutParams(dp(dp), 1));
    }

    // dp в пиксели, иначе размеры будут плавать
    public int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    // цвет статьи зависит от ее типа
    public int colorFor(ru.textayga.mobile.model.CategoryType type) {
        if (type == ru.textayga.mobile.model.CategoryType.INCOME) return GREEN;
        if (type == ru.textayga.mobile.model.CategoryType.EXPENSE) return RED;
        return BLUE;
    }

    // один пункт нижней навигации
    private View navItem(String title, NavTarget target, NavTarget active, NavListener listener) {
        boolean selected = target == active;
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setBackground(bg(selected ? Color.rgb(247, 250, 255) : Color.TRANSPARENT, 10, Color.TRANSPARENT, 0));
        NavIconView iconView = new NavIconView(activity, target, selected);
        TextView text = label(title, 11, selected ? BLUE : Color.rgb(145, 145, 145), Typeface.NORMAL);
        text.setGravity(Gravity.CENTER);
        box.addView(iconView, new LinearLayout.LayoutParams(dp(32), dp(32)));
        box.addView(text);
        box.setOnClickListener(v -> listener.onNavigate(target));
        return box;
    }

    // рисую иконки навигации вручную через canvas, чтоб не тащить отдельные картинки
    private class NavIconView extends View {
        private final NavTarget target;
        private final boolean selected;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        NavIconView(Activity activity, NavTarget target, boolean selected) {
            super(activity);
            this.target = target;
            this.selected = selected;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(2));
        }

        @Override
        // ondraw вызывается android, когда view нужно нарисовать себя
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float p = dp(5);
            paint.setColor(selected ? BLUE : Color.rgb(145, 145, 145));

            if (target == NavTarget.BALANCE) {
                RectF oval = new RectF(p, p, w - p, h - p);
                canvas.drawArc(oval, -90, 300, false, paint);
                canvas.drawLine(w / 2f, h / 2f, w / 2f, p, paint);
                canvas.drawLine(w / 2f, h / 2f, w - p, h / 2f, paint);
            } else if (target == NavTarget.PLAN) {
                RectF rect = new RectF(p + 1, p + dp(3), w - p - 1, h - p);
                canvas.drawRoundRect(rect, dp(4), dp(4), paint);
                canvas.drawLine(p + 1, p + dp(10), w - p - 1, p + dp(10), paint);
                canvas.drawLine(p + dp(7), p, p + dp(7), p + dp(6), paint);
                canvas.drawLine(w - p - dp(7), p, w - p - dp(7), p + dp(6), paint);
            } else if (target == NavTarget.FACT) {
                for (int i = 0; i < 3; i++) {
                    float y = p + dp(6) + i * dp(8);
                    canvas.drawCircle(p + dp(3), y, dp(1), paint);
                    canvas.drawLine(p + dp(9), y, w - p, y, paint);
                }
            } else if (target == NavTarget.EXPORT) {
                RectF tray = new RectF(p + 1, h - p - dp(10), w - p - 1, h - p);
                canvas.drawRoundRect(tray, dp(3), dp(3), paint);
                canvas.drawLine(w / 2f, p + dp(3), w / 2f, h - p - dp(13), paint);
                canvas.drawLine(w / 2f, p + dp(3), w / 2f - dp(6), p + dp(9), paint);
                canvas.drawLine(w / 2f, p + dp(3), w / 2f + dp(6), p + dp(9), paint);
            }
        }
    }

    // иконка категории: круг и простой символ внутри
    private class CategoryIconView extends View {
        private final CategoryType type;
        private final int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CategoryIconView(Activity activity, CategoryType type, int color) {
            super(activity);
            this.type = type;
            this.color = color;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(w, h) / 2f - dp(1);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawCircle(cx, cy, r, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.WHITE);

            if (type == CategoryType.INCOME) {
                RectF bag = new RectF(cx - dp(9), cy - dp(3), cx + dp(9), cy + dp(8));
                canvas.drawRoundRect(bag, dp(3), dp(3), paint);
                canvas.drawLine(cx - dp(5), cy - dp(3), cx - dp(5), cy - dp(8), paint);
                canvas.drawLine(cx + dp(5), cy - dp(3), cx + dp(5), cy - dp(8), paint);
                canvas.drawLine(cx - dp(5), cy - dp(8), cx + dp(5), cy - dp(8), paint);
            } else if (type == CategoryType.EXPENSE) {
                canvas.drawLine(cx - dp(9), cy - dp(6), cx - dp(5), cy + dp(7), paint);
                canvas.drawLine(cx - dp(4), cy + dp(7), cx + dp(8), cy + dp(7), paint);
                canvas.drawLine(cx - dp(6), cy - dp(1), cx + dp(9), cy - dp(1), paint);
                canvas.drawCircle(cx - dp(3), cy + dp(11), dp(1), paint);
                canvas.drawCircle(cx + dp(7), cy + dp(11), dp(1), paint);
            } else {
                canvas.drawLine(cx - dp(9), cy - dp(5), cx + dp(8), cy - dp(5), paint);
                canvas.drawLine(cx + dp(8), cy - dp(5), cx + dp(3), cy - dp(10), paint);
                canvas.drawLine(cx + dp(8), cy - dp(5), cx + dp(3), cy, paint);
                canvas.drawLine(cx + dp(9), cy + dp(6), cx - dp(8), cy + dp(6), paint);
                canvas.drawLine(cx - dp(8), cy + dp(6), cx - dp(3), cy + dp(1), paint);
                canvas.drawLine(cx - dp(8), cy + dp(6), cx - dp(3), cy + dp(11), paint);
            }
        }
    }

    // маленькие иконки редактирования и удаления
    private class LineActionIconView extends View {
        private final String kind;
        private final int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        LineActionIconView(Activity activity, String kind, int color) {
            super(activity);
            this.kind = kind;
            this.color = color;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(2));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            paint.setColor(color);
            if ("delete".equals(kind)) {
                RectF body = new RectF(cx - dp(7), cy - dp(5), cx + dp(7), cy + dp(9));
                canvas.drawRoundRect(body, dp(2), dp(2), paint);
                canvas.drawLine(cx - dp(10), cy - dp(8), cx + dp(10), cy - dp(8), paint);
                canvas.drawLine(cx - dp(4), cy - dp(11), cx + dp(4), cy - dp(11), paint);
                canvas.drawLine(cx - dp(2), cy - dp(2), cx - dp(2), cy + dp(5), paint);
                canvas.drawLine(cx + dp(3), cy - dp(2), cx + dp(3), cy + dp(5), paint);
            } else {
                canvas.drawLine(cx - dp(8), cy + dp(8), cx + dp(7), cy - dp(7), paint);
                canvas.drawLine(cx + dp(4), cy - dp(10), cx + dp(10), cy - dp(4), paint);
                canvas.drawLine(cx - dp(9), cy + dp(9), cx - dp(4), cy + dp(7), paint);
            }
        }
    }

    // возвращаю root и content, так экран сам добавит блоки
    public static class Screen {
        public final View root;
        public final LinearLayout content;

        Screen(View root, LinearLayout content) {
            this.root = root;
            this.content = content;
        }
    }
}
