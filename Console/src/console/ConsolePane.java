/*
 * This work is licensed under a Creative Commons Attribution-NonCommercial 3.0 United States License.
 * For more information go to http://creativecommons.org/licenses/by-nc/3.0/us/
 */
package console;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

/**
 *
 * @author gmein, stolen from StackOverflow member skiwi
 */
public class ConsolePane extends VBox {

    private final TextArea textArea = new TextArea();
    private final TextField textField = new TextField();

    private final List<String> history = new ArrayList<>();
    private int historyPointer = 0;
    private int msgCounter = 0;

    private Consumer<String> onMessageReceivedHandler;

    public ConsolePane() {

        textArea.setEditable(false);
        textArea.setStyle("-fx-control-inner-background: black;");
        textField.setStyle("-fx-background-color: black; -fx-text-fill: red;");
        this.setStyle("-fx-background-color: black;");
        textArea.setPrefRowCount(3);

        this.getChildren().addAll(textArea, textField);

        this.textField.addEventHandler(KeyEvent.KEY_RELEASED, keyEvent -> {
            switch (keyEvent.getCode()) {
                case ENTER:
                    String text = textField.getText();
                    textArea.appendText(text + System.lineSeparator());
                    history.add(text);
                    historyPointer++;
                    if (onMessageReceivedHandler != null) {
                        onMessageReceivedHandler.accept(text);
                    }
                    textField.clear();
                    break;
                case UP:
                    if (historyPointer == 0) {
                        break;
                    }
                    historyPointer--;
                    runSafe(() -> {
                        textField.setText(history.get(historyPointer));
                        textField.selectAll();
                    });
                    break;
                case DOWN:
                    if (historyPointer == history.size() - 1) {
                        break;
                    }
                    historyPointer++;
                    runSafe(() -> {
                        textField.setText(history.get(historyPointer));
                        textField.selectAll();
                    });
                    break;
                default:
                    break;
            }
        });

        this.setPadding(new Insets(4, 4, 4, 4));

    }

    @Override
    public void requestFocus() {
        super.requestFocus();
        textField.requestFocus();
    }

    private void setOnMessageReceivedHandler(final Consumer<String> onMessageReceivedHandler) {
        this.onMessageReceivedHandler = onMessageReceivedHandler;
    }

    public void clear() {
        runSafe(() -> textArea.clear());
        history.clear();
    }

    public void print(final String text) {
        Objects.requireNonNull(text, "text");
        runSafe(() -> textArea.appendText(text));
    }

    public void println(final String text) {
        if (++msgCounter > 100) {
            msgCounter = 0;
            clear();
        }
        Objects.requireNonNull(text, "text");
        runSafe(() -> textArea.appendText(text + System.lineSeparator()));
    }

    public void println() {
        runSafe(() -> textArea.appendText(System.lineSeparator()));
    }

    public static void runSafe(final Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

}
