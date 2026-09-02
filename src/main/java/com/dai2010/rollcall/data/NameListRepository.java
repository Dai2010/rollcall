package com.dai2010.rollcall.data;

import com.dai2010.rollcall.model.NameList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stores all lists and the default-list selection in a JSON file. */
public final class NameListRepository {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public NameListRepository() {
        this(Path.of(System.getProperty("user.home"), ".rollcall", "lists.json"));
    }

    public NameListRepository(Path file) {
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    public Snapshot load() throws IOException {
        if (!Files.exists(file)) {
            return new Snapshot();
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Snapshot snapshot = gson.fromJson(json, Snapshot.class);
        if (snapshot == null) {
            return new Snapshot();
        }
        snapshot.sanitize();
        return snapshot;
    }

    public void save(Snapshot snapshot) throws IOException {
        snapshot.sanitize();
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, gson.toJson(snapshot), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static final class Snapshot {
        private List<NameList> lists = new ArrayList<>();
        private String defaultListId;

        public List<NameList> getLists() {
            if (lists == null) {
                lists = new ArrayList<>();
            }
            return lists;
        }

        public String getDefaultListId() {
            return defaultListId;
        }

        public void setDefaultListId(String defaultListId) {
            this.defaultListId = defaultListId;
        }

        public void sanitize() {
            getLists().removeIf(list -> list == null);
            for (NameList list : getLists()) {
                if (list.getId() == null || list.getId().isBlank()) {
                    list.setId(UUID.randomUUID().toString());
                }
                if (list.getRemark() == null) {
                    list.setRemark("");
                }
                list.normalizeIds();
            }
            if (defaultListId == null || getLists().stream().noneMatch(list -> list.getId().equals(defaultListId))) {
                defaultListId = getLists().isEmpty() ? null : getLists().get(0).getId();
            }
        }
    }
}
