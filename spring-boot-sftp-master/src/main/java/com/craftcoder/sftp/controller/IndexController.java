package com.craftcoder.sftp.controller;

import com.craftcoder.sftp.service.SftpService;
import com.craftcoder.sftp.util.ResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Controller
public class IndexController {

    private final Logger logger = LoggerFactory.getLogger(IndexController.class);


    @Autowired
    SftpService sftpService;

    @GetMapping("/")
    public String index() {
        return "upload";
    }

    @GetMapping("/upload_boot")
    public String upload_boot() {
        return "upload_boot";
    }


    @PostMapping("/upload_boot")
    @ResponseBody
    public ResponseMessage uploadData(@RequestParam(value = "files", required = false) MultipartFile uploadFiles) {
        logger.info("Single file upload!");

        ResponseMessage message = new ResponseMessage();
        if (uploadFiles == null) {
            return new ResponseMessage();
        }
        logger.info(uploadFiles.getOriginalFilename());

        try {
            sftpService.uploadFilesSelf(uploadFiles);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return message;
    }

}