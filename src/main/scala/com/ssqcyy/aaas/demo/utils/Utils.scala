package com.ssqcyy.aaas.demo.utils

import org.apache.spark.SparkContext
import scopt.OptionParser
/**
 * @author suqiang song(Jack)
 *
 */
object Utils {
  val seperator = ","
  case class AppParams(
    batchSize:         Int               = 2000,
    maxEpoch:          Int               = 10,
    uOutput:           Int               = 200,
    trainingStart:     Int               = 20130530,
    trainingEnd:       Int               = 20140615,
    validationEnd:     Int               = 20140630,
    defaultPartition:  Int               = 10,
    mOutput:           Int               = 100,
    learningRate:      Double            = 1e-3,
    learningRateDecay: Double            = 1e-7,
    rank:              Int               = 10,
    brank:             Int               = 50,
    regParam:          Double            = 0.01,
    bregParam:         Double            = 0.20,
    alpha:             Double            = 0.01,
    balpha:            Double            = 0.15,
    debug:             Boolean           = true,
    saveModel:         Boolean           = true,
    randomSampling:    Boolean           = true,
    negRate:           Double            = 0.2,
    dataFilePath:      String            = "/opt/work/data/pcard.csv",
    modelFilePath:     String            = "/opt/work/",
    offerFilePath:     String            = "/opt/work/data/offerList.csv",
    dfPath:        String            = "/opt/work/df/",
    kafkaStreamParams: KafkaStreamParams = KafkaStreamParams("localhost:9092", "StreamingRaw", "localhost:9092", "StreamingResult", "/opt/work/", 5),
    clusterParams:     ClusterParams     = ClusterParams(2, 30))

  val trainParser = new OptionParser[AppParams]("Spark ML DL Benchmark Project") {
    head("AppParams:")
    opt[Int]("batchSize")
      .text("batch size for deep learning")
      .action((x, c) => c.copy(batchSize = x))
    opt[Int]("maxEpoch")
      .text("max Epoch")
      .action((x, c) => c.copy(maxEpoch = x))
    opt[Int]("uOutput")
      .text("User matrix output")
      .action((x, c) => c.copy(uOutput = x))
    opt[Int]("trainingStart")
      .text("trainingStart time yyyymmdd")
      .action((x, c) => c.copy(trainingStart = x))
    opt[Int]("trainingEnd")
      .text("trainingEnd time yyyymmdd")
      .action((x, c) => c.copy(trainingEnd = x))
    opt[Int]("validationEnd")
      .text("validationEnd time yyyymmdd")
      .action((x, c) => c.copy(validationEnd = x))
    opt[Int]("defaultPartition")
      .text("default spark shuff Partition number")
      .action((x, c) => c.copy(defaultPartition = x))
    opt[Int]("mOutput")
      .text("Merchant matrix output")
      .action((x, c) => c.copy(mOutput = x))
    opt[Double]("learningRate")
      .text("Learning Rate")
      .action((x, c) => c.copy(learningRate = x))
    opt[Double]("learningRateDecay")
      .text("Learning Rate Decay")
      .action((x, c) => c.copy(learningRateDecay = x))
    opt[Int]("rank")
      .text("ALS rank")
      .action((x, c) => c.copy(rank = x))
    opt[Int]("brank")
      .text("ALS benchmark Rank")
      .action((x, c) => c.copy(brank = x))
    opt[Double]("regParam")
      .text("ALS regParam")
      .action((x, c) => c.copy(regParam = x))
    opt[Double]("bregParam")
      .text("ALS benchmark regParam")
      .action((x, c) => c.copy(bregParam = x))
    opt[Double]("alpha")
      .text("ALS alpha")
      .action((x, c) => c.copy(alpha = x))
    opt[Double]("balpha")
      .text("ALS benchmark alpha")
      .action((x, c) => c.copy(balpha = x))
    opt[Boolean]("debug")
      .text("turn on debug mode or not")
      .action((x, c) => c.copy(debug = x))
    opt[Boolean]("randomSampling")
      .text("force to use random Sampling")
      .action((x, c) => c.copy(randomSampling = x))
    opt[Double]("negRate")
      .text("negRate")
      .action((x, c) => c.copy(negRate = x))
    opt[String]("dataFilePath")
      .text("dataFilePath")
      .action((x, c) => c.copy(dataFilePath = x))
    opt[String]("modelFilePath")
      .text("modelFilePath")
      .action((x, c) => c.copy(modelFilePath = x))
    opt[String]("offerFilePath")
      .text("offerFilePath")
      .action((x, c) => c.copy(offerFilePath = x))
    opt[String]("dfPath")
      .text("dfPath")
      .action((x, c) => c.copy(dfPath = x))
    opt[String]("kafkaStreamParams")
      .text("kafkaStreamParams")
      .action((x, c) => {
        val pArr = x.split(seperator).map(_.trim)
        val p = KafkaStreamParams(pArr(0).trim, pArr(1).trim, pArr(2).trim, pArr(3).trim, pArr(4).trim, pArr(5).toLong)
        c.copy(kafkaStreamParams = p)
      })
    opt[String]("clusterParams")
      .text("ClusterParams")
      .action((x, c) => {
        val pArr = x.split(seperator).map(_.trim)
        val p = ClusterParams(pArr(0).toInt, pArr(1).toInt)
        c.copy(clusterParams = p)
      })
  }
  case class ClusterParams(
    numClusters:   Int,
    numIterations: Int)

  case class KafkaStreamParams(
    inputBootstrapServer:  String = "",
    inputTopic:            String = "",
    outputBootstrapServer: String = "",
    outputTopic:           String = "",
    checkpointLocation:    String = "",
    interval:              Long   = 5)
}
