package com.ssqcyy.aaas.demo.pipeline

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import com.ssqcyy.aaas.demo.evaluate.Evaluation
import org.apache.log4j.{ Level, Logger }
import com.ssqcyy.aaas.demo.utils.Utils
import com.ssqcyy.aaas.demo.utils.Utils.AppParams
import com.intel.analytics.bigdl.Module
import com.intel.analytics.bigdl.nn._
import com.intel.analytics.bigdl.tensor.Tensor
import com.intel.analytics.bigdl.tensor.TensorNumericMath.TensorNumeric.NumericFloat
import com.intel.analytics.bigdl.utils.Engine
import com.intel.analytics.bigdl.optim.Adam
import com.intel.analytics.bigdl.dlframes.DLEstimator
import com.intel.analytics.bigdl.dlframes.DLModel
import com.ssqcyy.aaas.demo.model.keras.KerasMLPModel
import org.apache.spark.SparkContext
import org.apache.spark.sql.{ DataFrame, SQLContext }
import com.intel.analytics.bigdl.convCriterion
import scala.reflect.api.materializeTypeTag
/**
 * @author suqiang.song
 *
 */
object DeepLearningPipeline {

  def main(args: Array[String]): Unit = {

    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("com.intel.analytics.bigdl").setLevel(Level.INFO)

    if (args.size >= 1) {
      Utils.trainParser.parse(args, Utils.AppParams()).foreach { params =>
        val conf = Engine.createSparkConf().setAppName("DeepLearningPipelineWithNCF")
          .set("spark.sql.shuffle.partitions", params.defaultPartition.toString())
          .set("spark.sql.crossJoin.enabled", "true")
          .set("spark.driver.allowMultipleContexts", "true")
          .set("spark.sql.autobroadcastjointhreshold", "500000000")
        val sc = new SparkContext(conf)
        val spark = new SQLContext(sc)
        // # Start BigDL engine
        Engine.init
        executePipeline(spark, params)
        sc.stop()
      }
    } else {
      runDefault()
    }

  }

  def runDefault(): Unit = {

    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("com.intel.analytics.bigdl").setLevel(Level.INFO)
    val params = Utils.AppParams()
    val conf = Engine.createSparkConf().setAppName("DeepLearningPipelineWithNCF")
      .set("spark.sql.shuffle.partitions", params.defaultPartition.toString())
      .set("spark.sql.crossJoin.enabled", "true")
      .set("spark.driver.allowMultipleContexts", "true")
      .set("spark.sql.autobroadcastjointhreshold", "500000000")
    val sc = new SparkContext(conf)
    val spark = new SQLContext(sc)
    // # Start BigDL engine
    Engine.init
    executePipeline(spark, params)
    sc.stop()
  }

  def executePipeline(spark: SQLContext, params: AppParams): Unit = {
    val st = System.nanoTime()
    println("Appparamss are " + params)
    import spark.implicits._
    val trainingStart = params.trainingStart
    val trainingEnd = params.trainingEnd
    val validationStart = trainingEnd
    val validationEnd = params.validationEnd
    val batchSize = params.batchSize
    val maxEpoch = params.maxEpoch
    val uOutput = params.uOutput
    val mOutput = params.mOutput
    val learningRate = params.learningRate
    val learningRateDecay = params.learningRateDecay
    val dataDF = DataPipeline.loadPublicCSV(spark, params)
    val ulimit = dataDF.groupBy("uid").count().count().toInt
    val mlimit = dataDF.groupBy("mid").count().count().toInt
    val filterTrainingRawDF = dataDF
      .filter(s"date>=$trainingStart")
      .filter(s"date<=$trainingEnd")
      .drop("date").cache()
    // #Feature Engineering (only this step) : Add some negative sampling for training
    val positiveTrainingDF = filterTrainingRawDF.select("uid", "mid").distinct().withColumn("label", lit(1.0f)).cache()
    val trainingDF = DataPipeline.mixNegativeAndCombineFeatures(positiveTrainingDF, filterTrainingRawDF, params, true)
    // # There is no extra features except user and item
    val featureTrainDF = DataPipeline.norm(trainingDF)
    val trainingCount = featureTrainDF.count()
    // # Start the NCF deep learning
    println("Start Deep Learning trainning , training records count: " + trainingCount + " batchSize is " + batchSize + " maxEpoch is " + maxEpoch + " learningRate is " + learningRate + " learningRateDecay is " + learningRateDecay)
    //val model = getBigDLModel(ulimit, uOutput, mlimit, mOutput, params, 2)
    // use Keras model
    val model = getKerasDLModel(ulimit + 1, uOutput, mlimit + 1, mOutput, params, 2)

    val criterion = ClassNLLCriterion()
    val dlc = new DLEstimator(model, criterion, Array(2), Array(1))
      .setBatchSize(batchSize)
      .setOptimMethod(new Adam())
      .setLearningRate(learningRate)
      .setLearningRateDecay(learningRateDecay)
      .setMaxEpoch(maxEpoch)

    val dlModel = dlc.fit(featureTrainDF)
    // # Do the same things for test data set
    val filterValidationRawDF = dataDF
      .filter(s"date>$validationStart")
      .filter(s"date<=$validationEnd")
      .drop("date").cache()
    val positiveValidationDF = filterValidationRawDF.select("uid", "mid").distinct().withColumn("label", lit(1.0f)).cache()
    val validationDF = DataPipeline.mixNegativeAndCombineFeatures(positiveValidationDF, filterValidationRawDF, params, true)
    val featureValidationDF = DataPipeline.norm(validationDF)
    // # Deep learning model evaluation
    evaluate(dlModel, featureValidationDF, params);
    println("saving model to modelFilePath")
    dlModel.model.saveModule(params.modelFilePath + "demo.model", params.modelFilePath + "demo.weight", true)
    println("saving formatted data to csv")
    dataDF.select("u", "m", "uid", "mid").distinct().filter(!$"m".contains(",")).limit(200).repartition(1).write.format("com.databricks.spark.csv").mode("overwrite").option("header", "true").save(params.dfPath + "umPairDF")
    println("total time: " + (System.nanoTime() - st) / 1e9)
  }

  // uid, mid, count
  def evaluate(model: DLModel[Float], validDF: DataFrame, params: AppParams): Unit = {

    println("positiveDF count: " + validDF.count())
    println("validationDF count: " + validDF.count())

    val label2Binary = udf { d: Float => if (d == 2.0f) 0.0 else 1.0 }
    val raw2Classification = udf { d: Seq[Double] =>
      require(d.length == 2, "actual length:" + d.length)
      if (d(0) > d(1)) 1.0
      else 0.0
    }

    val deepPredictions = model.transform(validDF)
    val evaluateDF = deepPredictions
      .withColumnRenamed("prediction", "raw")
      .withColumn("label", label2Binary(col("label")))
      .withColumn("prediction", raw2Classification(col("raw")))

    Evaluation.evaluateByMid(params.dfPath, "dl", "overwrite", evaluateDF)

  }

  def getBigDLModel(
    ulimit: Int, uOutput: Int,
    mlimit: Int, mOuput: Int,
    params:           AppParams,
    featureDimension: Int): Module[Float] = {

    val initialWeight = 0.5
    // construct LookupTable for user
    val user_table = LookupTable(ulimit, uOutput)
    // construct LookupTable for item
    val item_table = LookupTable(mlimit, mOuput)
    // set weights parameter ( random way) to all the LookupTable
    user_table.setWeightsBias(Array(Tensor[Float](ulimit, uOutput).randn(0, initialWeight)))
    item_table.setWeightsBias(Array(Tensor[Float](mlimit, mOuput).randn(0, initialWeight)))
    // how many extra dense features
    val numExtraFeature = featureDimension - 2
    // contact MF features together to be CMul hidden layer
    val embedded_layer = if (numExtraFeature > 0) {
      Concat(2)
        .add(Sequential().add(Select(2, 1)).add(user_table))
        .add(Sequential().add(Select(2, 2)).add(item_table))
        .add(Sequential().add(Narrow(2, 3, numExtraFeature)))
    } else {
      Concat(2)
        .add(Sequential().add(Select(2, 1)).add(user_table))
        .add(Sequential().add(Select(2, 2)).add(item_table))
    }
    // use Sequentical model structure
    val model = Sequential()
    // add CMul layer to model
    model.add(embedded_layer)
    // construct MLP hidden layers
    val numEmbeddingOutput = uOutput + mOuput + numExtraFeature
    val linear1 = math.pow(2.0, (math.log(numEmbeddingOutput) / math.log(2)).toInt).toInt
    val linear2 = linear1 / 2
    model.add(Linear(numEmbeddingOutput, linear1)).add(ReLU()).add(Dropout())
    model.add(Linear(linear1, linear2)).add(ReLU())
    // Concate the CMul and MLP hidden layers and adopt logSoftMax activation functions to predict final output layer
    model.add(Linear(linear2, 2)).add(LogSoftMax())
    if (params.debug) {
      println(model)
    }
    model
  }

  def getKerasDLModel(
    ulimit: Int, uOutput: Int,
    mlimit: Int, mOuput: Int,
    params:           AppParams,
    featureDimension: Int): Module[Float] = {
    KerasMLPModel.getKErasMLPModel(ulimit, uOutput, mlimit, mOuput, params, featureDimension)

  }

}
