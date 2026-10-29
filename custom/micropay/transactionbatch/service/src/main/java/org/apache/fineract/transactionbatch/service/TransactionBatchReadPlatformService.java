/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.OrganisationCurrencyRepository;
import org.apache.fineract.transactionbatch.data.TransactionBatchAccountData;
import org.apache.fineract.transactionbatch.data.TransactionBatchCurrencySettingData;
import org.apache.fineract.transactionbatch.data.TransactionBatchData;
import org.apache.fineract.transactionbatch.data.TransactionBatchItemData;
import org.apache.fineract.transactionbatch.data.TransactionBatchPageData;
import org.apache.fineract.transactionbatch.data.TransactionBatchSettingsData;
import org.apache.fineract.transactionbatch.data.TransactionBatchSummaryData;
import org.apache.fineract.transactionbatch.data.TransactionBatchTemplateData;
import org.apache.fineract.transactionbatch.domain.TransactionBatchAccountType;
import org.apache.fineract.transactionbatch.domain.TransactionBatchConfig;
import org.apache.fineract.transactionbatch.domain.TransactionBatchFrequency;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItemStatus;
import org.apache.fineract.transactionbatch.domain.TransactionBatchPostingStatus;
import org.apache.fineract.transactionbatch.domain.TransactionBatchSetting;
import org.apache.fineract.transactionbatch.domain.TransactionBatchSettingRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchSide;
import org.apache.fineract.transactionbatch.domain.TransactionBatchStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchReadPlatformService {

    private static final String HEADER = """
            select b.id, b.code, b.description, b.office_id, o.name as office_name, b.is_recurring, b.effective_date,
            b.frequency_number, b.frequency_unit, b.next_posting_date, b.expiry_date, b.status,
            b.submitted_by, b.submitted_on, b.decided_by, b.decided_on, b.posted_by, b.posted_on, b.copied_from_batch_id,
            b.created_by, u.username as created_by_username, b.created_on_utc, b.last_modified_by, b.last_modified_on_utc,
            (select max(c.id) from m_portfolio_command_source c
              where c.entity_name = 'TRANSACTIONBATCH' and c.action_name = 'SUBMIT'
              and c.resource_id = b.id and c.status = 2) as pending_command_id
            from m_txn_batch b
            join m_office o on o.id = b.office_id
            left join m_appuser u on u.id = b.created_by
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final TransactionBatchAccess access;
    private final TransactionBatchSettingRepository settingRepository;
    private final OrganisationCurrencyRepository currencyRepository;

    public TransactionBatchPageData search(final Long officeId, final Long createdBy, final Long batchNumber, final String code,
            final LocalDate fromDate, final LocalDate toDate, final String status, final Integer offset, final Integer limit,
            final String orderBy, final String sortOrder) {
        this.access.user().validateHasReadPermission("TRANSACTIONBATCH");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            final List<ApiParameterError> errors = new ArrayList<>();
            new DataValidatorBuilder(errors).resource("transactionbatch").parameter("fromDate").value(fromDate)
                    .failWithCode("after.toDate");
            throw new PlatformApiDataValidationException(errors);
        }
        final String hierarchy = this.access.user().getOffice().getHierarchy();
        final MapSqlParameterSource params = new MapSqlParameterSource();
        final StringBuilder where = new StringBuilder(" where o.hierarchy like :hierarchy ");
        params.addValue("hierarchy", hierarchy + "%");
        if (officeId != null) {
            this.access.assertOffice(officeId);
            where.append(" and b.office_id = :officeId ");
            params.addValue("officeId", officeId);
        }
        if (createdBy != null) {
            where.append(" and b.created_by = :createdBy ");
            params.addValue("createdBy", createdBy);
        }
        if (batchNumber != null) {
            where.append(" and b.id = :batchNumber ");
            params.addValue("batchNumber", batchNumber);
        }
        if (code != null && !code.isBlank()) {
            where.append(" and b.code = :code ");
            params.addValue("code", code.trim());
        }
        if (fromDate != null) {
            where.append(" and b.created_on_utc >= :fromDate ");
            params.addValue("fromDate", startOf(fromDate));
        }
        if (toDate != null) {
            where.append(" and b.created_on_utc < :toDate ");
            params.addValue("toDate", startOf(toDate.plusDays(1)));
        }
        if (status != null && !status.isBlank()) {
            if (TransactionBatchStatus.SUBMITTED.getCode().equalsIgnoreCase(status)) {
                where.append(" and pending_filter.id is not null ");
            } else {
                final TransactionBatchStatus stored;
                try {
                    stored = TransactionBatchStatus.fromCode(status);
                } catch (final IllegalArgumentException ex) {
                    final List<ApiParameterError> errors = new ArrayList<>();
                    new DataValidatorBuilder(errors).resource("transactionbatch").parameter("status").value(status).failWithCode("invalid");
                    throw new PlatformApiDataValidationException(errors);
                }
                where.append(" and b.status = :status and pending_filter.id is null ");
                params.addValue("status", stored.getValue());
            }
        }
        final String pendingJoin = """
                 left join m_portfolio_command_source pending_filter on pending_filter.id = (
                    select max(c.id) from m_portfolio_command_source c
                    where c.entity_name = 'TRANSACTIONBATCH' and c.action_name = 'SUBMIT'
                    and c.resource_id = b.id and c.status = 2)
                """;
        final String from = " from m_txn_batch b join m_office o on o.id = b.office_id " + pendingJoin + where;
        final Integer total = this.namedJdbc.queryForObject("select count(b.id) " + from, params, Integer.class);
        final int pageOffset = offset == null || offset < 0 ? 0 : offset;
        final int pageLimit = limit == null || limit < 1 ? 50 : Math.min(limit, 200);
        final String order = orderColumn(orderBy) + " " + ("ASC".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC");
        final String sql = HEADER + pendingJoin + where + " order by " + order + " " + this.sqlGenerator.limit(pageLimit, pageOffset);
        final List<TransactionBatchData> rows = this.namedJdbc.query(sql, params, (rs, row) -> mapHeader(rs, false));
        return new TransactionBatchPageData(total == null ? 0 : total, rows);
    }

    public TransactionBatchData retrieveOne(final Long batchId, final String associations) {
        final TransactionBatchData header = one(batchId);
        final boolean items = associations != null && associations.contains("items");
        final boolean summary = associations != null && associations.contains("summary");
        return new TransactionBatchData(header.id(), header.code(), header.description(), header.officeId(), header.officeName(),
                header.recurring(), header.effectiveDate(), header.frequencyNumber(), header.frequencyUnit(), header.nextPostingDate(),
                header.expiryDate(), header.status(), header.pendingCommandId(), header.submittedBy(), header.submittedOn(),
                header.decidedBy(), header.decidedOn(), header.postedBy(), header.postedOn(), header.copiedFromBatchId(), header.createdBy(),
                header.createdByUsername(), header.createdOn(), header.lastModifiedBy(), header.lastModifiedOn(),
                items ? items(batchId) : null, summary ? summary(batchId) : null);
    }

    public List<TransactionBatchItemData> items(final Long batchId) {
        this.access.user().validateHasReadPermission("TRANSACTIONBATCH");
        this.access.requireBatch(batchId);
        final String sql = """
                select i.id, i.batch_id, i.account_type, i.gl_account_id, i.savings_account_id, i.side, i.currency_code, i.amount,
                i.narrative, i.reference, i.value_date, i.status, i.last_error, i.created_by, i.created_on_utc,
                case when i.account_type = 1 then gl.gl_code else sa.account_no end as account_number,
                case when i.account_type = 1 then gl.name else coalesce(c.display_name, g.display_name) end as account_title,
                (select count(p.id) from m_txn_batch_posting p where p.item_id = i.id and p.status = ?) as suspended_runs,
                (select min(p.id) from m_txn_batch_posting p where p.item_id = i.id and p.status = ?) as repair_posting_id
                from m_txn_batch_item i
                left join acc_gl_account gl on gl.id = i.gl_account_id
                left join m_savings_account sa on sa.id = i.savings_account_id
                left join m_client c on c.id = sa.client_id
                left join m_group g on g.id = sa.group_id
                where i.batch_id = ? order by i.id
                """;
        final Integer suspended = TransactionBatchPostingStatus.SUSPENDED.getValue();
        return this.jdbcTemplate.query(sql, (rs, row) -> mapItem(rs), suspended, suspended, batchId);
    }

    public List<TransactionBatchSummaryData> summary(final Long batchId) {
        this.access.user().validateHasReadPermission("TRANSACTIONBATCH");
        this.access.requireBatch(batchId);
        final String sql = """
                select currency_code, value_date,
                sum(case when side = 1 then 1 else 0 end) as debits,
                sum(case when side = 2 then 1 else 0 end) as credits,
                sum(case when side = 1 then amount else 0 end) as total_debits,
                sum(case when side = 2 then amount else 0 end) as total_credits
                from m_txn_batch_item where batch_id = ?
                group by currency_code, value_date order by currency_code, value_date
                """;
        return this.jdbcTemplate.query(sql, (rs, row) -> {
            final BigDecimal debits = rs.getBigDecimal("total_debits");
            final BigDecimal credits = rs.getBigDecimal("total_credits");
            final BigDecimal net = debits.subtract(credits);
            return new TransactionBatchSummaryData(rs.getString("currency_code"), JdbcSupport.getLocalDate(rs, "value_date"),
                    rs.getInt("debits"), rs.getInt("credits"), debits, credits, net, net.compareTo(BigDecimal.ZERO) == 0);
        }, batchId);
    }

    public TransactionBatchTemplateData template() {
        this.access.user().validateHasReadPermission("TRANSACTIONBATCH");
        final List<CurrencyData> currencies = this.currencyRepository.findAllSorted(Sort.by("code"));
        return new TransactionBatchTemplateData(DateUtils.getBusinessLocalDate(),
                List.of("INACTIVE", "SUBMITTED", "ACTIVE", "REJECTED", "POSTED", "PARTIALLY_POSTED"),
                List.of(TransactionBatchAccountType.GL_ACCOUNT.getCode(), TransactionBatchAccountType.SAVINGS_ACCOUNT.getCode()),
                List.of(TransactionBatchSide.DEBIT.getCode(), TransactionBatchSide.CREDIT.getCode()),
                List.of(TransactionBatchFrequency.DAYS.getCode(), TransactionBatchFrequency.WEEKS.getCode(),
                        TransactionBatchFrequency.MONTHS.getCode(), TransactionBatchFrequency.YEARS.getCode()),
                currencies);
    }

    public TransactionBatchSettingsData settings() {
        this.access.user().validateHasReadPermission("TRANSACTIONBATCHSETTING");
        final TransactionBatchConfig config = this.access.config();
        final List<TransactionBatchCurrencySettingData> currencies = new ArrayList<>();
        for (final TransactionBatchSetting setting : this.settingRepository.findAll()) {
            currencies.add(new TransactionBatchCurrencySettingData(setting.getCurrencyCode(), setting.getClearingGlAccountId(),
                    setting.getSuspenseGlAccountId()));
        }
        return new TransactionBatchSettingsData(config.getCodePrefix(), config.getImportRowCap(), currencies);
    }

    public List<TransactionBatchAccountData> accounts(final String search) {
        this.access.user().validateHasReadPermission("TRANSACTIONBATCH");
        final String hierarchy = this.access.user().getOffice().getHierarchy();
        final String like = "%" + (search == null ? "" : search.trim()) + "%";
        final String sql = """
                select sa.id, sa.account_no, sa.currency_code, sa.office_id, coalesce(c.display_name, g.display_name) as client_name
                from m_savings_account sa
                join m_office o on o.id = sa.office_id
                left join m_client c on c.id = sa.client_id
                left join m_group g on g.id = sa.group_id
                where sa.status_enum = 300 and sa.deposit_type_enum = 100 and o.hierarchy like ?
                and (sa.account_no like ? or c.display_name like ? or g.display_name like ?)
                order by sa.account_no
                """
                + this.sqlGenerator.limit(50, 0);
        return this.jdbcTemplate.query(sql,
                (rs, row) -> new TransactionBatchAccountData(rs.getLong("id"), rs.getString("account_no"), rs.getString("client_name"),
                        rs.getString("currency_code"), rs.getLong("office_id")),
                hierarchy + "%", like, like, like);
    }

    private TransactionBatchData one(final Long batchId) {
        this.access.user().validateHasReadPermission("TRANSACTIONBATCH");
        this.access.requireBatch(batchId);
        final String hierarchy = this.access.user().getOffice().getHierarchy();
        final List<TransactionBatchData> rows = this.jdbcTemplate.query(HEADER + " where b.id = ? and o.hierarchy like ?",
                (rs, row) -> mapHeader(rs, false), batchId, hierarchy + "%");
        if (rows.isEmpty()) {
            throw new org.apache.fineract.transactionbatch.exception.TransactionBatchNotFoundException(batchId);
        }
        return rows.get(0);
    }

    private TransactionBatchData mapHeader(final ResultSet rs, final boolean ignored) throws SQLException {
        final Long pending = rs.getObject("pending_command_id") == null ? null : rs.getLong("pending_command_id");
        final TransactionBatchStatus stored = TransactionBatchStatus.fromInt(rs.getInt("status"));
        final String status = pending != null ? TransactionBatchStatus.SUBMITTED.getCode() : stored.getCode();
        final Integer frequencyUnit = rs.getObject("frequency_unit") == null ? null : rs.getInt("frequency_unit");
        final OffsetDateTime created = JdbcSupport.getOffsetDateTime(rs, "created_on_utc");
        final OffsetDateTime modified = JdbcSupport.getOffsetDateTime(rs, "last_modified_on_utc");
        return new TransactionBatchData(rs.getLong("id"), rs.getString("code"), rs.getString("description"), rs.getLong("office_id"),
                rs.getString("office_name"), rs.getBoolean("is_recurring"), JdbcSupport.getLocalDate(rs, "effective_date"),
                rs.getObject("frequency_number") == null ? null : rs.getInt("frequency_number"),
                frequencyUnit == null ? null : TransactionBatchFrequency.fromInt(frequencyUnit).getCode(),
                JdbcSupport.getLocalDate(rs, "next_posting_date"), JdbcSupport.getLocalDate(rs, "expiry_date"), status, pending,
                longOrNull(rs, "submitted_by"), JdbcSupport.getLocalDate(rs, "submitted_on"), longOrNull(rs, "decided_by"),
                JdbcSupport.getLocalDate(rs, "decided_on"), longOrNull(rs, "posted_by"), JdbcSupport.getLocalDate(rs, "posted_on"),
                longOrNull(rs, "copied_from_batch_id"), rs.getLong("created_by"), rs.getString("created_by_username"),
                created == null ? null : created.toString(), longOrNull(rs, "last_modified_by"), modified == null ? null : modified.toString(),
                null, null);
    }

    private TransactionBatchItemData mapItem(final ResultSet rs) throws SQLException {
        final boolean debit = TransactionBatchSide.DEBIT.getValue().equals(rs.getInt("side"));
        final BigDecimal amount = rs.getBigDecimal("amount");
        final OffsetDateTime created = JdbcSupport.getOffsetDateTime(rs, "created_on_utc");
        return new TransactionBatchItemData(rs.getLong("id"), rs.getLong("batch_id"),
                TransactionBatchAccountType.fromInt(rs.getInt("account_type")).getCode(), longOrNull(rs, "gl_account_id"),
                longOrNull(rs, "savings_account_id"), rs.getString("account_number"), rs.getString("account_title"),
                TransactionBatchSide.fromInt(rs.getInt("side")).getCode(), rs.getString("currency_code"), amount, debit ? amount : null,
                debit ? null : amount, rs.getString("narrative"), rs.getString("reference"), JdbcSupport.getLocalDate(rs, "value_date"),
                TransactionBatchItemStatus.fromInt(rs.getInt("status")).getCode(), rs.getString("last_error"), rs.getLong("created_by"),
                created == null ? null : created.toString(), rs.getLong("suspended_runs"), longOrNull(rs, "repair_posting_id"));
    }

    private static Long longOrNull(final ResultSet rs, final String column) throws SQLException {
        final long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static OffsetDateTime startOf(final LocalDate date) {
        final ZoneId zone = DateUtils.getDateTimeZoneOfTenant();
        return date.atStartOfDay(zone).toOffsetDateTime();
    }

    private static String orderColumn(final String orderBy) {
        if (orderBy == null) {
            return "b.id";
        }
        return switch (orderBy) {
            case "code" -> "b.code";
            case "description" -> "b.description";
            case "createdOn" -> "b.created_on_utc";
            case "status" -> "b.status";
            case "officeName" -> "o.name";
            default -> "b.id";
        };
    }
}
