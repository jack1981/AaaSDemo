package com.ssqcyy.aaas.demo.pipeline

import org.apache.log4j.{ Level, Logger }
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.feature.{ StringIndexer, StringIndexerModel }
import org.apache.spark.mllib.feature.StandardScaler
import org.apache.spark.mllib.linalg._
import org.apache.spark.sql.functions.{ col, _ }
import org.apache.spark.sql.types.{ DoubleType, FloatType, IntegerType }
import org.apache.spark.sql.{ DataFrame, Column, Row, SQLContext, SaveMode }
import java.text.SimpleDateFormat
import scala.collection.mutable.ArrayBuilder
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{ ArrayType, DoubleType, FloatType }
import org.apache.spark.sql.{ DataFrame, SparkSession }
import org.apache.hadoop.fs.Path
import com.ssqcyy.aaas.demo.utils.Utils.AppParams
import scala.util.Random
/**
 * @author suqiang song(Jack)
 *
 */
object DataPipeline {

  private val seed = 42L
  private var uidSIModel: StringIndexerModel = _
  private var midSIModel: StringIndexerModel = _

  /**
   * @return DataFrame ("uid", "mid", "date")
   */
  def loadPublicCSV(spark: SQLContext, params: AppParams): DataFrame = {

    //    pre-process the datasets including :
    //    1) Remove duplicates
    //    2) Fill NULL value with 0
    //    3) Transfer the date format
    //    4) Mask PCI/PII information and build indexes and lookup tables for user-item

    val raw = spark.read
      .format("csv")
      .option("header", "true") //reading the headers
      .option("mode", "DROPMALFORMED")
      .csv(params.dataFilePath)
      .dropDuplicates()
      .na.fill(0)

    val dataDF = raw.select("Cardholder Last Name", "Cardholder First Initial", "Amount", "Vendor",
      "Transaction Date", "Merchant Category Code (MCC)")
      .select(
        concat(col("Cardholder Last Name"), lit(" "), col("Cardholder First Initial")).as("u"),
        col("amount").cast(DoubleType),
        col("Merchant Category Code (MCC)").as("m"),
        col("Transaction Date"))

    val toDate = udf { s: String =>
      val splits = s.substring(0, 10).split("/")
      val year = splits(2).toInt
      val month = splits(0).toInt
      val day = splits(1).toInt
      year * 10000 + month * 100 + day
    }
    val dates = dataDF.withColumn("date", toDate(col("Transaction Date"))).drop("Transaction Date")
    val simpleDF = dates.select("u", "amount", "m", "date")

    val si1 = new StringIndexer().setInputCol("u").setOutputCol("uid").setHandleInvalid("skip")
    val si2 = new StringIndexer().setInputCol("m").setOutputCol("mid").setHandleInvalid("skip")

    val pipeline = new Pipeline().setStages(Array(si1, si2))
    val pipelineModel = pipeline.fit(simpleDF)

    val transDF = pipelineModel.transform(simpleDF)
      .select("u", "uid", "m", "mid", "amount", "date")
      .withColumn("uid", col("uid") + 1)
      .withColumn("mid", col("mid") + 1)
      .select("u", "uid", "m", "mid", "amount", "date")
    // save userDF
    createUDF(spark, params, transDF)
    // save itemDF
    createMDF(spark, params, transDF)
    transDF
  }
  /**
   * @return DataFrame ("uid", "mid", "date")
   */
  def loadMCCSV(spark: SQLContext, params: AppParams): DataFrame = {

    val raw = spark.read
      .format("csv")
      .option("header", "true") //reading the headers
      .option("mode", "DROPMALFORMED")
      .csv(params.dataFilePath)
      .dropDuplicates()
      .na.fill(0)

    val dataDF = raw.select("external_userid", "txn_timestamp", "amount", "merchant")
      .select(
        col("external_userid").as("u"),
        col("amount").cast(DoubleType),
        col("merchant").as("m"),
        col("txn_timestamp"))

    val df: SimpleDateFormat = new SimpleDateFormat("MM/dd/yyyy")
    // use timestamp type
    val toDate = udf { ts: String =>
      val lts = if (ts.size == 10) ts.toLong * 1000L else if (ts.size == 13) ts.toLong
      val date: String = df.format(lts)
      val splits = date.split("/")
      val year = splits(2).toInt
      val month = splits(0).toInt
      val day = splits(1).toInt
      year * 10000 + month * 100 + day
    }
    val dates = dataDF.withColumn("date", toDate(col("txn_timestamp"))).drop("txn_timestamp")
    val simpleDF = dates.select("u", "amount", "m", "date")

    val si1 = new StringIndexer().setInputCol("u").setOutputCol("uid").setHandleInvalid("skip")
    val si2 = new StringIndexer().setInputCol("m").setOutputCol("mid").setHandleInvalid("skip")

    val pipeline = new Pipeline().setStages(Array(si1, si2))
    val pipelineModel = pipeline.fit(simpleDF)

    val transDF = pipelineModel.transform(simpleDF)
      .select("u", "uid", "m", "mid", "amount", "date")
      .withColumn("uid", col("uid") + 1)
      .withColumn("mid", col("mid") + 1)
      .select("u", "uid", "m", "mid", "amount", "date")
    // save userDF
    createUDF(spark, params, transDF)
    // save itemDF
    createMDF(spark, params, transDF)
    transDF
  }

  def getStreamingRawDF(spark: SparkSession, params: AppParams): DataFrame = {

    val df_stream =
      spark.readStream
        .format("kafka")
        .option("kafka.bootstrap.servers", params.kafkaStreamParams.inputBootstrapServer)
        .option("startingOffsets", "latest")
        .option("subscribe", params.kafkaStreamParams.inputTopic)
        .option("failOnDataLoss", "false")
        .load()
    df_stream
  }


  def mixNegativeAndCombineFeatures(
    tDF: DataFrame, rawDF: DataFrame,
    param: AppParams, ifBigDL: Boolean): DataFrame = {

    val positiveDF = tDF
    val beforeMixCount = positiveDF.count()
    val ulimit = positiveDF.groupBy("uid").count().count().toInt
    val mlimit = positiveDF.groupBy("mid").count().count().toInt
    println(s"positive samples count: $beforeMixCount")
    println(s"ulimit count: $ulimit")
    println(s"mlimit count: $mlimit")
    val getNegFunc: (DataFrame, AppParams, Int, Int, Boolean) => DataFrame =
      // add param control to pick up sampling method
      if (param.randomSampling) {
        println("randomNegativeSamples")
        randomNegativeSamples
      } else {
        println("invertNegativeSamples")
        invertNegativeSamples
      }

    var combinedDF = getNegFunc(tDF, param, ulimit, mlimit, ifBigDL)
    combinedDF = combinedDF.join(createUmTotalFrequencyDF(param, rawDF), Array("uid", "mid"), "left_outer").withColumnRenamed("count", "totalVisits").na.fill(0)
    combinedDF = combinedDF.join(createUmSumDF(param, rawDF), Array("uid", "mid"), "left_outer").na.fill(0)
    combinedDF = combinedDF.distinct()
    if (param.debug) {
      println(s"combinedDF count: ${combinedDF.count()}")
      combinedDF.show(5);
    }

    combinedDF
  }

  /**
   * @param trainingData (uid, mid, count)
   */
  def invertNegativeSamples(
    trainingData: DataFrame,
    param:        AppParams,
    ulimit:       Int,
    mlimit:       Int, ifBigDL: Boolean): DataFrame = {
    import trainingData.sparkSession.implicits._
    val sc = trainingData.sparkSession.sparkContext
    val all = sc.range(1, ulimit).map(_.toDouble).cartesian(sc.range(1, mlimit).map(_.toDouble))

    val existing = trainingData.select("uid", "mid").rdd.map { r =>
      (r.getDouble(0), r.getDouble(1))
    }

    val negativeRDD = all.subtract(existing)
    var negativeDF = negativeRDD.map {
      case (uid, mid) =>
        (uid, mid)
    }.toDF("uid", "mid")

    negativeDF = if (ifBigDL) negativeDF.withColumn("label", lit(2.0f)) else negativeDF.withColumn("label", lit(0.0f))

    val combinedDF = trainingData.union(negativeDF)
    require(combinedDF.count() == all.count(), s"combined: ${combinedDF.count()}. all ${all.count()}")
    combinedDF
  }

  def randomNegativeSamples(
    tDF:    DataFrame,
    param:  AppParams,
    ulimit: Int,
    mlimit: Int, ifBigDL: Boolean): DataFrame = {
    import tDF.sparkSession.implicits._

    val beforeMix = tDF.count()
    val numRecords = (param.negRate * beforeMix).toInt
    val sc = tDF.sparkSession.sparkContext

    val negativeIDDF = sc.parallelize(1 to numRecords).mapPartitionsWithIndex {
      case (pid, iter) =>
        val ran = new Random(System.nanoTime())
        iter.map { i =>
          val uid = Math.abs(ran.nextInt(ulimit)).toFloat + 1
          val mid = Math.abs(ran.nextInt(mlimit)).toFloat + 1
          (uid, mid)
        }
    }.distinct().toDF("uid", "mid")

    val removeDupDF = if (ifBigDL) negativeIDDF.join(tDF, Seq("uid", "mid"), "leftanti").withColumn("label", lit(2.0f)) else negativeIDDF.join(tDF, Seq("uid", "mid"), "leftanti").withColumn("label", lit(0.0f))
    val combinedDF = removeDupDF.union(tDF)
    combinedDF
  }

  def norm(df: DataFrame): DataFrame = {
    val sqlContext = df.sqlContext

    import sqlContext.implicits._
    val assembleDF = df
    val scaleUDFWithoutFeatures = udf { (uid: Int, mid: Int) =>
      Array[Float](uid, mid)
    }

    val resultDF = assembleDF.withColumn("features", scaleUDFWithoutFeatures($"uid", $"mid"))
    resultDF.select("uid", "mid", "label", "features").cache()
  }

  def streamingNorm(df: DataFrame, params: AppParams): DataFrame = {
    val sqlContext = df.sqlContext
    import sqlContext.implicits._
    val scaleUDFWithoutFeatures = udf { (uid: Int, mid: Int) =>
      Array[Float](uid, mid)
    }
    val resultDF = df.withColumn("features", scaleUDFWithoutFeatures($"uid", $"mid"))
    resultDF.select("u", "m", "uid", "mid", "offer", "raward", "threshold", "features")
  }

  def normFeatures(df: DataFrame, params: AppParams): DataFrame = {
    val sqlContext = df.sqlContext

    val cols = df.columns

    val filteredDF = df.select(cols.map(col): _*)
    if (params.debug) {
      println("original features: ")
      filteredDF.show(5)
    }
    import sqlContext.implicits._
    val featureCols = filteredDF.columns.diff(Seq("uid", "mid", "label")).map(col).map(c => c.cast(DoubleType))
    println("featureCols are " + featureCols.mkString(","));
    val assembleFunc = udf { r: Row =>
      val values = ArrayBuilder.make[Double]
      r.toSeq.foreach(v => values += v.asInstanceOf[Double])
      Vectors.dense(values.result())
    }
    val assembleDF = filteredDF.withColumn("assembled", assembleFunc(struct(featureCols: _*)))
    val mllibVectorRDD = assembleDF.select("assembled").rdd.map(r => r.getAs[Vector](0))
    val scalerModel = new StandardScaler(true, true).fit(mllibVectorRDD)
    val scaleUDF = udf { (uid: Int, mid: Int, vec: Vector) =>
      Array[Float](uid, mid) ++ scalerModel.transform(vec).toArray.map(_.toFloat)
    }

    val resultDF = assembleDF.withColumn("features", scaleUDF($"uid", $"mid", $"assembled"))
    resultDF.select("uid", "mid", "label", "features").cache()
  }

  def createUmTotalFrequencyDF(params: AppParams, sourceDF: DataFrame): DataFrame = {
    val umTotalFrequency = sourceDF.groupBy("uid", "mid").count()
    umTotalFrequency
  }

  def createUmSumDF(params: AppParams, sourceDF: DataFrame): DataFrame = {
    val umSum = sourceDF.groupBy("uid", "mid").agg(sum("amount").as("totalAmount"))
    umSum
  }

  def loadOfferCSV(
    spark: SQLContext, param: AppParams): DataFrame = {
    val raw = spark.read
      .format("com.databricks.spark.csv")
      .option("header", "false") //reading the headers
      .option("mode", "DROPMALFORMED")
      .csv(param.offerFilePath)
      .dropDuplicates()
      .na.fill(0)

    val cols = raw.columns
    raw.select(
      col(cols(0)).as("m"),
      col(cols(1)).as("offer"),
      col(cols(2)).as("raward"),
      col(cols(3)).cast(IntegerType).as("threshold"))
  }

  def createUDF(spark: SQLContext, params: AppParams, sourceDF: DataFrame): DataFrame = {

    val uDF = sourceDF.select("uid", "u").distinct().repartition(1)
    uDF.write.mode(SaveMode.Overwrite).parquet(new Path(params.dfPath, "uDF").toString)
    uDF.write.format("com.databricks.spark.csv").mode("overwrite").option("header", "true").save(params.dfPath + "uDFCsv")
    uDF
  }

  def getUDF(spark: SQLContext, params: AppParams): DataFrame = {
    val uDF = spark.read.parquet(new Path(params.dfPath, "uDF").toString).filter(col("uid") >= 0).coalesce(1).cache()
    uDF
  }

  def createMDF(spark: SQLContext, params: AppParams, sourceDF: DataFrame): DataFrame = {

    val mDF = sourceDF.select("mid", "m").distinct().repartition(1)
    mDF.write.mode(SaveMode.Overwrite).parquet(new Path(params.dfPath, "mDF").toString)
    mDF.write.format("com.databricks.spark.csv").mode("overwrite").option("header", "true").save(params.dfPath + "mDFCsv")
    mDF
  }

  def getMDF(spark: SQLContext, params: AppParams): DataFrame = {
    val mDF = spark.read.parquet(new Path(params.dfPath, "mDF").toString).filter(col("mid") >= 0).coalesce(1).cache()
    mDF
  }

}
