package com.spendsmart.export;

import static com.spendsmart.export.CsvWriter.number;
import static com.spendsmart.export.CsvWriter.text;

import com.spendsmart.common.Money;
import com.spendsmart.transaction.Transaction;
import java.io.Writer;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TransactionCsvExporter {

    public static final String[] HEADER = {"Date", "Type", "Amount", "Currency", "Category", "Description",
            "Merchant", "Payment method", "Notes", "Sample data", "Transaction ID"};

    public void write(List<Transaction> rows, Writer out) {
        CsvWriter csv = new CsvWriter(out);
        csv.header(HEADER);
        for (Transaction t : rows) {
            csv.row(List.of(
                    text(t.getTransactionDate().toString()),
                    text(t.getType().name()),
                    number(Money.scale(t.getAmount(), t.getCurrency()).toPlainString()),
                    text(t.getCurrency()),
                    text(t.getCategory().getName()),
                    text(t.getDescription()),
                    text(t.getMerchant()),
                    text(t.getPaymentMethod() == null ? null : t.getPaymentMethod().name()),
                    text(t.getNotes()),
                    text(t.isSample() ? "yes" : "no"),
                    text(t.getId().toString())));
        }
    }
}
