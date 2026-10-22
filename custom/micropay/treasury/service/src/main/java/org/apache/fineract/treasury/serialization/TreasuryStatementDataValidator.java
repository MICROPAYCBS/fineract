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
package org.apache.fineract.treasury.serialization;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.ACTIVE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.AMOUNT_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.BALANCE_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.CLOSING_BALANCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.CONTENT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.CREDIT_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DATE_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DATE_FORMAT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DEBIT_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DECIMAL_SEPARATOR_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DELIMITER_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DESCRIPTION_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.EXPLAIN_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.FILE_NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.FORMAT_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.FORMAT_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.FORMAT_TYPE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.GL_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.HEADER_ROWS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.INVERT_AMOUNT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.JOURNAL_ENTRY_IDS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.LINE_IDS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MATCH_CREATE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MAX_STATEMENT_FILE_BYTES;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.NOTE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.OPENING_BALANCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.REASON_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.REASON_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.REFERENCE_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.STATEMENT_IMPORT_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TRANSACTION_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_RECON_MATCH_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_STATEMENT_FORMAT_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_STATEMENT_LINE_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_STATEMENT_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.UNMATCH_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.VALUE_DATE_COLUMN_PARAM;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.domain.TreasuryStatementFormatType;
import org.springframework.stereotype.Component;

/**
 * Field-level validation of statement imports, manual matches, unmatches, explanations and statement formats.
 */
@Component
@RequiredArgsConstructor
public class TreasuryStatementDataValidator {

    private static final String CODE_PATTERN = "[A-Za-z0-9_\\-]{1,50}";
    private static final List<String> FORMAT_TYPES = Arrays.stream(TreasuryStatementFormatType.values()).map(Enum::name).toList();

    private final FromJsonHelper fromApiJsonHelper;

    public record ImportRequest(Long treasuryAccountId, String formatCode, String fileName, byte[] content, BigDecimal openingBalance,
            BigDecimal closingBalance) {
    }

    public record MatchRequest(Set<Long> lineIds, Set<Long> journalEntryIds, String note) {
    }

    /** {@code reasonCode} is the code value name (BANK_CHARGE) or its id. */
    public record ExplainRequest(String reasonCode, String note, Long glAccountId, LocalDate transactionDate) {
    }

    /** Only the fields present are changed on update. */
    public record FormatRequest(Set<String> parametersPassed, String code, String name, TreasuryStatementFormatType formatType,
            String delimiter, Integer headerRows, String dateFormat, String decimalSeparator, String dateColumn, String valueDateColumn,
            String descriptionColumn, String referenceColumn, String debitColumn, String creditColumn, String amountColumn,
            String balanceColumn, Boolean invertAmount, Boolean active) {

        public boolean has(final String parameterName) {
            return this.parametersPassed.contains(parameterName);
        }
    }

    public ImportRequest validateImport(final String json) {
        final JsonObject object = parse(json, STATEMENT_IMPORT_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(TREASURY_STATEMENT_RESOURCE_NAME);
        final Long accountId = this.fromApiJsonHelper.extractLongNamed(TREASURY_ACCOUNT_ID_PARAM, object);
        final String formatCode = trimmed(object, FORMAT_CODE_PARAM);
        final String fileName = trimmed(object, FILE_NAME_PARAM);
        final String content = trimmed(object, CONTENT_PARAM);
        validator.reset().parameter(TREASURY_ACCOUNT_ID_PARAM).value(accountId).notNull().longGreaterThanZero();
        validator.reset().parameter(FORMAT_CODE_PARAM).value(formatCode).notBlank().notExceedingLengthOf(50);
        validator.reset().parameter(FILE_NAME_PARAM).value(fileName).ignoreIfNull().notExceedingLengthOf(255);
        validator.reset().parameter(CONTENT_PARAM).value(content).notBlank();
        byte[] bytes = null;
        if (content != null) {
            try {
                bytes = Base64.getMimeDecoder().decode(content);
            } catch (IllegalArgumentException e) {
                validator.reset().parameter(CONTENT_PARAM).failWithCode("not.base64");
            }
            if (bytes != null && bytes.length == 0) {
                validator.reset().parameter(CONTENT_PARAM).failWithCode("empty");
            }
            if (bytes != null && bytes.length > MAX_STATEMENT_FILE_BYTES) {
                validator.reset().parameter(CONTENT_PARAM).failWithCode("too.large", MAX_STATEMENT_FILE_BYTES);
            }
        }
        final BigDecimal opening = amount(object, OPENING_BALANCE_PARAM);
        final BigDecimal closing = amount(object, CLOSING_BALANCE_PARAM);
        throwIfAny(errors);
        return new ImportRequest(accountId, formatCode.toUpperCase(Locale.ROOT), fileName, bytes, opening, closing);
    }

    public MatchRequest validateMatch(final String json) {
        final JsonObject object = parse(json, MATCH_CREATE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(TREASURY_RECON_MATCH_RESOURCE_NAME);
        final Set<Long> lineIds = ids(object, LINE_IDS_PARAM, validator);
        final Set<Long> entryIds = ids(object, JOURNAL_ENTRY_IDS_PARAM, validator);
        final String note = trimmed(object, NOTE_PARAM);
        if (lineIds.isEmpty()) {
            validator.reset().parameter(LINE_IDS_PARAM).failWithCode("empty");
        }
        if (entryIds.isEmpty()) {
            validator.reset().parameter(JOURNAL_ENTRY_IDS_PARAM).failWithCode("empty");
        }
        validator.reset().parameter(NOTE_PARAM).value(note).ignoreIfNull().notExceedingLengthOf(500);
        throwIfAny(errors);
        return new MatchRequest(lineIds, entryIds, note);
    }

    public String validateUnmatch(final String json) {
        final JsonObject object = parse(json, UNMATCH_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final String reason = trimmed(object, REASON_PARAM);
        new DataValidatorBuilder(errors).resource(TREASURY_RECON_MATCH_RESOURCE_NAME).parameter(REASON_PARAM).value(reason).notBlank()
                .notExceedingLengthOf(500);
        throwIfAny(errors);
        return reason;
    }

    public ExplainRequest validateExplain(final String json) {
        final JsonObject object = parse(json, EXPLAIN_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(TREASURY_STATEMENT_LINE_RESOURCE_NAME);
        final String reasonCode = trimmed(object, REASON_CODE_PARAM);
        final String note = trimmed(object, NOTE_PARAM);
        final Long glAccountId = this.fromApiJsonHelper.extractLongNamed(GL_ACCOUNT_ID_PARAM, object);
        final LocalDate date = date(object, TRANSACTION_DATE_PARAM, validator);
        validator.reset().parameter(REASON_CODE_PARAM).value(reasonCode).notBlank().notExceedingLengthOf(100);
        validator.reset().parameter(NOTE_PARAM).value(note).ignoreIfNull().notExceedingLengthOf(500);
        validator.reset().parameter(GL_ACCOUNT_ID_PARAM).value(glAccountId).ignoreIfNull().longGreaterThanZero();
        if (date != null && glAccountId == null) {
            validator.reset().parameter(TRANSACTION_DATE_PARAM).failWithCode("only.with.glAccountId");
        }
        throwIfAny(errors);
        return new ExplainRequest(reasonCode, note, glAccountId, date);
    }

    public FormatRequest validateFormat(final String json, final boolean create) {
        final JsonObject object = parse(json, FORMAT_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(TREASURY_STATEMENT_FORMAT_RESOURCE_NAME);
        final String code = trimmed(object, CODE_PARAM);
        final String name = trimmed(object, NAME_PARAM);
        final String type = trimmed(object, FORMAT_TYPE_PARAM);
        final String delimiter = raw(object, DELIMITER_PARAM);
        final String decimal = trimmed(object, DECIMAL_SEPARATOR_PARAM);
        final Integer headerRows = this.fromApiJsonHelper.extractIntegerWithLocaleNamed(HEADER_ROWS_PARAM, object);
        final String dateFormat = trimmed(object, DATE_FORMAT_PARAM);
        if (create) {
            validator.reset().parameter(CODE_PARAM).value(code).notBlank().matchesRegularExpression(CODE_PATTERN,
                    "The parameter `code` may hold letters, digits, - and _ only, 50 at most");
            validator.reset().parameter(NAME_PARAM).value(name).notBlank().notExceedingLengthOf(150);
            validator.reset().parameter(FORMAT_TYPE_PARAM).value(type == null ? null : type.toUpperCase(Locale.ROOT)).notBlank()
                    .isOneOfTheseStringValues(FORMAT_TYPES);
        } else {
            if (object.has(CODE_PARAM) || object.has(FORMAT_TYPE_PARAM)) {
                validator.reset().parameter(object.has(CODE_PARAM) ? CODE_PARAM : FORMAT_TYPE_PARAM).failWithCode("cannot.be.changed");
            }
            if (object.has(NAME_PARAM)) {
                validator.reset().parameter(NAME_PARAM).value(name).notBlank().notExceedingLengthOf(150);
            }
            if (object.keySet().isEmpty()) {
                validator.reset().anyOfNotNull();
            }
        }
        if (delimiter != null && delimiter.length() != 1) {
            validator.reset().parameter(DELIMITER_PARAM).value(delimiter).failWithCode("must.be.one.character");
        }
        if (decimal != null && !".".equals(decimal) && !",".equals(decimal)) {
            validator.reset().parameter(DECIMAL_SEPARATOR_PARAM).value(decimal).failWithCode("must.be.dot.or.comma");
        }
        if (object.has(HEADER_ROWS_PARAM)) {
            validator.reset().parameter(HEADER_ROWS_PARAM).value(headerRows).notNull().integerZeroOrGreater().notGreaterThanMax(20);
        }
        if (dateFormat != null) {
            try {
                DateTimeFormatter.ofPattern(dateFormat);
            } catch (IllegalArgumentException e) {
                validator.reset().parameter(DATE_FORMAT_PARAM).value(dateFormat).failWithCode("invalid.pattern");
            }
        }
        final Boolean invert = this.fromApiJsonHelper.extractBooleanNamed(INVERT_AMOUNT_PARAM, object);
        final Boolean active = this.fromApiJsonHelper.extractBooleanNamed(ACTIVE_PARAM, object);
        throwIfAny(errors);
        return new FormatRequest(Set.copyOf(object.keySet()), code == null ? null : code.toUpperCase(Locale.ROOT), name,
                type == null ? null : TreasuryStatementFormatType.valueOf(type.toUpperCase(Locale.ROOT)), delimiter, headerRows, dateFormat,
                decimal, trimmed(object, DATE_COLUMN_PARAM), trimmed(object, VALUE_DATE_COLUMN_PARAM),
                trimmed(object, DESCRIPTION_COLUMN_PARAM), trimmed(object, REFERENCE_COLUMN_PARAM), trimmed(object, DEBIT_COLUMN_PARAM),
                trimmed(object, CREDIT_COLUMN_PARAM), trimmed(object, AMOUNT_COLUMN_PARAM), trimmed(object, BALANCE_COLUMN_PARAM), invert,
                active);
    }

    private JsonObject parse(final String json, final Set<String> supportedParameters) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        if (element == null || !element.isJsonObject()) {
            throw new InvalidJsonException();
        }
        final JsonObject object = element.getAsJsonObject();
        this.fromApiJsonHelper.checkForUnsupportedParameters(object, supportedParameters);
        return object;
    }

    private static Set<Long> ids(final JsonObject object, final String parameterName, final DataValidatorBuilder validator) {
        final Set<Long> ids = new LinkedHashSet<>();
        final JsonElement value = object.get(parameterName);
        if (value == null || value.isJsonNull()) {
            return ids;
        }
        if (!value.isJsonArray()) {
            validator.reset().parameter(parameterName).failWithCode("not.an.array");
            return ids;
        }
        final JsonArray array = value.getAsJsonArray();
        for (final JsonElement element : array) {
            try {
                final long id = element.getAsLong();
                if (id <= 0 || !ids.add(id)) {
                    validator.reset().parameter(parameterName).value(id).failWithCode(id <= 0 ? "not.greater.than.zero" : "duplicate");
                }
            } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException e) {
                validator.reset().parameter(parameterName).value(element.toString()).failWithCode("not.an.id");
            }
        }
        return ids;
    }

    private static BigDecimal amount(final JsonObject object, final String parameterName) {
        final JsonElement value = object.get(parameterName);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsBigDecimal();
        } catch (NumberFormatException | UnsupportedOperationException e) {
            final List<ApiParameterError> errors = new ArrayList<>();
            new DataValidatorBuilder(errors).parameter(parameterName).value(value.toString()).failWithCode("not.a.number");
            throw new PlatformApiDataValidationException(errors, e);
        }
    }

    private static String raw(final JsonObject object, final String parameterName) {
        final JsonElement value = object.get(parameterName);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static String trimmed(final JsonObject object, final String parameterName) {
        return StringUtils.trimToNull(raw(object, parameterName));
    }

    private static LocalDate date(final JsonObject object, final String parameterName, final DataValidatorBuilder validator) {
        final String value = trimmed(object, parameterName);
        if (value == null) {
            return null;
        }
        final String format = trimmed(object, DATE_FORMAT_PARAM);
        try {
            return LocalDate.parse(value,
                    format == null ? DateTimeFormatter.ISO_LOCAL_DATE : DateTimeFormatter.ofPattern(format, Locale.ENGLISH));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            validator.reset().parameter(parameterName).value(value).failWithCode("invalid.date.format", format);
            return null;
        }
    }

    private static void throwIfAny(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
