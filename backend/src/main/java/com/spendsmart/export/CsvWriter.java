package com.spendsmart.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;

/**
 * RFC 4180 CSV writer with spreadsheet formula-injection protection.
 *
 * <p>Text cells beginning with {@code = + - @}, tab or carriage return are prefixed with an apostrophe so Excel,
 * LibreOffice and Google Sheets treat them as text rather than formulas (OWASP "CSV Injection"). Cells containing a
 * comma, quote, CR or LF are quoted and embedded quotes doubled. Numeric cells produced by the application itself
 * are written through {@link #number} and are not prefixed, so negative values stay numeric.
 */
public final class CsvWriter {

    private final Writer out;

    public CsvWriter(Writer out) {
        this.out = out;
    }

    public void row(List<Cell> cells) {
        try {
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0) {
                    out.write(',');
                }
                out.write(cells.get(i).render());
            }
            out.write("\r\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void header(String... names) {
        row(java.util.Arrays.stream(names).map(CsvWriter::text).toList());
    }

    public static Cell text(String value) {
        return new Cell(value, false);
    }

    /** For values the server generated as numbers (amounts, counts); never for user-entered text. */
    public static Cell number(String value) {
        return new Cell(value, true);
    }

    public record Cell(String value, boolean trustedNumber) {

        String render() {
            if (value == null || value.isEmpty()) {
                return "";
            }
            String v = trustedNumber ? value : neutraliseFormula(value);
            boolean needsQuotes = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0
                    || v.indexOf('\r') >= 0 || !v.equals(v.strip());
            return needsQuotes ? '"' + v.replace("\"", "\"\"") + '"' : v;
        }
    }

    static String neutraliseFormula(String value) {
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
