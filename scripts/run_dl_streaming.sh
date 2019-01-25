#!/bin/bash
rm -r /opt/work/check/
spark-submit \
--deploy-mode client \
--class com.ssqcyy.aaas.demo.pipeline.StreamingPipeline \
--jars zoo.jar,kafka_2.11-0.10.2.1.jar,kafka-clients-0.10.0.1.jar,spark-sql-kafka-0-10_2.11-2.3.2.jar \
--executor-memory 2G \
--driver-memory 1g \
--conf spark.dynamicAllocation.enabled=false \
--conf spark.ui.showConsoleProgress=false \
--conf spark.yarn.max.executor.failures=4 \
--conf spark.executor.memoryOverhead=512 \
--conf spark.driver.memoryOverhead=512 \
--conf spark.sql.tungsten.enabled=true \
--conf spark.locality.wait=1s \
--conf spark.yarn.maxAppAttempts=4 \
--conf spark.serializer=org.apache.spark.serializer.JavaSerializer \
aaas-demo.jar \
--kafkaStreamParams "localhost:9092,StreamingRaw,localhost:9092,StreamingResult,/opt/work/check/,5"