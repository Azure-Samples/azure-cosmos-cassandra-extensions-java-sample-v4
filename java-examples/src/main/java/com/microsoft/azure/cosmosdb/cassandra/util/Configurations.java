package com.microsoft.azure.cosmosdb.cassandra.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration utility to read the configurations from properties file
 */
public class Configurations {
    private static final Logger LOGGER = LoggerFactory.getLogger(Configurations.class);
    private static final String PROPERTY_FILE = "config.properties";
    private static Properties prop = null;

    public String getProperty(final String propertyName) throws IOException {
        if (prop == null) {
            this.loadProperties();
        }
        return prop.getProperty(propertyName);

    }

    private void loadProperties() throws IOException {
        final InputStream input = this.getClass().getClassLoader().getResourceAsStream(PROPERTY_FILE);
        if (input == null) {
            LOGGER.error("Sorry, unable to find {}", PROPERTY_FILE);
            return;
        }
        prop = new Properties();
        prop.load(input);
    }
}
