package com.spendsmart.export;

import static com.spendsmart.export.CsvWriter.number;
import static com.spendsmart.export.CsvWriter.text;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvWriterTest {

    private static String render(CsvWriter.Cell... cells) {
        StringWriter out = new StringWriter();
        new CsvWriter(out).row(List.of(cells));
        return out.toString();
    }

    @Test
    void quotesAndEscapesSpecialCharacters() {
        assertThat(render(text("plain"), text("a,b"), text("say \"hi\""), text("line1\nline2")))
                .isEqualTo("plain,\"a,b\",\"say \"\"hi\"\"\",\"line1\nline2\"\r\n");
    }

    @Test
    void emptyAndNullCellsAreEmpty() {
        assertThat(render(text(null), text(""), text("x"))).isEqualTo(",,x\r\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+SUM(A1)", "-2+3", "@cmd", "\tTAB", "\rCR"})
    void neutralisesSpreadsheetFormulaPrefixes(String dangerous) {
        String rendered = render(text(dangerous));
        assertThat(rendered.replace("\"", "")).startsWith("'");
    }

    @Test
    void trustedNumbersStayNumeric() {
        assertThat(render(number("-12.50"), number("100"))).isEqualTo("-12.50,100\r\n");
    }

    @Test
    void preservesSignificantSurroundingWhitespaceByQuoting() {
        assertThat(render(text(" padded "))).isEqualTo("\" padded \"\r\n");
    }
}
