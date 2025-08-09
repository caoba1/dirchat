# A Java Directory Chat Application

A desktop application using JavaFX for question answering over local documents using Apache Tika, LangChain4j, and Ollama.

## Features - What it does
- Select a folder with documents (`.pdf`, `.docx`, `.txt`)
- Extracts text from documents using Apache Tika
- Generates embeddings from the documents, which are stored as index files per document
- Answers user questions with context issued from relevant document chunks
- Displays answers with references to the original document

## Architecture
- **DocumentLoader**: Loads and parses documents via Apache Tika
- **TextChunker**: Splits extracted text into chunks
- **EmbeddingService**: Embeds text chunks using LangChain4j and the model of your choice
- **VectorStore**: Stores and retrieves relevant chunks
- **ChatService**: Handles user queries and LLM responses
- **UIController**: Manages JavaFX UI and user interactions

## Requirements
- Java 17+
- JavaFX SDK
- Maven
- Ollama running locally or on your server, usually at port 11434.

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

## To-Dos
- Improve UI/UX
- Improve contextuality. Implement [this](https://www.youtube.com/watch?v=6efwN_US-zk).
- Use ServiceExecutor for calls to embedding services
- Integrate ChromaDB for persistent storage. At the moment the index is saved locally in a JSON file. Emebbings are saved on overlapping chunks of text which point to a cursor position in the document.
- Add a settings page to change the model and other parameters
- Try other models, explore other API providers, use a task-specific model (**!**)
- Develop towards a distributable
- Linux support


(c) [caoba1](https://github.com/caoba1)  
Licensed under the [MIT License](LICENSE)
