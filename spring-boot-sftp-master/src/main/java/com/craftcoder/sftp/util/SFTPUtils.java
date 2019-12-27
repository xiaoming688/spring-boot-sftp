package com.craftcoder.sftp.util;

import com.craftcoder.sftp.controller.RestUploadController;
import com.jcraft.jsch.*;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SFTPUtils {
    private final org.slf4j.Logger log = LoggerFactory.getLogger(RestUploadController.class);

    private String host;//服务器连接ip
    private String username;//用户名
    private String password;//密码
    private int port = 22;//端口号
    private ChannelSftp sftp = null;
    private Session sshSession = null;

    public SFTPUtils() {
    }

    public SFTPUtils(String host, int port, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
        this.port = port;
    }

    public SFTPUtils(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
    }

    /**
     * 通过SFTP连接服务器
     */
    public void connect() {
        try {
            JSch jsch = new JSch();
            jsch.getSession(username, host, port);
            sshSession = jsch.getSession(username, host, port);
            if (log.isInfoEnabled()) {
                log.info("Session created.");
            }
            sshSession.setPassword(password);
            Properties sshConfig = new Properties();
            sshConfig.put("StrictHostKeyChecking", "no");
            sshSession.setConfig(sshConfig);
            sshSession.connect();
            if (log.isInfoEnabled()) {
                log.info("Session connected.");
            }
//            Channel channel = sshSession.openChannel("sftp");
            ChannelSftp channel = (ChannelSftp) sshSession.openChannel("sftp");
            channel.connect();

//            Class cl = ChannelSftp.class;
//            Field f =cl.getDeclaredField("server_version");
//            f.setAccessible(true);
//            f.set(channel, 2);
//
//            channel.setFilenameEncoding("GBK");
            if (log.isInfoEnabled()) {
                log.info("Opening Channel.");
            }
            sftp = (ChannelSftp) channel;
            if (log.isInfoEnabled()) {
                log.info("Connected to " + host + ".");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭连接
     */
    public void disconnect() {
        if (this.sftp != null) {
            if (this.sftp.isConnected()) {
                this.sftp.disconnect();
                if (log.isInfoEnabled()) {
                    log.info("sftp is closed already");
                }
            }
        }
        if (this.sshSession != null) {
            if (this.sshSession.isConnected()) {
                this.sshSession.disconnect();
                if (log.isInfoEnabled()) {
                    log.info("sshSession is closed already");
                }
            }
        }
    }

    /**
     * 批量下载文件
     *
     * @param remotePath：远程下载目录(以路径符号结束,可以为相对路径eg:/assess/sftp/jiesuan_2/2014/)
     * @param localPath：本地保存目录(以路径符号结束,D:\Duansha\sftp\)
     * @param fileFormat：下载文件格式(以特定字符开头,为空不做检验)
     * @param fileEndFormat：下载文件格式(文件格式)
     * @param del：下载后是否删除sftp文件
     * @return
     */
    public List<String> batchDownLoadFile(String remotePath, String localPath,
                                          String fileFormat, String fileEndFormat, boolean del) {
        List<String> filenames = new ArrayList<String>();
        try {
            // connect();
            Vector v = listFiles(remotePath);
            // sftp.cd(remotePath);
            log.info("batchDownLoadFile: " + remotePath + " :size:" + v.size());
            if (v.size() > 0) {
                log.info("本次处理文件个数不为零,开始下载" + remotePath + ": " + fileEndFormat);
                Iterator it = v.iterator();
                while (it.hasNext()) {
                    ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) it.next();
                    String filename = entry.getFilename();
                    if (filename.equals(".") || filename.equals("..")) {
                        continue;
                    }
                    SftpATTRS attrs = entry.getAttrs();
                    if (!attrs.isDir()) {
                        boolean flag = false;
                        String localFileName = localPath + filename;
                        fileFormat = fileFormat == null ? "" : fileFormat
                                .trim();
                        fileEndFormat = fileEndFormat == null ? ""
                                : fileEndFormat.trim();
                        String remoteModifyTime = CSTFormat(attrs.getMtimeString());

                        // 三种情况
                        if (fileFormat.length() > 0 && fileEndFormat.length() > 0) {
                            if (filename.startsWith(fileFormat) && filename.endsWith(fileEndFormat)) {
                                flag = downloadFile(remotePath, filename, localPath, filename, remoteModifyTime);
                                if (flag) {
                                    filenames.add(localFileName);
                                    if (flag && del) {
//                                        deleteSFTP(remotePath, filename);
                                    }
                                }
                            }
                        } else if (fileFormat.length() > 0 && "".equals(fileEndFormat)) {
                            if (filename.startsWith(fileFormat)) {
                                flag = downloadFile(remotePath, filename, localPath, filename, remoteModifyTime);
                                if (flag) {
                                    filenames.add(localFileName);
                                    if (flag && del) {
//                                        deleteSFTP(remotePath, filename);
                                    }
                                }
                            }
                        } else if (fileEndFormat.length() > 0 && "".equals(fileFormat)) {
                            if (filename.endsWith(fileEndFormat)) {
                                flag = downloadFile(remotePath, filename, localPath, filename, remoteModifyTime);
                                if (flag) {
                                    filenames.add(localFileName);
                                    if (flag && del) {
                                        deleteSFTP(remotePath, filename);
                                        //放到bak
                                        String split = remotePath.replace("/orig", "/bak");
                                        uploadFile(split, filename, localPath, filename, false);
                                    }
                                }
                            }
                        } else {
                            flag = downloadFile(remotePath, filename, localPath, filename, remoteModifyTime);
                            if (flag) {
                                filenames.add(localFileName);
                                if (flag && del) {
//                                    deleteSFTP(remotePath, filename);


                                }
                            }
                        }
                    } else {
                        String localPathTemp = localPath + filename + "\\";
                        String remotePathTemp = remotePath + filename + "/";
                        File tempFile = new File(localPathTemp);
                        if (!tempFile.exists()) {
                            tempFile.mkdirs();
                        }
                        batchDownLoadFile(remotePathTemp, localPathTemp, fileFormat, fileEndFormat, false);
                    }
                }
            }
            if (log.isInfoEnabled()) {
                log.info("文件目录下载完成 :remotePath=" + remotePath
                        + " and localPath=" + localPath + " fileEndFormat is: " + fileEndFormat + ",file size is "
                        + v.size());
            }
        } catch (SftpException e) {
            e.printStackTrace();
        } finally {
            // this.disconnect();
        }
        return filenames;
    }

    /**
     * 将CST时间类型字符串进行格式化输出
     *
     * @param str
     * @return
     */
    public static String CSTFormat(String str) {
        SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
        Date date = null;
        try {
            date = (Date) formatter.parse(str);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    /**
     * 判断文件是否存在
     *
     * @param path 文件路径
     * @return 存在返回true, 否则返回false
     */
    public static boolean isExistFile(String path) {

        if (null == path || "".equals(path.trim())) {
            return false;
        }

        File targetFile = new File(path);
        return targetFile.exists();
    }

    /**
     * 下载单个文件
     *
     * @param remotePath：远程下载目录(以路径符号结束)
     * @param remoteFileName：下载文件名
     * @param localPath：本地保存目录(以路径符号结束)
     * @param localFileName：保存文件名
     * @param remoteModifyTime：远程文件修改时间
     * @return
     */
    public boolean downloadFile(String remotePath, String remoteFileName, String localPath, String localFileName, String remoteModifyTime) {
        FileOutputStream fieloutput = null;
        try {
            // sftp.cd(remotePath);
            File file = new File(localPath + localFileName);
            // mkdirs(localPath + localFileName);
            String localModifyTime = numberDateFormat(String.valueOf(file.lastModified()), "yyyy-MM-dd HH:mm:ss");

            if (file.exists() && localModifyTime.compareTo(remoteModifyTime) >= 0) {
                log.info("localModifyTime:" + localModifyTime + "remoteTime: " + remoteModifyTime);
                log.info("文件:" + localPath + remoteFileName + " 已存在，不需要下载 !!!");
                return false;
            }
            //判断该文件eof文件是否存在，不存在就不下载
            String name = remoteFileName.substring(0, remoteFileName.lastIndexOf("."));
            try {
                Vector content = sftp.ls(remotePath + name + ".eof");
                if (content == null || content.isEmpty()) {
                    log.info("文件:" + localPath + remoteFileName + " eof不存在存在，不需要下载 !!!");
                    return false;
                }
            } catch (Exception e) {
                log.info("文件:" + localPath + remoteFileName + " eof不存在存在，不需要下载 !!!.....");
                return false;
            }

            fieloutput = new FileOutputStream(file);
            sftp.get(remotePath + remoteFileName, fieloutput);
            if (log.isInfoEnabled()) {
                log.info("文件:" + remotePath + remoteFileName + " 下载成功.");
            }
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (SftpException e) {
            e.printStackTrace();
        } finally {
            if (null != fieloutput) {
                try {
                    fieloutput.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    /**
     * 上传单个文件
     *
     * @param remotePath：远程保存目录
     * @param remoteFileName：保存文件名
     * @param localPath：本地上传目录(以路径符号结束)
     * @param localFileName：上传的文件名
     * @return
     */
    public boolean uploadFile(String remotePath, String remoteFileName, String localPath, String localFileName, Boolean flag) {
        FileInputStream in = null;
        FileInputStream eofIn = null;
        try {
            createDir(remotePath);
            Vector vector = sftp.ls(remotePath);

            File file = new File(localPath + "\\" + localFileName);
            if (vector.size() > 0) {
                vector.iterator();
                Iterator it = vector.iterator();
                while (it.hasNext()) {
                    ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) it.next();
                    String filename = entry.getFilename();
                    SftpATTRS attrs = entry.getAttrs();
                    //文件名一样
                    if (filename.equals(remoteFileName)) {
                        String localFileLastModify = SFTPUtils.numberDateFormat(String.valueOf(file.lastModified()), "yyyy-MM-dd HH:mm:ss");

                        String remoteFileLastModify = SFTPUtils.CSTFormat(attrs.getMtimeString());

                        if (remoteFileLastModify.compareTo(localFileLastModify) >= 0) {
                            log.info("localFile: " + localFileName + " localFileLastModify: " + localFileLastModify +
                                    " remote lastModify: " + remoteFileLastModify + " 不需要上传");
                            return false;
                        }
                        log.info("localFile: " + localFileName + " localFileLastModify: " + localFileLastModify +
                                " remote lastModify: " + remoteFileLastModify);

                    }
                }
            }
            in = new FileInputStream(file);
            if (flag) {
                String eofName = localFileName.substring(0, localFileName.lastIndexOf(".")) + ".eof";
                File eofFile = new File(localPath + "\\" + eofName);
                if (!eofFile.exists()) {
                    eofFile.createNewFile();
                }
                eofIn = new FileInputStream(eofFile);
                sftp.put(eofIn, eofName);
                if (eofIn != null) {
                    eofIn.close();
                }
                deleteFile(localPath + "\\" + eofName);
            }
            sftp.put(in, remoteFileName);
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (SftpException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    public boolean uploadFileBoot(String remotePath, String remoteFileName, File file) {
        FileInputStream in = null;
        try {
            createDir(remotePath);
            in = new FileInputStream(file);
            sftp.put(in, remoteFileName);
            return true;
        } catch (SftpException | FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (sftp != null) {
                sftp.disconnect();
            }
        }
        return false;
    }

    /**
     * 10位13位时间戳转String 格式（2018-10-15 16:03:27） 日期
     *
     * @param timestamp
     * @param simpleDateFormatType 时间戳类型（"yyyy-MM-dd HH:mm:ss"）
     * @return
     */
    public static String numberDateFormat(String timestamp, String simpleDateFormatType) {
        SimpleDateFormat sdf = new SimpleDateFormat(simpleDateFormatType);//要转换的时间格式
        String date = null;
        if (timestamp.length() == 13) {
            date = sdf.format(Long.parseLong(timestamp));
        } else {
            date = sdf.format(Long.parseLong(timestamp) * 1000);
        }
        return date;
    }

    /**
     * 批量上传文件
     *
     * @param remotePath：远程保存目录
     * @param localPath：本地上传目录(以路径符号结束)
     * @param del：上传后是否删除本地文件
     * @return
     */
    public boolean bacthUploadFile(String remotePath, String localPath,
                                   boolean del) {
        try {
//            connect();
            File file = new File(localPath);
            if (file == null) {
                return false;
            }
            File[] files = file.listFiles();
            if (files == null) {
                return false;
            }
            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile()
                        && files[i].getName().indexOf("bak") == -1) {

                    if (this.uploadFile(remotePath, files[i].getName(),
                            localPath, files[i].getName(), true)
                            && del) {
                        deleteFile(localPath + "\\" + files[i].getName());
                    }
                } else if (files[i].isDirectory()) {
                    //只能一层
                    String pathName = files[i].getName();
                    //建立远端目录
                    String remotePathTemp = remotePath + pathName;
                    String localPathTemp = localPath + pathName;
                    createDir(remotePathTemp);
                    log.info("remotePathTemp: " + remotePathTemp + " localPathTemp:" + localPathTemp);
                    bacthUploadFile(remotePathTemp, localPathTemp, del);
                }
            }
            if (log.isInfoEnabled()) {
                log.info("upload file is success:remotePath=" + remotePath
                        + "and localPath=" + localPath + ",file size is "
                        + files.length);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
//            this.disconnect();
        }
        return false;

    }

    /**
     * 删除本地文件
     *
     * @param filePath
     * @return
     */
    public boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return false;
        }

        if (!file.isFile()) {
            return false;
        }
        boolean rs = file.delete();
        if (rs && log.isInfoEnabled()) {
            log.info("delete file " + filePath + " success from local.");
        }
        return rs;
    }

    /**
     * 创建目录
     *
     * @param createpath
     * @return
     */
    public boolean createDir(String createpath) {
        try {
            if (isDirExist(createpath)) {
                this.sftp.cd(createpath);
                return true;
            }
            String pathArry[] = createpath.split("/");
            StringBuffer filePath = new StringBuffer("/");
            for (String path : pathArry) {
                if (path.equals("")) {
                    continue;
                }
                filePath.append(path + "/");
                if (isDirExist(filePath.toString())) {
                    sftp.cd(filePath.toString());
                } else {
                    // 建立目录
                    sftp.mkdir(filePath.toString());
                    // 进入并设置为当前目录
                    sftp.cd(filePath.toString());
                }

            }
            this.sftp.cd(createpath);
            return true;
        } catch (SftpException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 判断目录是否存在
     *
     * @param directory
     * @return
     */
    public boolean isDirExist(String directory) {
        boolean isDirExistFlag = false;
        try {
            SftpATTRS sftpATTRS = this.sftp.lstat(directory);
            isDirExistFlag = true;
            return sftpATTRS.isDir();
        } catch (Exception e) {
            if (e.getMessage().toLowerCase().equals("no such file")) {
                isDirExistFlag = false;
            }
        }
        return isDirExistFlag;
    }

    /**
     * 删除stfp文件
     *
     * @param directory：要删除文件所在目录
     * @param deleteFile：要删除的文件
     */
    public void deleteSFTP(String directory, String deleteFile) {
        try {
            sftp.cd(directory);
            sftp.rm(directory + deleteFile);
            if (log.isInfoEnabled()) {
                log.info("delete " + directory + deleteFile + " success from sftp.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 如果目录不存在就创建目录
     *
     * @param path
     */
    public void mkdirs(String path) {
        File f = new File(path);

        String fs = f.getParent();

        f = new File(fs);

        if (!f.exists()) {
            f.mkdirs();
        }
    }

    /**
     * 列出目录下的文件
     *
     * @param directory：要列出的目录
     * @return
     * @throws SftpException
     */
    public Vector listFiles(String directory) throws SftpException {
        return sftp.ls(directory);
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public ChannelSftp getSftp() {
        return sftp;
    }

    public void setSftp(ChannelSftp sftp) {
        this.sftp = sftp;
    }

    /**
     * 测试
     */
    public static void main(String[] args) {
        System.out.println(Arrays.toString(args));

        SFTPUtils sftp = null;

        Map<String, String> remotePaths = new HashMap<String, String>();
        remotePaths.put("/sclp/orig/article/", "/article/");

        remotePaths.put("/sclp/orig/article/", "/article/");
        remotePaths.put("/sclp/orig/articlelist/", "/articlelist/");
        remotePaths.put("/sclp/orig/order/", "order/");
        remotePaths.put("/sclp/orig/orderstatus/", "/orderstatus/");
        remotePaths.put("/sclp/orig/orderunfreeze/", "/orderunfreeze/");
        remotePaths.put("/sclp/orig/pos/", "/pos/");
        remotePaths.put("/sclp/orig/starange/", "/starange/");

        // 本地存放地址
        String localPath = "D:\\downLoadFiles\\";
        if (args.length > 0) {
            localPath = args[0];
        }
        if (!localPath.endsWith("\\")) {
            localPath = localPath + "\\";
        }

        System.out.println(localPath);
        // Sftp下载路径
        String sftpPath = "/sclp/orig/article/";

        String remotePath = "/sclp/orig/";
        try {
            File file = new File(localPath);
            if (!file.exists()) {
                file.mkdirs();
            }

            sftp.connect();

            sftp.batchDownLoadFile(remotePath, localPath, null, ".eof", false);
//            for (String remotePath : remotePaths.keySet()) {
//                String local = remotePaths.get(remotePath);
//                String desPath = localPath + local;
//                File fileDes = new File(desPath);
//                if (!fileDes.exists()) {
//                    fileDes.mkdirs();
//                }
//                // 下载
//                sftp.batchDownLoadFile(remotePath, desPath, null, ".eof", false);
//                // 下载
//                sftp.batchDownLoadFile(remotePath, desPath, null, ".zip", false);
//            }
//            sftp.bacthUploadFile(remotePath, localPath, false);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sftp.disconnect();
        }
    }
}

///**
// * 时间处理工具类（简单的）
// *
// * @author Aaron
// * @version 1.0
// * @date 2014-6-17
// * @time 下午1:39:44
// */
//class DateUtil {
//    /**
//     * 默认时间字符串的格式
//     */
//    public static final String DEFAULT_FORMAT_STR = "yyyyMMddHHmmss";
//
//    public static final String DATE_FORMAT_STR = "yyyyMMdd";
//
//    /**
//     * 获取系统时间的昨天
//     *
//     * @return
//     */
//    public static String getSysTime() {
//        Calendar ca = Calendar.getInstance();
//        ca.set(Calendar.DATE, ca.get(Calendar.DATE) - 1);
//        Date d = ca.getTime();
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
//        String a = sdf.format(d);
//        return a;
//    }
//
//    /**
//     * 获取当前时间
//     *
//     * @param formatStr
//     * @return
//     */
//    public static String getCurrentDate(String formatStr) {
//        if (null == formatStr) {
//            formatStr = DEFAULT_FORMAT_STR;
//        }
//        return date2String(new Date(), formatStr);
//    }
//
//    /**
//     * 返回年月日
//     *
//     * @return yyyyMMdd
//     */
//    public static String getTodayChar8(String dateFormat) {
//        return DateFormatUtils.format(new Date(), dateFormat);
//    }
//
//    /**
//     * 将Date日期转换为String
//     *
//     * @param date
//     * @param formatStr
//     * @return
//     */
//    public static String date2String(Date date, String formatStr) {
//        if (null == date || null == formatStr) {
//            return "";
//        }
//        SimpleDateFormat df = new SimpleDateFormat(formatStr);
//
//        return df.format(date);
//    }


//}
