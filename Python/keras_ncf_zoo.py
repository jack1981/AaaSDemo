import random
import time
import numpy as np

from bigdl.optim.optimizer import *
from bigdl.nn.criterion import CrossEntropyCriterion
from zoo import init_nncontext, init_spark_conf
from zoo.pipeline.api.keras.layers import *
from zoo.pipeline.api.keras.models import Model
from zoo.pipeline.nnframes import *
from pyspark.ml.evaluation import BinaryClassificationEvaluator, MulticlassClassificationEvaluator
from pyspark.sql import SparkSession
from pyspark.sql.functions import concat, col, udf, lit
from pyspark.ml.feature import StringIndexer
from pyspark.sql.types import FloatType
from pyspark.ml.feature import VectorAssembler


csv_path = '/opt/work/data/pcard.csv'
u_limit = 20000
m_limit = 200
sliding_length = 3
train_start = '201702'
train_end = '201710'
neg_rate = 2
num_features = 3
u_output = 200
m_output = 100

sparkConf = init_spark_conf()
sc = init_nncontext(sparkConf)
spark = SparkSession \
    .builder \
    .appName("Train NCF") \
    .getOrCreate()

def _date_to_month(date):
    return (int(date) // 100 - 2000) * 12 + int(date) % 100


def _generate_id_pair():
    uid = random.randint(1, u_limit)
    mid = random.randint(1, m_limit)
    return (uid, mid)


def load_csv():
    raw_df = spark.read.format("csv") \
        .option("header", "true") \
        .option("mode", "DROPMALFORMED") \
        .load(csv_path)

    data_df = raw_df.select("Cardholder Last Name",
                            "Cardholder First Initial",
                            "Amount",
                            "Vendor",
                            "Year-Month") \
        .select(
        concat(col("Cardholder Last Name"), lit(" "), col("Cardholder First Initial")).alias("u"),
        concat(col("Vendor")).alias("m"),
        col("Year-Month").alias("date"),
        col("Amount")
    )

    userIndexer = StringIndexer(inputCol="u", outputCol="uid").fit(data_df)
    itemIndexer = StringIndexer(inputCol="m", outputCol="mid").fit(data_df)

    data_df = itemIndexer.transform(userIndexer.transform(data_df)) \
        .withColumn("uid", (col("uid") + 1).cast(FloatType())) \
        .withColumn("mid", (col("mid") + 1).cast(FloatType())) \
        .cache()

    month_seq_udf = udf(lambda s: _date_to_month(s))
    uDF = data_df.select("uid", "u").distinct().orderBy("uid")
    mDF = data_df.select("mid", "m").distinct().orderBy("mid")
    tDF = data_df.filter(data_df["uid"] <= u_limit).filter(data_df["mid"] <= m_limit) \
        .withColumn("month", month_seq_udf(col("date"))) \
        .drop("u", "m")
    return uDF, mDF, tDF


def genData(tDF, label_start_date, label_end_date):
    label_start_month = _date_to_month(label_start_date)
    label_end_month = _date_to_month(label_end_date)
    transaction_start_month = label_start_month - sliding_length
    tdf_in_feature_range = tDF.filter(tDF["month"] < label_start_month) \
                      .filter(tDF["month"] >= transaction_start_month)

    umFreq_df = tdf_in_feature_range.groupBy("uid", "mid").count()
    slidingDFs = []
    for label_month in range(label_start_month, label_end_month):
        positiveID_df = tDF.filter(tDF["month"] == label_month) \
            .select("uid", "mid").distinct()
        posCount = positiveID_df.count()

        list = sc.parallelize(range(1, posCount * neg_rate)).map(lambda x: _generate_id_pair())
        negativeID_df = spark.createDataFrame(list).select(col("_1").alias("uid"), col("_2").alias("mid")) \
            .distinct().subtract(positiveID_df)
        label_df = positiveID_df.withColumn("label", lit(1.0)).union(
            negativeID_df.withColumn("label", lit(2.0))
        )
        print("pos: ", posCount, "neg: ", negativeID_df.count())
        print("label df count: ", label_df.count())
        featureDF = label_df.join(umFreq_df, ["uid", "mid"], how="left").na.fill(0)
        slidingDFs.append(featureDF)

    resultDF = slidingDFs[0]
    for i in range(1, len(slidingDFs)):
        resultDF = resultDF.union(slidingDFs[i])

    assembler = VectorAssembler(
        inputCols=["uid", "mid", "count"],
        outputCol="features")

    resultDF = assembler.transform(resultDF).select("features", "label").coalesce(4)
    return resultDF

def getKerasModel():
    input = Input(shape=(3,))
    user_select = Select(1, 0)(input)
    item_select = Select(1, 1)(input)
    other_feature = Narrow(1, 2, num_features - 2)(input)

    u2D = Flatten()(user_select)
    item2D = Flatten()(item_select)

    userEmbedding = Embedding(u_limit + 1, u_output)(u2D)
    itemEmbedding = Embedding(m_limit + 1, m_output)(item2D)

    u_flatten = Flatten()(userEmbedding)
    m_flatten = Flatten()(itemEmbedding)

    latent = merge(inputs=[u_flatten, m_flatten, other_feature], mode="concat")

    numEmbeddingOutput = u_output + m_output + num_features - 2
    linear1 = Dense(numEmbeddingOutput // 2)(latent)
    output = Dense(2)(linear1)
    model = Model(input, output)
    model.summary()
    return model


if __name__ == "__main__":
    uDF, mDF, tDF = load_csv()
    trainingDF = genData(tDF, "201312", "201401")
    print("trainingDF count: ", trainingDF.count())
    trainingDF.groupBy("label").count().show()

    validationDF = genData(tDF, "201402", "201403")
    model = getKerasModel()
    criterion = CrossEntropyCriterion()
    classifier = NNClassifier(model, criterion) \
        .setBatchSize(2000) \
        .setOptimMethod(Adam(learningrate=1e-3, learningrate_decay=1e-7)) \
        .setMaxEpoch(3)

    nnClassifierModel = classifier.fit(trainingDF)
    predictionDF = nnClassifierModel.transform(validationDF)

    predictionDF.show()
    predictionDF.groupBy("label").count().show()

    roc_score = MulticlassClassificationEvaluator().setMetricName("weightedPrecision") \
        .evaluate(predictionDF)
    print(roc_score)
