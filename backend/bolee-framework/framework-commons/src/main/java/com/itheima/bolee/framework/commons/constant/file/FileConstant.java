package com.itheima.bolee.framework.commons.constant.file;

/**
 * @ClassName FileConstant.java
 * @Description 文件常量类
 */
public class FileConstant {

    // Legacy marker sent by the prebuilt admin client. The file service reroutes new uploads to MinIO.
    public static final String ALIYUN_OSS = "ALIYUN_OSS";

    //七牛KODO对象存储
    public static final String QINIU_KODO = "QINIU_KODO";

    // MinIO object storage used for local insurance source documents.
    public static final String MINIO = "MINIO";

    //状态：成功
    public static final String STATUS_SUCCEED = "SUCCEED";

    //状态：发送中
    public static final String STATUS_SENDING = "SENDING";



}
