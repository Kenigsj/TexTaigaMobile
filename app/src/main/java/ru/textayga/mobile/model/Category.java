package ru.textayga.mobile.model;

import org.json.JSONException;
import org.json.JSONObject;

// статья бюджета, например зарплата или еда
public class Category {
    // id нужен, чтоб планы и факты не ломались при переименовании статьи
    public String id;
    public String name;
    public String description;
    public String keywords;
    public String icon;
    public CategoryType type;
    public boolean archived;

    // статью превращаю в json
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("description", description);
        json.put("keywords", keywords);
        json.put("icon", icon);
        json.put("type", type.name());
        json.put("archived", archived);
        return json;
    }

    // читаю статью из json, optstring сейвит старые базы
    public static Category fromJson(JSONObject json) {
        Category category = new Category();
        category.id = json.optString("id");
        category.name = json.optString("name");
        category.description = json.optString("description");
        category.keywords = json.optString("keywords");
        category.icon = json.optString("icon", "");
        category.type = CategoryType.valueOf(json.optString("type", CategoryType.EXPENSE.name()));
        category.archived = json.optBoolean("archived");
        return category;
    }
}
