package com.ssqcyy.aaas.demo.pipeline

import org.apache.log4j.{ Level, Logger }
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.feature.{ StringIndexer, StringIndexerModel }
import org.apache.spark.mllib.feature.StandardScaler
import org.apache.spark.mllib.linalg._
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{ col, _ }
import org.apache.spark.sql.types.{ DoubleType, FloatType }
import org.apache.spark.sql.{ DataFrame, Column, Row, SQLContext, SaveMode }
import org.apache.hadoop.fs.Path
import java.text.SimpleDateFormat
import scala.collection.mutable.ArrayBuilder
import com.ssqcyy.aaas.demo.utils.Utils
import com.ssqcyy.aaas.demo.utils.Utils.AppParams
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{ ArrayType, DoubleType, FloatType, IntegerType }
import org.apache.spark.sql.{ DataFrame, SparkSession }
import org.apache.spark.SparkContext
import org.apache.spark.sql.streaming.Trigger
import com.intel.analytics.zoo.pipeline.api.Net
import com.intel.analytics.bigdl.tensor.{ Storage, Tensor }
import com.intel.analytics.bigdl.tensor.TensorNumericMath.TensorNumeric
import org.apache.spark.SparkConf
import com.intel.analytics.zoo.common.NNContext
import org.apache.spark.sql.streaming.DataStreamWriter
import org.apache.spark.sql.streaming.StreamingQuery
import org.apache.nifi._
import org.apache.nifi.spark.NiFiReceiver
import org.apache.nifi.remote._
import org.apache.nifi.remote.client.SiteToSiteClient
import org.apache.spark.{ SparkConf, SparkContext }
import org.apache.spark.streaming._
import org.apache.spark.storage.StorageLevel
import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory

object NiFiStreamingPipeline {
  val logger = LoggerFactory.getLogger(getClass)
  def main(args: Array[String]): Unit = {

    Logger.getLogger("com.ssqcyy.spark.ml").setLevel(Level.INFO)
    Logger.getLogger("com.intel.analytics.bigdl").setLevel(Level.INFO)

    if (args.size >= 1) {
      Utils.trainParser.parse(args, Utils.AppParams()).foreach { params =>
        val conf = NNContext.createSparkConf()
        conf.setAppName("StreaingServingPipeline").set("spark.sql.crossJoin.enabled", "true").set("spark.sql.shuffle.partitions", params.defaultPartition.toString())
          .set("spark.sql.crossJoin.enabled", "true")
          .set("spark.driver.allowMultipleContexts", "true")
          .set("spark.streaming.concurrentJobs", "3")

        val sc = NNContext.initNNContext(conf)
        val sparkSession = SparkSession.builder().config(conf).getOrCreate()
        // execute Streaming Pipeline
        executePipeline(sparkSession, params)

      }
    } else {
      logger.error(s"require kafkaStreamParams!")
    }
  }

  def executePipeline(spark: SparkSession, params: AppParams): Unit = {

    scala.util.control.Exception.ignoring(classOf[Exception]) {
      import spark.implicits._
      // get model
      val modelPath = params.modelFilePath
      logger.info(s"load model from $modelPath")
      val dlModel = Net.load[Float](modelPath + "demo.model", modelPath + "demo.weight")
      logger.info(s"loaded model as $dlModel")
      // broadcast models
      val modelBroadCast = spark.sparkContext.broadcast(dlModel)
      val ssc = new StreamingContext(spark.sparkContext, Duration(params.niFiStreamParams.seconds))
      val conf = new SiteToSiteClient.Builder().url(params.niFiStreamParams.url).portName(params.niFiStreamParams.portName).buildConfig()
      val stream = ssc.receiverStream(new NiFiReceiver(conf, StorageLevel.MEMORY_ONLY))
      val transaction = stream.map(dataPacket => ("", new String(dataPacket.getContent, StandardCharsets.UTF_8)))

      transaction.foreachRDD(rdd => {
        import spark.implicits._
        val df: DataFrame = rdd.toDF("timestamp", "value")
        df.createOrReplaceTempView("NiFiStreams")
        val NiFiStreamsDF: DataFrame = spark.sql("select * from NiFiStreams")
        //get StreamingFeatureDF
        val featureDF = getStreamingFeatureDF(NiFiStreamsDF, spark, params)
        // predict
        def predict(features: Seq[Float])(implicit ev: TensorNumeric[Float]): Seq[Float] = {
          // create tensor from input column
          if (features.length == 0)
            throw new IllegalArgumentException
          val input = Tensor(features.asInstanceOf[Seq[Float]].toArray, Array(1, features.length))
          // predict
          val output = modelBroadCast.value.forward(input).toTensor[Float]
          val predict = if (output.dim == 2) {
            output(1).storage().array()
          } else if (output.dim == 1) {
            output(0).storage().array()
          } else {
            throw new IllegalArgumentException
          }
          predict
        }
        val classiferUDF = udf(predict(_: Seq[Float]))
        val predictions = featureDF.withColumn("prediction", classiferUDF($"features"))
        val raw2Prob = udf { d: Seq[Float] => 1.0 / (1.0 + math.exp(d(1) - d(0))) }
        val raw2Classification = udf { d: Seq[Float] =>
          require(d.length == 2, "actual length:" + d.length)
          if (d(0) > d(1)) 1.0
          else 0.0
        }
        val evaluateDF = predictions
          .withColumnRenamed("prediction", "raw")
          .withColumn("prob", (raw2Prob(col("raw")) * 1000).cast(IntegerType))
          .withColumn("prediction", raw2Classification(col("raw"))).filter($"prob" >= $"threshold")

        val resultDF = evaluateDF
          .withColumn("key", col("u").cast("String"))
          .withColumn("value", concat(col("u"), lit(","), col("m"), lit(","), col("offer"), lit(","), col("raward"), lit(","), col("threshold"), lit(","), col("prob").cast("String")))
        resultDF.write.format("com.databricks.spark.csv").mode("overwrite").option("header", "true").save(params.dfPath + "resultDFCsv")
      })

      ssc.start()
      ssc.awaitTermination()
    }

  }

  def getStreamingFeatureDF(df_stream: DataFrame, spark: SparkSession, params: AppParams): DataFrame = {
    import spark.implicits._
    val offerDF = DataPipeline.loadOfferCSV(spark.sqlContext, params)
    // split value to u and m
    val df = df_stream.select($"timestamp", $"value".cast("string").as("data"))
      .withColumn("_tmp", split($"data", ","))
      .select(
        $"timestamp",
        $"_tmp".getItem(0).as("u"),
        $"_tmp".getItem(1).as("m")).drop("_tmp")

    // join u and m features
    val uDF = DataPipeline.getUDF(spark.sqlContext, params)
    val mDF = DataPipeline.getMDF(spark.sqlContext, params)
    // join the offer
    val offerColumnNames = Seq("m", "mid", "offer", "raward", "threshold")
    val mODF = mDF.join(offerDF, Array("m"), "inner").select(offerColumnNames.map(c => col(c)): _*)

    val allColumnNames = Seq("u", "uid") ++ offerColumnNames
    var allDF = df
      .join(uDF, Array("u"), "inner")
      .join(mODF, Array("m"), "inner")
      .select(allColumnNames.map(c => col(c)): _*)
    // generate featureDF
    val featureDF = DataPipeline.streamingNorm(allDF, params)
    featureDF
  }

}