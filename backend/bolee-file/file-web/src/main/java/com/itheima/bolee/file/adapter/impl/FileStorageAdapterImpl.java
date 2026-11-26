package com.itheima.bolee.file.adapter.impl;

import com.itheima.bolee.file.adapter.FileStorageAdapter;
import com.itheima.bolee.file.handler.FileStorageHandler;
import com.itheima.bolee.framework.commons.constant.file.FileConstant;
import com.itheima.bolee.framework.commons.dto.file.FileVO;
import com.itheima.bolee.framework.commons.dto.file.FilePartVO;
import com.itheima.bolee.framework.commons.utils.RegisterBeanHandler;
import com.itheima.bolee.file.pojo.File;
import com.itheima.bolee.file.handler.minio.MinioFileStorageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @ClassName FileStorageAdapterImpl.java
 * @Description 文件存储适配处理
 */
@Component
public class FileStorageAdapterImpl implements FileStorageAdapter {

    @Autowired
    RegisterBeanHandler registerBeanHandler;

    @Autowired(required = false)
    MinioFileStorageProperties minioProperties;

    private static Map<String,String> fileStorageHandlers =new HashMap<>();

    static {
        fileStorageHandlers.put(FileConstant.QINIU_KODO,"kodoFileStorageHandler");
        fileStorageHandlers.put(FileConstant.MINIO,"minioFileStorageHandler");
    }

    @Override
    public String uploadFile(FileVO fileVO, InputStream inputStream) {
        normalizeForUpload(fileVO);
        FileStorageHandler fileStorageHandler = resolveHandler(fileVO.getStoreFlag());
        return fileStorageHandler.uploadFile(fileVO.getSuffix(), fileVO.getFileName(), fileVO.getBucketName(), fileVO.getAutoCatalog(), inputStream);
    }

    @Override
    public File initiateMultipartUpload(FileVO fileVO) {
        normalizeForUpload(fileVO);
        FileStorageHandler fileStorageHandler = resolveHandler(fileVO.getStoreFlag());
        return fileStorageHandler.initiateMultipartUpload(fileVO.getSuffix(),fileVO.getFileName(),fileVO.getBucketName(),fileVO.getAutoCatalog());
    }

    @Override
    public String uploadPart(FilePartVO filePartVo, InputStream inputStream) {
        normalizeForUpload(filePartVo);
        FileStorageHandler fileStorageHandler = resolveHandler(filePartVo.getStoreFlag());
        return fileStorageHandler.uploadPart(filePartVo.getUploadId(),filePartVo.getFileName(),filePartVo.getPartNumber(),filePartVo.getPartSize(),filePartVo.getBucketName(),inputStream);
    }

    @Override
    public String completeMultipartUpload(FileVO fileVO) {
        normalizeForUpload(fileVO);
        FileStorageHandler fileStorageHandler = resolveHandler(fileVO.getStoreFlag());
        return fileStorageHandler.completeMultipartUpload(fileVO.getUploadId(),fileVO.getPartETags(),fileVO.getFileName(),fileVO.getBucketName());
    }

    @Override
    public InputStream downloadFile(String storeFlag,String bucketName,String pathUrl) throws IOException {
        FileStorageHandler fileStorageHandler = resolveHandler(storeFlag);
        return fileStorageHandler.downloadFile(bucketName,pathUrl);
    }

    @Override
    public void delete(String storeFlag,String bucketName,String pathUrl) {
        FileStorageHandler fileStorageHandler = resolveHandler(storeFlag);
        fileStorageHandler.delete(bucketName,pathUrl);
    }

    @Override
    public void deleteBatch(String storeFlag,String bucketName,List<String> pathUrls) {
        FileStorageHandler fileStorageHandler = resolveHandler(storeFlag);
        fileStorageHandler.deleteBatch(bucketName,pathUrls);
    }

    @Override
    public String getFileContent(String storeFlag,String bucketName,String pathUrl) throws IOException {
        FileStorageHandler fileStorageHandler = resolveHandler(storeFlag);
        return fileStorageHandler.getFileContent(bucketName,pathUrl);
    }

    private void normalizeForUpload(FileVO fileVO) {
        if (!StringUtils.hasText(fileVO.getStoreFlag())
                || FileConstant.ALIYUN_OSS.equalsIgnoreCase(fileVO.getStoreFlag())) {
            fileVO.setStoreFlag(FileConstant.MINIO);
            fileVO.setBucketName(requireMinioProperties().getBucket());
        }
    }

    private void normalizeForUpload(FilePartVO filePartVO) {
        if (!StringUtils.hasText(filePartVO.getStoreFlag())
                || FileConstant.ALIYUN_OSS.equalsIgnoreCase(filePartVO.getStoreFlag())) {
            filePartVO.setStoreFlag(FileConstant.MINIO);
            filePartVO.setBucketName(requireMinioProperties().getBucket());
        }
    }

    private MinioFileStorageProperties requireMinioProperties() {
        if (minioProperties == null || !minioProperties.isEnabled()) {
            throw new IllegalStateException("MinIO storage is disabled; configure INSURANCE_TERMS_MINIO_ENABLED and credentials");
        }
        return minioProperties;
    }

    private FileStorageHandler resolveHandler(String storeFlag) {
        if (!StringUtils.hasText(storeFlag) || FileConstant.ALIYUN_OSS.equalsIgnoreCase(storeFlag)) {
            throw new IllegalStateException("Aliyun OSS is disabled; new uploads must be routed to MinIO");
        }
        String handlerName = fileStorageHandlers.get(storeFlag);
        if (!StringUtils.hasText(handlerName)) {
            throw new IllegalArgumentException("Unsupported file storage provider: " + storeFlag);
        }
        if (FileConstant.MINIO.equals(storeFlag)) {
            requireMinioProperties();
        }
        return registerBeanHandler.getBean(handlerName, FileStorageHandler.class);
    }
}
