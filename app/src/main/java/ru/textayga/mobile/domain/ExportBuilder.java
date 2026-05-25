package ru.textayga.mobile.domain;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import ru.textayga.mobile.data.BudgetRepository;
import ru.textayga.mobile.model.Category;
import ru.textayga.mobile.model.DateRange;
import ru.textayga.mobile.model.FactEntry;
import ru.textayga.mobile.model.PeriodBalance;
import ru.textayga.mobile.model.PlanEntry;

// экспорт вынес отдельно, иначе экран будет огромный
public class ExportBuilder {
    // цвета pdf
    private static final int INK = Color.rgb(7, 18, 47);
    private static final int MUTED = Color.rgb(125, 135, 160);
    private static final int LINE = Color.rgb(220, 225, 235);
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int GREEN = Color.rgb(18, 184, 112);
    private static final int RED = Color.rgb(255, 45, 58);
    private static final int PURPLE = Color.rgb(124, 58, 237);

    private final BudgetRepository repository;
    private final BudgetCalculator calculator;

    // экспорту нужна база плюс расчеты
    public ExportBuilder(BudgetRepository repository, BudgetCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    // csv делаю через excel так лучше ест русский файл
    public String buildCsv(DateRange range, boolean includePlan, boolean includeFact, boolean includeBalance, boolean includeComments) {
        StringBuilder csv = new StringBuilder();
        csv.append("Раздел;Статья;Период;План;Факт;Действие;Комментарий\n");
        for (Category category : repository.data().categories) {
            if (category.archived) continue;
            if (includePlan) appendPlans(csv, category, range, includeComments);
            if (includeFact) appendFacts(csv, category, range, includeComments);
        }
        if (includeBalance) {
            // баланс пишу отдельными строками для excel
            for (PeriodBalance balance : calculator.balanceSeries(Periods.weeksBetween(range.start, range.end))) {
                if (!range.intersects(balance.range)) continue;
                // сюда добавил все новые показатели из аналитики, чтобы выписка не врала по депозитам и займам
                csv.append("Баланс;Итоговый баланс;")
                        .append(escape(balance.label)).append(';')
                        .append(';')
                        .append(Money.amount(balance.closingBalance)).append(';')
                        .append(';')
                        .append("Доходы: ").append(Money.rub(balance.projectedIncome))
                        .append(", Расходы: ").append(Money.rub(balance.projectedExpense))
                        .append(", Депозиты: ").append(Money.rub(balance.projectedDepositAdd - balance.projectedDepositWithdraw))
                        .append(", Займы: ").append(Money.rub(balance.projectedLoanReceive - balance.projectedLoanRepay))
                        .append(", Накопления: ").append(Money.rub(balance.closingSavings))
                        .append(", Задолженность: ").append(Money.rub(balance.closingDebt))
                        .append('\n');
            }
        }
        return csv.toString();
    }

    // xls тут это html, который открывает excel
    public String buildHtmlXls(DateRange range) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset=\"UTF-8\"></head><body>");
        html.append("<h2>TexTayga: выписка</h2>");
        html.append("<p>").append(Periods.fullDate(range.start)).append(" - ").append(Periods.fullDate(range.end)).append("</p>");
        html.append("<table border=\"1\"><tr><th>Раздел</th><th>Статья</th><th>План</th><th>Факт</th></tr>");
        for (Category category : repository.data().categories) {
            if (category.archived) continue;
            double plan = calculator.planForRange(category.id, range);
            double fact = calculator.factNetForRange(category.id, range);
            boolean hasFact = calculator.hasFactForRange(category.id, range);
            if (plan == 0 && !hasFact) continue;
            html.append("<tr><td>").append(htmlEscape(category.type.title)).append("</td><td>").append(htmlEscape(category.name)).append("</td><td>")
                    .append(plan == 0 ? "—" : Money.amount(plan)).append("</td><td>").append(exportFactText(category, fact, hasFact)).append("</td></tr>");
        }
        html.append("</table>");
        html.append("<h3>Баланс по периодам</h3><table border=\"1\"><tr><th>Период</th><th>Доходы</th><th>Расходы</th><th>Депозиты</th><th>Займы</th><th>Накопления</th><th>Задолженность</th><th>Итоговый баланс</th></tr>");
        for (PeriodBalance balance : calculator.balanceSeries(Periods.weeksBetween(range.start, range.end))) {
            if (!range.intersects(balance.range)) continue;
            // таблицу в excel расширил под новую формулу доступного баланса
            html.append("<tr><td>").append(htmlEscape(balance.label)).append("</td><td>")
                    .append(Money.amount(balance.projectedIncome)).append("</td><td>")
                    .append(Money.amount(balance.projectedExpense)).append("</td><td>")
                    .append(Money.amount(balance.projectedDepositAdd - balance.projectedDepositWithdraw)).append("</td><td>")
                    .append(Money.amount(balance.projectedLoanReceive - balance.projectedLoanRepay)).append("</td><td>")
                    .append(Money.amount(balance.closingSavings)).append("</td><td>")
                    .append(Money.amount(balance.closingDebt)).append("</td><td>")
                    .append(Money.amount(balance.closingBalance)).append("</td></tr>");
        }
        html.append("</table></body></html>");
        return html.toString();
    }

    // pdf рисую через canvas
    public byte[] buildPdf(DateRange range) throws IOException {
        PdfDocument document = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int pageNumber = 1;
        PdfDocument.Page page = startPdfPage(document, pageNumber);
        Canvas canvas = page.getCanvas();
        int y = drawPdfHeader(canvas, paint, range);
        y = drawPdfTableHeader(canvas, paint, y);
        for (Category category : repository.data().categories) {
            if (category.archived) continue;
            double plan = calculator.planForRange(category.id, range);
            double fact = calculator.factNetForRange(category.id, range);
            boolean hasFact = calculator.hasFactForRange(category.id, range);
            if (plan == 0 && !hasFact) continue;
            if (y > 790) {
                // если строки закончились по высоте, продолжаю на новом листе
                document.finishPage(page);
                page = startPdfPage(document, ++pageNumber);
                canvas = page.getCanvas();
                y = drawPdfHeader(canvas, paint, range);
                y = drawPdfTableHeader(canvas, paint, y);
            }
            drawPdfCategoryRow(canvas, paint, category, plan, fact, hasFact, y);
            y += 24;
        }
        if (y > 750) {
            // сводку лучше перенести, чем лепить в самый низ страницы
            document.finishPage(page);
            page = startPdfPage(document, ++pageNumber);
            canvas = page.getCanvas();
            y = drawPdfHeader(canvas, paint, range);
        } else {
            y += 16;
        }
        drawPdfSummary(canvas, paint, range, y);
        document.finishPage(page);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // эти байты андроид запишет в файл
        document.writeTo(output);
        document.close();
        return output.toByteArray();
    }

    // один лист pdf, размер a4 в android-точках
    private PdfDocument.Page startPdfPage(PdfDocument document, int pageNumber) {
        return document.startPage(new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create());
    }

    // верх страницы одинаковый на всех листах
    private int drawPdfHeader(Canvas canvas, Paint paint, DateRange range) {
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setColor(INK);
        paint.setTextSize(24);
        canvas.drawText("TexTayga: выписка", 42, 58, paint);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        paint.setTextSize(12);
        paint.setColor(MUTED);
        canvas.drawText(Periods.fullDate(range.start) + " - " + Periods.fullDate(range.end), 42, 82, paint);
        return 122;
    }

    // заголовки таблицы pdf
    private int drawPdfTableHeader(Canvas canvas, Paint paint, int y) {
        paint.setTextSize(13);
        paint.setColor(INK);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        canvas.drawText("Статья", 42, y, paint);
        canvas.drawText("План", 230, y, paint);
        canvas.drawText("Факт", 330, y, paint);
        canvas.drawText("Тип", 430, y, paint);
        y += 12;
        paint.setStrokeWidth(1);
        paint.setColor(LINE);
        canvas.drawLine(42, y, 552, y, paint);
        return y + 24;
    }

    // одна строка статьи в pdf
    private void drawPdfCategoryRow(Canvas canvas, Paint paint, Category category, double plan, double fact, boolean hasFact, int y) {
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        paint.setTextSize(12);
        paint.setColor(INK);
        canvas.drawText(trim(category.name, 24), 42, y, paint);
        paint.setColor(colorFor(category.type.title));
        canvas.drawText(plan == 0 ? "—" : Money.amount(plan), 230, y, paint);
        canvas.drawText(exportFactText(category, fact, hasFact), 330, y, paint);
        canvas.drawText(category.type.title, 430, y, paint);
    }

    // для файлов тоже показываю движение депозитов/займов со знаком
    private String exportFactText(Category category, double fact, boolean hasFact) {
        if (!hasFact) return "—";
        if (category.type == ru.textayga.mobile.model.CategoryType.DEPOSIT || category.type == ru.textayga.mobile.model.CategoryType.LOAN) {
            return (fact >= 0 ? "+" : "-") + Money.amount(Math.abs(fact));
        }
        return Money.amount(fact);
    }

    // сводка в конце файла
    private void drawPdfSummary(Canvas canvas, Paint paint, DateRange range, int y) {
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setColor(BLUE);
        paint.setTextSize(15);
        canvas.drawText("Прогнозный баланс на конец периода: " + Money.rub(calculator.projectedBalanceAt(range.end)), 42, y, paint);
        y += 24;
        paint.setTextSize(12);
        paint.setColor(INK);
        canvas.drawText("Накопления: " + Money.rub(calculator.savingsUpTo(range.end)) + "   Задолженность: " + Money.rub(calculator.debtUpTo(range.end)), 42, y, paint);
    }

    // плановые строки для csv
    private void appendPlans(StringBuilder csv, Category category, DateRange range, boolean includeComments) {
        for (PlanEntry plan : repository.data().plans) {
            if (!plan.categoryId.equals(category.id) || !range.intersects(Periods.rangeForKey(plan.periodKey))) continue;
            csv.append(category.type.title).append(';')
                    .append(escape(category.name)).append(';')
                    .append(escape(Periods.label(plan.periodKey))).append(';')
                    .append(Money.amount(plan.amount)).append(';')
                    .append(';')
                    .append(';')
                    .append(includeComments ? escape(plan.comment) : "")
                    .append('\n');
        }
    }

    // факты для csv
    private void appendFacts(StringBuilder csv, Category category, DateRange range, boolean includeComments) {
        for (FactEntry fact : repository.data().facts) {
            LocalDate date = LocalDate.parse(fact.date, Periods.ISO);
            if (!fact.categoryId.equals(category.id) || !range.contains(date)) continue;
            csv.append(category.type.title).append(';')
                    .append(escape(category.name)).append(';')
                    .append(escape(Periods.label(fact.periodKey))).append(';')
                    .append(';')
                    .append(Money.amount(fact.amount)).append(';')
                    .append(escape(actionTitle(fact))).append(';')
                    .append(includeComments ? escape(fact.comment) : "")
                    .append('\n');
        }
    }

    // для обычных доходов/расходов действие пустое, а для депозитов/займов пишу понятнее
    private String actionTitle(FactEntry fact) {
        return fact.action == null || "NONE".equals(fact.action.name()) ? "" : fact.action.title;
    }

    // чищу текст, чтоб csv не ломался
    private String escape(String value) {
        if (value == null) return "";
        return value.replace(";", ",").replace("\n", " ");
    }

    // html тоже надо чистить, а то название статьи может сломать таблицу
    private String htmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // длинный текст в pdf режу по колонке
    private String trim(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, max - 1) + "…";
    }

    // цвет суммы беру от типа статьи
    private int colorFor(String title) {
        if ("Доход".equals(title)) return GREEN;
        if ("Расход".equals(title)) return RED;
        if ("Заём".equals(title)) return PURPLE;
        return BLUE;
    }
}
