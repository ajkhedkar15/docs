package com.ss.corp.map.api.model;

/**
 * A user: owner, lock holder, last modifier or tagger.
 *
 * <p>Owners are stored as names today, so id and name hold the same text until real user ids exist.
 */
public record Person(String id, String name) {

    public static Person ofName(String name) {
        return name == null ? null : new Person(name, name);
    }
}
