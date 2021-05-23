// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.cassandra.example;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.System.out;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.Fail.fail;

/**
 * Verifies that the java-driver-app example runs and exits with status code zero.
 * <p>
 * Three permutations of load balancing policy are tested to ensure there are no surprises based on a valid
 * specification of load balancing policy options. This test should be run against single-region, multi-region, and
 * multi-master accounts.
 */
public class ApplicationCommandLineRunnerTest {

    // region Fields

    private static final Pattern PROPERTY_TO_ENVIRONMENT_VARIABLE_PATTERN = Pattern.compile("([^.-]*)([.-]?)");
    private static final Map<String, String> PROPERTIES = new TreeMap<>();
    private static final Map<String, String> VARIABLES = new TreeMap<>();

    static final List<String> EXPECTED_OUTPUT;

    static final String JAVA = Paths.get(System.getProperty("java.home"), "bin", "java").toString();

    static final String JAR = getPropertyOrEnvironmentVariable(
        "azure.cosmos.cassandra.jar",
        null);

    static final String GLOBAL_ENDPOINT = getPropertyOrEnvironmentVariable(
        "azure.cosmos.cassandra.global-endpoint",
        null);

    static final String USERNAME = getPropertyOrEnvironmentVariable(
        "azure.cosmos.cassandra.username",
        null);

    static final String PASSWORD = getPropertyOrEnvironmentVariable(
        "azure.cosmos.cassandra.password",
        null);

    static final List<String> PREFERRED_REGIONS = Arrays.asList(getPropertyOrEnvironmentVariable(
        "azure.cosmos.cassandra.preferred-regions",
        "").split("\\s*,\\s*"));

    static final String TRUSTSTORE_PASSWORD = getPropertyOrEnvironmentVariable(
        "azure.cosmos.cassandra.truststore-password",
        null);

    static final String TRUSTSTORE_PATH = getPropertyOrEnvironmentVariable(
        "azure.cosmos.cassandra.truststore-path",
        null);

    static final String LOG_PATH = getPropertyOrEnvironmentVariable(
        "azure.cosmos.cassandra.log-path",
        Paths.get(System.getProperty("user.home"), ".local", "var", "log").toString());

    static final String JAVA_OPTIONS = getPropertyOrEnvironmentVariable(
        "azure.cosmos.cassandra.java.options",
        null);

    private static final long TIMEOUT_IN_MINUTES = 2;

    static {

        PROPERTIES.remove("azure.cosmos.cassandra.jar");
        PROPERTIES.remove("azure.cosmos.cassandra.java.options");

        out.println("--------------------------------------------------------------");
        out.println("T E S T  P A R A M E T E R S");
        out.println("--------------------------------------------------------------");
        out.println("azure.cosmos.cassandra.jar = " + JAR);

        for (final Map.Entry<String, String> property : PROPERTIES.entrySet()) {
            out.println(property.getKey() + " = " + property.getValue());
        }

        assertThat(JAR).withFailMessage("AZURE_COSMOS_CASSANDRA_JAR is unset").isNotBlank();
        assertThat(Paths.get(JAR)).withFailMessage("Jar %s does not exist", JAR).exists();

        // EXPECTED_OUTPUT

        final InputStream stream = ApplicationCommandLineRunnerTest.class
            .getClassLoader()
            .getResourceAsStream("expected.output");

        assertThat(stream).withFailMessage("could not load expected.output resource").isNotNull();
        assert stream != null;

        List<String> expectedOutput;
        IOException error;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            expectedOutput = reader.lines().collect(Collectors.toList());
            error = null;
        } catch (final IOException exception) {
            expectedOutput = null;
            error = exception;
        }

        assertThat(error).withFailMessage("could not read expected.output resource: ", error).isNull();
        EXPECTED_OUTPUT = expectedOutput;

        // Extra variables for HOCON support (see application.conf)

        int i = 0;

        for (final String preferredRegion : PREFERRED_REGIONS) {
            VARIABLES.put("AZURE_COSMOS_CASSANDRA_PREFERRED_REGION_" + ++i, preferredRegion);
        }
    }

    // endregion

    // region Methods

    /**
     * Creates the azure_cosmos_cassandra_driver_4_examples keyspace, if it doesn't already exist.
     */
    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "False alarm on Java 11+")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeEach
    public void createKeyspaceIfNotExists() {

        int i = 0;

        for (final String preferredRegion : PREFERRED_REGIONS) {
            System.setProperty("AZURE_COSMOS_CASSANDRA_PREFERRED_REGION_" + (++i), preferredRegion);
        }

        try (CqlSession session = CqlSession.builder().build()) {
            session.execute(SimpleStatement.newInstance("CREATE KEYSPACE IF NOT EXISTS "
                + "azure_cosmos_cassandra_driver_4_examples WITH "
                + "REPLICATION={"
                + "'class':'SimpleStrategy',"
                + "   'replication_factor':4"
                + "} AND "
                + "cosmosdb_provisioned_throughput=100000").setConsistencyLevel(ConsistencyLevel.ALL));
        } catch (final Throwable error) {
            fail("could not create table azure_cosmos_cassandra_driver_4_examples.people", error);
        }
    }

    /**
     * Runs the spring-boot-app and ensures that it completes with status code zero.
     * <p>
     * CosmosLoadBalancingPolicy is configured with and without multi-region writes.
     *
     * @param multiRegionWrites {@code true} if multi-region writes should be enabled; otherwise {@code false}.
     */
    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "False alarm on Java 11+")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void run(final boolean multiRegionWrites) {

        final String baseName = getBaseName(JAR) + ".run.withMultiRegionWrites-" + multiRegionWrites;
        final Path logFile = Paths.get(LOG_PATH, baseName + ".log");
        final Path outputPath = Paths.get(LOG_PATH, baseName + ".output");

        assertThatCode(() -> Files.createDirectories(Paths.get(LOG_PATH))).doesNotThrowAnyException();
        assertThatCode(() -> Files.deleteIfExists(logFile)).doesNotThrowAnyException();
        assertThatCode(() -> Files.deleteIfExists(outputPath)).doesNotThrowAnyException();

        final ProcessBuilder builder = new ProcessBuilder(getCommand(multiRegionWrites));
        builder.environment().putAll(VARIABLES);
        final Process process;

        out.println("\nRunning command: '" + String.join("' '", builder.command()) + '\'');
        final Instant start = Instant.now();

        try {
            process = builder.start();
        } catch (final Throwable error) {
            fail("failed to execute command '%s' due to %s", builder.command(), error);
            return;
        }

        final List<String> output;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),
            StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.toList());
        } catch (final IOException error) {
            fail("failed to execute command '%s' due to %s", builder.command(), error);
            return;
        }

        final File outputFile = outputPath.toFile();

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            for (final String line : output) {
                writer.write(line);
                writer.write('\n');
            }
        } catch (final IOException error) {
            fail("failed to write command output to '%s' due to %s", outputPath, error);
            return;
        }

        try {
            assertThat(process.waitFor(TIMEOUT_IN_MINUTES, TimeUnit.MINUTES)).isTrue();
        } catch (final InterruptedException error) {
            fail("command '%s timed out after %d minutes", builder.command(), TIMEOUT_IN_MINUTES);
            return;
        }

        try {
            assertThat(process.exitValue()).isEqualTo(0);
            assertThat(output).hasSize(EXPECTED_OUTPUT.size());
            assertThat(output).startsWith(EXPECTED_OUTPUT.get(0));
            assertThat(output).endsWith(EXPECTED_OUTPUT.get(EXPECTED_OUTPUT.size() - 1));

        } catch (final AssertionError assertionError) {

            out.println("---------------------------------------------------------------------------------");
            out.println("LOG DUMP");
            out.println("---------------------------------------------------------------------------------");
            out.println("command = " + builder.command());
            out.println("exit-value: " + process.exitValue());
            out.println("log-file: " + logFile);
            out.println("output-file: " + outputFile);
            out.println("environment: " + builder.environment());

            try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
                reader.lines().forEach(out::println);
            } catch (final IOException error) {
                out.println("---------------------------------------------------------------------------------");
                out.println("LOG DUMP ERROR");
                out.println("---------------------------------------------------------------------------------");
                error.printStackTrace(out);
                assertionError.addSuppressed(error);
            }

            out.println("---------------------------------------------------------------------------------");
            out.println("LOG DUMP END: ");
            out.println("---------------------------------------------------------------------------------");

            throw assertionError;
        }

        final Instant end = Instant.now();

        out.println("Run succeeded");
        out.println("  Total time: " + Duration.between(start, end));
        out.println("  Finished at: " + LocalDateTime.now());
        out.println("  Log file at: " + logFile);
        out.println("  Output file at: " + outputFile);
    }

    // endregion

    // region Privates

    /**
     * Returns the base name of the file or directory denoted by {@code path} as a {@linkplain String string}.
     *
     * The base name is the file name without its extension.
     *
     * @param path A relative or absolute path name.
     *
     * @return The base name of the file or directory denoted by {@code path}
     */
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    @SuppressWarnings("SameParameterValue")
    @NonNull
    private static String getBaseName(@NonNull final String path) {
        final Path filename = requireNonNull(Paths.get(path).getFileName(), "expected file or directory name");
        final String value = filename.toString();
        final int index = value.lastIndexOf('.');
        return index < 0 ? value : value.substring(0, index);
    }

    /**
     * Gets the command line for a test run.
     *
     * The command line constructed enables or disables multi-region writes by setting the value of system property
     * {@code azure.cosmos.cassandra.multi-region-writes} to {@code true} or {@code false}.
     *
     * @param multiRegionWrites {@code true} if multi-region writes should be enabled; otherwise {@code false}.
     *
     * @return A list of the command line arguments.
     */
    private static List<String> getCommand(final boolean multiRegionWrites) {

        final List<String> command = new ArrayList<>();

        command.add(JAVA);

        if (!(JAVA_OPTIONS == null || JAVA_OPTIONS.isEmpty())) {
            command.addAll(Arrays.asList(JAVA_OPTIONS.split("\\s+")));
        }

        System.setProperty("azure.cosmos.cassandra.multi-region-writes", multiRegionWrites ? "true" : "false");
        command.add("-Dazure.cosmos.cassandra.multi-region-writes=" + (multiRegionWrites ? "true" : "false"));

        for (final Map.Entry<String, String> property : PROPERTIES.entrySet()) {
            command.add("-D" + property.getKey() + '=' + property.getValue());
        }

        command.add("-jar");
        command.add(JAR);

        return command;
    }

    /**
     * Get the value of the specified system {@code property} or--if it is unset--environment {@code variable}.
     * <p>
     * If neither {@code property} or {@code variable} is set, {@code defaultValue} is returned.
     *
     * @param property     a system property name.
     * @param defaultValue the default value--which may be {@code null}--to be used if neither {@code property} or
     *                     {@code variable} is set.
     *
     * @return The value of the specified {@code property}, the value of the specified environment {@code variable}, or
     * {@code defaultValue}.
     */
    private static String getPropertyOrEnvironmentVariable(@NonNull final String property, final String defaultValue) {

        final StringBuilder builder = new StringBuilder(property.length());

        property.chars().forEachOrdered(c -> {
            builder.appendCodePoint(c == '.' || c == '-' ? '_' : Character.toUpperCase(c));
        });

        final String variable = builder.toString();

        String value = System.getProperty(property);

        if (value == null || value.isEmpty()) {
            value = System.getenv(variable);
        }

        if (value == null) {
            value = defaultValue;
        }

        if (value != null) {
            System.setProperty(property, value);
        }

        PROPERTIES.put(property, value);
        VARIABLES.put(variable, value);

        return value;
    }

    // endregion
}
