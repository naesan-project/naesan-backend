package com.naesan.share.adapter.in.web;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.naesan.share.application.PublicFileMatchResult;
import com.naesan.share.application.PublicPassportVerification;
import com.naesan.share.application.PublicShareException;
import com.naesan.share.application.VerifyPublicShareService;
import com.naesan.share.domain.PublicShareCapability;

@RestController
@RequestMapping("/api/public/passport-verification")
public class PublicVerificationApiController {
    public static final String SHARE_TOKEN_HEADER = "X-Public-Share-Token";
    private static final String REFERRER_POLICY_HEADER = "Referrer-Policy";
    private static final String NO_REFERRER = "no-referrer";

    private final VerifyPublicShareService verifyPublicShareService;

    public PublicVerificationApiController(
            VerifyPublicShareService verifyPublicShareService
    ) {
        this.verifyPublicShareService = verifyPublicShareService;
    }

    @GetMapping
    public ResponseEntity<PublicVerificationResponse> verify(
            @RequestHeader(name = SHARE_TOKEN_HEADER, required = false)
            String rawToken
    ) {
        PublicPassportVerification verification =
                verifyPublicShareService.verify(rawToken);
        return noStoreResponse(toResponse(verification));
    }

    private PublicVerificationResponse toResponse(
            PublicPassportVerification verification
    ) {
        if (verification.capability() == PublicShareCapability.FILE_MATCH) {
            return PublicFileMatchVerificationResponse.from(verification);
        }
        return PublicSummaryVerificationResponse.from(verification);
    }

    private ResponseEntity<PublicVerificationResponse> noStoreResponse(
            PublicVerificationResponse response
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                .body(response);
    }

    @PostMapping("/file-match")
    public ResponseEntity<PublicFileMatchResponse> match(
            @RequestHeader(name = SHARE_TOKEN_HEADER, required = false)
            String rawToken,
            @RequestPart("file") MultipartFile file
    ) {
        PublicFileMatchResult result = verifyPublicShareService.match(
                rawToken,
                fileContent(file),
                file.getContentType()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(REFERRER_POLICY_HEADER, NO_REFERRER)
                .body(PublicFileMatchResponse.from(result));
    }

    private InputStream fileContent(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException exception) {
            throw PublicShareException.fileReadFailed(exception);
        }
    }
}
