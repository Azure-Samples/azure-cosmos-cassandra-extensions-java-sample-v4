// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.cassandra.example;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static com.datastax.oss.driver.api.core.ConsistencyLevel.QUORUM;

/**
 * This class gives implementations of create, delete table on Cassandra database Insert and Select data from the table.
 */
@SuppressWarnings("UnnecessaryLocalVariable")
public class UserRepository {

    private static final ConsistencyLevel CONSISTENCY_LEVEL = QUORUM;
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRepository.class);
    private final CqlSession session;

    /**
     * Initializes a new user repository instance.
     *
     * @param session Reference to a {@link CqlSession CQLSession}.
     */
    public UserRepository(final CqlSession session) {
        this.session = session;
    }

    /**
     * Create keyspace in Cassandra DB
     *
     * @param queryString A query string for creating a keyspace.
     */
    public void createKeyspace(final String queryString) {
        this.session.execute(queryString);
        LOGGER.info("Executed query: " + queryString);
    }

    /**
     * Create user table in Cassandra DB
     *
     * @param queryString A query string for creating a table.
     */
    public void createTable(final String queryString) {
        final String query = queryString;
        this.session.execute(query);
        LOGGER.info("Executed query: " + queryString);
    }

    /**
     * Delete user table.
     *
     * @param queryString A query string for dropping a table.
     */
    public void dropTable(final String queryString) {
        final String query = queryString;
        this.session.execute(query);
    }

    /**
     * Insert a row into the user table
     *
     * @param preparedStatement A prepared statement for inserting a user into the user table.
     * @param id                id   The user's unique ID
     * @param name              name The user's name.
     * @param bcity             bcity The user's city of birth.
     */
    public void insertUser(final String preparedStatement, final String id, final String name, final String bcity) {
        final PreparedStatement prepared = this.session.prepare(preparedStatement);
        final BoundStatement bound = prepared.bind(bcity, id, name).setIdempotent(true);
        this.session.execute(bound);
    }

    /**
     * Select a row from user table.
     *
     * @param id       User ID.
     * @param keyspace Keyspace name.
     * @param table    Table name.
     */
    public void selectUser(final String id, final String keyspace, final String table) {
        final String query = "SELECT * FROM " + keyspace + "." + table + " where user_id ='" + id + "'";
        final Row row = this.session.execute(query).one();
        LOGGER.info("Obtained row: {} | {} | {} ",
            Objects.requireNonNull(row).getString("user_id"), row.getString("user_name"),
            row.getString("user_bcity"));
    }

    /**
     * Computes a count of the number of rows in the user table.
     *
     * @param queryString A query for computing a count of the number of rows in the user tble.
     *
     * @return The number of rows in the user table.
     */
    public long selectUserCount(final String queryString) {
        final String query = queryString;
        final Row row = this.session.execute(query).one();
        return row == null ? 0 : row.getLong(0);
    }
}
