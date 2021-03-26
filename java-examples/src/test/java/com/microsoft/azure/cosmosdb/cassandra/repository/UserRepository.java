package com.microsoft.azure.cosmosdb.cassandra.repository;

import java.util.List;

import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Statement;
import static com.datastax.oss.driver.api.core.ConsistencyLevel.QUORUM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class gives implementations of create, delete table on Cassandra
 * database Insert & select data from the table
 */
public class UserRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRepository.class);
    private CqlSession session;
    private static final ConsistencyLevel CONSISTENCY_LEVEL = QUORUM;

    public UserRepository(CqlSession session) {
        this.session = session;

    }

    /**
     * Create keyspace uprofile in cassandra DB
     */
    public void createKeyspace(String queryString) {
        session.execute(queryString);
        LOGGER.info("Executed query: " + queryString);
    }

    /**
     * Create user table in cassandra DB
     */
    public void createTable(String queryString) {
        final String query = queryString;
        session.execute(query);
        LOGGER.info("Executed query: " + queryString);
    }

    /**
     * Select all rows from user table
     */
    public void selectAllUsers() {

        final String query = "SELECT * FROM uprofile.user";
        List<Row> rows = session.execute(query).all();

        for (Row row : rows) {
            LOGGER.info("Obtained row: {} | {} | {} ", row.getInt("user_id"), row.getString("user_name"),
                    row.getString("user_bcity"));
        }
    }

    /**
     * Select a row from user table
     *
     * @param id user_id
     */
    public void selectUser(String id) {
        final String query = "SELECT * FROM uprofile.user where user_id ='"+id+"'";
        Row row = session.execute(query).one();
        LOGGER.info("Obtained row: {} | {} | {} ", row.getString("user_id"), row.getString("user_name"),
                row.getString("user_bcity"));
    }

    /**
     * Delete user table.
     */
    public void deleteTable(String queryString) {
        final String query = queryString;
        session.execute(query);
    }

    /**
     * Insert a row into user table
     *
     * @param id   user_id
     * @param name user_name
     * @param city user_bcity
     */
    public void insertUser(String preparedStatement, String id, String name, String city) {
        PreparedStatement prepared = session.prepare(preparedStatement);
        BoundStatement bound = prepared.bind(city, id, name).setIdempotent(true);
        session.execute(bound);
    }

    public void simpleInsertUser(Statement statement) {
        BatchStatement batch = BatchStatement.builder(BatchType.UNLOGGED).build();
        batch.setConsistencyLevel(CONSISTENCY_LEVEL);
        session.execute(batch);
    }

    public long selectUserCount(String queryString) {
        final String query = queryString;
        Row row = session.execute(query).one();
        return row.getLong(0);
    }

    /**
     * Create a PrepareStatement to insert a row to user table
     *
     * @return PreparedStatement
     */
    public PreparedStatement prepareInsertStatement(String queryString) {
        final String insertStatement = queryString;
        return session.prepare(insertStatement);
    }
}