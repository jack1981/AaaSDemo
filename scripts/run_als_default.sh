#!/bin/bash
spark-submit \
--deploy-mode client \
--class com.ssqcyy.aaas.demo.pipeline.MllibPipeline \
--jars zoo.jar \
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
aaas-demo.jar
