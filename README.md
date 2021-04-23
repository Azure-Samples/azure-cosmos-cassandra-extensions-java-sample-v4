---
page_type: sample
languages:
- java
products:
- azure
description: "Azure Cosmos DB is a globally distributed multi-model database. One of the supported APIs is the Cassandra API"
urlFragment: azure-cosmos-cassandra-extensions-java-sample-v4
---

# Using Retry and Load Balancing policies in Azure Cosmos DB Cassandra API (v4 Driver)
Azure Cosmos DB is a globally distributed multi-model database. One of the supported APIs is the Cassandra API. This sample illustrates how to handle rate limited requests, also known as [429 errors](https://docs.microsoft.com/rest/api/cosmos-db/http-status-codes-for-cosmosdb) (when consumed throughput exceeds the number of [Request Units](https://docs.microsoft.com/azure/cosmos-db/request-units) provisioned for the service), and use a load balancing policy to specify preferred read or write regions. In this code sample, we implement the [Azure Cosmos DB extension for Cassandra API](https://github.com/Azure/azure-cosmos-cassandra-extensions/tree/feature/java-driver-4/improved-concurrency-and-test-coverage) for the [Java v4 Datastax Apache Cassandra OSS Driver](https://github.com/datastax/java-driver/tree/4.x). The extension JAR is offered as a **public preview** release [in maven](https://search.maven.org/artifact/com.azure/azure-cosmos-cassandra-driver-4-extensions/0.1.0-beta.1/jar). 

```xml
        <dependency>
            <groupId>com.azure</groupId>
            <artifactId>azure-cosmos-cassandra-driver-4-extensions</artifactId>
            <version>0.1.0-beta.1</version>
        </dependency>
```


## Prerequisites
* Before you can run this sample, you must have the following prerequisites:
    * An active Azure Cassandra API account - If you don't have an account, refer to the [Create Cassandra API account](https://aka.ms/cassapijavaqs). For illustration purposes, the sample assumes an account with two regions that are very far apart: in this case Australia East and UK South (where UK South is very close to where the client application has been deployed). 
    * [Java Development Kit (JDK) 1.8+](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
        * On Ubuntu, run `apt-get install default-jdk` to install the JDK.
    * Be sure to set the JAVA_HOME environment variable to point to the folder where the JDK is installed.
    * [Download](http://maven.apache.org/download.cgi) and [install](http://maven.apache.org/install.html) a [Maven](http://maven.apache.org/) binary archive
        * On Ubuntu, you can run `apt-get install maven` to install Maven.
    * [Git](https://www.git-scm.com/)
        * On Ubuntu, you can run `sudo apt-get install git` to install Git.

## Running this sample
1. Clone this repository using `git clone git@github.com:Azure-Samples/azure-cosmos-cassandra-extensions-java-sample-v4.git cosmosdb`.

1. Change directories to the repo using `cd cosmosdb/java-examples`

1. Next, substitute the Cassandra `username`, and `password` in the auth-provider section of the file `java-examples\src\test\resources\application.conf` (you can get all these values from "connection string" tab in Azure portal):

    ```conf
    auth-provider {
      # By default we use the PlainTextAuthProvider (see reference.conf) and specify the username and password here.
      username = "<FILLME>"
      password = "<FILLME>"
      class = PlainTextAuthProvider
    }   
    ```

    By default <JAVA_HOME>/jre/lib/security/cacerts will be used for the SSL keystore, and default password 'changeit' will be used - see `src/test/java/com/microsoft/azure/cosmosdb/cassandra/util/CassandraUtils.java`.

1. Now find the `basic` configuration section within `application.conf`. Replace `<FILLME>` in the `contact-points` parameter with the `CONTACT POINT` value from "connection string" tab in Azure portal:

    ```conf
      basic {   
        contact-points = ["<FILLME>:10350"]    
        load-balancing-policy {
            class = com.azure.cosmos.cassandra.CosmosLoadBalancingPolicy
          # Global endpoint for connecting to Cassandra
          #
          #   When global-endpoint is specified, you may specify a read-datacenter, but must not specify a write-datacenter.
          #   Writes will go to the default write region when global-endpoint is specified.
          #
          #   When global-endpoint is not specified, you must specify values for read-datacenter, write-datacenter, and
          #   datastax-java-driver.basic.contact-points.
          #
          #   Set the variables referenced here to match the topology and preferences for your
          #   Cosmos DB Cassandra API instance.
    
          global-endpoint = ""
    
          # Datacenter for read operations
    
          # Datacenter for read operations
          read-datacenter = "Australia East"
          # Datacenter for write operations
          write-datacenter = "UK South"
        }
      }
    ``` 

Note that `application.conf` contains various connection settings that are recommended for Cosmos DB Cassandra API, as well as implementing the retry and load balancing policies in the extension library. You will also notice a preferred write region and read region have been defined. For illustration in this sample, the account is initially created in UK South (which becomes the write region), and then Australia East is chosen as an additional read region, where UK South is very close to the client code. You can choose any two regions with a similar distance between them, where the client is deployed very close to the write region.

   ![Console output](./media/regions.png)

1. Run `mvn clean install` from java-examples folder to build the project. This will generate cosmosdb-cassandra-examples.jar under target folder.
 
1. Run `java -cp target/cosmosdb-cassandra-examples.jar com.microsoft.azure.cosmosdb.cassandra.examples.UserProfile` in a terminal to start your java application. This will create a keyspace and user table, and then run a load test with many concurrent threads attempting to force rate limiting (429) errors in the database. The test will also collect the ids of all the records and then read them back sequentially, measuring the latency. The output will include a report of the average latencies for both reads and writes. The "users in table" and "inserts attempted" should be identical since rate limiting has been successfully handled. Notice that although requests are all successful, you may see significant "average latency" of writes due to requests being retried after rate limiting. You should also see a high latency for reads as the read region (in this case Australia East) is much further away.

   ![Console output](./media/output.png)

    If you do not see higher latencies for writes, you can increase the number of threads in UserProfile.java in order to force more rate limiting: 

    ```java
        public static final int NUMBER_OF_THREADS = 40;
    ```

1. To improve the read latency for our UK South client application instance, we can change the `read-datacenter` parameter (implemented by the load balancing policy in the extension) within `application.conf` to make sure that reads are served from the region local to the application. Of course, for the application instance that is running in Australia East, the settings would stay as Australia East, to ensure that each client is communicating with the closest region:

    ```conf
          # Datacenter for read operations
          read-datacenter = "UK South"
          # Datacenter for write operations
          write-datacenter = "UK South"
    ```

1. Run the application again and you should observe lower read latencies.

   ![Console output](./media/local-read-output.png)

 
Bear in mind that when writing data to Cassandra, you should ensure that you account for [query idempotence](https://docs.datastax.com/en/developer/java-driver/3.0/manual/idempotence/), with respect to the relevant rules for [retries](https://docs.datastax.com/en/developer/java-driver/3.0/manual/retries/#retries-and-idempotence). You should always perform sufficient load testing to ensure that the implementation meets your requirements.

## About the code
The code included in this sample is a load test to simulate a scenario where Cosmos DB will rate limit requests (return a 429 error) because there are too many requests for the [provisioned throughput](https://docs.microsoft.com/azure/cosmos-db/how-to-provision-container-throughput) in the service. The retry policy handles errors such as OverLoadedException (which may occur due to rate limiting), and uses an exponential growing back-off scheme for retries. The time between retries is increased by a growing back off time (default: 1000 ms) on each retry, unless maxRetryCount is -1, in which case it backs off with a fixed duration. It is important to handle rate limiting in Azure Cosmos DB to prevent errors when [provisioned throughput](https://docs.microsoft.com/azure/cosmos-db/how-to-provision-container-throughput) has been exhausted. The parameters (maxRetryCount, growingBackOffTimeMillis, fixedBackOffTimeMillis) for retry policy are defined within `src/test/resources/application.conf`:

```conf
    retry-policy {
      # When you take a dependency on azure-cosmos-cassandra-driver-4-extensions CosmosRetryPolicy is used by default.
      # This provides a good out-of-box experience for communicating with Cosmos Cassandra instances.
      class = com.azure.cosmos.cassandra.CosmosRetryPolicy
      max-retries = 5              # Maximum number of retries
      fixed-backoff-time = 5000    # Fixed backoff time in milliseconds
      growing-backoff-time = 1000  # Growing backoff time in milliseconds
    }
```


In this sample, we create a Keyspace and table, and run a multi-threaded process that will insert users concurrently into the user table. To help generate random data for users, we use a java library called "javafaker", which is included in the build dependencies. The loadTest() will eventually exhaust the provisioned Keyspace RU allocation (default is 400RUs). After the writes have finished, we read all of the records written to the database and measure the latencies. This is intended to illustrate the difference between using a preferred read region in the load balancing policy, vs the default region. The class for load balancing policy is added in `application.conf`:

```conf
    load-balancing-policy {
        class = com.azure.cosmos.cassandra.CosmosLoadBalancingPolicy

```


## Review the code

You can review the following files: `src/test/java/com/microsoft/azure/cosmosdb/cassandra/util/CassandraUtils.java` and `src/test/java/com/microsoft/azure/cosmosdb/cassandra/repository/UserRepository.java` to understand how the sessions are created. You should also review the main class file  `src/test/java/com/microsoft/azure/cosmosdb/cassandra/examples/UserProfile.java` where the load test is created and run. 

## More information

- [Azure Cosmos DB](https://docs.microsoft.com/azure/cosmos-db/introduction)
- [Java driver Source](https://github.com/datastax/java-driver)
- [Java driver Documentation](https://docs.datastax.com/en/developer/java-driver/)
