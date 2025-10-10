<p align="left">
  <img src="src/main/resources/icons/dirchat-logo-full.png" alt="dirchat full logo" height="80">
</p>

# A directory chat application written in Java

A desktop application for question answering over local documents using Apache Tika, LangChain4j, and JavaFX. 

The current application implements SemanticSearch and Retrieval-Augmented Generation (RAG) using embeddings and a local LLM via [Ollama](https://ollama.com/). Please fell free to message me or do a pull request if you want to contibute adding image-to-text or pdf-to-markup or other featurs which can contribute to the indexing process. 

Indexing and retrieval methods are still in evaluatory phase. 

For the time being, the application supports `.pdf` files and interfaces exist for expanding that to `.docx`, and `.txt`. More file types should be added in the future, as well as support for saving images in the same semantic space.

## Motivation
Current open source implmentations for searching in directories lack support for semantic search and RAG generation in an open and straightforward way.  I also needed a fast alternative to catfish and recoll for searching and retrieving relevant files in a large document dataset.

## Evaluation
For testing and improving the semantic search semantic and the RAG process, I am using [tinyllama](https://ollama.com/models/tinyllama) as the embedding model. 
The following models have been tested: 
- For the embedding model:
  - `SBERT`
  - `tynillama`
- For LLM retrieval and answer generation:
  - `tynillama`
  - `mistral`

## Indexing
For indexing each of the folders, the text on each document is extracted, embeddings are generated on overlapping pieces of information, and stored in a local SQLite database. The index is stored in a subdirectory of the selected folder.
The indexing process may take some time depending on the number and size of the documents. Currently, embeddings are extracted based on a group of paragraphs. **This is a crucial step to improve for day-to-day use.**


## Features - What it does
- Select a folder with documents (`.pdf`, `.docx`, `.txt`)
- Extracts text from documents using Apache Tika
- Generates embeddings from the documents, which are stored as index files per document
- Answers user questions with context issued from relevant document chunks
- Displays answers with references to the original document

## Architecture
I am using a modular Java approach with the following main components:
- **UIController**: Handles threading and UI updates.
- **ChatService**: Handles user queries and LLM responses
- **DocumentLoader**: Loads and parses documents via Apache Tika
- **TextChunker**: Splits extracted text into _bitesize_ chunks
- **EmbeddingService**: Embeds text using LangChain4j and the model of your choice
- **VectorStore**: Stores and retrieves relevant document vectors

## Requirements
- Java 17+
- JavaFX SDK
- Maven
  -- LangChain4j
  -- Apache Tika
  -- Jackson Databind
- Ollama running locally or on your server, usually at port 11434.
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
The generated index files are stored as JSON files in a subdirectory. These embeddings are loaded in the next session if needed. I am currently using _tinyllama_ as the embedding model. Be warned, it may take some time to index all your files.

## Urgent
- Move long-running operations (embedding, indexing, answering questions) to JavaFX Tasks or background threads.
- Use Platform.runLater to update UI elements (progress bars, cursor, messageBar) from those threads.

## To-Dos
- Improve UI/UX
- Run background tasks for the UI
- Improve contextuality. Implement [this](https://www.youtube.com/watch?v=6efwN_US-zk).
- Explore using SBERT for document and context retrieval.
- Use ServiceExecutor for calls to embedding services
- Integrate ChromaDB for persistent storage. At the moment the index is saved locally in a JSON file. Emebbings are saved on overlapping chunks of text which point to a cursor position in the document.
- Add a settings page to change the model and other parameters
- Try other models, explore other API providers, use a task-specific model (**!**)
- Develop towards a distributable
- Linux support
- **Improve Indexing Pipeline readability**
- **Change name to retriever**
- **Prompt augmentation task**
- **Configurable RAG process**
- 


## Database Structure and Pointer-Based Chunk Access

The application uses a SQLite database for all vector storage and retrieval. File-based index is no longer supported.

- **TABLE_INDEX**: Stores global index properties (e.g., embedding method).
- **TABLE_DOCUMENT**: Stores metadata for each document (filename, location, number of pages, filetype, TOC, metadata).
- **TABLE_CHUNK**: Stores chunk pointers and embedding vectors. Each chunk entry includes:
    - `document_id`: Reference to the document in TABLE_DOCUMENT
    - `chunk_index`: Index of the chunk within the document
    - `page_number`: Page number for the chunk (if applicable)
    - `cursor_pointer`: Character offset or pointer for chunk reconstruction
    - `embedding_vector`: The embedding vector for the chunk
    - `metadata`: Additional metadata for the chunk

**Pointer-Based Retrieval**
- Chunk content is not stored in the database. Instead, chunk pointers (document_id, page_number, cursor_pointer) are used to reconstruct the chunk content at runtime using Apache Tika and the original document.
- This approach reduces storage requirements and ensures accurate retrieval of chunk content.

**How Retrieval Works**
- When querying, the application retrieves the top relevant chunk pointers and uses them to reconstruct the chunk content from the original document using Tika and the chunker.
- All metadata is stored in the appropriate table, not as a chunk.

## Example Workflow
1. **Indexing**: Documents are parsed and chunked. Embeddings and pointers are saved to the database.
2. **Querying**: When a user asks a question, the most relevant chunks are found using embeddings. Their pointers are used to fetch the actual text from the original document.
3. **Answering**: The retrieved chunk contents are provided as context to the language model for generating answers.

(c) [caoba1](https://github.com/caoba1)  
Licensed under the [MIT License](LICENSE)
