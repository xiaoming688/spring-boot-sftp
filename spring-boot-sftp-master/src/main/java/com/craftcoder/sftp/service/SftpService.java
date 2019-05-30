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

    @Autowired(required = false)
    private SftpAdapter.UploadGateway gateway;


    private static Map<String, String> DOWNLOAD_30_MIN = null;

    private static Map<String, String> DOWNLOAD_7_CLOCK = null;

    private String saveLocalPath = "C:\\工作\\en\\really\\Orig\\";

    static {
        DOWNLOAD_30_MIN = new HashMap<>();
        DOWNLOAD_30_MIN.put("/sclp/orig/orderstatus/", "Orderstatus\\");
        DOWNLOAD_30_MIN.put("/sclp/orig/orderunfreeze/", "Orderunfreeze\\");

        DOWNLOAD_7_CLOCK = new HashMap<>();
        DOWNLOAD_7_CLOCK.put("/sclp/orig/article/", "Article\\");
        DOWNLOAD_7_CLOCK.put("/sclp/orig/articlelist/", "Articlelist\\");
        DOWNLOAD_7_CLOCK.put("/sclp/orig/pos/", "Pos\\");
        DOWNLOAD_7_CLOCK.put("/sclp/orig/starange/", "Starange\\");

    }


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

    /**
     * 每隔30min
     */
//    @Scheduled(cron = "0 0/30 * * * ?")
    @Scheduled(cron = "0/30 * * * * ?")
    public void downloadTask() {
        SFTPUtils sftp = null;
        try {
            logger.info(new Date() + " download 30 min task start...");
            sftp = new SFTPUtils(sftpHost, sftpUser, sftpPassword);
            sftp.connect();
            for (String remotePath : DOWNLOAD_30_MIN.keySet()) {
                String localPathTemp = saveLocalPath + DOWNLOAD_30_MIN.get(remotePath);
                processDownloadTask(localPathTemp, remotePath, sftp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
        }
    }

    /**
     * 每隔10min 上传
     */
//    @Scheduled(cron = "0 0/10 * * * ?")
    @Scheduled(cron = "0 0/2 * * * ?")
    public void uploadTask() {
        SFTPUtils sftp = null;
        try {
            logger.info(new Date() + " uploadTask start...");
            String remoteSendPath = "/sclpsend/orig/";
            // 本地存放地址
            String localPath = "C:\\工作\\en\\reallySend\\Orig\\";
            sftp = new SFTPUtils(sftpHost, sftpUser, sftpPassword);
            sftp.connect();
            sftp.bacthUploadFile(remoteSendPath, localPath, true);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
        }
    }

    /**
     * 每天7点
     */
//    @Scheduled(cron = "0 30 22 * * ?")
    @Scheduled(cron = "0/20 * * * * ?")
    public void downloadEveryTask() {
        SFTPUtils sftp = null;
        try {
            logger.info(new Date() + " every 7:00 download start...");
            // 本地存放地址
            sftp = new SFTPUtils(sftpHost, sftpUser, sftpPassword);
            sftp.connect();
            for (String remotePath : DOWNLOAD_7_CLOCK.keySet()) {
                String localPathTemp = saveLocalPath + DOWNLOAD_7_CLOCK.get(remotePath);
                processDownloadTask(localPathTemp, remotePath, sftp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (sftp != null) {
                logger.info("disconnect");
                sftp.disconnect();
            }
        }
    }

    public void processDownloadTask(String localPathTemp, String remotePath, SFTPUtils sftp) {
        File file = new File(localPathTemp);
        if (!file.exists()) {
            file.mkdirs();
        }
        //zip需要先下載，有eof文件判斷
        sftp.batchDownLoadFile(remotePath, localPathTemp, null, ".zip", true);
        sftp.batchDownLoadFile(remotePath, localPathTemp, null, ".eof", true);
        unCompress(file);
    }


    public void unCompress(File file) {
        File[] localDir = file.listFiles();
        for (File fileLocal : localDir) {
            if (fileLocal.isDirectory()) {
                unCompress(fileLocal);
            }
            String localPathTemp = fileLocal.getParent();
            //不准确
            if (fileLocal.getName().endsWith("zip")) {
                ZipUtil.unzip(fileLocal.getAbsolutePath(), localPathTemp);
            }
        }


    }
}
