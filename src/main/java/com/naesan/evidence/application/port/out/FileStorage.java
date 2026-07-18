package com.naesan.evidence.application.port.out;

import java.io.InputStream;

import com.naesan.evidence.domain.StorageKey;

public interface FileStorage {

    StorageKey storeTemporary(InputStream content);

    InputStream open(StorageKey key);

    void delete(StorageKey key);
}
