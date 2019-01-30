'''Trains a simple deep MLP on the MNIST dataset.
'''

from __future__ import print_function

import keras
from keras.datasets import mnist
from keras.models import Sequential
from keras.layers import Dense, Dropout
from keras.optimizers import RMSprop
from keras.callbacks import ModelCheckpoint

batch_size = 128
num_classes = 10
epochs = 3

# the data, split between train and test sets
(x_train, y_train), (x_test, y_test) = mnist.load_data()
# rescale [0,255] -> [0,1]
x_train = x_train.reshape(60000, 784)
x_test = x_test.reshape(10000, 784)
x_train = x_train.astype('float32')
x_test = x_test.astype('float32')
x_train /= 255
x_test /= 255

# print train and test samples 
print(x_train.shape[0], 'train samples')
print(x_test.shape[0], 'test samples')

# print first ten trainning labels 
print('Integer-valued labels:')
print(y_train[:10])
# convert class vectors to binary class matrices one-hot encode the labels
y_train = keras.utils.to_categorical(y_train, num_classes)
y_test = keras.utils.to_categorical(y_test, num_classes)
print('one-hot labels:')
print(y_train[:10])

model = Sequential()
model.add(Dense(512, activation='relu', input_shape=(784,)))
model.add(Dropout(0.2))
model.add(Dense(512, activation='relu'))
model.add(Dropout(0.2))
model.add(Dense(num_classes, activation='softmax'))

model.summary()

model.compile(loss='categorical_crossentropy',
              optimizer=RMSprop(),
              metrics=['accuracy'])

# evalute test accuracy
score = model.evaluate(x_test,y_test,verbose=0)
accuracy = 100*score[1]

# print test accuracy
print('Test Accuracy : %.4f%%' % accuracy)

#train the model
checkpointer = ModelCheckpoint(filepath='minsst.model.best.hdf5',verbose=1,save_best_only=True)

history = model.fit(x_train, y_train,
                    batch_size=batch_size,
                    epochs=epochs,
                    verbose=1,
                    validation_data=(x_test, y_test),callbacks=[checkpointer],shuffle=True)
# load the weights that yielded the best validation accuracy
model.load_weights('minsst.model.best.hdf5')
# evaluate test accuracy 
score = model.evaluate(x_test, y_test, verbose=0)
print('Test loss:', score[0])
print('Test accuracy:', score[1])