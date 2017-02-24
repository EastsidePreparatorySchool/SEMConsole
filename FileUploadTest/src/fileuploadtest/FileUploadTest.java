/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fileuploadtest;

import java.io.File;
import java.io.IOException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.*;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.codec.DecoderException;


public class FileUploadTest {
    public static void main(String[] args) throws Exception {
        uploadImage("http://semphotogallery.appspot.com/upload", "lamprey_ra.jpg");
    }
    public static void uploadImage(String urlString, String imagePath) {
        try {
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
        } catch (HttpException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }         
    }
}