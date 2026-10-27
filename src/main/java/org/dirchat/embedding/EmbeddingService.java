/*
 * dirchat — Java directory chat application.
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 * License: GNU AGPL v3 or later. See LICENSE for details.
 */

package org.dirchat.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
//import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;

import org.dirchat.util.LogMessages;
import org.dirchat.util.Messages;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.dirchat.util.ConfigManager;
import org.dirchat.util.Dirchat;

public class EmbeddingService {

    //public static final String BASE_URL = ConfigManager.getOllamaBaseUrl();
    public static final int MAX_CHUNKS = 10;

    // TODO
    private static final ExecutorService SERVICE_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()));
    private final String baseUrl;

    private EmbeddingModel embeddingModel;
    private final String embeddingMethod;

    // Provides a tokenizer and the embedding model based on the embedding method
    //public EmbeddingService() {
    //    this(BASE_URL, ConfigManager.getIndexingMethod());
    //}
    public String getBaseUrl() {
        return baseUrl;
    }


    public EmbeddingService(String baseUrl, String embeddingMethod) {
        this.baseUrl = baseUrl;
        this.embeddingMethod = embeddingMethod;
        try
        {
            this.embeddingModel = getMethodEmbeddingModel(this.embeddingMethod);
        } catch (RuntimeException e) {
            LogMessages.logWarning("Could not initialize the embedding model.", e);
            throw e;
        }
    }

    // Generates embeddings for a single text chunk
    public List<Double> embedText(String text) {
        float[] vector = embeddingModel.embed(text).content().vector();
        return Dirchat.floatArrayToDoubleList(vector);
    }

    public String getEmbeddingMethod() {
        return embeddingMethod;
    }

    /**
     * Returns a new EmbeddingModel instance based on the current embeddingMethod.
     */
    public EmbeddingModel getMethodEmbeddingModel(String embeddingMethod) {
        EmbeddingModel methodEmbeddingModel = null;
        if (embeddingMethod.equalsIgnoreCase("default")) {
            throw new IllegalArgumentException(Messages.get("embedding.error.method-not-implemented"));
        } else if (embeddingMethod.equalsIgnoreCase("sbert")) {
            methodEmbeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .modelName("all-minilm:33m")
                    .build();
        } else if (embeddingMethod.equalsIgnoreCase("langchainALLMINI")) {
            throw new UnsupportedOperationException("The langchainALLMINI embedding method is unavailable.");
        } else if (embeddingMethod.equalsIgnoreCase("ollama")
                || embeddingMethod.equalsIgnoreCase("tinyllama")) {
            methodEmbeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .modelName("tinyllama")
                    .build();
        } else {
            throw new IllegalArgumentException("Unsupported embedding method: " + embeddingMethod);
        }
        return methodEmbeddingModel;
    }
}