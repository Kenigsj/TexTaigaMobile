package ru.textayga.mobile.model;

import org.json.JSONException;
import org.json.JSONObject;

// тут план, ожидаемая сумма по статье и периоду
public class PlanEntry {
    // не храню id, т.к. уникальность задается парой categoryid + periodkey
    public String categoryId;
    public String periodKey;
    public double amount;
    public String comment;

    // сохраняю план в json
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("categoryId", categoryId);
        json.put("periodKey", periodKey);
        json.put("amount", amount);
        json.put("comment", comment);
        return json;
    }

    // читаю план из json
    public static PlanEntry fromJson(JSONObject json) {
        PlanEntry plan = new PlanEntry();
        plan.categoryId = json.optString("categoryId");
        plan.periodKey = json.optString("periodKey");
        plan.amount = json.optDouble("amount");
        plan.comment = json.optString("comment");
        return plan;
    }
}
