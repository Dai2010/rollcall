package com.dai2010.rollcall.service;

import com.dai2010.rollcall.model.Person;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses manual input and common text/JSON name-list formats. */
public final class NameParser {
    private static final Pattern QUOTED = Pattern.compile("[\\\"“”'‘’]([^\\\"“”'‘’]+)[\\\"“”'‘’]");
    private static final Pattern ID_FIRST = Pattern.compile("^\\s*(\\d+)\\s*(?:[,，;；\\t|:]\\s*|\\s+)(.+?)\\s*$");
    private static final Pattern NAME_FIRST = Pattern.compile("^\\s*(.+?)\\s*[,，;；\\t|:]\\s*(\\d+)\\s*$");

    public List<Person> parseFile(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        String trimmed = content.stripLeading();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return normalize(parseJson(JsonParser.parseString(trimmed)));
            } catch (RuntimeException ignored) {
                // A malformed JSON file is still useful as plain text when possible.
            }
        }
        return normalize(parseText(content));
    }

    public List<Person> parseManual(String input) {
        return normalize(namesWithoutIds(input));
    }

    public List<Person> parseText(String text) {
        List<Person> entries = new ArrayList<>();
        if (text == null) {
            return entries;
        }
        for (String rawLine : text.replace("\uFEFF", "").split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || isHeader(line)) {
                continue;
            }
            Matcher idFirst = ID_FIRST.matcher(line);
            if (idFirst.matches()) {
                int id = parseId(idFirst.group(1));
                if (id > 0) {
                    entries.add(new Person(id, cleanName(idFirst.group(2))));
                    continue;
                }
            }
            Matcher nameFirst = NAME_FIRST.matcher(line);
            if (nameFirst.matches()) {
                int id = parseId(nameFirst.group(2));
                if (id > 0) {
                    entries.add(new Person(id, cleanName(nameFirst.group(1))));
                    continue;
                }
            }
            entries.addAll(namesWithoutIds(line));
        }
        return normalize(entries);
    }

    private List<Person> parseJson(JsonElement element) {
        List<Person> entries = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return entries;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                entries.addAll(parseJson(child));
            }
            return entries;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                entries.addAll(namesWithoutIds(primitive.getAsString()));
            }
            return entries;
        }
        JsonObject object = element.getAsJsonObject();
        String name = firstString(object, "name", "姓名", "displayName", "title");
        if (name != null && !name.isBlank()) {
            entries.add(new Person(firstId(object), cleanName(name)));
            return entries;
        }
        for (String key : List.of("people", "members", "names", "list", "名单", "data")) {
            if (object.has(key)) {
                entries.addAll(parseJson(object.get(key)));
            }
        }
        if (entries.isEmpty()) {
            for (var property : object.entrySet()) {
                if (property.getValue().isJsonPrimitive() && property.getValue().getAsJsonPrimitive().isString()) {
                    int id = parseId(property.getKey());
                    entries.add(new Person(id, cleanName(property.getValue().getAsString())));
                }
            }
        }
        return entries;
    }

    private static List<Person> namesWithoutIds(String input) {
        List<Person> entries = new ArrayList<>();
        if (input == null) {
            return entries;
        }
        Matcher matcher = QUOTED.matcher(input);
        boolean foundQuoted = false;
        while (matcher.find()) {
            foundQuoted = true;
            String name = cleanName(matcher.group(1));
            if (!name.isBlank()) {
                entries.add(new Person(0, name));
            }
        }
        if (foundQuoted) {
            return entries;
        }
        String normalized = input.replaceAll("[\\[\\]{}()]", " ");
        for (String token : normalized.split("[,，;；、|\\s]+")) {
            String name = cleanName(token);
            if (!name.isBlank()) {
                entries.add(new Person(0, name));
            }
        }
        return entries;
    }

    private static List<Person> normalize(List<Person> entries) {
        List<Person> result = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        int nextId = 1;
        for (Person entry : entries) {
            if (entry == null || entry.getName() == null || entry.getName().isBlank()) {
                continue;
            }
            int id = entry.getId();
            if (id <= 0 || used.contains(id)) {
                while (used.contains(nextId)) {
                    nextId++;
                }
                id = nextId;
            }
            used.add(id);
            nextId = Math.max(nextId, id + 1);
            result.add(new Person(id, cleanName(entry.getName())));
        }
        return result;
    }

    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                return object.get(key).getAsString();
            }
        }
        return null;
    }

    private static int firstId(JsonObject object) {
        for (String key : List.of("id", "number", "index", "编号", "no")) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                int id = parseId(object.get(key).getAsString());
                if (id > 0) {
                    return id;
                }
            }
        }
        return 0;
    }

    private static int parseId(String value) {
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 && parsed <= Integer.MAX_VALUE ? (int) parsed : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String cleanName(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("^[\\\"“”'‘’]+|[\\\"“”'‘’]+$", "").trim();
    }

    private static boolean isHeader(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return (normalized.contains("姓名") || normalized.contains("name"))
                && (normalized.contains("编号") || normalized.contains("id") || normalized.contains("number"));
    }
}
