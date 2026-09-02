package com.dai2010.rollcall.service;

import com.dai2010.rollcall.model.Person;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameParserTest {
    private final NameParser parser = new NameParser();

    @Test
    void parsesQuotedManualNamesWithFullWidthSeparators() {
        List<Person> people = parser.parseManual("“张三”；“李四”‘王五’");

        assertEquals(List.of("张三", "李四", "王五"), people.stream().map(Person::getName).toList());
        assertEquals(List.of(1, 2, 3), people.stream().map(Person::getId).toList());
    }

    @Test
    void assignsIdsToTextEntriesAndKeepsExistingIds() {
        List<Person> people = parser.parseText("7,张三\n李四\n9,王五\n9,赵六");

        assertEquals(List.of(7, 8, 9, 10), people.stream().map(Person::getId).toList());
        assertEquals(List.of("张三", "李四", "王五", "赵六"), people.stream().map(Person::getName).toList());
    }

    @Test
    void parsesJsonObjectsAndMissingIds() throws Exception {
        Path file = Files.createTempFile("rollcall", ".json");
        Files.writeString(file, "[{\"id\":4,\"name\":\"张三\"},{\"name\":\"李四\"}]");

        List<Person> people = parser.parseFile(file);

        assertEquals(List.of(4, 5), people.stream().map(Person::getId).toList());
        assertTrue(people.stream().allMatch(person -> !person.getName().isBlank()));
        Files.deleteIfExists(file);
    }
}
