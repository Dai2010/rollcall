package com.dai2010.rollcall.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A named collection of people that can be selected as the default list. */
public final class NameList {
    private String id;
    private String remark;
    private List<Person> people;

    public NameList() {
        this(UUID.randomUUID().toString(), "", new ArrayList<>());
    }

    public NameList(String remark, List<Person> people) {
        this(UUID.randomUUID().toString(), remark, people);
    }

    public NameList(String id, String remark, List<Person> people) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.remark = remark == null ? "" : remark.trim();
        this.people = people == null ? new ArrayList<>() : new ArrayList<>(people);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark == null ? "" : remark.trim();
    }

    public List<Person> getPeople() {
        if (people == null) {
            people = new ArrayList<>();
        }
        return people;
    }

    public void setPeople(List<Person> people) {
        this.people = people == null ? new ArrayList<>() : new ArrayList<>(people);
    }

    public void addPerson(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            return;
        }
        int nextId = getPeople().stream().mapToInt(Person::getId).max().orElse(0) + 1;
        getPeople().add(new Person(nextId, normalized));
    }

    public void normalizeIds() {
        var used = new java.util.HashSet<Integer>();
        int next = 1;
        for (Person person : getPeople()) {
            if (person == null) {
                continue;
            }
            if (person.getName() == null) {
                person.setName("");
            }
            if (person.getId() <= 0 || !used.add(person.getId())) {
                while (used.contains(next)) {
                    next++;
                }
                person.setId(next);
                used.add(next);
            }
            next = Math.max(next, person.getId() + 1);
        }
        getPeople().removeIf(person -> person == null || person.getName().isBlank());
    }
}
