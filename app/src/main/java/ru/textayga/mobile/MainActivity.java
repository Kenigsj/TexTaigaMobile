package ru.textayga.mobile;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import ru.textayga.mobile.data.BudgetRepository;
import ru.textayga.mobile.domain.BudgetCalculator;
import ru.textayga.mobile.domain.ExportBuilder;
import ru.textayga.mobile.domain.Money;
import ru.textayga.mobile.domain.Periods;
import ru.textayga.mobile.model.Category;
import ru.textayga.mobile.model.CategoryType;
import ru.textayga.mobile.model.BalanceViewMode;
import ru.textayga.mobile.model.DateRange;
import ru.textayga.mobile.model.ExportFormat;
import ru.textayga.mobile.model.ExportRangeMode;
import ru.textayga.mobile.model.FactAction;
import ru.textayga.mobile.model.FactEntry;
import ru.textayga.mobile.model.PeriodChoice;
import ru.textayga.mobile.model.PlanViewMode;
import ru.textayga.mobile.screens.BalanceScreen;
import ru.textayga.mobile.screens.ExportScreen;
import ru.textayga.mobile.screens.FactScreen;
import ru.textayga.mobile.screens.PlanScreen;
import ru.textayga.mobile.screens.SettingsScreen;
import ru.textayga.mobile.ui.AppDialogs;
import ru.textayga.mobile.ui.NavListener;
import ru.textayga.mobile.ui.NavTarget;
import ru.textayga.mobile.ui.UiKit;

// главная activity, все крутится отсюда
public class MainActivity extends Activity implements NavListener {
    // код запроса для сохранения файла
    private static final int REQ_EXPORT = 4101;

    // сервисы создаю один раз, потом просто таскаю по экранам
    public BudgetRepository repository;
    public BudgetCalculator calculator;
    public ExportBuilder exportBuilder;
    public UiKit ui;
    public AppDialogs dialogs;

    // состояние экранов храню тут. по сути viewmodel, но попроще
    public NavTarget activeTarget = NavTarget.BALANCE;
    public String settingsTab = "categories";
    public CategoryType categoryFilter = null;
    public CategoryType balanceFilter = null;
    public BalanceViewMode balanceViewMode = BalanceViewMode.WEEK;
    // депозиты и займы в бюджете свернуты отдельно, чтобы таблица не становилась простыней
    public boolean balanceDepositsExpanded = false;
    public boolean balanceLoansExpanded = false;
    public CategoryType planFilter = null;
    public PlanViewMode planMode = PlanViewMode.WEEK;
    public String balancePeriodKey;
    public String planPeriodKey;
    public String factPeriodKey;
    public LocalDate factDate = LocalDate.now();
    public String factCategoryId;
    public FactAction factAction = FactAction.NONE;
    public String factPayment = "Карта";
    public String factAmountDraft = "";
    public String factExpressionDraft = "";
    public String factCommentDraft = "";
    public String reconcileRealDraft = "";
    public ExportRangeMode exportRangeMode = ExportRangeMode.MONTH;
    public ExportFormat exportFormat = ExportFormat.PDF;
    public LocalDate customStart = LocalDate.now().minusDays(30);
    public LocalDate customEnd = LocalDate.now();
    public boolean includePlan = true;
    public boolean includeFact = true;
    public boolean includeBalance = true;
    public boolean includeComments = true;

    // запоминаю вертикальный скролл, иначе экран прыгает наверх после showFact/showPlan
    private int balanceScrollY = 0;
    private int planScrollY = 0;
    private int factScrollY = 0;
    private int exportScrollY = 0;
    private int settingsScrollY = 0;
    private NavTarget scrollOwner = NavTarget.BALANCE;

    private byte[] pendingExportBytes;
    private String pendingExportMime;
    private String pendingExportName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // системные панели под светлую тему
        getWindow().setStatusBarColor(UiKit.BG);
        getWindow().setNavigationBarColor(android.graphics.Color.WHITE);
        ui = new UiKit(this);
        dialogs = new AppDialogs(this, ui);
        repository = new BudgetRepository(this);
        // после load база уже в памяти
        repository.load();
        calculator = new BudgetCalculator(repository);
        exportBuilder = new ExportBuilder(repository, calculator);
        ensureSelections();
        showBalance();
    }

    // сначала проверяю выбранное, иначе можно словить null
    public void ensureSelections() {
        LocalDate today = LocalDate.now();
        if (balancePeriodKey == null || !balancePeriodKey.startsWith("M:")) {
            balancePeriodKey = Periods.monthKey(YearMonth.from(today));
        }
        if (planPeriodKey == null) planPeriodKey = Periods.keyFor(today, planMode);
        String factPrefix = repository.data().periodKind == ru.textayga.mobile.model.PeriodKind.WEEK ? "W:" : "M:";
        if (factPeriodKey == null || !factPeriodKey.startsWith(factPrefix)) {
            factPeriodKey = Periods.factKey(today, repository.data().periodKind);
        }
        DateRange factRange = Periods.rangeForKey(factPeriodKey);
        if (factDate == null || !factRange.contains(factDate)) {
            // дату факта держу отдельно, иначе старые операции вставали на начало недели
            factDate = factRange.contains(today) ? today : factRange.start;
        }
        if (factCategoryId == null || repository.findCategory(factCategoryId) == null) {
            // если статья не выбрана или была удалена, ставлю первый расход
            Category category = repository.firstCategory(CategoryType.EXPENSE);
            factCategoryId = category == null ? null : category.id;
        }
        // действие факта всегда подгоняю под выбранный тип статьи
        syncFactAction();
        if (repository.data().paymentTypes.isEmpty()) repository.data().paymentTypes.add("Карта");
        if (factPayment == null || !repository.data().paymentTypes.contains(factPayment)) {
            factPayment = repository.data().paymentTypes.get(0);
        }
    }

    @Override
    // нижнее меню дергает это, дальше выбираю экран
    public void onNavigate(NavTarget target) {
        if (target == NavTarget.BALANCE) showBalance();
        if (target == NavTarget.PLAN) showPlan();
        if (target == NavTarget.FACT) showFact();
        if (target == NavTarget.EXPORT) showExport();
        if (target == NavTarget.SETTINGS) showSettings();
    }

    // пересобираю экран целиком, так меньше мороки
    public void showBalance() {
        showScreen(NavTarget.BALANCE, new BalanceScreen().render(this), true);
    }

    public void showPlan() {
        showScreen(NavTarget.PLAN, new PlanScreen().render(this), true);
    }

    public void showFact() {
        showScreen(NavTarget.FACT, new FactScreen().render(this), true);
    }

    public void showExport() {
        showScreen(NavTarget.EXPORT, new ExportScreen().render(this), true);
    }

    public void showSettings() {
        showScreen(NavTarget.SETTINGS, new SettingsScreen().render(this), true);
    }

    // для деталей бюджета и сверки: старый скролл сохраняю, новый экран открываю сверху
    public void showTemporaryScreen(NavTarget owner, View root) {
        showScreen(owner, root, false);
    }

    // общий показ экрана, чтобы не терять место, где пользователь был до клика
    private void showScreen(NavTarget target, View root, boolean restoreScroll) {
        saveCurrentScroll();
        activeTarget = target;
        scrollOwner = restoreScroll ? target : null;
        setContentView(root);
        if (restoreScroll) restoreScroll(target);
    }

    // перед заменой view забираю scrollY у текущего экрана
    private void saveCurrentScroll() {
        if (scrollOwner == null) return;
        ScrollView scroll = findViewById(UiKit.MAIN_SCROLL_ID);
        if (scroll == null) return;
        putScroll(scrollOwner, scroll.getScrollY());
    }

    // после setContentView жду layout, потом возвращаю прошлую позицию
    private void restoreScroll(NavTarget target) {
        ScrollView scroll = findViewById(UiKit.MAIN_SCROLL_ID);
        if (scroll == null) return;
        int y = getScroll(target);
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    // храню позиции отдельно, потому что у каждой вкладки своя длина экрана
    private void putScroll(NavTarget target, int y) {
        if (target == NavTarget.BALANCE) balanceScrollY = y;
        if (target == NavTarget.PLAN) planScrollY = y;
        if (target == NavTarget.FACT) factScrollY = y;
        if (target == NavTarget.EXPORT) exportScrollY = y;
        if (target == NavTarget.SETTINGS) settingsScrollY = y;
    }

    // вытаскиваю сохраненный scrollY для нужной вкладки
    private int getScroll(NavTarget target) {
        if (target == NavTarget.BALANCE) return balanceScrollY;
        if (target == NavTarget.PLAN) return planScrollY;
        if (target == NavTarget.FACT) return factScrollY;
        if (target == NavTarget.EXPORT) return exportScrollY;
        if (target == NavTarget.SETTINGS) return settingsScrollY;
        return 0;
    }

    public void showPeriodPicker(String title, java.util.List<PeriodChoice> choices, String selected, AppDialogs.PeriodCallback callback) {
        dialogs.periods(title, choices, selected, callback);
    }

    public void setFactPeriod(String periodKey) {
        factPeriodKey = periodKey;
        DateRange range = Periods.rangeForKey(periodKey);
        LocalDate today = LocalDate.now();
        // при смене периода оставляю дату внутри него, иначе факт потом может улететь не туда
        if (factDate == null || !range.contains(factDate)) {
            factDate = range.contains(today) ? today : range.start;
        }
    }

    public void setFactDate(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            // факт на будущее не даю ставить, это уже планирование, а не реальная операция
            date = LocalDate.now();
        }
        factDate = date;
        // период пересобираю от даты, чтобы 9 мая не сохранялось как 4 мая
        factPeriodKey = Periods.factKey(date, repository.data().periodKind);
    }

    // обертка над диалогами, чтоб не дергать appdialogs везде
    public void showChoiceDialog(String title, String[] labels, AppDialogs.ChoiceCallback callback) {
        dialogs.choices(title, labels, callback);
    }

    // в факт даю только неархивные статьи
    public void showCategoryPicker() {
        Dialog dialog = dialogs.dialog();
        LinearLayout box = dialogs.dialogBox();
        box.addView(dialogs.title("Выберите статью", dialog));
        boolean hasItems = false;
        for (Category category : repository.data().categories) {
            if (category.archived) continue;
            hasItems = true;
            box.addView(categoryChoiceRow(dialog, category));
        }
        if (!hasItems) box.addView(ui.emptyText("Нет доступных статей"));
        dialog.setContentView(dialogs.wrap(box));
        dialogs.show(dialog);
    }

    // цветной пункт в выборе статьи, чтобы тип было видно сразу
    private TextView categoryChoiceRow(Dialog dialog, Category category) {
        TextView row = ui.label(category.name + "  ˅", 18, ui.colorFor(category.type), android.graphics.Typeface.BOLD);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(16), 0, ui.dp(16), 0);
        row.setBackground(ui.bg(softCategoryColor(category.type), 8, ui.colorFor(category.type), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ui.dp(58));
        lp.setMargins(0, ui.dp(8), 0, 0);
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> {
            factCategoryId = category.id;
            syncFactAction();
            dialog.dismiss();
            showFact();
        });
        return row;
    }

    // мягкий фон под цвет типа статьи, чтобы не било по глазам
    private int softCategoryColor(CategoryType type) {
        if (type == CategoryType.INCOME) return Color.rgb(235, 252, 244);
        if (type == CategoryType.EXPENSE) return Color.rgb(255, 244, 245);
        if (type == CategoryType.LOAN) return Color.rgb(246, 241, 255);
        return Color.rgb(237, 245, 255);
    }

    // кнопка сохранить факт
    public void saveFact() {
        Category category = repository.findCategory(factCategoryId);
        double amount = Money.parse(factAmountDraft);
        DateRange range = Periods.rangeForKey(factPeriodKey);
        if (category == null) {
            toast("Выберите статью");
            return;
        }
        if (amount <= 0) {
            toast("Введите сумму");
            return;
        }
        FactEntry fact = new FactEntry();
        // операций по одной статье может быть куча, поэтому id
        fact.id = UUID.randomUUID().toString();
        fact.categoryId = category.id;
        fact.categoryType = category.type;
        fact.periodKey = factPeriodKey;
        if (factDate == null || !range.contains(factDate)) {
            // на всякий случай чиню дату перед сохранением, если экран пересобрался криво
            factDate = range.contains(LocalDate.now()) ? LocalDate.now() : range.start;
        }
        fact.date = factDate.toString();
        fact.amount = amount;
        fact.payment = factPayment;
        fact.action = factAction;
        fact.comment = factCommentDraft == null ? "" : factCommentDraft;
        repository.data().facts.add(fact);
        repository.save();
        // после сохранения сбрасываю только сумму и коммент
        factAmountDraft = "";
        factExpressionDraft = "";
        factCommentDraft = "";
        dialogs.ready("Операция сохранена", "Баланс периода пересчитан.", this::showBalance);
    }

    // если выбрали депозит или займ, ставлю нормальное действие для этой статьи
    public void syncFactAction() {
        Category category = repository.findCategory(factCategoryId);
        if (category == null) {
            factAction = FactAction.NONE;
            return;
        }
        if (category.type == CategoryType.DEPOSIT && factAction != FactAction.DEPOSIT_ADD && factAction != FactAction.DEPOSIT_WITHDRAW) {
            factAction = FactAction.DEPOSIT_ADD;
        } else if (category.type == CategoryType.LOAN && factAction != FactAction.LOAN_RECEIVE && factAction != FactAction.LOAN_REPAY) {
            // при выборе кредита сначала показываю "получить займ", это основной сценарий добавления долга
            factAction = FactAction.LOAN_RECEIVE;
        } else if (category.type == CategoryType.INCOME || category.type == CategoryType.EXPENSE) {
            factAction = FactAction.NONE;
        }
    }

    // калькулятор собираю обычной строкой
    public void appendAmount(String key) {
        if ("⌫".equals(key)) {
            if (!factExpressionDraft.isEmpty()) {
                factExpressionDraft = trimExpressionTail(factExpressionDraft);
            } else if (factAmountDraft.length() > 0) {
                factAmountDraft = factAmountDraft.substring(0, factAmountDraft.length() - 1);
            }
        } else if ("×".equals(key) || "−".equals(key) || "+".equals(key)) {
            if (factExpressionDraft.isEmpty()) factExpressionDraft = factAmountDraft;
            factExpressionDraft = appendOperator(factExpressionDraft, key);
        } else if (",".equals(key)) {
            factExpressionDraft = appendDigit(factExpressionDraft, ".");
        } else {
            factExpressionDraft = appendDigit(factExpressionDraft, key);
        }
        if (!factExpressionDraft.isEmpty()) {
            double calculated = evaluateExpression(factExpressionDraft);
            factAmountDraft = calculated == 0 ? "" : Money.amount(calculated).replace(" ", "");
        }
        showFact();
    }

    // добавляю цифру или десятичную точку, следя чтоб точка в числе была одна
    private String appendDigit(String expression, String digit) {
        if (expression == null) expression = "";
        if (".".equals(digit)) {
            String current = currentNumber(expression);
            if (current.contains(".")) return expression;
            return expression + ".";
        }
        return expression + digit;
    }

    // два оператора подряд не нужны, просто заменяю
    private String appendOperator(String expression, String operator) {
        String clean = expression == null ? "" : expression.trim();
        if (clean.isEmpty()) return "";
        if (endsWithOperator(clean)) clean = clean.substring(0, clean.length() - 1).trim();
        return clean + " " + operator + " ";
    }

    // удаляю оператор или последний символ через бэкспейс
    private String trimExpressionTail(String expression) {
        String clean = expression == null ? "" : expression.trim();
        if (clean.isEmpty()) return "";
        if (endsWithOperator(clean)) {
            clean = clean.substring(0, clean.length() - 1).trim();
            return clean;
        }
        return clean.substring(0, clean.length() - 1).trim();
    }

    private boolean endsWithOperator(String expression) {
        return expression.endsWith("+") || expression.endsWith("−") || expression.endsWith("×");
    }

    // текущее число беру после последнего оператора
    private String currentNumber(String expression) {
        String clean = expression == null ? "" : expression.trim();
        int plus = clean.lastIndexOf('+');
        int minus = clean.lastIndexOf('−');
        int multiply = clean.lastIndexOf('×');
        int index = Math.max(plus, Math.max(minus, multiply));
        return index < 0 ? clean : clean.substring(index + 1).trim();
    }

    // считаю слева направо
    private double evaluateExpression(String expression) {
        String clean = expression == null ? "" : expression.trim();
        if (clean.isEmpty()) return 0;
        String[] tokens = clean.split("\\s+");
        double result = Money.parse(tokens[0]);
        String operator = "+";
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            if ("+".equals(token) || "−".equals(token) || "×".equals(token)) {
                operator = token;
                continue;
            }
            double value = Money.parse(token);
            if ("+".equals(operator)) result += value;
            if ("−".equals(operator)) result -= value;
            if ("×".equals(operator)) result *= value;
        }
        return Math.max(0, result);
    }

    // стандартный datepicker
    public void showDatePicker(String title, LocalDate current, DateCallback callback) {
        DatePickerDialog dialog = new DatePickerDialog(this, (DatePicker view, int year, int month, int day) -> {
            callback.onDate(LocalDate.of(year, month + 1, day));
        }, current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth());
        dialog.setTitle(title);
        dialog.show();
    }

    // сначала байты файла, потом отдаю в сохранение
    public void startExport() {
        DateRange range = Periods.exportRange(exportRangeMode, customStart, customEnd);
        String baseName = "TexTayga_" + range.start + "_" + range.end;
        try {
            if (exportFormat == ExportFormat.PDF) {
                // передаю те же галочки, что выбраны на экране выписки
                pendingExportBytes = exportBuilder.buildPdf(range, includePlan, includeFact, includeBalance, includeComments);
                pendingExportMime = "application/pdf";
                pendingExportName = baseName + ".pdf";
            } else if (exportFormat == ExportFormat.CSV) {
                pendingExportBytes = exportBuilder.buildCsv(range, includePlan, includeFact, includeBalance, includeComments).getBytes(StandardCharsets.UTF_8);
                pendingExportMime = "text/csv";
                pendingExportName = baseName + ".csv";
            } else {
                // excel тоже должен собираться по выбранному составу
                pendingExportBytes = exportBuilder.buildHtmlXls(range, includePlan, includeFact, includeBalance, includeComments).getBytes(StandardCharsets.UTF_8);
                pendingExportMime = "application/vnd.ms-excel";
                pendingExportName = baseName + ".xls";
            }
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(pendingExportMime);
            intent.putExtra(Intent.EXTRA_TITLE, pendingExportName);
            startActivityForResult(intent, REQ_EXPORT);
        } catch (IOException exception) {
            dialogs.ready("Не удалось сформировать файл", exception.getMessage(), null);
        }
    }

    @Override
    // сюда приходит uri выбранного файла
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output != null && pendingExportBytes != null) output.write(pendingExportBytes);
                dialogs.ready("Файл готов", "Выписка сохранена в выбранное место.", null);
            } catch (IOException exception) {
                dialogs.ready("Ошибка сохранения", exception.getMessage(), null);
            }
        }
    }

    // клаву показываю с задержкой, иначе фокус иногда тупит
    public void showKeyboard(TextView input) {
        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 200);
    }

    // короткие toast-сообщения
    public void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    // пустой textwatcher, чтоб каждый раз не писать 3 метода
    public static abstract class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    public interface DateCallback {
        void onDate(LocalDate date);
    }
}
