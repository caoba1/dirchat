# Requirements and functionality
The main functionality goals are listed here

+ Load Directory
+ + Indexing
+ Search files
+ + Retrieving
+ Augmentation
+ + Chat LLM
+ Export

In the first run, the functionality is just one chat! Just one conext window and one question.

_Context capability for further search and questions should be scalable in the future._

The previous suggests that a context-aware generation strategy should be studied for its implementation in the project.

## Examples
For example give a less score to information from the metadata of the file to the chat prompt




# Model requirements
I need the model to be generalizable enough so that the search "fried eggs" and "search me for a fried egg recipe" are sufficiently close in the same semantic space.

# Layout 
The layout is in ConfigMenu.fxml; the dialog sizing and labels are handled in ConfigDialog.java, with text in messages.properties.

## Text, settings, and diagnostics

- Keep all user-visible text in `messages.properties` (English fallback) and the matching `messages_es.properties`, `messages_de.properties`, and `messages_fr.properties` bundles.
- Namespace message keys by feature: `directory`, `indexing`, `search`, `retrieval`, `augmentation`, `chat`, `export`, and `config`.
- Use the configured `ui.language` to select the bundle. FXML text uses `%key`; Java code uses `Messages.get` or `Messages.format`.
- Keep `config.properties` for default setting values only. Persist user overrides in the per-user configuration file, with setting keys grouped by feature.
- Keep prompts for the language model in prompt resources rather than in the UI message bundles.
- Keep diagnostic logs in stable English, separate from localized UI messages. With Maven, enable debug logging using `JAVA_TOOL_OPTIONS="-Ddirchat.log.level=DEBUG" mvn javafx:run`.

# Program architecture
Structure:
+ Load Directory
+ + Indexing
+ Search files
+ + Retrieving
+ Augmentation
+ + Chat LLM
+ Export

# Directory loading and indexing flow
`UIController.handleLoadFolder` starts an asynchronous recursive scan using
`DocumentLoader.scanDirectory`. The scan excludes the `.dirchat` index directory
and returns supported files plus the number of subdirectories.

`UIController.prepareDirectoryLoad` checks for an existing SQLite index. If a
new index or configured regeneration is needed, it asks the user to confirm the
file and subdirectory counts before deleting or creating any index data.
`UIController.startIndexing` then starts `IndexingTask`, which indexes the
discovered files or loads their indexed status from the existing store.
Documents are chunked and embedded in `StreamSplitter.splitDocument`, and the
completed file list is displayed in the main table.

# Programm Flow
IndexingTask calls handler.splitDocument().
For PDFs, splitDocument() calls chunkTextBySize(text, pageNumber) for each page. That cuts the text into fixed-size pieces with overlap.
For other file types, Tika parses the file and calls characters(...). That fills a buffer and calls processChunk(...) whenever the buffer reaches CHUNK_CHAR_LIMIT.
Both routes end up in dirchatStoreChunk(chunk, text), which does the real work:
Embedding: embeddingService.embedText(chunkText), which calls Ollama (the //magic line).
Saving: vectorStore.saveChunkEmbeddingToSqlite(chunk, embedding), which writes to table_chunk.
Progress and cancel: it updates the progress dialog and checks whether you cancelled.
