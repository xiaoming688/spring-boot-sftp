package com.craftcoder.sftp.service;

import com.craftcoder.sftp.config.SftpAdapter;
import com.craftcoder.sftp.util.FileUtils;
import com.craftcoder.sftp.util.SFTPUtils;
import com.craftcoder.sftp.util.ZipUtil;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SftpService {

    private static final Logger logger = LoggerFactory.getLogger(SftpService.class);

    @Autowired
    private SftpRemoteFileTemplate remoteFileTemplate;

    @Value("${sftp.host}")
    private String sftpHost;

    @Value("${sftp.port}")
    private int sftpPort;

    @Value("${sftp.user}")
    private String sftpUser;

    @Value("${sftp.password}")
    private String sftpPassword;

    @Autowired
    private SftpAdapter.UploadGateway gateway;

    /**
     * 单文件上传
     *
     * @param file File
     */
    public void uploadFile(File file) {
        gateway.upload(file, "");
    }

    /**
     * 查询某个路径下所有文件
     *
     * @param path
     * @return
     */
    public List<String> listAllFile(String path) {
        return remoteFileTemplate.execute(session -> {
            Stream<String> names = Arrays.stream(session.listNames(path));
            names.forEach(name -> logger.info("Name = " + name));
            return names.collect(Collectors.toList());
        });
    }

    /**
     * 下载文件
     *
     * @param fileName 文件名
     * @param savePath 本地文件存储位置
     * @return
     */
    public File downloadFile(String fileName, String savePath) {
        return remoteFileTemplate.execute(session -> {
            boolean existFile = session.exists(fileName);
            if (existFile) {
                InputStream is = session.readRaw(fileName);
                return FileUtils.convertInputStreamToFile(is, savePath);
            } else {
                logger.info("file : {} not exist", fileName);
                return null;
            }
        });
    }

    /**
     * 文件是否存在
     *
     * @param filePath 文件名
     * @return
     */
    public boolean existFile(String filePath) {
        return remoteFileTemplate.execute(session ->
                session.exists(filePath));
    }

    /**
     * 删除文件
     *
     * @param fileName 待删除文件名
     * @return
     */
    public boolean deleteFile(String fileName) {
        return remoteFileTemplate.execute(session -> {
            boolean existFile = session.exists(fileName);
            if (existFile) {
                return session.remove(fileName);
            } else {
                logger.info("file : {} not exist", fileName);
                return false;
            }
        });
    }

    /**
     * 批量上传 (MultipartFile)
     *
     * @param files List<MultipartFile>
     * @throws IOException
     */
    public void uploadFiles(List<MultipartFile> files, boolean deleteSource) throws IOException {
        for (MultipartFile multipartFile : files) {
            if (multipartFile.isEmpty()) {
                continue;
            }
            File file = FileUtils.convert(multipartFile);
            gateway.upload(file, "remote/tt");
            if (deleteSource) {
                file.delete();
            }
        }
    }

    /**
     * 批量上传 (MultipartFile)
     *
     * @param files List<MultipartFile>
     * @throws IOException
     */
    public void uploadFiles(List<MultipartFile> files) throws IOException {
        uploadFiles(files, true);
    }

    /**
     * 单文件上传 (MultipartFile)
     *
     * @param multipartFile MultipartFile
     * @throws IOException
     */
    public void uploadFile(MultipartFile multipartFile) throws IOException {
        gateway.upload(FileUtils.convert(multipartFile), "");
    }

    public void uploadFilesSelf(MultipartFile files) throws IOException {
        SFTPUtils sftp = new SFTPUtils(sftpHost, sftpUser, sftpPassword);
        File file = FileUtils.convert(files);
        logger.info("file.getAbsolutePath():" + file.getAbsolutePath());
        logger.info("file.getCanonicalPath():" + file.getCanonicalPath());
        logger.info("file.getPath():" + file.getPath());
        logger.info("file.getName():" + file.getName());
        sftp.connect();
        sftp.uploadFileBoot("/sclpsend/test/", new String(file.getName().getBytes(), "utf-8"), file);


    }

    @Scheduled(cron = "0/60 * * * * ?")
    public void downloadTask() {
        SFTPUtils sftp = null;
        try {
            logger.info(new Date() + " task start...");
            String remotePath = "/sclp/orig/";
            String remoteSendPath = "/sclpsend/test/";
            // 本地存放地址
            String localPath = "D:\\downLoadFiles\\";
            if (!localPath.endsWith("\\")) {
                localPath = localPath + "\\";
            }
            File file = new File(localPath);
            if (!file.exists()) {
                file.mkdirs();
            }
            sftp = new SFTPUtils(sftpHost, sftpUser, sftpPassword);
            sftp.connect();
            sftp.batchDownLoadFile(remotePath, localPath, null, ".eof", false);
            sftp.batchDownLoadFile(remotePath, localPath, null, ".zip", false);
//            sftp.bacthUploadFile(remoteSendPath, localPath, false);

            unCompress(file);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
        }

    }

    public void unCompress(File file) {
        File[] localDir = file.listFiles();
        for (File fileLocal : localDir) {
            if (fileLocal.isDirectory()) {
                unCompress(fileLocal);
            }
            String localPathTemp = fileLocal.getParent() + "\\temp\\";
            //不准确
            if (fileLocal.getName().endsWith("zip")) {
                ZipUtil.unzip(fileLocal.getAbsolutePath(), localPathTemp);
            }
        }


    }
}
