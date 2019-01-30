# AaaS Demo
This git project is for the tutorial to build shared AI services.
# Background
Only a small fraction of a real-world industry AI application is composed of the Machine learning code or algorithm, the required surrounding infrastructure for building a shared AI services is vast and complex. If you are sucking with those hidden truths of AI, this course is right for you.
This course gets you up to build a shared AI service end to end by answering critical questions including: 
- best deep learning technical approach for enterprise AI? 
- key factors to consider an AI Engineering platforms? 
- become a qualified AI Engineer? etc..
There are also 2 hands on code labs and one live demo, 
- Elaborate a benchmark between Spark Machine learning and Spark Deep learning with a user item propensity model example
- Step by step teach you how to build an end to end AI Pipeline with Kafka, NiFi, Spark Streaming and Keras on Spark.

# Content
## Module 1: Case study: AI as a Service
- A typical end to end AI Service.
- Hidden truths of AI
- Options of AI as a Service
- The journey of AI as a Service
- Challenges of traditional machine learning
- How deep learning can improve
- Enterprise requirements for deep learning
- Deep learning approaches evaluation and Keras on Spark

## Module 2: Keras on Spark
- Keras introduction
- Options of Keras on Spark
- Build a User Item Propensity model with deep learning algorithms
- Use case for user item propensity model
- Neural Collaborative Filtering deep learning algorithm

## Code Lab 1
- Build a docker image and run a Keras on Spark container
- Run the NCF deep learning pipeline for User Item Propensity model

## Module 3: AI Engineering platform and AI Engineers
- Key factors to consider an AI Engineering platform
- Architect a data pipeline framework
- Apache NiFi introduction
- Traditional AI Tribe and its challenges
- Knowledges and skills are required for AI Engineer
- Growing path for an AI Engineer

## Module 4: Benchmark between Spark Machine learning and Deep learning
- Traditional Collaborative Filtering approach with Spark Mllib ALS (Scala)
- Build an NCF deep learning approach with Intel Analytic Zoo on Spark (Scala)

## Code Lab 2: Spark Mllib (Als) Vs Intel Analytic Zoo on Spark (NCF)

## Live Demo: Build an end to end AI Pipeline for AI as a Service with Kafka, NiFi, Spark Streaming and Keras on Spark


# Installation
AaaSDemo requires docker container to run if you are working at a windows pc or laptop ( prefer windows 10) 
## Install Docker and Docker Toolbox
### Install the docker for window
https://store.docker.com/editions/community/docker-ce-desktop-windows
- please use your own docker account
- Get Docker CE for Windows (stable)
- Double-click Docker for Windows Installer to run the installer.
- When the installation finishes, Docker starts automatically. The whale  in the notification area indicates that Docker is running, and accessible from a terminal.
### Install the Docker Toolbox
- https://docs.docker.com/toolbox/toolbox_install_windows/
- After installation , click Kitematic (Alpha) shortcut
- Then click DOCKER-CLI on the left corner, you will enter a docker cli window

# Create a standalone Keras environment with python and backend ready 
At Module 2, we are going to learn Keras 
## Get docker image for Keras
- create a folder at your laptop (such as C:\AaasDemo)
```sh
$ cd C:\AaaSDemo\
$ docker pull ufoym/deepo:keras-py27-cpu
$ docker run -it ufoym/deepo:keras-py27-cpu bash
```
### Get docker image for Keras with Juniper (optional)
```sh
$ cd C:\AaaSDemo\
$ docker pull ufoym/deepo:all-py27-jupyter-cpu
$ docker run -it -p 8888:8888  --ipc=host ufoym/deepo:all-py27-jupyter-cpu jupyter notebook --no-browser --ip=0.0.0.0 --allow-root --NotebookApp.token="demo" --notebook-dir='/root'
```
**Note! don't close window or exit the shell , then the container will be terminated , if you want to quit the container and want to attach it back , you should Ctrl+p then Ctrl+q to leave container safely**

## Keras python examaple 
We will go though this Keras python example at Juptyer notebook , you can find it under /Python folder
- mnist_cnn.py
- mnist_mlp.py


# Create a standalone Keras on Spark environment with python and analytic-zoo lib ready 
Module 2 and Code Lab 1 require another standalone spark/python environment to run Keras on Spark jobs
## Benchmark Tools and environments
### Spark ML
  The project uses Spark Mlib (https://spark.apache.org/mllib/) as machine learning library and Apache Spark is the run time container to run machine learning job and the programming language will be Scala
  - The project designed those steps for this part : 
	1)	Feature Engineering. Pre-compute some aggregated features. 
	2)	Clustering. Pre-compute the clusters (segmentation) of users. Considering the amount of users and items are both large and it is hard to calculate all combinations of user-item propensity model, so cluster users based on behavior similarities is a very popular way to divided big scopes to small parts in parallel. For clustering of users, I am going to try Kmeans non-supervisor learning model (https://spark.apache.org/docs/2.2.0/ml-clustering.html#k-means).
	3)	Modeling and Training. Based on the clusters , abstract the training/test data for each cluster and fit collaborative filtering model and predict user-item propensity scores which I am going to use ALS (https://spark.apache.org/docs/2.2.0/ml-collaborative-filtering.html)
	4)	Tuning. Run Hyper-Parameters-Tuning for ALS and find the best parameters combination , I am going to use hyper parameter tuning (https://spark.apache.org/docs/2.2.0/ml-tuning.html)
	5)	Validation and models evaluation. Generate model evaluation performance metrics for each cluster.

### Spark DL
   The project uses BigDL Keras lib(https://github.com/intel-analytics/analytics-zoo) as deep learning library on top of Spark. 
   With deep learning , remove the Feature Engineering , clustering and Tuning parts as we assume deep learning will automate those optimization procedures and will get similar or better performance metrics.

### Data source
   The project uses real credit transactions from public data source (https://catalog.data.gov/dataset/purchase-card-pcard-fiscal-year-2014).
   
## Build a docker image(don't connect VPN or http proxy) 
- Download the images.zip file and put it in the local folder (such as C:\AaaSDemo\) then unzip it
- If you are running docker at Windows 
```sh
$ cd C:\AaaSDemo
$ Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ Invoke-WebRequest "https://github.com/jack1981/AaaSDemo/raw/master/docker/images.zip" -OutFile "C:\Docker\images.zip" -UseBasicParsing
$ unzip images.zip
```
- If you are running docker at Linux 
```sh
$ cd /home/AaaSDemo
$ wget https://github.com/jack1981/AaaSDemo/raw/master/docker/images.zip
$ unzip images.zip -UseBasicParsing
```
- build the docker images
```sh
$ cd C:\AaaSDemo\images
$ docker build -f demo.df -t demo .
$ docker images
```
- That will take a while depend on your network condition , after successful message , You can check the images will be ready
 
```sh
$ docker images
REPOSITORY          TAG                 IMAGE ID            CREATED             SIZE
demo                latest              738a5dd0e550        36 hours ago        4.4GB
```
## start a container instance from a shell windows
- If you are running at Windows , then you need to set up the $env:COMPOSE_CONVERT_WINDOWS_PATHS=1, if not , you don't need to run that commandd
```sh
$ $env:COMPOSE_CONVERT_WINDOWS_PATHS=1
$ docker run -it -p 8080:8080 -p 8443:8443 -p 10000:10000 -p 8998:8998 -p 12345:12345 -p 8088:8088 -p 4040:4040 -p 7077:7077 -e NotebookPort=12345 -e NotebookToken="demo" -e RUNTIME_DRIVER_CORES_ENV=1 -e RUNTIME_DRIVER_MEMORY=2g -e RUNTIME_EXECUTOR_CORES=1 -e RUNTIME_EXECUTOR_MEMORY=4g -e RUNTIME_TOTAL_EXECUTOR_CORES=1 --name demo -h demo demo:latest bash
```
- You should enter root@demo:/opt/work# or you can attach it later like this

```sh
$ docker exec -it demo /bin/bash
```
## set up hostIP environment
- if you are running docker container at Linux , then you should set up hostIP to the IPV4 IP address.
- if you running docker container at Windows , please remember the hostIP should be configured to to DockerNAT IPV4 Address, at below example , the host-ip is 10.0.75.1
```sh
$ C:\Users\jacks> ipconfig

Windows IP Configuration

Ethernet adapter vEthernet (DockerNAT):

   Connection-specific DNS Suffix  . :
   Link-local IPv6 Address . . . . . : fe80::415f:67c7:bb51:6e11%11
   IPv4 Address. . . . . . . . . . . : 10.0.75.1
   Subnet Mask . . . . . . . . . . . : 255.255.255.0
   Default Gateway . . . . . . . . . :
```
- set up the host-ip env 
```sh
root@demo:/opt/work# export hostIP=10.0.75.1
```

## start and verify notebook
```sh
root@demo:/opt/work# nohup /opt/work/start-notebook.sh >/dev/null 2>&1 & 
```
- You can view the notebook on http://${hostIP}:12345  the token is "demo"

## Keras on Spark examaple
At Module 2 and Code Lab 1, We will go though this Keras on spark example , you can find it under /Python folder
- keras_ncf_zoo.py

# Build the benchmark artifacts
This is for Module 4 and codelab 2
Git clone the current project to download artifacts , build and run

```sh
root@demo:/opt/work# cd /home
root@demo:/home# git config --global http.sslverify false 
root@driver:/home# git clone https://github.com/jack1981/AaaSDemo.git
```
- Build the project with maven command

```sh
root@demo:/home# cd AaaSDemo/
root@demo:/home/AaaSDemo# mvn clean install
```
- Move the data files
 
```sh
root@demo:/home/AaaSDemo# cd data
root@demo:/home/AaaSDemo/data# unzip pcard.zip
root@demo:/home/AaaSDemo/data# mkdir /opt/work/data
root@demo:/home/AaaSDemo/data# mv pcard.csv /opt/work/data
root@demo:/home/AaaSDemo/data# mv offerList.csv /opt/work/data
```
- copy dependent jars

```sh
root@demo:/home/AaaSDemo/data# cd ..
root@demo:/home/AaaSDemo# mv target/aaas-demo-1.0-SNAPSHOT.jar aaas-demo.jar
root@demo:/home/AaaSDemo# cp /opt/work/analytics-zoo-0.4.0-SNAPSHOT/lib/analytics-zoo-bigdl_0.6.0-spark_2.3.1-0.4.0-SNAPSHOT-jar-with-dependencies.jar zoo.jar
root@demo:/home/AaaSDemo# cp jars/*.jar .
root@demo:/home/AaaSDemo# cp scripts/*.sh .
root@demo:/home/AaaSDemo# chmod 777 *
```

# Run the project and check the results
## explain the parameters 
```sh
--trainingStart 20130530 # training start date 
--trainingEnd 20140615  # training end date
--validationEnd 20140630 # validation end date 
--rank 10 # value of ALS rank parameter 
--brank 50 # value of benchmark rank parameter 
--regParam 0.01 # value of ALS regParam parameter 
--bregParam 0.20 # value of benchmark regParam parameter 
--alpha 0.01 # value of ALS alpha parameter 
--balpha 0.15 # value of benchmark alpha parameter
--maxEpoch 10 # value of max iterations parameter
--batchSize 2000 # value of batch size for BigDL
--learningRate 1e-3 # value of learningRate for BigDL
--learningRateDecay 1e-7 # value of learningRateDecay for BigDL
--defaultPartition 10 # spark shuffling partition
--dataFilePath "/opt/work/data/pcard.csv" # the path of data source csv
--negRate 0.2 # the rate to generate negtive sampling 
--randomSampling true # Sampling mode
--debug true # turn on debug or not 
```
- You can create your own run script with different parameters , taking reference of run_als.sh and run_dl.sh
## execute the run default script for Clustering+ALS
```sh
root@driver:/home/AaaSDemo# ./run_als_default.sh
```
## the major milestones and result of performance metrics from Clustering+ALS
```sh
Start Kmeans trainning , training records count: 194118 numClusters is 2 numIterations is 30
...
Start ALS pipeline for cluster: 0
Count of cluster: 0 is 1123
Split data into Training and Validation for cluster : 0:
cluster : 0: training records count: 36068
cluster : 0: validation records count: 4272
...
CrossValidator:54 - Best set of parameters:
{
        als_6c506539bd12-alpha: 0.15,
        als_6c506539bd12-rank: 50,
        als_6c506539bd12-regParam: 0.2
}
CrossValidator:54 - Best cross-validation metric: 1.824787084728743.
...
best rank = 50
positiveDF count: 4272
validationDF count: 4272
evaluation by mid: ****************************************************************************************************
+----+--------+----+----+----+-------------------+------------------+
|mid |posCount|tp  |fp  |fn  |recall             |precision         |
+----+--------+----+----+----+-------------------+------------------+
|1.0 |48.0    |38.0|8.0 |10.0|0.7916666666666666 |0.8260869565217391|
|2.0 |46.0    |26.0|10.0|20.0|0.5652173913043478 |0.7222222222222222|
|3.0 |73.0    |54.0|13.0|19.0|0.7397260273972602 |0.8059701492537313|
|4.0 |44.0    |36.0|8.0 |8.0 |0.8181818181818182 |0.8181818181818182|
|5.0 |98.0    |79.0|14.0|19.0|0.8061224489795918 |0.8494623655913979|
|6.0 |80.0    |49.0|6.0 |31.0|0.6125             |0.8909090909090909|
|7.0 |85.0    |68.0|15.0|17.0|0.8                |0.8192771084337349|
|8.0 |21.0    |9.0 |1.0 |12.0|0.42857142857142855|0.9               |
|9.0 |7.0     |0.0 |1.0 |7.0 |0.0                |0.0               |
|10.0|67.0    |42.0|3.0 |25.0|0.6268656716417911 |0.9333333333333333|
+----+--------+----+----+----+-------------------+------------------+
only showing top 10 rows

total tp: 1046.0
total fp: 152.0
total fn: 2986.0
total recall: 0.2594246031746032
total precision: 0.8731218697829716
cluster : 0:Train and Evaluate End
Start ALS pipeline for cluster: 1
Count of cluster: 1 is 2412
Split data into Training and Validation for cluster : 1:
cluster : 1: training records count: 108556
cluster : 1: validation records count: 22103
...
best rmse  = 54.34150909598154
best rank = 50
positiveDF count: 22103
validationDF count: 22103
evaluation by mid: ****************************************************************************************************
+----+--------+-----+----+----+------------------+------------------+
|mid |posCount|tp   |fp  |fn  |recall            |precision         |
+----+--------+-----+----+----+------------------+------------------+
|1.0 |272.0   |252.0|52.0|20.0|0.9264705882352942|0.8289473684210527|
|2.0 |282.0   |266.0|47.0|16.0|0.9432624113475178|0.8498402555910544|
|3.0 |255.0   |236.0|57.0|19.0|0.9254901960784314|0.8054607508532423|
|4.0 |206.0   |183.0|29.0|23.0|0.8883495145631068|0.8632075471698113|
|5.0 |282.0   |270.0|42.0|12.0|0.9574468085106383|0.8653846153846154|
|6.0 |308.0   |302.0|56.0|6.0 |0.9805194805194806|0.8435754189944135|
|7.0 |246.0   |227.0|50.0|19.0|0.9227642276422764|0.8194945848375451|
|8.0 |112.0   |94.0 |11.0|18.0|0.8392857142857143|0.8952380952380953|
|9.0 |93.0    |59.0 |7.0 |34.0|0.6344086021505376|0.8939393939393939|
|10.0|177.0   |153.0|26.0|24.0|0.864406779661017 |0.8547486033519553|
+----+--------+-----+----+----+------------------+------------------+
only showing top 10 rows

total tp: 9506.0
total fp: 1146.0
total fn: 11382.0
total recall: 0.4550938337801609
total precision: 0.8924145700337964
cluster : 1:Train and Evaluate End
total time: 460.6690918

```
## execute the run default script for Keras (with Intel Analytic ZOO + BigDL) 
```sh
root@driver:/home/AaaSDemo# ./run_dl_default.sh
```
## the major milestones and result of performance metrics from Keras
```sh
Set mkl threads to 1 on thread 1
Engine$:103 - Auto detect executor number and executor cores number
positive samples count: 67334
ulimit count: 5178
mlimit count: 435
randomNegativeSamples
combinedDF count: 194177
+---+-----+-----+-----------+-----------------+
|uid|  mid|label|totalVisits|      totalAmount|
+---+-----+-----+-----------+-----------------+
|3.0|147.0|  1.0|          5|           2902.0|
|5.0| 54.0|  1.0|         78|62692.45999999999|
|5.0| 94.0|  1.0|          8|7548.799999999999|
|7.0|273.0|  2.0|          0|              0.0|
|9.0|263.0|  2.0|          0|              0.0|
+---+-----+-----+-----------+-----------------+
only showing top 5 rows

Start Deep Learning trainning , training records count: 194177 batchSize is 2000 maxEpoch is 10 learningRate is 0.001 learningRateDecay is 1.0E-7
Model Summary:
------------------------------------------------------------------------------------------------------------------------
Layer (type)                            Output Shape              Param #       Connected to
========================================================================================================================
Input275128c7 (Input)                   (None, 2)                 0
________________________________________________________________________________________________________________________
Selecte5d8f6cd (Select)                 (None)                    0             Input275128c7
________________________________________________________________________________________________________________________
Selectdd0eedb1 (Select)                 (None)                    0             Input275128c7
________________________________________________________________________________________________________________________
Flattenbbd63660 (Flatten)               (None, 1)                 0             Selecte5d8f6cd
________________________________________________________________________________________________________________________
Flattene6de7263 (Flatten)               (None, 1)                 0             Selectdd0eedb1
________________________________________________________________________________________________________________________
Embeddingca6bf738 (Embedding)           (None, 1, 200)            1042800       Flattenbbd63660
________________________________________________________________________________________________________________________
Embedding679f4e36 (Embedding)           (None, 1, 100)            43600         Flattene6de7263
________________________________________________________________________________________________________________________
Flattenad445ff5 (Flatten)               (None, 100)               0             Embedding679f4e36
________________________________________________________________________________________________________________________
Flattendb8cb4c4 (Flatten)               (None, 200)               0             Embeddingca6bf738
________________________________________________________________________________________________________________________
Merge5428d273 (Merge)                   (None, 300)               0             Flattendb8cb4c4
                                                                                Flattenad445ff5
________________________________________________________________________________________________________________________
Dense167489b8 (Dense)                   (None, 256)               77056         Merge5428d273
________________________________________________________________________________________________________________________
Dense8dbd8fed (Dense)                   (None, 128)               32896         Dense167489b8
________________________________________________________________________________________________________________________
Densefef17fbc (Dense)                   (None, 2)                 258           Dense8dbd8fed
________________________________________________________________________________________________________________________
Total params: 1,196,610
Trainable params: 1,196,610
Non-trainable params: 0
------------------------------------------------------------------------------------------------------------------------
INFO  DistriOptimizer$:895 - caching training rdd ...
INFO  DistriOptimizer$:672 - Cache thread models...
INFO  DistriOptimizer$:654 - model thread pool size is 1
INFO  DistriOptimizer$:674 - Cache thread models... done
INFO  DistriOptimizer$:144 - Count dataset
INFO  DistriOptimizer$:148 - Count dataset complete. Time elapsed: 0.3187034s
INFO  DistriOptimizer$:156 - config  {
        learningRate: 0.001
        computeThresholdbatchSize: 100
        maxDropPercentage: 0.0
        learningRateDecay: 1.0E-7
        warmupIterationNum: 200
        isLayerwiseScaled: false
        dropPercentage: 0.0
 }
INFO  DistriOptimizer$:160 - Shuffle data
INFO  DistriOptimizer$:163 - Shuffle data complete. Takes 0.0131923s
INFO  DistriOptimizer$:386 - [Epoch 1 2000/194177][Iteration 1][Wall Clock 0.6541267s] Trained 2000 records in 0.6541267 seconds. Throughput is 3057.5117 records/second. Loss is 0.69250727.
...
INFO  DistriOptimizer$:430 - [Epoch 10 196000/194177][Iteration 980][Wall Clock 111.2932391s] Epoch finished. Wall clock time is 111351.252 ms
positive samples count: 8924
ulimit count: 2824
mlimit count: 260
randomNegativeSamples
combinedDF count: 26398
+------+-----+-----+-----------+-----------+
|   uid|  mid|label|totalVisits|totalAmount|
+------+-----+-----+-----------+-----------+
|2559.0| 51.0|  2.0|          0|        0.0|
|2662.0|132.0|  2.0|          0|        0.0|
|1693.0| 65.0|  2.0|          0|        0.0|
|2588.0|  7.0|  2.0|          0|        0.0|
|1368.0|215.0|  2.0|          0|        0.0|
+------+-----+-----+-----------+-----------+
only showing top 5 rows

positiveDF count: 26398
validationDF count: 26398
delete key = a792a896-6b69-42bd-bf7e-06c8c9904c14 4
evaluation by mid: ****************************************************************************************************
+----+--------+-----+----+----+------------------+------------------+
|mid |posCount|tp   |fp  |fn  |recall            |precision         |
+----+--------+-----+----+----+------------------+------------------+
|1.0 |324.0   |314.0|61.0|10.0|0.9691358024691358|0.8373333333333334|
|2.0 |327.0   |320.0|47.0|7.0 |0.9785932721712538|0.8719346049046321|
|3.0 |332.0   |326.0|51.0|6.0 |0.9819277108433735|0.8647214854111406|
|4.0 |244.0   |237.0|43.0|7.0 |0.9713114754098361|0.8464285714285714|
|5.0 |390.0   |385.0|49.0|5.0 |0.9871794871794872|0.8870967741935484|
|6.0 |394.0   |387.0|67.0|7.0 |0.9822335025380711|0.8524229074889867|
|7.0 |348.0   |339.0|51.0|9.0 |0.9741379310344828|0.8692307692307693|
|8.0 |59.0    |57.0 |18.0|2.0 |0.9661016949152542|0.76              |
|9.0 |45.0    |38.0 |16.0|7.0 |0.8444444444444444|0.7037037037037037|
|10.0|214.0   |205.0|56.0|9.0 |0.9579439252336449|0.7854406130268199|
+----+--------+-----+----+----+------------------+------------------+
only showing top 10 rows

total tp: 8223.0
total fp: 4808.0
total fn: 701.0
total recall: 0.9214477812640072
total precision: 0.6310336888957102
saving model to modelFilePath
saving formatted data to csv
total time: 209.8500461
```
# Insights 
I observed with Deep Learning technologies, the whole data mining process reduced lots of workloads and procedure and the deep learning achieved much better recall performance metric in a shorter time.
- High recall is more important to most of recommendation business cases. 
- ALS can get better precision but the recall was worse ,that meaning it assumes most of user-item pairs have low propensity
- NCF can still improve precision by tuning more combinations of parameter, for this demo because we want to simply and make minimize efforts and time, so we did not use automate hyper-parameters tuning such as grid-search.  

# Live Demo
We are going to start couple services such as Kafka, NiFi, Livy. For benchmark purpose , we don't need those services , but for the AI as a Service demo which cover the lifecyle of a deep learning project , then we need to start them
## Build the customized NiFi nar for AaaS demo
- The demo requires to deploy a customized NiFi nar into NiFi. There is a github project for customized NiFi Nar and need to download artifacts , build and run
```sh
root@demo:/opt/work# cd /home
root@driver:/home# git clone https://github.com/jack1981/nifi-custom-processors.git
```
- Build the project with maven command

```sh
root@demo:/home# cd nifi-custom-processors/
root@demo:/home/nifi-custom-processors# mvn clean install
```
## Deploy the customized NiFi nar to nifi lib
```sh
root@demo:/home/nifi-custom-processors# cp nifi-custom-nar/target/nifi-custom-nar-1.0.1-SNAPSHOT.nar /opt/nifi/nifi-current/lib
``` 
## start the services 
```sh
root@demo:/opt/work# /opt/nifi/nifi-current/bin/nifi.sh start
root@demo:/opt/work# /opt/distribute/livy-bin/bin/livy-server start
```
## check the services
### livy 
- You can view the notebook on http://[host-ip]:8998/ui

### nifi
- You can view the notebook on http://[host-ip]:8080/nifi/

## Deploy Kafka at another docker container 
It is better to deploy kafka at another container and make it open to the docker machine and other containers
### Build the right docker image for kafka you need to back to the windows terminal 
```sh
$ cd C:\AaaSDemo\
$ docker pull spotify/kafka
```
- Why spotify/kafka ? 
```sh
1) The main hurdle of running Kafka in Docker is that it depends on Zookeeper. Compared to other Kafka docker images, this one runs both Zookeeper and Kafka in the same container. This means:
2) No dependency on an external Zookeeper host, or linking to another container
Zookeeper and Kafka are configured to work together out of the box
```
### Start the kafka service and Verify it ,let we say the hostIP is 10.0.75.1
```sh
$ docker run -d --name kafka -p 2181:2181 -p 9092:9092 --env ADVERTISED_HOST=10.0.75.1 --env ADVERTISED_PORT=9092 spotify/kafka
$ docker exec -it kafka /bin/bash
$ root@70a349c84edd:/# cd /opt/kafka_2.11-0.10.1.0/bin
$ root@70a349c84edd:/# export KAFKA=10.0.75.1:9092
$ root@70a349c84edd:/# export ZOOKEEPER=10.0.75.1:2181
$ root@70a349c84edd:/opt/kafka_2.11-0.10.1.0/bin# ./kafka-console-producer.sh --broker-list $KAFKA --topic test
$ root@70a349c84edd:/opt/kafka_2.11-0.10.1.0/bin# ./kafka-console-consumer.sh --zookeeper $ZOOKEEPER --topic test --from-beginning
```
## Start a Spark Streaming job

```sh
root@driver:/home/AaaSDemo# ./run_dl_streaming.sh
```

## Right now your are ready to perform the AaaS demo