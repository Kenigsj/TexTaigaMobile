package ru.textayga.mobile.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

import ru.textayga.mobile.model.BudgetData;
import ru.textayga.mobile.model.Category;
import ru.textayga.mobile.model.CategoryType;
import ru.textayga.mobile.model.FactEntry;
import ru.textayga.mobile.model.PeriodKind;
import ru.textayga.mobile.model.PlanEntry;

// репозиторий между экранами и сохранением
public class BudgetRepository {
    // имя sharedpreferences
    private static final String PREFS = "textayga-budget";
    // весь бюджет одним json
    private static final String KEY_DATA = "data";

    // preferences читает и пишет на телефоне
    private final SharedPreferences preferences;
    // data - текущая модель приложения
    private final BudgetData data = new BudgetData();

    // локальное хранилище android
    public BudgetRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // отдаю общую модель
    public BudgetData data() {
        return data;
    }

    // читаю json и собираю модель назад
    public void load() {
        // сначала очищаю списки
        data.categories.clear();
        data.plans.clear();
        data.facts.clear();
        data.paymentTypes.clear();
        data.startingBalance = 0;
        data.defaultDifferenceCategoryId = "";
        String raw = preferences.getString(KEY_DATA, null);
        if (raw != null) {
            try {
                // разбираю главный json на настройки, статьи, планы, факты
                JSONObject root = new JSONObject(raw);
                data.periodKind = PeriodKind.valueOf(root.optString("periodKind", PeriodKind.WEEK.name()));
                data.startingBalance = root.optDouble("startingBalance", 0);
                data.negativeBalanceColor = root.optString("negativeBalanceColor", "red");
                data.defaultDifferenceCategoryId = root.optString("defaultDifferenceCategoryId", "");
                JSONArray categories = root.optJSONArray("categories");
                if (categories != null) {
                    for (int i = 0; i < categories.length(); i++) {
                        // статья сама умеет читаться из json
                        data.categories.add(Category.fromJson(categories.getJSONObject(i)));
                    }
                }
                JSONArray plans = root.optJSONArray("plans");
                if (plans != null) {
                    for (int i = 0; i < plans.length(); i++) {
                        data.plans.add(PlanEntry.fromJson(plans.getJSONObject(i)));
                    }
                }
                JSONArray facts = root.optJSONArray("facts");
                if (facts != null) {
                    for (int i = 0; i < facts.length(); i++) {
                        data.facts.add(FactEntry.fromJson(facts.getJSONObject(i)));
                    }
                }
                JSONArray paymentTypes = root.optJSONArray("paymentTypes");
                if (paymentTypes != null) {
                    for (int i = 0; i < paymentTypes.length(); i++) {
                        String payment = paymentTypes.optString(i);
                        // пустые и дубли типов оплаты не добавляю
                        if (!payment.trim().isEmpty() && !data.paymentTypes.contains(payment)) data.paymentTypes.add(payment);
                    }
                }
            } catch (Exception ignored) {
                // если json сломался, стираю data, вместо падения приложения
                data.categories.clear();
                data.plans.clear();
                data.facts.clear();
                data.paymentTypes.clear();
                data.periodKind = PeriodKind.WEEK;
                data.startingBalance = 0;
                data.negativeBalanceColor = "red";
                data.defaultDifferenceCategoryId = "";
            }
        }
        ensurePaymentTypes();
        if (data.categories.isEmpty()) {
            // первый запуск - создаю базовые статьи
            seedCategories();
            save();
        }
    }

    // сохраняю всю модель обратно в json
    public void save() {
        try {
            JSONObject root = new JSONObject();
            root.put("periodKind", data.periodKind.name());
            root.put("startingBalance", data.startingBalance);
            root.put("negativeBalanceColor", data.negativeBalanceColor);
            // id статьи для сверки сохраняю отдельно от списка статей
            root.put("defaultDifferenceCategoryId", data.defaultDifferenceCategoryId);
            JSONArray categories = new JSONArray();
            // каждую статью кладу в json-массив
            for (Category category : data.categories) categories.put(category.toJson());
            root.put("categories", categories);
            JSONArray plans = new JSONArray();
            for (PlanEntry plan : data.plans) plans.put(plan.toJson());
            root.put("plans", plans);
            JSONArray facts = new JSONArray();
            for (FactEntry fact : data.facts) facts.put(fact.toJson());
            root.put("facts", facts);
            JSONArray paymentTypes = new JSONArray();
            for (String payment : data.paymentTypes) paymentTypes.put(payment);
            root.put("paymentTypes", paymentTypes);
            preferences.edit().putString(KEY_DATA, root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    // по id нахожу статью для факта или плана
    public Category findCategory(String id) {
        for (Category category : data.categories) {
            if (category.id.equals(id)) return category;
        }
        return null;
    }

    // первая активная статья идет по умолчанию
    public Category firstCategory(CategoryType type) {
        for (Category category : data.categories) {
            if (!category.archived && category.type == type) return category;
        }
        return null;
    }

    // автоподбор статьи по тексту операции
    public Category matchCategory(String text) {
        String normalized = text == null ? "" : text.toLowerCase();
        for (Category category : data.categories) {
            if (category.archived) continue;
            String[] words = (category.keywords == null ? "" : category.keywords).toLowerCase().split(",");
            for (String word : words) {
                String trimmed = word.trim();
                // ключевое слово нашлось - статья подходит
                if (!trimmed.isEmpty() && normalized.contains(trimmed)) return category;
            }
        }
        Category fallback = firstCategory(CategoryType.EXPENSE);
        return fallback == null && !data.categories.isEmpty() ? data.categories.get(0) : fallback;
    }

    // стартовые статьи, чтоб не было пусто
    private void seedCategories() {
        addSeed("Зарплата", "Основной доход", "зарплата, salary, аванс", "₽", CategoryType.INCOME);
        addSeed("Подработка", "Дополнительный доход", "фриланс, перевод", "+", CategoryType.INCOME);
        addSeed("Еда", "Продукты и супермаркеты", "магазин, пятерочка, магнит, продукты", "🛒", CategoryType.EXPENSE);
        addSeed("Транспорт", "Такси, метро, автобус, топливо", "такси, метро, бензин", "🚕", CategoryType.EXPENSE);
        addSeed("Аренда", "Жилье", "аренда, квартира", "⌂", CategoryType.EXPENSE);
        addSeed("Коммунальные платежи", "ЖКХ", "жкх, коммунальные", "⌁", CategoryType.EXPENSE);
        addSeed("Интернет", "Связь", "интернет, провайдер", "⌁", CategoryType.EXPENSE);
        addSeed("Мобильная связь", "Телефон", "мобильная связь, телефон", "☎", CategoryType.EXPENSE);
        addSeed("Развлечения", "Досуг", "кино, игры, подписка", "★", CategoryType.EXPENSE);
        addSeed("Неучтенные операции", "Корректировка сверки", "корректировка", "!", CategoryType.EXPENSE);
        addSeed("Подушка безопасности", "Накопления", "депозит, накопления, подушка", "□", CategoryType.DEPOSIT);
        addSeed("Кредит", "Задолженность", "кредит, займ, долг", "▭", CategoryType.LOAN);
    }

    // метод, чтоб не собирать category каждый раз руками
    private void addSeed(String name, String description, String keywords, String icon, CategoryType type) {
        Category category = new Category();
        category.id = UUID.randomUUID().toString();
        category.name = name;
        category.description = description;
        category.keywords = keywords;
        category.icon = icon;
        category.type = type;
        // начальную сумму можно потом поставить при создании займа
        category.initialAmount = 0;
        data.categories.add(category);
    }

    // карту и наличные восстанавливаю всегда
    private void ensurePaymentTypes() {
        // депозит теперь статья, а не тип оплаты
        data.paymentTypes.remove("Депозит");
        if (!data.paymentTypes.contains("Карта")) data.paymentTypes.add("Карта");
        if (!data.paymentTypes.contains("Наличные")) data.paymentTypes.add("Наличные");
    }
}
