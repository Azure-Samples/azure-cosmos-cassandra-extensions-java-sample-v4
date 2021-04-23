package com.microsoft.azure.cosmosdb.cassandra.util;

import com.datastax.oss.driver.api.core.CqlSession;

/**
 * Cassandra utility class to handle the Cassandra Sessions
 */
public class CassandraUtils {
    /**
     * Initiates a connection to the configured Cosmos DB Cassandra API instance.
     * See src/resources/application.conf.
     */
    public CqlSession getSession() {
        return CqlSession.builder().build();
    }
}
