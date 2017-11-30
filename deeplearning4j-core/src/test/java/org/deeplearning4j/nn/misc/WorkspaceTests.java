package org.deeplearning4j.nn.misc;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.Debug4347;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.executioner.OpExecutioner;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.primitives.Pair;

@Slf4j
public class WorkspaceTests {

    @Before
    public void before(){
        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.SCOPE_PANIC);
    }

    @After
    public void after(){
        Nd4j.getExecutioner().setProfilingMode(OpExecutioner.ProfilingMode.DISABLED);
    }

    @Test
    public void checkScopesTestCGAS() throws Exception {
        ComputationGraph c = createNet();
        for(WorkspaceMode wm : new WorkspaceMode[]{WorkspaceMode.SEPARATE, WorkspaceMode.SINGLE}) {
            log.info("Starting test: {}", wm);
            c.getConfiguration().setTrainingWorkspaceMode(wm);
            c.getConfiguration().setInferenceWorkspaceMode(wm);

            INDArray f = Nd4j.rand(new int[]{8, 1, 28, 28});
            INDArray l = Nd4j.rand(8, 10);
            c.setInputs(f);
            c.setLabels(l);

            c.computeGradientAndScore();
        }
    }


    @Test
    public void testWorkspaceIndependence() {
        //https://github.com/deeplearning4j/deeplearning4j/issues/4337
        int depthIn = 2;
        int depthOut = 2;
        int nOut = 2;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().weightInit(WeightInit.XAVIER)
                .convolutionMode(ConvolutionMode.Same).seed(12345L).list()
                .layer(0, new ConvolutionLayer.Builder().nIn(depthIn).nOut(depthOut).kernelSize(2, 2)
                        .stride(1, 1).activation(Activation.TANH).build())
                .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                        .activation(Activation.SOFTMAX).nOut(nOut).build())
                .setInputType(InputType.convolutional(5,5,2))
                .pretrain(false).backprop(true).build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf.clone());
        net.init();
        net.getLayerWiseConfigurations().setInferenceWorkspaceMode(WorkspaceMode.SEPARATE);
        net.getLayerWiseConfigurations().setTrainingWorkspaceMode(WorkspaceMode.SEPARATE);

        MultiLayerNetwork net2 = new MultiLayerNetwork(conf.clone());
        net2.init();
        net2.getLayerWiseConfigurations().setInferenceWorkspaceMode(WorkspaceMode.NONE);
        net2.getLayerWiseConfigurations().setTrainingWorkspaceMode(WorkspaceMode.NONE);

        INDArray in = Nd4j.rand(new int[]{1,2,5,5});

        net.output(in);
        net2.output(in);    //Op [add_scalar] X argument uses leaked workspace pointer from workspace [LOOP_EXTERNAL]
    }

    public static ComputationGraph createNet() throws Exception {

        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .graphBuilder()
                .addInputs("in")
                .addLayer("0", new ConvolutionLayer.Builder().nOut(3)
                        .kernelSize(2,2).stride(2,2).build(), "in")
                .addLayer("1", new ConvolutionLayer.Builder().nOut(3)
                        .kernelSize(2,2).stride(2,2).build(), "0")
                .addLayer("out", new OutputLayer.Builder().nOut(10)
                        .activation(Activation.TANH).lossFunction(LossFunctions.LossFunction.MSE)
                        .build(), "1")
                .setOutputs("out")
                .setInputTypes(InputType.convolutional(28,28,1))
                .build();

        ComputationGraph model = new ComputationGraph(conf);
        model.init();

        return model;
    }


    @Test
    public void testWithPreprocessorsCG(){
        //https://github.com/deeplearning4j/deeplearning4j/issues/4347
        //Cause for the above issue was layerVertex.setInput() applying the preprocessor, with the result
        // not being detached properly from the workspace...

        for(WorkspaceMode wm : WorkspaceMode.values()) {
            System.out.println(wm);
            ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                    .trainingWorkspaceMode(wm)
                    .inferenceWorkspaceMode(wm)
                    .graphBuilder()
                    .addInputs("in")
                    .addLayer("e", new GravesLSTM.Builder().nIn(10).nOut(5).build(), new DupPreProcessor(), "in")
//                .addLayer("e", new GravesLSTM.Builder().nIn(10).nOut(5).build(), "in")    //Note that no preprocessor is OK
                    .addLayer("rnn", new GravesLSTM.Builder().nIn(5).nOut(8).build(), "e")
                    .addLayer("out", new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE)
                            .activation(Activation.SIGMOID).nOut(3).build(), "rnn")
                    .setInputTypes(InputType.recurrent(10))
                    .setOutputs("out")
                    .build();

            ComputationGraph cg = new ComputationGraph(conf);
            cg.init();


            INDArray[] input = new INDArray[]{Nd4j.zeros(1, 10, 5)};

            for( boolean train : new boolean[]{false, true}){
                cg.clear();
                cg.feedForward(input, train);
            }

            cg.setInputs(input);
            cg.setLabels(Nd4j.rand(1, 3, 5));
            cg.computeGradientAndScore();
        }
    }

    @Test
    public void testWithPreprocessorsMLN(){
//        for(WorkspaceMode wm : WorkspaceMode.values()) {
        for(WorkspaceMode wm : new WorkspaceMode[]{WorkspaceMode.SEPARATE}) {
            System.out.println(wm);
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .trainingWorkspaceMode(wm)
                    .inferenceWorkspaceMode(wm)
                    .list()
                    .layer(new GravesLSTM.Builder().nIn(10).nOut(5).build())
                    .layer(new GravesLSTM.Builder().nIn(5).nOut(8).build())
                    .layer(new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE).activation(Activation.SIGMOID).nOut(3).build())
                    .inputPreProcessor(0, new DupPreProcessor())
                    .setInputType(InputType.recurrent(10))
                    .build();

            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();


            INDArray input = Nd4j.zeros(1, 10, 5);

            for( boolean train : new boolean[]{false, true}){
                net.clear();
                net.feedForward(input, train);
            }

            net.setInput(input);
            net.setLabels(Nd4j.rand(1, 3, 5));
            net.computeGradientAndScore();
        }
    }

    public static class DupPreProcessor implements InputPreProcessor {
        @Override
        public INDArray preProcess(INDArray input, int miniBatchSize) {
            return input.dup();
        }

        @Override
        public INDArray backprop(INDArray output, int miniBatchSize) {
            return output.dup();
        }

        @Override
        public InputPreProcessor clone() {
            return new Debug4347.DupPreProcessor();
        }

        @Override
        public InputType getOutputType(InputType inputType) {
            return inputType;
        }

        @Override
        public Pair<INDArray, MaskState> feedForwardMaskArray(INDArray maskArray, MaskState currentMaskState, int minibatchSize) {
            return new Pair<>(maskArray, currentMaskState);
        }
    }
}
