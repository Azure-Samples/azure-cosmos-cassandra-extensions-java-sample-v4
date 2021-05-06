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
Azure Cosmos DB is a globally distributed multi-model database. One of the supported APIs is the Cassandra API. This sample illustrates how to handle rate limited requests, also known as [429 errors](https://docs.microsoft.com/rest/api/cosmos-db/http-status-codes-for-cosmosdb) (when consumed throughput exceeds the number of [Request Units](https://docs.microsoft.com/azure/cosmos-db/request-units) provisioned for the service), and use a load balancing policy to specify preferred read or write regions. In this code sample, we implement the [Azure Cosmos DB extension for Cassandra API](https://github.com/Azure/azure-cosmos-cassandra-extensions/tree/release/java-driver-4/1.0.0) for the [Java v4 Datastax Apache Cassandra OSS Driver](https://github.com/datastax/java-driver/tree/4.x). The extension JAR is offered as a **public preview** release [in maven](https://search.maven.org/artifact/com.azure/azure-cosmos-cassandra-driver-4-extensions/1.0.0/jar). 

```xml
        <dependency>
            <groupId>com.azure</groupId>
            <artifactId>azure-cosmos-cassandra-driver-4-extensions</artifactId>
            <version>1.0.0</version>
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

1. Next, create AZURE_COSMOS_CASSANDRA_USERNAME and AZURE_COSMOS_CASSANDRA_PASSWORD environment variables with corresponding values for Cassandra API `username`, and `password` in the auth-provider section of the file `java-examples/src/main/resources/application.conf` (you can get the values from "connection string" tab in Azure portal):

    ```conf
    auth-provider {
      # By default we use the PlainTextAuthProvider (see reference.conf) and specify the username and password here.
      username = ${AZURE_COSMOS_CASSANDRA_USERNAME}
      password = ${AZURE_COSMOS_CASSANDRA_PASSWORD}
    }
    ```

1. Now find the `basic` configuration section within `application.conf`. Create the `AZURE_COSMOS_CASSANDRA_GLOBAL_ENDPOINT` environment variable referenced in the `contact-points` parameter and give it the value of `CONTACT POINT` and `PORT` from "connection string" tab in Azure portal. The value of the `AZURE_COSMOS_CASSANDRA_GLOBAL_ENDPOINT` environment variable should be of the form `uniquehostname.cassandra.cosmos.azure.com:10350`.

    ```conf
      basic {   
        contact-points = [${AZURE_COSMOS_CASSANDRA_GLOBAL_ENDPOINT}]    
        load-balancing-policy {
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
          read-datacenter = "Australia East"
          write-datacenter = "UK South"
        }
      }
    ``` 

Note that `application.conf` contains settings that override [reference.conf](https://github.com/Azure/azure-cosmos-cassandra-extensions/blob/release/java-driver-4/0.1.0-beta.1/package/src/main/resources/reference.conf) in the [Azure Cosmos DB extension for Cassandra API], which we are implementing in this sample. The setting `datastax-java-driver.advanced.ssl-engine-factory.hostname-validation` has been set to `false` due to a known issue - see [here](https://github.com/Azure/azure-cosmos-cassandra-extensions/blob/develop/java-driver-4/package/KNOWN_ISSUES.md#hostname-verification-fails-when-accessing-a-multi-region-cosmos-cassandra-api-instance) (this will be fixed before the extension exits beta). Also note that `reference.conf` defines various connection settings that we recommend for a good experience using Cassandra API. You will notice a preferred write region and read region have been defined in `application.conf`. For illustration in this sample, the account is initially created in UK South (which becomes the write region), and then Australia East is chosen as an additional read region, where UK South is very close to the client code. You can choose any two regions with a similar distance between them, where the client is deployed very close to the write region.

   ![Console output](./media/regions.png)

1. Run `mvn clean package` from java-examples folder to build the project. This will generate azure-cosmos-cassandra-examples-1.0.0-SNAPSHOT.jar under target folder.
 
1. Run `java -jar target/azure-cosmos-cassandra-examples-1.0.0-SNAPSHOT.jar` in a terminal to start your java application. This will create a keyspace and user table, and then run a load test with many concurrent threads attempting to force rate limiting (429) errors in the database. The test will also collect the ids of all the records and then read them back sequentially, measuring the latency. The output will include a report of the average latencies for both reads and writes. The "users in table" and "inserts attempted" should be identical since rate limiting has been successfully handled. Notice that although requests are all successful, you may see significant "average latency" of writes due to requests being retried after rate limiting. You should also see a high latency for reads as the read region (in this case Australia East) is much further away.

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
The code included in this sample is a load test to simulate a scenario where Cosmos DB will rate limit requests (return a 429 error) because there are too many requests for the [provisioned throughput](https://docs.microsoft.com/azure/cosmos-db/how-to-provision-container-throughput) in the service. The retry policy handles errors such as OverLoadedException (which may occur due to rate limiting), and uses an exponential growing back-off scheme for retries. The time between retries is increased by a growing back off time (default: 1000 ms) on each retry, unless maxRetryCount is -1, in which case it backs off with a fixed duration. It is important to handle rate limiting in Azure Cosmos DB to prevent errors when [provisioned throughput](https://docs.microsoft.com/azure/cosmos-db/how-to-provision-container-throughput) has been exhausted. The parameters (maxRetryCount, growingBackOffTimeMillis, fixedBackOffTimeMillis) for retry policy are defined within [reference.conf](https://github.com/Azure/azure-cosmos-cassandra-extensions/blob/release/java-driver-4/0.1.0-beta.1/package/src/main/resources/reference.conf) of the [Azure Cosmos DB extension for Cassandra API](https://github.com/Azure/azure-cosmos-cassandra-extensions/tree/release/java-driver-4/0.1.0-beta.1). You can also override these in `src/main/resources/application.conf` within this sample:

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


In this sample, we create a Keyspace and table, and run a multi-threaded process that will insert users concurrently into the user table. To help generate random data for users, we use a java library called "javafaker", which is included in the build dependencies. The `loadTest()` will eventually exhaust the provisioned Keyspace RU allocation (default is 400RUs). After the writes have finished, we read all of the records written to the database and measure the latencies. This is intended to illustrate the difference between using a preferred local read region in the load balancing policy vs a default region that might be further away from your client application. The class for load balancing policy is referenced in [reference.conf](https://github.com/Azure/azure-cosmos-cassandra-extensions/blob/release/java-driver-4/0.1.0-beta.1/package/src/main/resources/reference.conf) of the [Azure Cosmos DB extension for Cassandra API], and the values for `global-endpoint`, `read-datacenter`, and `write-datacenter` are overriden in `src/main/resources/application.conf` within this sample:

```conf
    load-balancing-policy {

      # Cosmos load balancing policy parameters
      #
      #   When global-endpoint is specified, you may specify a read-datacenter, but must not specify a write-datacenter.
      #   Writes will go to the default write region when global-endpoint is specified. When global-endpoint is not
      #   specified, you must specify values for read-datacenter and write-datacenter.
      #
      #   Update this file or run this example with these system properties set to override the values provided here:
      #
      #   -Ddatastax-java-driver.basic.load-balancing-policy.global-endpoint=[<global-endpoint>|""]
      #   -DDdatastax-java-driver.basic.load-balancing-policy.read-datacenter=[<read-datacenter>|""]
      #   -DDdatastax-java-driver.basic.load-balancing-policy.write-datacenter=[<write-datacenter>|""]
      #
      #   Alternatively, set the environment variables referenced here to match the topology and preferences for your
      #   Cosmos DB Cassandra API instance.

      multi-region-writes = false
      global-endpoint = ""
      read-datacenter = "Australia East"
      write-datacenter = "UK South"
      preferred-regions = ["Australia East", "UK West"]
    }
```

## Failover scenarios

As illustrated above, the load balancing policy implemented in this sample (see [Cosmos Cassandra Extensions](https://github.com/Azure/azure-cosmos-cassandra-extensions/tree/release/java-driver-4/1.0.0)) includes a feature that allows you to specify a `read-datacenter` and `write-datacenter` for application level load balancing. Specifying these options will route read and write requests to their corresponding data centers in either case. 

If `read-datacenter` is specified, the policy prioritizes that region for read requests. Either one of `write-datacenter` or `global-endpoint` needs to be specified in order to determine the data center for write requests. If `write-datacenter` is specified, writes will be prioritized for that region. When `global-endpoint` is specified, the write requests will be prioritized for the default write region.

### Preferred regions

In the Cosmos Cassandra Extension for Java 4, the load balancing policy has been enhanced to include a `preferred-regions` parameter. This allows you to configure deterministic failover to specified regions in a multi-region deployment, in case of regional outages. The policy uses the regions in the list you specify, in priority order as determined by `preferred-regions`, to perform operations. If `preferred-regions` is null or not present, the policy uses the region specified in `read-datacenter` for reads, and either `write-datacenter` or `global-endpoint` to determine the region for writes. If neither `preferred-regions` or `read-datacenter` are present, the write region is the preferred location for all operations. When multi-region writes are enabled on the Cosmos DB account (and `multi-region-write` is set to `true`), the priority order for writes will be exactly the same as for reads.

## Review the code

You can review the following files:  the main class file  `src/main/java/com/azure/cosmos/cassandra/examples/UserProfile.java`, `src/main/resources/application.conf`, and `src/main/java/com/azure/cosmos/cassandra/repository/UserRepository.java` to understand how sessions are created. You should also review the main class file  `src/main/java/com/azure/cosmos/cassandra/examples/UserProfile.java` to see how the load test is created and run.

## More information

- [Azure Cosmos DB](https://docs.microsoft.com/azure/cosmos-db/introduction)
- [Java driver Source](https://github.com/datastax/java-driver)
- [Java driver Documentation](https://docs.datastax.com/en/developer/java-driver/)
