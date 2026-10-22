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
package org.apache.fineract.treasury.statement;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * File-level helpers of statement import: text decoding, the file hash and the duplicate-line keys.
 */
public final class StatementFiles {

    private StatementFiles() {}

    /** UTF-8 when the bytes are valid UTF-8, else ISO-8859-1 (many bank exports are Windows-1252). */
    public static String text(final byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            return new String(content, StandardCharsets.ISO_8859_1);
        }
    }

    public static String sha256(final byte[] content) {
        return HexFormat.of().formatHex(digest().digest(content));
    }

    /**
     * One key per line from its dates, amount, reference and description. Identical lines within one file (two equal
     * charges on one day) get different keys through their occurrence number, so only a line already imported from
     * another file collides.
     */
    public static List<String> lineKeys(final List<ParsedStatement.Line> lines) {
        final Map<String, Integer> seen = new HashMap<>();
        final List<String> keys = new ArrayList<>(lines.size());
        for (final ParsedStatement.Line line : lines) {
            final String base = line.transactionDate() + "|" + line.valueDate() + "|" + plain(line.amount()) + "|"
                    + Objects.toString(line.reference(), "") + "|" + Objects.toString(line.description(), "");
            final int occurrence = seen.merge(base, 1, Integer::sum);
            keys.add(HexFormat.of().formatHex(digest().digest((base + "|" + occurrence).getBytes(StandardCharsets.UTF_8))));
        }
        return keys;
    }

    private static String plain(final BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
