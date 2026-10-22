/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.treasury.liquidity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The regulatory liquidity return from a ratios run, in a provisional MDI 110A-style layout (pending MICROPAY's Q7):
 * for each ratio reported, A its numerator components (liquid assets) and total, B its denominator components (deposit
 * liabilities) and total, C the ratio, the limit and the status. The CSV is built from the stored lines only, so a
 * stored return exports the same every time.
 */
public final class ReturnBuilder {

    public static final String LAYOUT = "MDI 110A (provisional, pending MICROPAY Q7)";

    private ReturnBuilder() {}

    public record Ratio(String code, String name, String numeratorLabel, String denominatorLabel, BigDecimal numerator,
            BigDecimal denominator, BigDecimal result, BigDecimal limit, String status, List<Component> numeratorComponents,
            List<Component> denominatorComponents) {
    }

    public record Component(String label, BigDecimal eligible) {
    }

    public record Line(int seq, String section, String lineCode, String label, BigDecimal amount, String text) {
    }

    public static List<Line> lines(final List<Ratio> ratios) {
        final List<Line> lines = new ArrayList<>();
        int seq = 0;
        for (final Ratio ratio : ratios) {
            int n = 0;
            for (final Component component : ratio.numeratorComponents()) {
                lines.add(new Line(++seq, "A", ratio.code() + ".A" + (++n), component.label(), component.eligible(), null));
            }
            lines.add(new Line(++seq, "A", ratio.code() + ".A.TOTAL", "Total " + label(ratio.numeratorLabel(), "numerator"),
                    ratio.numerator(), null));
            n = 0;
            for (final Component component : ratio.denominatorComponents()) {
                lines.add(new Line(++seq, "B", ratio.code() + ".B" + (++n), component.label(), component.eligible(), null));
            }
            lines.add(new Line(++seq, "B", ratio.code() + ".B.TOTAL", "Total " + label(ratio.denominatorLabel(), "denominator"),
                    ratio.denominator(), null));
            lines.add(new Line(++seq, "C", ratio.code() + ".C1", ratio.name() + " (%)", ratio.result(), null));
            lines.add(new Line(++seq, "C", ratio.code() + ".C2", "Limit (%)", ratio.limit(), null));
            lines.add(new Line(++seq, "C", ratio.code() + ".C3", "Status", null, ratio.status()));
        }
        return lines;
    }

    /** CSV of the header fields and the lines; amounts with the given decimals. */
    public static String csv(final List<String[]> header, final List<Line> lines, final int scale) {
        final StringBuilder out = new StringBuilder();
        for (final String[] field : header) {
            out.append(quote("#")).append(',').append(quote(field[0])).append(',').append(quote(field[1])).append("\r\n");
        }
        out.append("Section,Line,Label,Amount\r\n");
        for (final Line line : lines) {
            out.append(quote(line.section())).append(',').append(quote(line.lineCode())).append(',').append(quote(line.label())).append(',')
                    .append(line.amount() != null ? line.amount().setScale(scale, RoundingMode.HALF_UP).toPlainString()
                            : quote(line.text() == null ? "" : line.text()))
                    .append("\r\n");
        }
        return out.toString();
    }

    private static String label(final String label, final String fallback) {
        return label == null || label.isBlank() ? fallback : label.toLowerCase(java.util.Locale.ROOT);
    }

    private static String quote(final String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
