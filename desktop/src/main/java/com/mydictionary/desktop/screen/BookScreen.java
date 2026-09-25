package com.mydictionary.desktop.screen;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.model.Tag;
import com.mydictionary.desktop.db.SqliteBookRepository;
import com.mydictionary.desktop.db.SqliteDatabase;
import com.mydictionary.desktop.db.SqliteNoteColumnRepository;
import com.mydictionary.desktop.db.SqliteNoteRepository;
import com.mydictionary.desktop.db.SqliteTagRepository;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** ブック画面。ブック内のノート一覧を表形式で表示し、検索・ソートを行う。 */
public class BookScreen {
    private final SceneNavigator navigator;
    private final SqliteDatabase database;
    private final long bookId;

    private final SqliteNoteRepository noteRepository;
    private final SqliteTagRepository tagRepository;
    private final SqliteNoteColumnRepository columnRepository;

    private final BorderPane view = new BorderPane();
    private final TableView<Note> table = new TableView<>();
    private final Label countLabel = new Label();
    private TextField searchField;
    private ComboBox<TagOption> tagFilterCombo;
    private ComboBox<NoteColumn> sortCombo;

    private Book book;
    private List<NoteColumn> columns;
    private Map<Long, Tag> tagById = new HashMap<>();

    public BookScreen(SceneNavigator navigator, SqliteDatabase database, long bookId) {
        this.navigator = navigator;
        this.database = database;
        this.bookId = bookId;
        this.noteRepository = new SqliteNoteRepository(database);
        this.tagRepository = new SqliteTagRepository(database);
        this.columnRepository = new SqliteNoteColumnRepository(database);
        build();
    }

    public BorderPane getView() {
        return view;
    }

    private void build() {
        book = new SqliteBookRepository(database).findById(bookId)
            .orElseThrow(() -> new IllegalStateException("ブックが見つかりません: " + bookId));
        columns = columnRepository.findByBookId(bookId);

        view.setStyle("-fx-font-family: '" + book.getFont().getFamilyName() + "';");

        view.setTop(buildHeader());
        view.setCenter(buildTable());

        loadTagOptions();
        refresh();
    }

    private VBox buildHeader() {
        String textColor = book.getTheme().getTextColorCode();

        Label titleLabel = new Label(book.getTitle());
        titleLabel.getStyleClass().add("screen-title");
        titleLabel.setStyle("-fx-text-fill: " + textColor + ";");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        countLabel.setStyle("-fx-text-fill: " + textColor + ";");

        HBox titleRow = new HBox(16, titleLabel, countLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Button backButton = new Button("戻る");
        backButton.setOnAction(e -> navigator.showBookList(book.getShelfId()));

        Button createNoteButton = new Button("ノートを作成");
        createNoteButton.setOnAction(e -> navigator.showNoteCreate(bookId));

        Button bookOptionsButton = new Button("ブックオプション");
        bookOptionsButton.setOnAction(e -> navigator.showBookOptions(bookId));

        HBox buttonRow = new HBox(8, backButton, createNoteButton, bookOptionsButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("文字列で検索（各項目・本文）");
        searchField.textProperty().addListener((obs, oldValue, newValue) -> refresh());

        tagFilterCombo = new ComboBox<>();
        tagFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TagOption option) {
                if (option == null) {
                    return "";
                }
                String indent = "　".repeat(option.depth);
                return indent + (option.tag == null ? "すべてのタグ" : option.tag.getName());
            }

            @Override
            public TagOption fromString(String s) {
                return null;
            }
        });
        tagFilterCombo.valueProperty().addListener((obs, oldValue, newValue) -> refresh());

        sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll(columns);
        sortCombo.setValue(columns.get(0));
        sortCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(NoteColumn column) {
                return column == null ? "" : column.getName() + "順";
            }

            @Override
            public NoteColumn fromString(String s) {
                return null;
            }
        });
        sortCombo.valueProperty().addListener((obs, oldValue, newValue) -> refresh());

        Label searchLabel = new Label("検索:");
        searchLabel.setStyle("-fx-text-fill: " + textColor + ";");
        Label tagLabel = new Label("タグ:");
        tagLabel.setStyle("-fx-text-fill: " + textColor + ";");
        Label sortLabel = new Label("並び順:");
        sortLabel.setStyle("-fx-text-fill: " + textColor + ";");

        HBox filterRow = new HBox(12,
            searchLabel, searchField,
            tagLabel, tagFilterCombo,
            sortLabel, sortCombo);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(8, titleRow, buttonRow, filterRow);
        header.setPadding(new Insets(16));
        header.setStyle("-fx-background-color: " + book.getTheme().getColorCode() + ";");
        return header;
    }

    @SuppressWarnings("unchecked")
    private TableView<Note> buildTable() {
        List<TableColumn<Note, ?>> tableColumns = new ArrayList<>();
        for (NoteColumn column : columns) {
            TableColumn<Note, String> col = new TableColumn<>(column.getName());
            col.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFieldValue(column.getId())));
            tableColumns.add(col);
        }

        TableColumn<Note, Note> tagsCol = new TableColumn<>("タグ");
        tagsCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        tagsCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Note note, boolean empty) {
                super.updateItem(note, empty);
                if (empty || note == null) {
                    setGraphic(null);
                    return;
                }
                VBox box = new VBox(2);
                for (Long tagId : note.getTagIds()) {
                    box.getChildren().add(new Label(Tag.fullPath(tagId, tagById)));
                }
                setGraphic(box);
            }
        });
        tableColumns.add(tagsCol);

        table.getColumns().addAll(tableColumns);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // ノート一覧の行を、テーマ色を少し明るくした色で敷く。1行ごとに明・暗を交互に切り替え、
        // ノート未作成の空行部分も同じ2色で続ける。ノートが1件も無いときは暗い方の色で全体を塗り、
        // 濃色テーマなら「ノートがありません」を白文字にする。白テーマは色を敷かない（null）。
        String listRowLight = book.getTheme().getListRowColorLightCode();
        String listRowDark = book.getTheme().getListRowColorDarkCode();

        Label placeholder = new Label("ノートがありません");
        if (book.getTheme().isDark()) {
            placeholder.setStyle("-fx-text-fill: white;");
        }
        table.setPlaceholder(placeholder);
        if (listRowDark != null) {
            // ノート0件のときに見える下地。行が並ぶときは各行が上に描画されるので隠れる。
            table.setStyle("-fx-control-inner-background: " + listRowDark + ";");
        }

        table.setRowFactory(tv -> {
            javafx.scene.control.TableRow<Note> row = new javafx.scene.control.TableRow<>() {
                @Override
                protected void updateItem(Note item, boolean empty) {
                    super.updateItem(item, empty);
                    int index = getIndex();
                    if (listRowLight == null || index < 0) {
                        setStyle("");
                    } else {
                        setStyle("-fx-background-color: "
                            + (index % 2 == 0 ? listRowLight : listRowDark) + ";");
                    }
                }
            };
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty() && e.getClickCount() == 1) {
                    navigator.showNote(bookId, row.getItem().getId());
                }
            });
            return row;
        });

        return table;
    }

    private void loadTagOptions() {
        List<Tag> flatTags = tagRepository.findByBookId(bookId);
        tagById = flatTags.stream().collect(Collectors.toMap(Tag::getId, tag -> tag));

        List<Tag> roots = Tag.buildTree(flatTags);
        List<TagOption> options = new ArrayList<>();
        options.add(new TagOption(null, 0));
        collectPreOrder(roots, 0, options);

        tagFilterCombo.setItems(FXCollections.observableArrayList(options));
        tagFilterCombo.setValue(options.get(0));
    }

    private void collectPreOrder(List<Tag> nodes, int depth, List<TagOption> out) {
        for (Tag node : nodes) {
            out.add(new TagOption(node, depth));
            collectPreOrder(node.getChildren(), depth + 1, out);
        }
    }

    private void refresh() {
        TagOption selectedTagOption = tagFilterCombo.getValue();
        List<Note> base = (selectedTagOption != null && selectedTagOption.tag != null)
            ? noteRepository.findByTag(bookId, selectedTagOption.tag.getId())
            : noteRepository.findByBookId(bookId);

        String keyword = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<Note> filtered = keyword.isEmpty() ? base : base.stream()
            .filter(n -> matchesKeyword(n, keyword))
            .collect(Collectors.toList());

        NoteColumn sortColumn = sortCombo.getValue() == null ? columns.get(0) : sortCombo.getValue();
        Collator collator = Collator.getInstance(Locale.JAPANESE);
        filtered.sort(Comparator.comparing(n -> n.getFieldValue(sortColumn.getId()), collator));

        table.setItems(FXCollections.observableArrayList(filtered));
        countLabel.setText(filtered.size() + " 件");
    }

    private boolean matchesKeyword(Note note, String keywordLowerCase) {
        if (containsIgnoreCase(note.getBody(), keywordLowerCase)) {
            return true;
        }
        for (String value : note.getFieldValues().values()) {
            if (containsIgnoreCase(value, keywordLowerCase)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String text, String keywordLowerCase) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(keywordLowerCase);
    }

    /** タグ絞り込みコンボの選択肢。tagがnullの場合は「すべてのタグ」を表す。 */
    private static final class TagOption {
        private final Tag tag;
        private final int depth;

        private TagOption(Tag tag, int depth) {
            this.tag = tag;
            this.depth = depth;
        }
    }
}
