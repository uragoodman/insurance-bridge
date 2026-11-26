package com.itheima.bolee.insurance.web.ai;

import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermSourceVO;
import com.itheima.bolee.insurance.service.ai.terms.InsuranceTermSourceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;

/** Public read-only download for published product terms displayed in the customer purchase flow. */
@RestController
@RequestMapping("/insurance-terms")
@ConditionalOnProperty(prefix = "insurance.terms.storage", name = "enabled", havingValue = "true")
public class InsuranceTermDocumentController {

    private final InsuranceTermSourceService sourceService;

    public InsuranceTermDocumentController(InsuranceTermSourceService sourceService) {
        this.sourceService = sourceService;
    }

    @GetMapping("/products/{productId}/versions/{profileVersionId}/sources/{sourceId}/document.pdf")
    public ResponseEntity<StreamingResponseBody> downloadPublishedPdf(
            @PathVariable Long productId, @PathVariable Long profileVersionId, @PathVariable Long sourceId) {
        try {
            InsuranceTermSourceVO source = sourceService.getPublished(sourceId);
            if (!productId.equals(source.getInsuranceId()) || !profileVersionId.equals(source.getProfileVersionId())) {
                return ResponseEntity.notFound().build();
            }
            InputStream content = sourceService.downloadPublishedPdf(sourceId, productId, profileVersionId);
            StreamingResponseBody body = output -> {
                try (InputStream input = content) {
                    input.transferTo(output);
                }
            };
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"insurance-terms.pdf\"");
            headers.setCacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic());
            headers.set("X-Content-Type-Options", "nosniff");
            return new ResponseEntity<StreamingResponseBody>(body, headers, HttpStatus.OK);
        } catch (ResponseStatusException exception) {
            if (exception.getStatus() == HttpStatus.NOT_FOUND) return ResponseEntity.notFound().build();
            throw exception;
        }
    }
}
