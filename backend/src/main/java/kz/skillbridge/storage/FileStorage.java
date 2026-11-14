package kz.skillbridge.storage;

/** Abstraction over document storage; local disk for MVP, S3-compatible later (spec §7). */
public interface FileStorage {

    /** Stores data and returns its key. */
    String save(String key, byte[] data);

    byte[] load(String key);

    void delete(String key);

    /** Removes everything under a prefix, e.g. all files of one user. */
    void deletePrefix(String prefix);
}
