/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpEntity;
import org.apache.http.client.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class FileUpload {

    final static String LOCAL_PATH = "http://localhost:13080/";
    final static String DEPLOYED_PATH = "http://semphotogallery.appspot.com/";

    final static boolean IS_LOCAL = false;

    public static void uploadFileAndMetaDataToServer(String imagePath, String operators, String channel, int kv, int mag, int wd) throws FileNotFoundException {
        String stem = IS_LOCAL ? LOCAL_PATH : DEPLOYED_PATH;
        try {
            String uploadURL = getUploadURL(stem + "geturl");
        } catch (IOException ex) {
            System.out.println("Malformed URL or other error");
        }

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost uploadFile = new HttpPost(DEPLOYED_PATH);
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("field1", "yes", ContentType.TEXT_PLAIN);

        // This attaches the file to the POST:
        File f = new File(imagePath);
        try {
            builder.addTextBody("working_depth", Integer.toString(wd));
            builder.addTextBody("magnification", Integer.toString(mag));
            builder.addTextBody("voltage", Integer.toString(kv * 1000));
            builder.addTextBody("operators", operators);
            builder.addTextBody("sensor", channel);
            builder.addTextBody("update_metadata", "1");

            builder.addBinaryBody(
                    "img",
                    new FileInputStream(f),
                    ContentType.APPLICATION_OCTET_STREAM,
                    f.getName()
            );
        } catch (FileNotFoundException ex) {
            System.out.println("HttpPost building failed");
        }

        HttpEntity multipart = builder.build();
        uploadFile.setEntity(multipart);
        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(uploadFile);
            HttpEntity responseEntity = response.getEntity();
        } catch (IOException ex) {
            System.out.println("HttpPost execution failed");
        }
    }

    public static String getUploadURL(String reqURL) throws MalformedURLException, IOException {

        URL url = new URL(reqURL);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(url.openStream()));

        return in.readLine(); // Will only ever return one line, so we don't need a loop
    }

    
}
