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

import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Queries over large tables (loan schedules, savings and deposit accounts, journal entries) read in batches of
 * {@link #FETCH_SIZE} rows instead of the whole result at once, without changing the shared template. PostgreSQL only
 * honours a fetch size inside a transaction, as the treasury jobs and API calls are.
 */
public final class StreamingQueries {

    public static final int FETCH_SIZE = 1000;

    private StreamingQueries() {}

    public static void query(final NamedParameterJdbcTemplate template, final String sql, final SqlParameterSource params,
            final RowCallbackHandler handler) {
        template.getJdbcOperations().query(creator(sql, params), handler);
    }

    public static <T> List<T> query(final NamedParameterJdbcTemplate template, final String sql, final SqlParameterSource params,
            final RowMapper<T> mapper) {
        return template.getJdbcOperations().query(creator(sql, params), new RowMapperResultSetExtractor<>(mapper));
    }

    static PreparedStatementCreator creator(final String sql, final SqlParameterSource params) {
        final ParsedSql parsed = NamedParameterUtils.parseSqlStatement(sql);
        final PreparedStatementCreator creator = new PreparedStatementCreatorFactory(
                NamedParameterUtils.substituteNamedParameters(parsed, params), NamedParameterUtils.buildSqlParameterList(parsed, params))
                .newPreparedStatementCreator(NamedParameterUtils.buildValueArray(parsed, params, null));
        return connection -> {
            final PreparedStatement statement = creator.createPreparedStatement(connection);
            statement.setFetchSize(FETCH_SIZE);
            return statement;
        };
    }
}
