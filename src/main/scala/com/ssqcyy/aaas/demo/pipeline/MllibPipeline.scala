package com.ssqcyy.aaas.demo.pipeline

import org.apache.spark.ml.recommendation.{ ALS, ALSModel }
import org.apache.spark.mllib.clustering.{ KMeans, KMeansModel }
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.sql.{ DataFrame, SparkSession }
import org.apache.spark.sql.functions._
import com.ssqcyy.aaas.demo.evaluate.Evaluation
import org.apache.spark.ml.{ Pipeline, PipelineModel }
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.tuning.{ CrossValidator, ParamGridBuilder }
import org.apache.log4j.{ Level, Logger }
import com.ssqcyy.aaas.demo.utils.Utils
import com.ssqcyy.aaas.demo.utils.Utils.AppParams
import scala.reflect.api.materializeTypeTag
/**
 * @author suqiang.song
 *
 */
object MllibPipeline {

  def main(args: Array[String]): Unit = {

    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("org.apache.spark.ml").setLevel(Level.INFO)
    if (args.size >= 1) {
      Utils.trainParser.parse(args, Utils.AppParams()).foreach { params =>
        val spark = SparkSession.builder().appName("MllibPipelineWithAls")
          .config("spark.sql.shuffle.partitions", params.defaultPartition).config("spark.driver.allowMultipleContexts", "true").getOrCreate()
        executePipeline(spark, params)
        spark.stop()
      }
    } else {
      runDefault()
    }

  }

  def runDefault(): Unit = {

    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("org.apache.spark.ml").setLevel(Level.INFO)
    val params = Utils.AppParams()
    val spark = SparkSession.builder().appName("MllibPipelineWithAls")
      .config("spark.sql.shuffle.partitions", params.defaultPartition).config("spark.driver.allowMultipleContexts", "true").getOrCreate()
    executePipeline(spark, params)
    spark.stop()

  }

  def executePipeline(spark: SparkSession, params: AppParams): Unit = {
    val st = System.nanoTime()
    println("AppParams are " + params)
    val trainingStart = params.trainingStart
    val trainingEnd = params.trainingEnd
    val validationStart = trainingEnd
    val validationEnd = params.validationEnd
    val maxEpoch = params.maxEpoch
    val rank = params.rank
    val brank = params.brank
    val regParam = params.regParam
    val bregParam = params.bregParam
    val alpha = params.alpha
    val balpha = params.balpha
    val numClusters = params.clusterParams.numClusters
    val numIterations = params.clusterParams.numIterations
    import spark.implicits._

    // #load the data source and fix the data quality issues
    val dataDF = DataPipeline.loadPublicCSV(spark.sqlContext, params)
    val filterTrainingRawDF = dataDF
      .filter(s"date>=$trainingStart")
      .filter(s"date<=$trainingEnd")
      .drop("date").cache()
    // #Feature Engineering part1: Add some negative sampling for training
    val trainingRawDF = filterTrainingRawDF.groupBy("uid", "mid").count().withColumnRenamed("count", "label").cache()
    val trainingDF = DataPipeline.mixNegativeAndCombineFeatures(trainingRawDF, filterTrainingRawDF, params, false)
    val trainingCount = trainingDF.count()
    // #Feature Engineering part2: generate extra features except user and items
    val clusterTrainDF = DataPipeline.normFeatures(trainingDF, params)
    // #Feature Engineering part3: Run clustering for user segmentations
    println("Start Kmeans trainning , training records count: " + trainingCount + " numClusters is " + numClusters + " numIterations is " + numIterations)

    val idTrainFeaturesRDD = clusterTrainDF.rdd.map(s => (s.getDouble(0), Vectors.dense(s.getSeq[Float](3).toArray.map { x => x.asInstanceOf[Double] }))).cache()
    // ##Cluster the data into two classes using KMeans
    val clusters: KMeansModel = KMeans.train(idTrainFeaturesRDD.map(_._2), numClusters, numIterations, KMeans.RANDOM)
    println("Cluster Number:" + clusters.clusterCenters.length)
    println("Cluster Centers Information Overview:")
    var clusterIndex: Int = 0
    clusters.clusterCenters.foreach(
      x => {
        println("Center Point of Cluster " + clusterIndex + ":")
        println(x)
        clusterIndex += 1
      })
    // # Do the same things for test data set
    val filterValidationRawDF = dataDF
      .filter(s"date>$validationStart")
      .filter(s"date<=$validationEnd")
      .drop("date").cache()

    val validationRawDF = filterValidationRawDF.groupBy("uid", "mid").count().withColumnRenamed("count", "label").cache()

    val validationDF = DataPipeline.mixNegativeAndCombineFeatures(validationRawDF, filterValidationRawDF, params, false)

    val clusterValidationDF = DataPipeline.normFeatures(validationDF, params)
    // # Clustering should use prediction results, then the result is "uid","cluster"
    val idValidationFeaturesRDD = clusterValidationDF.rdd.map(s => (s.getDouble(0), Vectors.dense(s.getSeq[Float](3).toArray.map { x => x.asInstanceOf[Double] }))).cache()
    val clustersRDD = clusters.predict(idValidationFeaturesRDD.map(_._2))
    val idClusterRDD = idValidationFeaturesRDD.map(_._1).zip(clustersRDD)
    val kMeanPredictions = idClusterRDD.toDF("uid", "cluster")
    val clusterPredictionsDF = kMeanPredictions
      .select("uid", "cluster")
      .cache()
    // # For each cluster , run ALS model processing
    var clusterNumber: Int = 0
    while (clusterNumber < numClusters) {
      println("Start ALS pipeline for cluster: " + clusterNumber);
      val clusterSingleDF = clusterPredictionsDF.filter(col("cluster") === clusterNumber).distinct();
      println("Count of cluster: " + clusterNumber + " is " + clusterSingleDF.count());
      val trainingClusterDF = trainingDF.join(clusterSingleDF, Array("uid"), "inner");
      val validationClusterDF = validationDF.join(clusterSingleDF, Array("uid"), "inner");
      println("Split data into Training and Validation for cluster : " + clusterNumber + ":");
      println("cluster : " + clusterNumber + ":" + " training records count: " + trainingClusterDF.count());
      println("cluster : " + clusterNumber + ":" + " validation records count: " + validationClusterDF.count());
      // ## Pass the pre-defined parameters to ALS model
      val als = new ALS()
        .setMaxIter(maxEpoch)
        .setRegParam(regParam)
        .setRank(rank)
        .setAlpha(alpha)
        .setUserCol("uid")
        .setItemCol("mid")
        .setRatingCol("label")

      // ## Hyper Parameter Tuning: Configure an ML pipeline, which consists of one stage
      val pipeline = new Pipeline().setStages(Array(als))
      // ## Hyper Parameter Tuning:Use a ParamGridBuilder to construct a grid of parameters to search over.
      val paramGrid = new ParamGridBuilder()
        .addGrid(als.rank, Array(brank, rank))
        .addGrid(als.regParam, Array(bregParam, regParam))
        .addGrid(als.alpha, Array(balpha, alpha))
        .build()
      // ## Hyper Parameter Tuning:Select RegressionEvaluator and rmse metric
      val evaluator = new RegressionEvaluator().
        setMetricName("rmse").
        setPredictionCol("prediction").
        setLabelCol("label")
      // ## Hyper Parameter Tuning:Run cross validation ,will benchmark hyper parameters combinations
      val cv = new CrossValidator()
        .setEstimator(pipeline)
        .setEvaluator(evaluator)
        .setEstimatorParamMaps(paramGrid)
        .setNumFolds(3)

      val choiceModel = cv.fit(trainingClusterDF)
      val predictions = choiceModel.transform(validationClusterDF)

      val rmse = evaluator.evaluate(predictions)
      println("best rmse  = " + rmse)
      // ## Hyper Parameter Tuning:Get best model
      val bestModel = choiceModel.bestModel.asInstanceOf[PipelineModel]
      val stages = bestModel.stages
      val bestAls = stages(stages.length - 1).asInstanceOf[ALSModel]
      println("best rank = " + bestAls.rank)
      // # Best model evaluation
      evaluate(bestAls, validationClusterDF, params);

      println("cluster : " + clusterNumber + ":" + "Train and Evaluate End");
      clusterNumber += 1
    }
    println("total time: " + (System.nanoTime() - st) / 1e9)
  }

  // uid, mid, count
  private[ssqcyy] def evaluate(model: ALSModel, validDF: DataFrame, params: AppParams): Unit = {

    println("positiveDF count: " + validDF.count())
    println("validationDF count: " + validDF.count())
    val prediction = model.transform(validDF)
    val label2Binary = udf { d: Float => if (d == 2.0f) 0.0 else 1.0 }
    val prediction2Binary = udf { d: Double => if (d > 1.0) 1.0 else 0.0 }

    val evaluateDF = prediction
      .withColumn("label", label2Binary(col("label")))
      .withColumn("prediction", prediction2Binary(col("prediction")))
    Evaluation.evaluateByMid(params.dfPath,"mllib","overwrite",evaluateDF)

  }

}
