---
page_type: sample
languages:
- java
products:
- azure
description: "Azure Cosmos DB is a globally distributed multi-model database. One of the supported APIs is the Cassandra API"
urlFragment: azure-cosmos-db-cassandra-java-retry-sample
---

# Handling rate limited requests in the Azure Cosmos DB API for Cassandra (v4 Driver)
Azure Cosmos DB is a globally distributed multi-model database. One of the supported APIs is the Cassandra API. This sample illustrates how to handle rate limited requests. These are also known as [429 errors](https://docs.microsoft.com/rest/api/cosmos-db/http-status-codes-for-cosmosdb), and are returned when the consumed throughput exceeds the number of [Request Units](https://docs.microsoft.com/azure/cosmos-db/request-units) that have been provisioned for the service. In this code sample, we implement a custom retry policy for Cosmos DB.

The retry policy handles errors such as OverLoadedException (which may occur due to rate limiting), and uses an exponential growing back-off scheme for retries. The time between retries is increased by a growing back off time (default: 1000 ms) on each retry, unless maxRetryCount is -1, in which case it backs off with a fixed duration. It is important to handle rate limiting in Azure Cosmos DB to prevent errors when [provisioned throughput](https://docs.microsoft.com/azure/cosmos-db/how-to-provision-container-throughput) has been exhausted. 

## Prerequisites
* Before you can run this sample, you must have the following prerequisites:
    * An active Azure Cassandra API account - If you don't have an account, refer to the [Create Cassandra API account](https://aka.ms/cassapijavaqs).
    * [Java Development Kit (JDK) 1.8+](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
        * On Ubuntu, run `apt-get install default-jdk` to install the JDK.
    * Be sure to set the JAVA_HOME environment variable to point to the folder where the JDK is installed.
    * [Download](http://maven.apache.org/download.cgi) and [install](http://maven.apache.org/install.html) a [Maven](http://maven.apache.org/) binary archive
        * On Ubuntu, you can run `apt-get install maven` to install Maven.
    * [Git](https://www.git-scm.com/)
        * On Ubuntu, you can run `sudo apt-get install git` to install Git.

## Running this sample
1. Clone this repository using `git clone git@github.com:Azure-Samples/azure-cosmos-db-cassandra-java-retry-sample.git cosmosdb`.

2. Change directories to the repo using `cd cosmosdb/java-examples`

3. Next, substitute the Cassandra host, username, and password in  `java-examples\src\test\resources\config.properties` for two regions, with your Cosmos DB account's values from connectionstring panel of the portal. Note that this sample assumes you have [multi-master](https://docs.microsoft.com/en-us/azure/cosmos-db/how-to-multi-master) configured, with two regions. When adding the regional contact point, you should specify the regional suffix, as below (e.g. "cassandrahost-westus"). However, if you do not have multi-master setup, you can simply use the same contact point and region value for both region 1 and region 2 (note however that if you only have 1 region, the load balancing portion of the test will not reduce the latency when rate limiting). Your config file should look like the below:

    ```
    cassandra_port=10350
    cassandra_region1ContactPoint=cassandrahost-francecentral.cassandra.cosmos.azure.com
    cassandra_region2ContactPoint=cassandrahost-westus.cassandra.cosmos.azure.com
    cassandra_region1=France Central
    cassandra_region2=West US
    cassandra_username=cassandrahost
    cassandra_password=******
    #ssl_keystore_file_path=<FILLME>
    #ssl_keystore_password=<FILLME>    
    ```
    If ssl_keystore_file_path is not given in config.properties, then by default <JAVA_HOME>/jre/lib/security/cacerts will be used. If ssl_keystore_password is not given in config.properties, then the default password 'changeit' will be used

4. Run `mvn clean install` from java-examples folder to build the project. This will generate cosmosdb-cassandra-examples.jar under target folder.
 
5. Run `java -cp target/cosmosdb-cassandra-examples.jar com.microsoft.azure.cosmosdb.cassandra.examples.UserProfile` in a terminal to start your java application. This will create a keyspace and user table, and then run a load test with many concurrent threads attempting to force rate limiting (429) errors in the database. The output will include the insert duration times, average latency, the number of users present in the table after the load test, and the number of user inserts that were attempted. Users in table and records attempted should be identical since rate limits have been successfully handled and retried. Notice that although requests are all successful, you may see significant "average latency" due to requests being retried after rate limiting:

   ![Console output](./media/output.png)

    If you do not see overloaded errors, you can increase the number of threads in UserProfile.java in order to force rate limiting: 

    ```java
        public static final int NUMBER_OF_THREADS = 40;
    ```

6. In a real world scenario, you may wish to take steps to increase the provisioned throughput when the system is experiencing rate limiting. Note that you can do this programmatically in the Azure Cosmos DB API for Cassandra by executing [ALTER commends in CQL](https://docs.microsoft.com/azure/cosmos-db/cassandra-support#keyspace-and-table-options). In production, you should handle 429 errors in a similar fashion to this sample, and monitor the system, increasing throughput if 429 errors are being recorded by the system. You can monitor whether requests are exceeding provisioned capacity using Azure portal metrics:

   ![Console output](./media/metrics.png)

    You can try increasing the provisioned RUs in the Cassandra Keyspace or table to see how this will improve latencies. You can consult our article on [elastic scale](https://docs.microsoft.com/en-us/azure/cosmos-db/manage-scale-cassandra) to understand the different methods for provisioning throughput in Cassandra API. 

7. One alternative to increasing RU provisioning for mitigating rate limiting, which might be more useful in scenarios where there is a highly asymmetrical distribution of consumed throughput between regions (i.e. you have many more reads/writes in one region than others), is to load balance between regions on the client. In version 4 of the Datastax OSS driver for Cassandra, there was a [change in philosophy](https://docs.datastax.com/en/developer/java-driver/4.5/manual/core/load_balancing/) concerning whether load balancing should be handled at the application level. Datastax now believe that this is not the right place to handle this: if a whole datacenter went down at once, it probably means a catastrophic failure happened in Region1, and the application node is down as well. Failover should therefore be cross-region instead (e.g. handled by a load balancer). As such, the default load balancing policy does not allow remote nodes, and you must provide a local datacenter name at the application level (this must also match the contact point you have provided). We simulate this in the sample, by creating two sessions, each pointing to different regions in Cosmos DB:

    To test this out, set the `loadBalanceRegions` variable to true:

    ```java
    Boolean loadBalanceRegions = true;
    ```
    Note: to observe any difference when running this, you would need to have two regions configured for replication, and [multi-master writes configured](https://docs.microsoft.com/en-us/azure/cosmos-db/how-to-multi-master). When you run the test again with load loadBalanceRegions set to true, you should see requests being written to different regions, with latencies reduced, without having to increase provisioned throughput (RUs):

    ![Console output](./media/loadbalancingoutput.png)

    Note: you may notice that if you are not experiencing rate limiting even when load balancing is set to false in this sample, then the average latency if load balancing is set to true might be higher. There is because there is a cost trade off between the latency incurred when routing to a further away region (in order to lower cost of RU provisioning by leveraging under-used regions) and keeping latency down to an absolute minimum by always routing to the nearest region, and ensuring that RUs are provisioned at a level which always accounts for the region that has the highest activity. This is trade-off that you will need to decide upon within your business.  
    
    Also bear in mind that when writing data to Cassandra, you should ensure that you account for [query idempotence](https://docs.datastax.com/en/developer/java-driver/3.0/manual/idempotence/), and the relevant rules for [retries](https://docs.datastax.com/en/developer/java-driver/3.0/manual/retries/#retries-and-idempotence). You should always perform sufficient load testing to ensure that the implementation meets your requirements.

## About the code
The code included in this sample is a load test to simulate a scenario where Cosmos DB will rate limit requests (return a 429 error) because there are too many requests for the [provisioned throughput](https://docs.microsoft.com/azure/cosmos-db/how-to-provision-container-throughput) in the service. In this sample, we create a Keyspace and table, and run a multi-threaded process that will insert users concurrently into the user table. To help generate random data for users, we use a java library called "javafaker", which is included in the build dependencies. The loadTest() will eventually exhaust the provisioned Keyspace RU allocation (default is 400RUs). We also provide a client side load balancing test. 



## Review the code

You can review the following files: `src/test/java/com/microsoft/azure/cosmosdb/cassandra/util/CassandraUtils.java` and `src/test/java/com/microsoft/azure/cosmosdb/cassandra/repository/UserRepository.java` to understand how the sessions are created. You should also review the main class file  `src/test/java/com/microsoft/azure/cosmosdb/cassandra/examples/UserProfile.java` where the load test is created and run. The parameters (maxRetryCount, growingBackOffTimeMillis, fixedBackOffTimeMillis) for retry policy are defined within `src/test/resources/reference.conf`

```
  profiles {
    # Cosmos DB Custom Policies
    cosmos-policies {
      cosmos.retry-policy {
        class = com.microsoft.azure.cosmos.cassandra.CosmosRetryPolicy
        maxRetryCount = "20"
        growingBackOffTimeMillis = "100"
        fixedBackOffTimeMillis = "1000"
      }         
    }
  }
```

Please note that timeout limits are also set in reference.conf:

```
    timeout = 60 seconds

```

You should also review the custom retry policy: `src/test/java/com/microsoft/azure/cosmos/cassandra/CosmosRetryPolicy.java`

## More information

- [Azure Cosmos DB](https://docs.microsoft.com/azure/cosmos-db/introduction)
- [Java driver Source](https://github.com/datastax/java-driver)
- [Java driver Documentation](https://docs.datastax.com/en/developer/java-driver/)
