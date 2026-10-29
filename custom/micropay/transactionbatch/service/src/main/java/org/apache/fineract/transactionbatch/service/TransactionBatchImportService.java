/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.apache.fineract.transactionbatch.data.TransactionBatchImportResult;
import org.apache.fineract.transactionbatch.domain.TransactionBatch;
import org.apache.fineract.transactionbatch.domain.TransactionBatchAccountType;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItemRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchSide;
import org.apache.fineract.transactionbatch.exception.TransactionBatchRuleException;
import org.apache.fineract.transactionbatch.service.TransactionBatchValidator.ItemFields;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CSV and XLSX import. It does not go through the command pipeline because a batch file does not fit in command JSON. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchImportService {

    private static final String[] COLUMNS = { "Account Type", "Account Number", "Transaction Type", "Amount", "Currency", "Narrative",
            "Reference", "Value Date" };

    private final TransactionBatchAccess access;
    private final TransactionBatchWritePlatformService writes;
    private final TransactionBatchItemRepository itemRepository;
    private final GLAccountRepository glAccountRepository;
    private final SavingsAccountRepository savingsAccountRepository;
    private final TransactionBatchAccountRules accountRules;

    @Transactional
    public TransactionBatchImportResult importFile(final Long batchId, final String fileName, final InputStream input) {
        this.access.user().validateHasPermissionTo("IMPORT_TRANSACTIONBATCHITEM");
        final TransactionBatch batch = this.access.requireBatch(batchId);
        this.access.assertEditable(batch);
        final List<String[]> rows = read(fileName, input);
        final int cap = this.access.config().getImportRowCap();
        if (rows.size() > cap) {
            throw new PlatformApiDataValidationException("validation.msg.transactionbatch.import.cap.exceeded",
                    "The file has more rows than the import cap of " + cap, "file", rows.size(), cap);
        }
        final List<ApiParameterError> errors = new ArrayList<>();
        final List<ItemFields> ready = new ArrayList<>();
        int rowNumber = 1;
        for (final String[] row : rows) {
            rowNumber++;
            try {
                ready.add(toFields(batch, row, rowNumber));
            } catch (final PlatformApiDataValidationException ex) {
                errors.addAll(ex.getErrors());
            } catch (final RuntimeException ex) {
                errors.add(ApiParameterError.parameterError("validation.msg.transactionbatch.import.row.invalid", ex.getMessage(), "row",
                        rowNumber));
            }
        }
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
        for (final ItemFields fields : ready) {
            this.itemRepository.saveAndFlush(this.writes.buildItem(batch, fields));
        }
        return new TransactionBatchImportResult(batch.getId(), ready.size());
    }

    private ItemFields toFields(final TransactionBatch batch, final String[] row, final int rowNumber) {
        final String accountTypeText = cell(row, 0);
        final String accountNumber = cell(row, 1);
        final String sideText = cell(row, 2);
        final String amountText = cell(row, 3);
        final String currency = cell(row, 4);
        final String narrative = blankToNull(cell(row, 5));
        final String reference = blankToNull(cell(row, 6));
        final String valueDateText = cell(row, 7);
        if (accountTypeText == null || accountNumber == null || sideText == null || amountText == null || currency == null) {
            throw new TransactionBatchRuleException("import.row", "Row " + rowNumber + " is missing a required column");
        }
        final TransactionBatchAccountType accountType;
        final TransactionBatchSide side;
        try {
            accountType = TransactionBatchAccountType.fromCode(accountTypeText);
            side = TransactionBatchSide.fromCode(sideText);
        } catch (final IllegalArgumentException ex) {
            throw new TransactionBatchRuleException("import.row", "Row " + rowNumber + " has an invalid account or transaction type");
        }
        final BigDecimal amount;
        try {
            amount = new BigDecimal(amountText);
        } catch (final NumberFormatException ex) {
            throw new TransactionBatchRuleException("import.amount", "Row " + rowNumber + " has an invalid amount");
        }
        if (amount.signum() <= 0) {
            throw new TransactionBatchRuleException("import.amount", "Row " + rowNumber + " amount must be greater than zero");
        }
        LocalDate valueDate = null;
        if (valueDateText != null) {
            try {
                valueDate = LocalDate.parse(valueDateText);
            } catch (final DateTimeParseException ex) {
                throw new TransactionBatchRuleException("import.date", "Row " + rowNumber + " value date must be yyyy-MM-dd");
            }
        }
        final LocalDate effective = valueDate == null ? org.apache.fineract.infrastructure.core.service.DateUtils.getBusinessLocalDate()
                : valueDate;
        if (batch.getEffectiveDate() != null && effective.isAfter(batch.getEffectiveDate())) {
            throw new TransactionBatchRuleException("value.date.after.effective",
                    "Row " + rowNumber + " value date cannot be after the batch Effective Date");
        }
        Long glAccountId = null;
        Long savingsAccountId = null;
        if (accountType.isGl()) {
            final GLAccount glAccount = this.glAccountRepository.findOneByGlCode(accountNumber)
                    .orElseThrow(() -> new TransactionBatchRuleException("import.account",
                            "Row " + rowNumber + " GL account " + accountNumber + " was not found"));
            this.accountRules.requirePostableGl(glAccount.getId());
            this.accountRules.requireOrganisationCurrency(currency);
            glAccountId = glAccount.getId();
        } else {
            final SavingsAccount savings = this.savingsAccountRepository.findSavingsAccountByAccountNumber(accountNumber);
            if (savings == null) {
                throw new TransactionBatchRuleException("import.account",
                        "Row " + rowNumber + " customer account " + accountNumber + " was not found");
            }
            this.accountRules.requireSavings(savings.getId(), side, currency);
            savingsAccountId = savings.getId();
        }
        return new ItemFields(accountType.getCode(), glAccountId, savingsAccountId, side.getCode(), currency, amount, narrative, reference,
                valueDate);
    }

    private List<String[]> read(final String fileName, final InputStream input) {
        final String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".xlsx")) {
                return readXlsx(input);
            }
            if (name.endsWith(".csv") || name.isEmpty()) {
                return readCsv(input);
            }
        } catch (final IOException ex) {
            throw new PlatformApiDataValidationException("validation.msg.transactionbatch.import.unreadable",
                    "The import file could not be read", "file");
        }
        throw new PlatformApiDataValidationException("validation.msg.transactionbatch.import.type", "Import accepts a CSV or XLSX file",
                "file", fileName);
    }

    private List<String[]> readCsv(final InputStream input) throws IOException {
        final CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true)
                .setIgnoreEmptyLines(true).get();
        try (CSVParser parser = format.parse(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            final List<String[]> rows = new ArrayList<>();
            for (final CSVRecord record : parser) {
                final String[] row = new String[COLUMNS.length];
                for (int i = 0; i < COLUMNS.length; i++) {
                    row[i] = record.isMapped(COLUMNS[i]) ? blankToNull(record.get(COLUMNS[i])) : null;
                }
                if (!empty(row)) {
                    rows.add(row);
                }
            }
            return rows;
        }
    }

    private List<String[]> readXlsx(final InputStream input) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            final XSSFSheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                return List.of();
            }
            final DataFormatter formatter = new DataFormatter();
            final Row header = sheet.getRow(0);
            final int[] index = new int[COLUMNS.length];
            for (int i = 0; i < COLUMNS.length; i++) {
                index[i] = -1;
                if (header == null) {
                    continue;
                }
                for (final Cell cell : header) {
                    if (COLUMNS[i].equalsIgnoreCase(formatter.formatCellValue(cell).trim())) {
                        index[i] = cell.getColumnIndex();
                    }
                }
            }
            final List<String[]> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                final Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                final String[] values = new String[COLUMNS.length];
                for (int i = 0; i < COLUMNS.length; i++) {
                    if (index[i] < 0) {
                        continue;
                    }
                    final Cell cell = row.getCell(index[i]);
                    values[i] = cellText(formatter, cell);
                }
                if (!empty(values)) {
                    rows.add(values);
                }
            }
            return rows;
        }
    }

    private static String cellText(final DataFormatter formatter, final Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return blankToNull(formatter.formatCellValue(cell).trim());
    }

    private static String cell(final String[] row, final int index) {
        return index < row.length ? row[index] : null;
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean empty(final String[] row) {
        for (final String value : row) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
