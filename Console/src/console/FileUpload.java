/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package console;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.*;

public class FileUpload {

    final static String LOCAL_PATH = "http://localhost:8000/";
    final static String DEPLOYED_PATH = "http://semphotogallery.appspot.com/";

    final static boolean IS_LOCAL = false;

    public static void uploadFileToServer(String imagePath) {
        String stem = DEPLOYED_PATH;
        if (IS_LOCAL) {
            stem = LOCAL_PATH;
        }

        try {
            String uploadUrl = getUploadURL(stem + "geturl");
            uploadImage(uploadUrl, imagePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void uploadFileAndMetaDataToServer(String imagePath, String operators, String channel, int kv, int mag, int wd) {
        String stem = DEPLOYED_PATH;
        if (IS_LOCAL) {
            stem = LOCAL_PATH;
        }

        try {
            String uploadUrl = getUploadURL(stem + "geturl");
            uploadImage(uploadUrl, imagePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    
    public static void uploadImage(String urlString, String imagePath) throws FileNotFoundException, IOException {
        File f = new File(imagePath);
        PostMethod postMessage = new PostMethod(urlString);
        Part[] parts = {
            new StringPart("specimen", "foobar"),
            new StringPart("magnification", "492"),
            new FilePart("img", f)
        };
        postMessage.setRequestEntity(new MultipartRequestEntity(parts, postMessage.getParams()));
        HttpClient client = new HttpClient();

        int status = client.executeMethod(postMessage);
    }

    public static String getUploadURL(String reqURL) throws MalformedURLException, IOException {

        URL url = new URL(reqURL);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(url.openStream()));

        return in.readLine(); // Will only ever return one line, so we don't need a loop
    }
}
