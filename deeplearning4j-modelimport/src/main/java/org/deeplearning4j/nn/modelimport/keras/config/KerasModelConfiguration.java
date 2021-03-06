/*-
 *
 *  * Copyright 2017 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */
package org.deeplearning4j.nn.modelimport.keras.config;

import lombok.Data;


/**
 * Basic properties and field names of serialised Keras models.
 *
 * @author Max Pumperla
 */
@Data
public class KerasModelConfiguration {

    /* Model meta information fields */
    private final String fieldClassName = "class_name";
    private final String fieldClassNameSequential = "Sequential";
    private final String fieldClassNameModel = "Model";
    private final String fieldKerasVersion = "keras_version";

    /* Model configuration field. */
    private final String modelFieldConfig = "config";
    private final String modelFieldLayers = "layers";
    private final String modelFieldInputLayers = "input_layers";
    private final String modelFieldOutputLayers = "output_layers";

    /* Training configuration field. */
    private final String trainingLoss = "loss";
    private final String trainingWeightsRoot = "model_weights";
    private final String trainingModelConfigAttribute = "model_config";
    private final String trainingTrainingConfigAttribute = "training_config";

}
