#!/bin/bash
rm /opt/work/demo.model
rm /opt/work/demo.weight
rm -r /opt/work/df/
spark-submit \
--deploy-mode client \
--class com.ssqcyy.aaas.demo.pipeline.DeepLearningPipeline \
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
aaas-demo.jar \
--trainingStart 20130530 \
--trainingEnd 20140615 \
--validationEnd 20140630 \
--batchSize 2000 \
--maxEpoch 10 \
--learningRate 1e-3 \
--learningRateDecay 1e-7 \
--defaultPartition 10 \
--dataFilePath "/opt/work/data/pcard.csv" \
--negRate 2 \
--randomSampling true \
--debug true