/*
 * dirchat — Java directory chat application.
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 * License: GNU AGPL v3 or later. See LICENSE for details.
 */

package org.dirchat.embedding;

import javafx.concurrent.Task;
import org.dirchat.controller.UIController;
import org.dirchat.llm.ChatService; // TODO: divide in two
import org.dirchat.util.ConfigManager;
import org.dirchat.util.LogMessages;
import org.dirchat.util.Messages;
import java.util.List;
import org.dirchat.document.DocumentLoader;

public class QueryAnswerTask extends Task<String>{
    String answer = "";
    private final String question;
    private final VectorStore vectorStore;
    private final List<DocumentLoader.LoadedDocument> loadedDocuments;
    private final UIController uiController;
    public QueryAnswerTask(VectorStore vectorStore,
                           List<DocumentLoader.LoadedDocument> loadedDocuments,
                           String question,
                           UIController uiController) {
        this.vectorStore = vectorStore;
        this.loadedDocuments = loadedDocuments;
        this.question = question;
        this.uiController = uiController;
    }

    protected String call() throws Exception {
        try {
            ChatService chatService = new ChatService(vectorStore, loadedDocuments, uiController);
            // Find documents and update their order
            String query = question;
            chatService.findTopDocuments(query); // Saves top documents
            // Previous call calls setIndexStatusListItems internally on this UIController
            if (ConfigManager.getQueryAnswer()) {
                answer = chatService.answerTopDocuments(question);
            }
            else {
                answer = Messages.get("chat.answer.disabled");
            }
            String citations = chatService.getTopDocumentCitations();
            citations = "\n\n" + Messages.get("chat.citations.header") + "\n" + citations;
            answer = answer.concat(citations);
        } catch (Exception ex) {
            LogMessages.logWarning("Could not generate a chat answer.", ex);
            answer = Messages.get("chat.answer.failed");
            throw ex;
        }
        return answer;
    }
}
