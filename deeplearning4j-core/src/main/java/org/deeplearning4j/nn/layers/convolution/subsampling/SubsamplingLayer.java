/*
 *
 *  * Copyright 2015 Skymind,Inc.
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

package org.deeplearning4j.nn.layers.convolution.subsampling;

import com.google.common.primitives.Ints;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.ParamInitializer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.params.ConvolutionParamInitializer;
import org.deeplearning4j.optimize.api.ConvexOptimizer;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.util.Dropout;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.convolution.Convolution;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.deeplearning4j.util.ConvolutionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.deeplearning4j.nn.conf.layers.SubsamplingLayer.PoolingType;


/**
 * Subsampling layer.
 *
 * Used for downsampling a convolution
 *
 * @author Adam Gibson
 */
public class SubsamplingLayer implements Layer {
    private NeuralNetConfiguration conf;
    private Layer convLayer;
    protected ParamInitializer paramInitializer;
    private Map<String,INDArray> params;
    protected int index = 0;
    protected INDArray input;
    private INDArray dropoutMask;

    public SubsamplingLayer(NeuralNetConfiguration conf) {
        this.conf = conf;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public double l2Magnitude() {
        return 0;
    }

    @Override
    public double l1Magnitude() {
        return 0;
    }

    @Override
    public Type type() {
        return Type.CONVOLUTIONAL;
    }

    @Override
    public Gradient error(INDArray input) {
        throw new UnsupportedOperationException();

    }

    @Override
    public INDArray derivativeActivation(INDArray input) {
        INDArray deriv = Nd4j.getExecutioner().execAndReturn(Nd4j.getOpFactory().createTransform(conf().getActivationFunction(), activate(input)).derivative());
        return deriv;
    }

    @Override
    public Gradient calcGradient(Gradient layerError, INDArray indArray) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Gradient errorSignal(Gradient error, INDArray input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, Gradient gradient, Layer layer) {

        int inHeight = (input.shape()[2] + conf.getKernelSize()[0]) * conf.getStride()[0] + 1;
        int inWidth = (input.shape()[3] + conf.getKernelSize()[1]) * conf.getStride()[1] + 1;

        INDArray scaled = null;
        Gradient ret = new DefaultGradient();
        ret.gradientForVariable().put(ConvolutionParamInitializer.CONVOLUTION_WEIGHTS, null);

        switch(conf.getPoolingType()) {
            case AVG:
                scaled = Convolution.col2im(epsilon, conf.getStride(), conf.getPadding(), inHeight, inWidth);
                return new Pair<>(ret,scaled);
            case MAX:
                // TODO pull argmax indicies and only put epsilon in that position in the new scale
                return new Pair<>(ret, scaled);
            case SUM:
                // TODO get percentage of each cell on how to apply epsilon
                return new Pair<>(ret, scaled);
            case NONE:
                return new Pair<>(ret,epsilon);

            default:
                throw new IllegalStateException("Pooling type not supported!");
        }

    }

    @Override
    public void merge(Layer layer, int batchSize) {
        throw new UnsupportedOperationException();

    }

    @Override
    public INDArray activationMean() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(Gradient gradient) {

    }

    @Override
    public INDArray preOutput(INDArray x) {
        throw new UnsupportedOperationException();
    }

    @Override
    public INDArray preOutput(INDArray x, boolean training) {
        return activate(x,training);
    }

    @Override
    public INDArray activate(boolean training) {
        return activate(this.input,training);
    }

    @Override
    public INDArray activate(INDArray input, boolean training) {
        if(training && conf.getDropOut() > 0) {
            this.dropoutMask = Dropout.applyDropout(input,conf.getDropOut(),dropoutMask);
        }

        INDArray pooled = Convolution.im2col(input,conf.getKernelSize(),conf.getStride(),conf.getPadding());

        switch(conf.getPoolingType()) {
            case AVG:
                return pooled.mean(2,3);
            case MAX:
                //number of images
                int n = pooled.size(0);
                //number of channels (depth)
                int c = pooled.size(1);
                //image height
                int kh = pooled.size(2);
                //image width
                int kw = pooled.size(3);
                int outWidth = pooled.size(4);
                int outHeight = pooled.size(5);
                INDArray ret = pooled.reshape(n,c,kh * kw,outHeight,outWidth);
                return ret.max(2);
            case SUM:
                return pooled.sum(2,3);
            case NONE:
                return input;
            default: throw new IllegalStateException("Pooling type not supported!");

        }
    }

    @Override
    public INDArray activate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public INDArray activate(INDArray input) {
        return activate(true);
    }

    @Override
    public Layer transpose() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Layer clone() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<IterationListener> getListeners() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setListeners(IterationListener... listeners) {

    }

    @Override
    public void setListeners(Collection<IterationListener> listeners) {

    }

    @Override
    public void fit() {

    }

    @Override
    public void update(INDArray gradient, String paramType) {

    }

    @Override
    public double score() {
        return 0;
    }

    @Override
    public void computeGradientAndScore() {

    }

    @Override
    public void accumulateScore(double accum) {

    }

    @Override
    public INDArray transform(INDArray data) {
        return activate(data);
    }

    /**
     * Returns the parameters of the neural network
     *
     * @return the parameters of the neural network
     */
    @Override
    public INDArray params() {
        List<INDArray> ret = new ArrayList<>();
        for(String s : params.keySet())
            ret.add(params.get(s));
        return Nd4j.toFlattened(ret);
    }

    @Override
    public int numParams() {
        int ret = 0;
        for(INDArray val : params.values())
            ret += val.length();
        return ret;
    }

    @Override
    public void setParams(INDArray params) {
        throw new UnsupportedOperationException();

    }

    @Override
    public void initParams() {
        paramInitializer.init(paramTable(),conf());
    }


    @Override
    public void fit(INDArray data) {
        throw new UnsupportedOperationException();

    }

    @Override
    public void iterate(INDArray input) {
        throw new UnsupportedOperationException();

    }

    @Override
    public Gradient gradient() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Pair<Gradient, Double> gradientAndScore() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int batchSize() {
        return input.size(0);
    }

    @Override
    public NeuralNetConfiguration conf() {
        return conf;
    }

    @Override
    public void setConf(NeuralNetConfiguration conf) {
        this.conf = conf;
    }

    @Override
    public INDArray input() {
        return input;
    }

    @Override
    public void validateInput() {

    }

    @Override
    public ConvexOptimizer getOptimizer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public INDArray getParam(String param) {
        return params.get(param);
    }



    @Override
    public Map<String, INDArray> paramTable() {
        return params;
    }

    @Override
    public void setParamTable(Map<String, INDArray> paramTable) {
        this.params = paramTable;
    }

    @Override
    public void setParam(String key, INDArray val) {
        this.params.put(key,val);
    }

    @Override
    public void clear() {

    }
}
