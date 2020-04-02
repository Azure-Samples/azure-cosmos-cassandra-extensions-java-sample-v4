package com.microsoft.azure.cosmosdb.cassandra.examples;

import java.io.IOException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.microsoft.azure.cosmosdb.cassandra.repository.UserRepository;
import com.microsoft.azure.cosmosdb.cassandra.util.CassandraUtils;
import com.microsoft.azure.cosmosdb.cassandra.util.Configurations;
import com.datastax.oss.driver.api.core.CqlSession;
import com.github.javafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example class which will demonstrate handling rate limiting using retry
 * policy, and client side load balancing using Cosmos DB multi-master capability
 */
public class UserProfile {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserProfile.class);
    private static Random random = new Random();
    private static Configurations config = new Configurations();

    public static final int NUMBER_OF_THREADS = 40;
    public static final int NUMBER_OF_WRITES_PER_THREAD = 5;

    AtomicInteger recordcount = new AtomicInteger(0);
    AtomicInteger exceptioncount = new AtomicInteger(0);
    AtomicInteger ratelimitcount = new AtomicInteger(0);
    AtomicInteger totalRetries = new AtomicInteger(0);
    AtomicLong insertCount = new AtomicLong(0);
    AtomicLong totalLatency = new AtomicLong(0);
    AtomicLong averageLatency = new AtomicLong(0);
    Boolean loadBalanceRegions = false;

    private static int PORT;
    private static String region1ContactPoint;
    private static String region2ContactPoint;
    private static String region1;
    private static String region2;
    static {
        try {
            PORT = Short.parseShort(config.getProperty("cassandra_port"));
            region1ContactPoint= config.getProperty("cassandra_region1ContactPoint");
            region2ContactPoint= config.getProperty("cassandra_region2ContactPoint");
            region1= config.getProperty("cassandra_region1");
            region2= config.getProperty("cassandra_region2");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] s) throws Exception {

        CassandraUtils utils = new CassandraUtils();
        UserProfile u = new UserProfile();
        CqlSession cassandraSessionWithRetry1 = utils.getSession(region1ContactPoint, PORT,  region1);
        CqlSession cassandraSessionWithRetry2 = utils.getSession(region2ContactPoint, PORT,  region2);
       
        UserRepository region1 = new UserRepository(cassandraSessionWithRetry1);
        UserRepository region2 = new UserRepository(cassandraSessionWithRetry2);
        String keyspace = "uprofile";
        String table = "user";
        try {
            // Create keyspace and table in cassandra database
            region1.deleteTable("DROP KEYSPACE IF EXISTS " + keyspace + "");
            System.out.println("Done dropping " + keyspace + "... ");
            Thread.sleep(5000);
            region1.createKeyspace("CREATE KEYSPACE " + keyspace
                    + " WITH REPLICATION = { 'class' : 'NetworkTopologyStrategy', 'datacenter1' : 1 }");
            Thread.sleep(5000);
            System.out.println("Done creating " + keyspace + " keyspace... ");
            region1.createTable("CREATE TABLE " + keyspace + "." + table
                    + " (user_id text PRIMARY KEY, user_name text, user_bcity text)");
            Thread.sleep(8000);
            System.out.println("Done creating " + table + " table... ");
            LOGGER.info("inserting records....");

            //Setup load test queries
            String loadTestPreparedStatement = "insert into "+keyspace + "." + table+" (user_bcity,user_id,user_name) VALUES (?,?,?)";
            String loadTestFinalSelectQuery = "SELECT COUNT(*) as coun FROM " + keyspace + "." + table + "";
            // Run Load Test - Insert rows into user table

           
            u.loadTest(region1, region2, u, loadTestPreparedStatement, loadTestFinalSelectQuery,NUMBER_OF_THREADS, NUMBER_OF_WRITES_PER_THREAD);
        } catch (Exception e) {
            System.out.println("Main Exception " + e);
        } finally {
            utils.close();
        }
    }

    public static Random getRandom() {
        return random;
    }

    public void loadTest(UserRepository region1, UserRepository region2, UserProfile u, String preparedStatement, String finalQuery,
            int noOfThreads, int noOfWritesPerThread) throws InterruptedException {

        Faker faker = new Faker();
        ExecutorService es = Executors.newCachedThreadPool();

        for (int i = 1; i <= noOfThreads; i++) {
            Runnable task = () -> {
                ;
                Random rand = new Random();
                int n = rand.nextInt(2);
                for (int j = 1; j <= noOfWritesPerThread; j++) {
                    UUID guid = java.util.UUID.randomUUID();
                    try {
                        String name = faker.name().lastName();
                        String city = faker.address().city();
                        u.recordcount.incrementAndGet();
                        long startTime = System.currentTimeMillis();

                        // load balancing across regions
                        if (loadBalanceRegions == true) {
                            // approx 50% of the time we will go to region 1
                            if (n == 1) {
                                System.out.print("writing to region 1! \n");
                                region1.insertUser(preparedStatement, guid.toString(), name, city);
                            }
                            // approx 50% of the time we will go to region 2
                            else {
                                System.out.print("writing to region 2! \n");
                                region2.insertUser(preparedStatement, guid.toString(), name, city);
                            }
                        } else {
                            region1.insertUser(preparedStatement, guid.toString(), name, city);
                        }
                        long endTime = System.currentTimeMillis();
                        long duration = (endTime - startTime);
                        System.out.print("insert duration time millis: " + duration + "\n");
                        totalLatency.getAndAdd(duration);
                        u.insertCount.incrementAndGet();
                    } 
                    catch (Exception e) {
                        u.exceptioncount.incrementAndGet();                        
                        System.out.println("Exception: " + e);
                    }                                      
                }
            };
            es.execute(task);
        }
        es.shutdown();
        boolean finished = es.awaitTermination(5, TimeUnit.MINUTES);
        if (finished) {
            Thread.sleep(3000);
            region1.selectUserCount(finalQuery);            
            System.out.println("count of records attempted: " + u.recordcount);
            long latency = (totalLatency.get() / insertCount.get());
            System.out.print("Average Latency: " + latency + "\n");
            System.out.println("Finished executing all threads.");
            System.exit(0);            
        }

    }
}
