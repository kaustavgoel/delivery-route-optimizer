package com.deliveryoptimizer.ui;

import com.deliveryoptimizer.services.AppLogger;
import com.deliveryoptimizer.services.AppLogger.LogEntry;
import com.deliveryoptimizer.services.AppLogger.LogLevel;
import com.deliveryoptimizer.services.AppLogger.LogListener;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class LiveLogsView extends VBox implements LogListener {

    private final ObservableList<LogEntry> entries = FXCollections.observableArrayList();
    private final ListView<LogEntry> listView = new ListView<>(entries);
    private final ComboBox<String> levelFilter = new ComboBox<>(FXCollections.observableArrayList("ALL", "INFO", "SUCCESS", "WARNING", "ERROR"));
    private final TextField searchField = new TextField();

    public LiveLogsView() {
        setSpacing(10);
        setPadding(new Insets(14));
        getStyleClass().add("card");

        Label title = new Label("Live Execution Logs");
        title.getStyleClass().add("section-title");

        levelFilter.setValue("ALL");
        levelFilter.setOnAction(e -> reload());

        searchField.setPromptText("Filter logs by algorithm or message...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((o, a, b) -> reload());

        Button clearBtn = new Button("Clear Logs");
        clearBtn.getStyleClass().add("btn-danger");
        clearBtn.setOnAction(e -> { AppLogger.getInstance().clear(); entries.clear(); });

        HBox toolbar = new HBox(10, title, levelFilter, searchField, clearBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        listView.setStyle("-fx-background-color: #0B1224; -fx-border-color: #23355C; -fx-border-radius: 8;");
        VBox.setVgrow(listView, Priority.ALWAYS);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(LogEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    Label t = new Label("[" + item.getFormattedTimestamp() + "]");
                    t.setStyle("-fx-text-fill: #6F84AC; -fx-font-family: 'Monospaced';");
                    Label lvl = new Label(item.getLevel().name());
                    lvl.setStyle("-fx-text-fill: " + item.getLevel().getColorHex() + "; -fx-font-weight: bold; -fx-min-width: 68;");
                    Label src = new Label("[" + item.getSource() + "]");
                    src.setStyle("-fx-text-fill: #B6C9EC; -fx-font-weight: bold;");
                    Label msg = new Label(item.getMessage());
                    msg.setStyle("-fx-text-fill: #EEF3FF;");
                    msg.setWrapText(true);
                    HBox row = new HBox(8, t, lvl, src, msg);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(row);
                    setStyle("-fx-background-color: #0E172E; -fx-padding: 4 8 4 8;");
                }
            }
        });

        getChildren().addAll(toolbar, listView);
        reload();
        AppLogger.getInstance().addListener(this);
    }

    @Override
    public void onLogPublished(LogEntry entry) {
        Runnable add = () -> {
            if (matches(entry)) {
                entries.add(entry);
                if (entries.size() > 800) entries.remove(0);
                listView.scrollTo(entries.size() - 1);
            }
        };
        if (Platform.isFxApplicationThread()) add.run();
        else Platform.runLater(add);
    }

    private void reload() {
        entries.clear();
        for (LogEntry e : AppLogger.getInstance().getHistorySnapshot()) if (matches(e)) entries.add(e);
        if (!entries.isEmpty()) listView.scrollTo(entries.size() - 1);
    }

    private boolean matches(LogEntry e) {
        String sel = levelFilter.getValue();
        if (sel != null && !"ALL".equals(sel) && e.getLevel() != LogLevel.valueOf(sel)) return false;
        String q = searchField.getText();
        if (q != null && !q.isBlank()) {
            String lo = q.toLowerCase();
            return e.getMessage().toLowerCase().contains(lo) || e.getSource().toLowerCase().contains(lo);
        }
        return true;
    }
}
