<p align="left">
  <img src="src/main/resources/icons/dirchat-logo-full.png" alt="dirchat full logo" height="80">
</p>

# A directory chat application written in Java

An app for directory querying using network embeddings and question answering on the retrieved local documents using language models.

The application is written in Java and implements an interface for the creation of embeddings and the reply of queries by poking a (Large) Language Model (LLM) either locally or on a network server.

When a user asks a question, the results of the top K documents are shown in the results window. The application shows the retrieved documents, along with its relative score and the result of a query to a conversational large language model. 

This application implement a Retrieval-Augmented Generation (RAG) pipeline using embeddings and a local LLM via [Ollama](https://ollama.com/).  

Work on the implementation of a local solution for a Model Context Protocol server (MCP) is also underway. Please fell free to message me or make a pull request if you want to contibute adding image-to-text or pdf-to-markup or other featurs which can contribute to the indexing and search processes. 

Indexing and retrieval methods are still in evaluatory phase. 

The application supports `.pdf` files and interfaces exist for expanding that to `.docx`, and `.txt`. More file types should be added in the future, as well as support for saving images in the same semantic space.

## Motivation
I created this application to have a fast and local solution for searching and querying large document datasets.

Current open source implmentations for searching in directories lack support for semantic search and RAG generation in an open and straightforward way.   Goal is to use the application as a replacement for other large dataset searching tools like catfish and recoll.  

## Evaluation
For testing and improving the semantic search semantic and the RAG process, I am using several language models for generating the embeddings.  For context aware answer generation I am using tinyllama.

The following models have been tested: 
- For the embedding model:
  - `SBERT`
  - `tynillama`
- For LLM retrieval and answer generation:
  - `tynillama`
  - `mistral`

For testbenching i have used a large set of PDF documents downloaded from [data.gov](https://data.labs.loc.gov/dot-gov/) and [loc.gov](https://www.loc.gov/item/2020445568/).  Please message me if you have information on current benchmarks for document retrieval.

## Indexing
For indexing each of the folders, a text snippet on each document is extracted for which embeddings are generated on overlapping pieces of information, and stored in a local SQLite database. The index for all documents is stored as a subdirectory of the selected folder.
The indexing process may take some time depending on the number and size of the documents. Currently, embeddings are extracted based on a fixed size of characters. **This is a crucial step to improve for day-to-day use.**


## Features - What it does
- Select a folder with documents (`.pdf`, `.docx`, `.txt`)
- Extracts text from documents using Apache Tika
- Generates embeddings from the documents, which are stored as index files per document
- Answers user questions with context issued from relevant document chunks
- Displays answers with references to the original document


## Architecture
A modular Java approach with the following main components:
- **Controller**: Handles threading and UI updates `org.dirchat.controller`
- **ChatService**: Handles user queries and LLM responses `org.dirchat.llm`
- **IndexingAndRetrieval**: Loads and parses documents via Apache Tika `org.dirchat.document`
- **EmbeddingService**: Generates document embeddings using LangChain4j and the model of your choice. `org.dirchat.embedding` 
- **VectorStore**: Stores and retrieves relevant document vectors `org.dirchat.embedding`
- **Util**: Utility functions `org.dirchat.util`


## Requirements
- Java 17+
- JavaFX SDK
- Maven
  -- LangChain4j
  -- Apache Tika
  -- Jackson Databind
- Ollama running locally or on a network server, usually at port 11434.
   -- tinyllama


## Getting Started
1. **Clone the repository**
2. **Start Ollama locally or on your server** (see [Ollama docs](https://ollama.com/))
4. **Build the project**
   ```sh
   mvn clean install 
5. **Run the application**
   ```sh
   mvn javafx:run
   ```

## Notes
The generated index files are stored on a local SQLite server. These embeddings are loaded in the next session if needed. Retrieval is performed with dirchat's own technology. 

## Urgent
- Migrate tasks to org.dirchat.util.ThreadManager
- Migrate uses of Platform.runLater for updating UI elements (progress bars, cursor, messageBar) to org.dirchat.util.ThreadManager.

## To-Dos
- Improve UI/UX
- Run background tasks for the UI
- Improve contextuality. 
- Explore using SBERT and BERTO for document and context retrieval.
- Use ServiceExecutor for calls to embedding services **For use with JavaFx's own implementation** 
- Try other models, explore other API providers, use a task-specific model (**!**)
- Build distributable
- Linux support
- **Use an open distributable model for the embeddings so to not depend on ollama**
- **Implement a new scoring mechanism**
- **Improve performance of the indexing pipeline**
- **Configurable RAG prompt augmentation task**
- **Implement MCP functionality**


## Contact

For questions regarding this project, licensing, or compliance with the
GNU Affero General Public License v3.0 (AGPL-3.0), please contact:
info@uiloops.com


(c) [caoba1](https://github.com/caoba1)  
Licensed under the [AGPL-3.0 License](LICENSE)
