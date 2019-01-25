package com.ssqcyy.aaas.demo.model.keras

import com.intel.analytics.bigdl.Module
import com.intel.analytics.bigdl.tensor.TensorNumericMath.TensorNumeric.NumericFloat
import com.intel.analytics.bigdl.utils.Shape
import com.intel.analytics.zoo.pipeline.api.keras.layers.Merge.merge
import com.intel.analytics.zoo.pipeline.api.keras.layers._
import com.intel.analytics.zoo.pipeline.api.keras.models.Model
import com.ssqcyy.aaas.demo.utils.Utils.AppParams

/**
 * @author suqiang.song
 *
 */
object KerasMLPModel {
  def getKErasMLPModel(
                        ulimit: Int, uOutput: Int,
                        mlimit: Int, mOuput: Int,
                        params: AppParams,
                        featureDimension: Int): Module[Float] = {

    val numExtraFeature = featureDimension - 2
    val input = Input[Float](inputShape = Shape(featureDimension))
    val userInput = Select(1, 0).inputs(input)
    val itemInput = Select(1, 1).inputs(input)
    val otherFeature = if(numExtraFeature>0) Narrow(1, 2, numExtraFeature).inputs(input) else null

    val u2D = Flatten().inputs(userInput)
    val m2D = Flatten().inputs(itemInput)

    val userEmbedding = Embedding(ulimit, uOutput).inputs(u2D)
    val itemEmbedding = Embedding(mlimit, mOuput).inputs(m2D)

    val uflatten = Flatten().inputs(userEmbedding)
    val mflatten = Flatten().inputs(itemEmbedding)

    val latent = if(numExtraFeature>0) merge(inputs = List(uflatten, mflatten, otherFeature), mode = "concat") else merge(inputs = List(uflatten, mflatten), mode = "concat")

    val numEmbeddingOutput = if(numExtraFeature>0) uOutput + mOuput + numExtraFeature else uOutput + mOuput
    val linear1 = math.pow(2.0, (math.log(numEmbeddingOutput) / math.log(2)).toInt).toInt
    val linear2 = linear1 / 2

    val dense1 = Dense(linear1, activation = "relu").inputs(latent)
    val dense2 = Dense(linear2, activation = "relu").inputs(dense1)
    val output = Dense(2, activation = "log_softmax").inputs(dense2)

    val model = Model(input, output)
    model.summary()
    model
  }

}