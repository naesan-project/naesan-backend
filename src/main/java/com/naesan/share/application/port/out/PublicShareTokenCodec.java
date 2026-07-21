package com.naesan.share.application.port.out;

import java.util.Optional;

public interface PublicShareTokenCodec {

    GeneratedPublicShareToken generate();

    Optional<byte[]> hash(String rawToken);
}
