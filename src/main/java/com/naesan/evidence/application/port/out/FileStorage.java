package com.naesan.evidence.application.port.out;

import java.io.InputStream;
import java.util.List;

import com.naesan.evidence.domain.StorageKey;

public interface FileStorage {

    StorageKey storeTemporary(InputStream content);

    InputStream open(StorageKey key);

    StorageKey promote(StorageKey temporaryKey);

    List<StoredObjectMetadata> listTemporaryObjects();

    List<StoredObjectMetadata> listPermanentObjects();

    void delete(StorageKey key);
}
