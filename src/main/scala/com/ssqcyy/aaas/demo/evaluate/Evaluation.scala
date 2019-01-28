package com.ssqcyy.aaas.demo.evaluate

import org.apache.spark.ml.evaluation.{BinaryClassificationEvaluator, MulticlassClassificationEvaluator}
import org.apache.spark.sql.{DataFrame, Row}
import org.apache.spark.sql.functions.col
import org.apache.spark.ml.linalg._
import org.apache.spark.sql.functions._
import org.apache.hadoop.fs._;

/**
 * @author suqiang.song
 *
 */
object Evaluation {

def evaluateByMid(path:String,prefix: String,saveMode: String,evaluateDF: DataFrame): Unit = {
    val tp = udf { (pred: Double, label: Double) =>
      if (pred == 1 && label == 1) 1.0 else 0.0
    }

    val fp = udf { (pred: Double, label: Double) =>
      if (pred == 1 && label == 0) 1.0 else 0.0
    }

    val fn = udf { (pred: Double, label: Double) =>
      if (pred == 0 && label == 1) 1.0 else 0.0
    }

    val records = evaluateDF.withColumn("tp", tp(col("prediction"), col("label")))
      .withColumn("fp", fp(col("prediction"), col("label")))
      .withColumn("fn", fn(col("prediction"), col("label")))

    val byMid = records.select("mid","tp", "fp", "fn", "label")
      .groupBy("mid").agg(sum("label").as("posCount"), sum("tp").as("tp"), sum("fp").as("fp"), sum("fn").as("fn"))
      .withColumn("recall", col("tp")/(col("tp") + col("fn")))
      .withColumn("precision", col("tp")/(col("tp") + col("fp")))
    println("evaluation by mid: " + "*" * 100)
    byMid.orderBy("mid").show(10, false)

    val tpSum = byMid.select(sum("tp")).head().getDouble(0)
    val fpSum = byMid.select(sum("fp")).head().getDouble(0)
    val fnSum = byMid.select(sum("fn")).head().getDouble(0)

    println(s"total tp: $tpSum")
    println(s"total fp: $fpSum")
    println(s"total fn: $fnSum")

    println("total recall: " + tpSum.toDouble / (tpSum + fnSum))
    println("total precision: " + tpSum.toDouble / (tpSum + fpSum))
    //byMid.repartition(1).write.format("com.databricks.spark.csv").mode(saveMode).option("header", "true").save(path+prefix+"EvaluationByIdDF")

  }


}
