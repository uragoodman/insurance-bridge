package com.itheima.bolee.insurance.service.ai.terms;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.itheima.bolee.insurance.config.terms.InsuranceTermsStorageProperties;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermSourcePageVO;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermProductVersionVO;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermSourceVO;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermSourceAuditVO;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermSourceDO;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceRepository;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "insurance.terms.storage", name = "enabled", havingValue = "true")
public class InsuranceTermSourceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InsuranceTermSourceService.class);

    private static final Set<String> ALLOWED_SOURCE_TYPES = new HashSet<String>(Arrays.asList(
            "OFFICIAL_PRODUCT_TERM", "INDUSTRY_SAMPLE", "REGULATORY_REFERENCE", "SYNTHETIC"));
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<String>(Arrays.asList(
            ".pdf", ".doc", ".docx", ".md", ".txt"));
    private static final Set<String> ALLOWED_STATUSES = new HashSet<String>(Arrays.asList(
            "DRAFT", "PENDING_REVIEW", "APPROVED", "REJECTED", "PUBLISHED", "WITHDRAWN", "DELETING"));

    private final InsuranceTermSourceRepository repository;
    private final InsuranceTermObjectStorage objectStorage;
    private final InsuranceTermsStorageProperties properties;
    private final IdentifierGenerator identifierGenerator;
    private final InsuranceTermIndexTaskService indexTaskService;
    private final InsuranceTermSourceAuditRepository auditRepository;
    private final InsuranceTermSourceRegistrationService registrationService;
    private final InsuranceTermSourceContentReader contentReader;
    private final InsuranceTermSourceDeletionLifecycleService deletionLifecycle;
    private final TransactionTemplate transactionTemplate;

    public InsuranceTermSourceService(InsuranceTermSourceRepository repository,
                                      InsuranceTermObjectStorage objectStorage,
                                      InsuranceTermsStorageProperties properties,
                                      IdentifierGenerator identifierGenerator,
                                      InsuranceTermIndexTaskService indexTaskService,
                                      InsuranceTermSourceAuditRepository auditRepository,
                                      InsuranceTermSourceRegistrationService registrationService,
                                      InsuranceTermSourceContentReader contentReader,
                                      InsuranceTermSourceDeletionLifecycleService deletionLifecycle,
                                      PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectStorage = objectStorage;
        this.properties = properties;
        this.identifierGenerator = identifierGenerator;
        this.indexTaskService = indexTaskService;
        this.auditRepository = auditRepository;
        this.registrationService = registrationService;
        this.contentReader = contentReader;
        this.deletionLifecycle = deletionLifecycle;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public InsuranceTermSourceVO upload(Long insuranceId, Long profileVersionId, String sourceType,
                                        String title, String sourceUri, LocalDate effectiveFrom,
                                        LocalDate effectiveTo, MultipartFile file, Long uploadedBy) {
        validateRequest(insuranceId, profileVersionId, sourceType, title, sourceUri,
                effectiveFrom, effectiveTo, file, uploadedBy);
        Integer productVersion = repository.findProductVersion(insuranceId, profileVersionId);
        if (productVersion == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Product version does not belong to the selected product");
        }

        String originalFileName = safeFileName(file.getOriginalFilename());
        String extension = extensionOf(originalFileName);
        try (InputStream signatureStream = file.getInputStream()) {
            validateFileSignature(extension, signatureStream);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded source document");
        }

        String digest = sha256(file);
        InsuranceTermSourceDO source = new InsuranceTermSourceDO();
        source.setId(((Number) identifierGenerator.nextId(source)).longValue());
        source.setInsuranceId(insuranceId);
        source.setProfileVersionId(profileVersionId);
        source.setProductVersion(productVersion);
        source.setSourceType(sourceType.trim().toUpperCase(Locale.ROOT));
        source.setTitle(title.trim());
        source.setSourceUri(normalizeSourceUri(sourceUri));
        source.setOriginalFileName(originalFileName);
        source.setContentType(contentTypeFor(extension));
        source.setFileSize(file.getSize());
        source.setSha256(digest);
        source.setStorageProvider("MINIO");
        source.setBucketName(properties.getBucket());
        source.setObjectKey(buildObjectKey(insuranceId, profileVersionId, source.getId(), extension));
        source.setStatus("DRAFT");
        source.setEffectiveFrom(effectiveFrom);
        source.setEffectiveTo(effectiveTo);
        source.setUploadedBy(uploadedBy);
        source.setCreateBy(uploadedBy);
        source.setUpdateBy(uploadedBy);
        source.setCreateTime(LocalDateTime.now());
        source.setUpdateTime(source.getCreateTime());

        try (InputStream uploadStream = file.getInputStream()) {
            objectStorage.put(source.getObjectKey(), uploadStream, source.getFileSize(),
                    source.getContentType(), source.getSha256());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded source document");
        }
        try {
            registrationService.register(source);
        } catch (RuntimeException exception) {
            try {
                objectStorage.delete(source.getObjectKey());
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
                LOGGER.error("Uploaded insurance term object could not be compensated after source registration failed; "
                        + "manual reconciliation is required, objectKey={}", source.getObjectKey(), cleanupException);
            }
            throw exception;
        }
        return toView(source);
    }

    public InsuranceTermSourcePageVO list(Long insuranceId, Long profileVersionId, String status,
                                          long page, int pageSize) {
        if (page < 1 || page > 1000000 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be positive and pageSize must be 1..100");
        }
        String normalizedStatus = null;
        if (StringUtils.hasText(status)) {
            normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported source status");
            }
        }
        long offset = Math.multiplyExact(page - 1, pageSize);
        long total = repository.count(insuranceId, profileVersionId, normalizedStatus);
        List<InsuranceTermSourceVO> records = repository.list(insuranceId, profileVersionId,
                        normalizedStatus, pageSize, offset).stream()
                .map(this::toView).collect(Collectors.toList());
        return new InsuranceTermSourcePageVO(page, pageSize, total, records);
    }

    public List<InsuranceTermProductVersionVO> listProductVersionsForAdmin(Long insuranceId) {
        if (insuranceId == null || insuranceId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid productId is required");
        }
        return repository.listProductVersionsForAdmin(insuranceId);
    }

    public InsuranceTermSourceVO get(Long id) { return toView(requireSource(id)); }

    public InputStream download(Long id) {
        InsuranceTermSourceDO source = requireSource(id);
        try {
            return contentReader.openVerified(source);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not retrieve source document");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long id, String title, String sourceUri, LocalDate effectiveFrom,
                            LocalDate effectiveTo, Long userId) {
        InsuranceTermSourceDO source = requireSource(id);
        if (!StringUtils.hasText(title) || title.trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A title of at most 255 characters is required");
        }
        String normalizedUri = normalizeSourceUri(sourceUri);
        validateEffectiveDates(effectiveFrom, effectiveTo);
        if (!repository.updateDraft(id, title.trim(), normalizedUri, effectiveFrom, effectiveTo,
                userId, LocalDateTime.now())) {
            throw stateConflict(id, "Only draft or rejected sources can be edited");
        }
        auditRepository.append(id, "METADATA_UPDATED", source.getStatus(), source.getStatus(), userId,
                "Source metadata updated");
    }

    @Transactional(rollbackFor = Exception.class)
    public InsuranceTermSourceVO submitForReview(Long id, Long userId) {
        InsuranceTermSourceDO source = requireSource(id);
        if (source.getFileSize() == null || source.getFileSize() <= 0) {
            throw stateConflict(id, "A source document is required before review");
        }
        if (!repository.submitForReview(id, userId, LocalDateTime.now())) {
            throw stateConflict(id, "Only draft or rejected sources can be submitted for review");
        }
        auditRepository.append(id, "SUBMITTED_FOR_REVIEW", source.getStatus(), "PENDING_REVIEW", userId, null);
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public InsuranceTermSourceVO review(Long id, boolean approved, String note, Long userId) {
        InsuranceTermSourceDO source = requireSource(id);
        if (userId != null && (userId.equals(source.getUploadedBy()) || userId.equals(source.getSubmittedBy()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The uploader or submitter cannot review the same source");
        }
        if (note != null && note.trim().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review note exceeds 1000 characters");
        }
        String normalizedNote = StringUtils.hasText(note) ? note.trim() : null;
        if (!approved && normalizedNote == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rejection reason is required");
        }
        if (!repository.review(id, approved ? "APPROVED" : "REJECTED", normalizedNote,
                userId, LocalDateTime.now())) {
            throw stateConflict(id, "Only pending review sources can be reviewed");
        }
        auditRepository.append(id, approved ? "REVIEW_APPROVED" : "REVIEW_REJECTED",
                "PENDING_REVIEW", approved ? "APPROVED" : "REJECTED", userId, normalizedNote);
        return get(id);
    }

    public InsuranceTermSourceVO publish(Long id, Long userId) {
        InsuranceTermSourceDO source = requireSource(id);
        if (!isRetrievableSourceType(source.getSourceType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This source type is not enabled for Agent retrieval");
        }
        validateEffectiveDates(source.getEffectiveFrom(), source.getEffectiveTo());
        if (source.getEffectiveTo() != null && source.getEffectiveTo().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An expired source cannot be published");
        }
        try {
            contentReader.verify(source);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stored source is unavailable or corrupt");
        }
        return transactionTemplate.execute(status -> {
            if (!repository.publish(id, userId, LocalDateTime.now())) {
                throw stateConflict(id, "Only approved sources can be published");
            }
            source.setStatus("PUBLISHING");
            indexTaskService.enqueue(source, "INDEX");
            auditRepository.append(id, "PUBLISH_REQUESTED", "APPROVED", "PUBLISHING", userId, null);
            return get(id);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public InsuranceTermSourceVO withdraw(Long id, String reason, Long userId) {
        InsuranceTermSourceDO source = requireSource(id);
        if (!StringUtils.hasText(reason) || reason.trim().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A withdrawal reason of at most 1000 characters is required");
        }
        if (!repository.withdraw(id, reason.trim(), userId, LocalDateTime.now())) {
            throw stateConflict(id, "Only published sources can be withdrawn");
        }
        source.setStatus("WITHDRAWN");
        indexTaskService.enqueue(source, "REMOVE");
        auditRepository.append(id, "WITHDRAWN", "PUBLISHED", "WITHDRAWN", userId, reason.trim());
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public InsuranceTermSourceVO retryIndexSync(Long id, Long userId) {
        InsuranceTermSourceDO source = requireSource(id);
        if (!repository.retryIndexSync(id, userId, LocalDateTime.now())) {
            throw stateConflict(id, "Only a failed indexing or removal task can be retried");
        }
        indexTaskService.enqueue(source, "PUBLISHING".equals(source.getStatus()) ? "INDEX" : "REMOVE");
        auditRepository.append(id, "INDEX_SYNC_RETRY", source.getStatus(), source.getStatus(), userId, null);
        return get(id);
    }

    public void deleteDraft(Long id, Long userId) {
        InsuranceTermSourceDO source = requireSource(id);
        if (!"DRAFT".equals(source.getStatus()) && !"REJECTED".equals(source.getStatus())
                && !"DELETING".equals(source.getStatus())) {
            throw stateConflict(id, "Only draft or rejected sources can be deleted");
        }
        if (!"DELETING".equals(source.getStatus())
                && !deletionLifecycle.reserve(id, source.getStatus(), userId)) {
            throw stateConflict(id, "Source state changed before deletion; retry the request");
        }
        if (!deleteReservedSource(id, userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not remove source document");
        }
    }

    public int retryReservedDeletions(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        int completed = 0;
        for (InsuranceTermSourceDO source : repository.findDeleting(boundedLimit)) {
            if (deleteReservedSource(source.getId(), source.getUpdateBy())) completed++;
        }
        return completed;
    }

    private boolean deleteReservedSource(Long id, Long actorId) {
        InsuranceTermSourceDO source = repository.findById(id);
        if (source == null || !"DELETING".equals(source.getStatus())) return false;
        try {
            objectStorage.delete(source.getObjectKey());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not remove reserved insurance term source object; deletion remains retryable, sourceId={}", id,
                    exception);
            return false;
        }
        try {
            return deletionLifecycle.finish(id, actorId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not finalize insurance term source deletion; object removal will be retried, sourceId={}",
                    id, exception);
            return false;
        }
    }

    public List<InsuranceTermSourceAuditVO> listAudit(Long sourceId) {
        if (repository.findById(sourceId) == null && !auditRepository.hasEvents(sourceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Insurance source was not found");
        }
        return auditRepository.list(sourceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int withdrawExpiredSources(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        int changed = 0;
        LocalDateTime now = LocalDateTime.now();
        for (InsuranceTermSourceDO source : repository.findSourcesNeedingExpiryCleanup(now, boundedLimit)) {
            if (repository.withdrawExpired(source.getId(), now)) {
                String previousStatus = source.getStatus();
                source.setStatus("WITHDRAWN");
                indexTaskService.enqueue(source, "REMOVE");
                auditRepository.append(source.getId(), "EXPIRED", previousStatus, "WITHDRAWN", null,
                        "Source or product version is no longer effective");
                changed++;
            }
        }
        return changed;
    }

    public List<InsuranceTermSourceVO> listActivePublished(Long insuranceId, Long profileVersionId) {
        if (insuranceId == null || insuranceId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product ID is required");
        }
        if (profileVersionId != null && repository.findProductVersion(insuranceId, profileVersionId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product version does not belong to the selected product");
        }
        List<InsuranceTermSourceDO> sources = repository.listActivePublished(
                insuranceId, profileVersionId, properties.isSyntheticSourcesEnabled());
        if (profileVersionId == null && sources.stream().map(InsuranceTermSourceDO::getProfileVersionId)
                .distinct().limit(2).count() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Multiple active product-term versions exist; select a profile version before retrieval");
        }
        return sources.stream()
                .filter(source -> isRetrievableSourceType(source.getSourceType()))
                .map(this::toView).collect(Collectors.toList());
    }

    public List<InsuranceTermSourceVO> listActivePublishedForProducts(List<Long> insuranceIds) {
        if (insuranceIds == null || insuranceIds.isEmpty()) return java.util.Collections.emptyList();
        return repository.listActivePublishedForProducts(insuranceIds, properties.isSyntheticSourcesEnabled())
                .stream().filter(source -> isRetrievableSourceType(source.getSourceType()))
                .filter(this::hasPublishedPdfRendition)
                .map(this::toView).collect(Collectors.toList());
    }

    public List<InsuranceTermProductVersionVO> listActivePublishedVersions(Long insuranceId) {
        if (insuranceId == null || insuranceId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product ID is required");
        }
        return repository.listActivePublishedVersions(insuranceId, properties.isSyntheticSourcesEnabled());
    }

    public InputStream downloadPublished(Long sourceId, Long insuranceId, Long profileVersionId) {
        if (!repository.isActivePublished(sourceId, insuranceId, profileVersionId,
                properties.isSyntheticSourcesEnabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Published source was not found");
        }
        InsuranceTermSourceDO source = requireSource(sourceId);
        if (!isRetrievableSourceType(source.getSourceType())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Published source was not found");
        }
        try {
            return contentReader.openVerified(source);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not retrieve source document");
        }
    }

    public InputStream downloadPublishedPdf(Long sourceId, Long insuranceId, Long profileVersionId) {
        if (!repository.isActivePublished(sourceId, insuranceId, profileVersionId,
                properties.isSyntheticSourcesEnabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Published source was not found");
        }
        InsuranceTermSourceDO source = requireSource(sourceId);
        if (!isRetrievableSourceType(source.getSourceType())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Published source was not found");
        }
        String originalName = source.getOriginalFileName();
        if (originalName != null && originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            if (!objectStorage.exists(source.getObjectKey())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Published PDF was not found");
            }
            return downloadPublished(sourceId, insuranceId, profileVersionId);
        }
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF rendition was not found");
        }
        String pdfObjectKey = source.getObjectKey() + ".pdf";
        if (!objectStorage.exists(pdfObjectKey)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF rendition was not found");
        }
        try {
            return objectStorage.open(pdfObjectKey);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF rendition was not found");
        }
    }

    private boolean hasPublishedPdfRendition(InsuranceTermSourceDO source) {
        String originalName = source.getOriginalFileName();
        if (originalName == null || source.getObjectKey() == null) return false;
        String normalizedName = originalName.toLowerCase(Locale.ROOT);
        if (normalizedName.endsWith(".pdf")) return objectStorage.exists(source.getObjectKey());
        if (normalizedName.endsWith(".md")) return objectStorage.exists(source.getObjectKey() + ".pdf");
        return false;
    }

    public InsuranceTermSourceVO getPublished(Long id) {
        InsuranceTermSourceDO source = requireSource(id);
        if (!repository.isActivePublished(id, source.getInsuranceId(), source.getProfileVersionId(),
                properties.isSyntheticSourcesEnabled()) || !isRetrievableSourceType(source.getSourceType())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Published source was not found");
        }
        return toView(source);
    }

    private boolean isRetrievableSourceType(String sourceType) {
        return "OFFICIAL_PRODUCT_TERM".equals(sourceType)
                || (properties.isSyntheticSourcesEnabled() && "SYNTHETIC".equals(sourceType));
    }

    private InsuranceTermSourceDO requireSource(Long id) {
        InsuranceTermSourceDO source = id == null ? null : repository.findById(id);
        if (source == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Insurance source was not found");
        return source;
    }

    private void validateRequest(Long insuranceId, Long profileVersionId, String sourceType,
                                 String title, String sourceUri, LocalDate effectiveFrom,
                                 LocalDate effectiveTo, MultipartFile file, Long uploadedBy) {
        if (insuranceId == null || insuranceId <= 0 || profileVersionId == null || profileVersionId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product and profile version are required");
        }
        if (!StringUtils.hasText(sourceType)
                || !ALLOWED_SOURCE_TYPES.contains(sourceType.trim().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported insurance source type");
        }
        if (!StringUtils.hasText(title) || title.trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A title of at most 255 characters is required");
        }
        normalizeSourceUri(sourceUri);
        validateEffectiveDates(effectiveFrom, effectiveTo);
        if (uploadedBy == null || uploadedBy <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated admin user is required");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source document is required");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Source document exceeds the upload limit");
        }
        String extension = extensionOf(safeFileName(file.getOriginalFilename()));
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only PDF, DOC, DOCX, Markdown, and text source documents are supported");
        }
    }

    private void validateEffectiveDates(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Effective date range is invalid");
        }
    }

    private void validateFileSignature(String extension, InputStream input) throws IOException {
        byte[] signature = new byte[8];
        int length = 0;
        while (length < signature.length) {
            int read = input.read(signature, length, signature.length - length);
            if (read < 0) break;
            if (read == 0) {
                int singleByte = input.read();
                if (singleByte < 0) break;
                signature[length++] = (byte) singleByte;
                continue;
            }
            length += read;
        }
        if (length <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source document is empty");
        boolean valid;
        switch (extension) {
            case ".pdf":
                valid = length >= 5 && signature[0] == '%' && signature[1] == 'P'
                        && signature[2] == 'D' && signature[3] == 'F' && signature[4] == '-';
                break;
            case ".doc":
                valid = length == 8 && (signature[0] & 0xff) == 0xd0 && (signature[1] & 0xff) == 0xcf
                        && (signature[2] & 0xff) == 0x11 && (signature[3] & 0xff) == 0xe0;
                break;
            case ".docx":
                valid = length >= 4 && signature[0] == 'P' && signature[1] == 'K'
                        && signature[2] == 3 && signature[3] == 4;
                break;
            case ".md":
            case ".txt":
                valid = true;
                for (int index = 0; index < length; index++) if (signature[index] == 0) valid = false;
                break;
            default:
                valid = false;
        }
        if (!valid) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "File content does not match its declared extension");
    }

    private String safeFileName(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file name is required");
        }
        String normalized = originalFileName.trim().replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (fileName.length() > 255 || fileName.contains("\r") || fileName.contains("\n")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file name is invalid");
        }
        return fileName;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private String contentTypeFor(String extension) {
        switch (extension) {
            case ".pdf": return "application/pdf";
            case ".doc": return "application/msword";
            case ".docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".md": return "text/markdown; charset=UTF-8";
            case ".txt": return "text/plain; charset=UTF-8";
            default: throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type");
        }
    }

    private String normalizeSourceUri(String sourceUri) {
        if (!StringUtils.hasText(sourceUri)) return null;
        String value = sourceUri.trim();
        if (value.length() > 1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source URL is too long");
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || !("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source URL must be an absolute HTTP(S) URL");
        }
        return value;
    }

    private String buildObjectKey(Long insuranceId, Long profileVersionId, Long sourceId, String extension) {
        return "products/" + insuranceId + "/profile-versions/" + profileVersionId
                + "/sources/" + sourceId + extension;
    }

    private String sha256(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    int singleByte = input.read();
                    if (singleByte < 0) break;
                    digest.update((byte) singleByte);
                } else {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded source document");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String toHex(byte[] hash) {
        StringBuilder value = new StringBuilder(hash.length * 2);
        for (byte item : hash) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }

    private ResponseStatusException stateConflict(Long id, String reason) {
        if (repository.findById(id) == null) return new ResponseStatusException(HttpStatus.NOT_FOUND, "Insurance source was not found");
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private InsuranceTermSourceVO toView(InsuranceTermSourceDO source) {
        InsuranceTermSourceVO view = new InsuranceTermSourceVO();
        view.setId(source.getId());
        view.setInsuranceId(source.getInsuranceId());
        view.setProfileVersionId(source.getProfileVersionId());
        view.setProductVersion(source.getProductVersion());
        view.setSourceType(source.getSourceType());
        view.setTitle(source.getTitle());
        view.setSourceUri(source.getSourceUri());
        view.setOriginalFileName(source.getOriginalFileName());
        view.setContentType(source.getContentType());
        view.setFileSize(source.getFileSize());
        view.setSha256(source.getSha256());
        view.setStatus(source.getStatus());
        view.setEffectiveFrom(source.getEffectiveFrom());
        view.setEffectiveTo(source.getEffectiveTo());
        view.setCreateTime(source.getCreateTime());
        view.setUploadedBy(source.getUploadedBy());
        view.setSubmittedBy(source.getSubmittedBy());
        view.setSubmittedAt(source.getSubmittedAt());
        view.setReviewedBy(source.getReviewedBy());
        view.setReviewedAt(source.getReviewedAt());
        view.setReviewNote(source.getReviewNote());
        view.setPublishedBy(source.getPublishedBy());
        view.setPublishedAt(source.getPublishedAt());
        view.setWithdrawnBy(source.getWithdrawnBy());
        view.setWithdrawnAt(source.getWithdrawnAt());
        view.setWithdrawalReason(source.getWithdrawalReason());
        view.setIndexStatus(source.getIndexStatus());
        view.setIndexedSha256(source.getIndexedSha256());
        view.setIndexError(source.getIndexError());
        return view;
    }
}
