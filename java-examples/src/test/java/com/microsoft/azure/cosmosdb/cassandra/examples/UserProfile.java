package com.microsoft.azure.cosmosdb.cassandra.examples;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
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
    AtomicLong totalReadLatency = new AtomicLong(0);
    AtomicLong averageReadLatency = new AtomicLong(0);
    Queue<String> docIDs = new ConcurrentLinkedQueue<String>();

    private static int PORT;
    private static String region1ContactPoint;

    public static void main(String[] s) throws Exception {

        CassandraUtils utils = new CassandraUtils();
        UserProfile u = new UserProfile();
        CqlSession session = utils.getSession(region1ContactPoint, PORT);
        //CqlSession session = CqlSession.builder().build(); 
        UserRepository repository = new UserRepository(session);
        String keyspace = "uprofile";
        String table = "user2";
        try {
            //Create keyspace and table in cassandra database
            repository.deleteTable("DROP KEYSPACE IF EXISTS " + keyspace + "");
            System.out.println("Done dropping " + keyspace + "... ");
            repository.createKeyspace("CREATE KEYSPACE " + keyspace
                    + " WITH REPLICATION = { 'class' : 'NetworkTopologyStrategy', 'datacenter1' : 1 }");
            System.out.println("Done creating " + keyspace + " keyspace... ");
            repository.createTable("CREATE TABLE " + keyspace + "." + table
                    + " (user_id text PRIMARY KEY, user_name text, user_bcity text)");
            Thread.sleep(2000);
            System.out.println("Done creating " + table + " table... ");
            LOGGER.info("inserting records....");

            //Setup load test queries
            String loadTestPreparedStatement = "insert into "+keyspace + "." + table+" (user_bcity,user_id,user_name) VALUES (?,?,?)";
            String loadTestFinalSelectQuery = "SELECT COUNT(*) as coun FROM " + keyspace + "." + table + "";
            
            // Run Load Test - Insert rows into user table
            u.loadTest(keyspace, table, repository, u, loadTestPreparedStatement, loadTestFinalSelectQuery,NUMBER_OF_THREADS, NUMBER_OF_WRITES_PER_THREAD);
        } catch (Exception e) {
            System.out.println("Main Exception " + e);
        } finally {
            //session.close();
            utils.close();
        }
    }

    public static Random getRandom() {
        return random;
    }

    public void loadTest(String keyspace, String table, UserRepository repository, UserProfile u, String preparedStatement, String finalQuery,
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
                    String strGuid = guid.toString();
                    docIDs.add(strGuid);
                    try {
                        String name = faker.name().lastName();
                        String city = faker.address().city();
                        u.recordcount.incrementAndGet();
                        long startTime = System.currentTimeMillis();
                        repository.insertUser(preparedStatement, guid.toString(), name, city);
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
        //boolean finished = true;
        if (finished) {
            Thread.sleep(3000);    
               
            long latency = (totalLatency.get() / insertCount.get());

            //lets look at latency for reads in local region by reading all the records just written
            int readcount = NUMBER_OF_THREADS * NUMBER_OF_WRITES_PER_THREAD;
            long noOfUsersInTable = 0;
            noOfUsersInTable = repository.selectUserCount(finalQuery);
            for (String id : docIDs) {
                long startTime = System.currentTimeMillis();
                repository.selectUser(id.toString(), keyspace, table);
                long endTime = System.currentTimeMillis();
                long duration = (endTime - startTime);  
                System.out.print("read duration time millis: " + duration + "\n");
                totalReadLatency.getAndAdd(duration);
            }
            System.out.println("count of inserts attempted: " + u.recordcount);             
            System.out.println("count of users in table: " + noOfUsersInTable);
            long readLatency = (totalReadLatency.get() / readcount); 
            System.out.print("Average write Latency: " + latency + "\n");
            System.out.println("Average read latency: " + readLatency);      
            System.out.println("Finished executing all threads.");    
            System.exit(0);            
        }

    }
}
