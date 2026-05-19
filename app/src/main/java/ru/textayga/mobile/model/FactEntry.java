package ru.textayga.mobile.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;

// тут "факты", случившиеся операции
public class FactEntry {
    // у факта есть собственный id, т.к. операций по одной статье может быть много
    public String id;
    public String categoryId;
    public CategoryType categoryType;
    public String periodKey;
    public String date;
    public double amount;
    public String payment;
    public String comment;

    // перевожу факт в json для сохранения
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("categoryId", categoryId);
        json.put("categoryType", categoryType.name());
        json.put("periodKey", periodKey);
        json.put("date", date);
        json.put("amount", amount);
        json.put("payment", payment);
        json.put("comment", comment);
        return json;
    }

    // восстанавливаю факт из json. если даты нет, ставлю сегодня, чтоб не падать
    public static FactEntry fromJson(JSONObject json) {
        FactEntry fact = new FactEntry();
        fact.id = json.optString("id");
        fact.categoryId = json.optString("categoryId");
        fact.categoryType = CategoryType.valueOf(json.optString("categoryType", CategoryType.EXPENSE.name()));
        fact.periodKey = json.optString("periodKey");
        fact.date = json.optString("date", LocalDate.now().toString());
        fact.amount = json.optDouble("amount");
        fact.payment = json.optString("payment", "Карта");
        fact.comment = json.optString("comment");
        return fact;
    }
}
