package com.dai2010.rollcall.data;

import com.dai2010.rollcall.model.NameList;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NameListRepositoryTest {
    @Test
    void savesAndLoadsSnapshotAtomically() throws Exception {
        Path directory = Files.createTempDirectory("rollcall-repository");
        Path file = directory.resolve("nested").resolve("lists.json");
        NameListRepository repository = new NameListRepository(file);
        NameList list = new NameList("课堂名单", List.of());
        list.addPerson("张三");
        NameListRepository.Snapshot snapshot = new NameListRepository.Snapshot();
        snapshot.getLists().add(list);
        snapshot.setDefaultListId(list.getId());

        repository.save(snapshot);
        NameListRepository.Snapshot loaded = repository.load();

        assertEquals("课堂名单", loaded.getLists().get(0).getRemark());
        assertEquals("张三", loaded.getLists().get(0).getPeople().get(0).getName());
        assertNotNull(loaded.getDefaultListId());
    }
}
