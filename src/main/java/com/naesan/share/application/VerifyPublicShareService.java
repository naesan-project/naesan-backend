package com.naesan.share.application;

import java.time.Clock;
import java.io.InputStream;
import java.util.HexFormat;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.share.application.port.out.PublicShareTokenCodec;
import com.naesan.share.application.port.out.PublicShareVerificationRepository;
import com.naesan.share.domain.PublicShareCapability;

public class VerifyPublicShareService {
    private static final String DOMAIN = "NAESAN_ANCHOR";
    private static final String SNAPSHOT_HASH_ALGORITHM = "SHA-256";
    private static final String COMMITMENT_HASH_ALGORITHM = "KECCAK-256";
    private static final String COMMITMENT_ENCODING = "ABI";

    private final PublicShareTokenCodec tokenCodec;
    private final PublicShareVerificationRepository verificationRepository;
    private final PublicFileMatchVerifier fileMatchVerifier;
    private final Clock clock;

    public VerifyPublicShareService(
            PublicShareTokenCodec tokenCodec,
            PublicShareVerificationRepository verificationRepository,
            PublicFileMatchVerifier fileMatchVerifier,
            Clock clock
    ) {
        this.tokenCodec = Objects.requireNonNull(tokenCodec);
        this.verificationRepository = Objects.requireNonNull(verificationRepository);
        this.fileMatchVerifier = Objects.requireNonNull(fileMatchVerifier);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public PublicPassportVerification verify(String rawToken) {
        PublicShareVerificationSource source = findAvailableSource(rawToken);
        String commitment = HexFormat.of().formatHex(source.commitment());
        return new PublicPassportVerification(
                source.publicShare().capability(),
                source.productName(),
                source.purchasedAt(),
                source.passportStatus().name(),
                PublicTrustStage.from(source.proofState()).name(),
                commitment,
                PublicEvmAnchorVerification.from(source.evmEvidence()),
                verificationMaterial(source, commitment)
        );
    }

    private PublicShareVerificationSource findAvailableSource(String rawToken) {
        byte[] tokenHash = tokenCodec.hash(rawToken)
                .orElseThrow(PublicShareException::notFound);
        return verificationRepository.findByTokenHash(tokenHash)
                .filter(source -> source.publicShare().isAvailableAt(clock.instant()))
                .orElseThrow(PublicShareException::notFound);
    }

    private PublicVerificationMaterial verificationMaterial(
            PublicShareVerificationSource source,
            String commitment
    ) {
        if (source.publicShare().capability() != PublicShareCapability.FILE_MATCH) {
            return null;
        }
        return new PublicVerificationMaterial(
                source.snapshotDigest(),
                HexFormat.of().formatHex(source.anchorSalt()),
                commitment,
                source.snapshotSchemaVersion(),
                source.commitmentSchemaVersion(),
                DOMAIN,
                SNAPSHOT_HASH_ALGORITHM,
                COMMITMENT_HASH_ALGORITHM,
                COMMITMENT_ENCODING
        );
    }

    public PublicFileMatchResult match(
            String rawToken,
            InputStream candidateFile,
            String declaredMediaType
    ) {
        PublicShareVerificationSource source = findAvailableSource(rawToken);
        if (source.publicShare().capability() != PublicShareCapability.FILE_MATCH) {
            throw PublicShareException.notFound();
        }
        boolean matched = fileMatchVerifier.matches(
                candidateFile,
                declaredMediaType,
                source
        );
        return new PublicFileMatchResult(
                matched,
                PublicTrustStage.from(source.proofState()).name(),
                HexFormat.of().formatHex(source.commitment())
        );
    }
}
