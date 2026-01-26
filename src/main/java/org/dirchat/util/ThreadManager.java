/**
 * Utility class for managing background tasks using the centralized {@link ThreadManager}.
 * This class provides examples for running tasks in the background, with or without GUI updates,
 * and is designed for use in both JavaFX applications and headless environments (e.g., CLI or server).
 * Using this class improves consistency, thread safety, and reusability.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>1. Running a Simple Background Task</h3>
 * Use this for tasks that do not require a GUI update, such as logging, data processing, or neural network inference.
 * Errors can be handled optionally.
 *
 * <pre>
 * ThreadManager.getInstance().runInBackground(
 *     () -> {
 *         // Your background task (e.g., data processing, neural network inference)
 *         System.out.println("Running task in background...");
 *     },
 *     error -> {
 *         // Handle errors (optional)
 *         error.printStackTrace();
 *     }
 * );
 * </pre>
 *
 * <h3>2. Running a Task with GUI Update</h3>
 * Use this for tasks that require updating the GUI after completion, such as loading data or updating UI elements.
 * The result and errors are automatically routed to the JavaFX Application Thread.
 *
 * <pre>
 * ThreadManager.getInstance().runInBackground(
 *     () -> {
 *         // Simulate a long-running task
 *         Thread.sleep(2000);
 *         return "Task completed at " + System.currentTimeMillis();
 *     },
 *     result -> {
 *         // Update GUI (runs in JavaFX Application Thread)
 *         myLabel.setText(result);
 *     },
 *     error -> {
 *         // Show error in GUI
 *         myLabel.setText("Error: " + error.getMessage());
 *     }
 * );
 * </pre>
 *
 * <h3>3. Running a Task Without GUI (e.g., CLI or Server)</h3>
 * Use this for headless environments, such as CLI tools or servers, where no GUI updates are required.
 * The result can be printed to the console or logged.
 *
 * <pre>
 * ThreadManager.getInstance().runInBackground(
 *     () -> {
 *         // Pure data processing (no GUI)
 *         String result = DataProcessor.processData();
 *         System.out.println(result);
 *     },
 *     null // No GUI update needed
 * );
 * </pre>
 */

import javafx.application.Platform;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ThreadManager {
    // Thread pool for background tasks
    private final ExecutorService executor;

    // Singleton pattern (optional, but recommended for centralized management)
    private static ThreadManager instance;

    private ThreadManager() {
        // Use a fixed thread pool or cached pool depending on your needs
        this.executor = Executors.newFixedThreadPool(4); // Adjust pool size as needed
    }

    public static synchronized ThreadManager getInstance() {
        if (instance == null) {
            instance = new ThreadManager();
        }
        return instance;
    }

    // Run a task in the background, with optional GUI update
    public void runInBackground(Runnable task, Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                if (onError != null) {
                    onError.accept(e);
                }
            }
        });
    }

    // Run a task in the background and update GUI with the result
    public <T> void runInBackground(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                T result = task.call();
                if (onSuccess != null) {
                    Platform.runLater(() -> onSuccess.accept(result));
                }
            } catch (Exception e) {
                if (onError != null) {
                    Platform.runLater(() -> onError.accept(e));
                }
            }
        });
    }

    // Shutdown the thread pool (call this when your application exits)
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
