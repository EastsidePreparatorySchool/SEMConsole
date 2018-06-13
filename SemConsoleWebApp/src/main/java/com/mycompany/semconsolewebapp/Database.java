/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.semconsolewebapp;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.imageio.ImageIO;

/**
 *
 * @author shinson
 */
public class Database {

    //This class will combine code from the JDBCFramework project with code from the Session class
    //this will add support for a sql database that will hold the images along with their meta data
    public Connection conn;
    public int currentSessionID = -1;

    Database() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.connect();
        } catch (ClassNotFoundException ex) {
            System.out.println(ex);
        }
    }

    // sets the conn field to a connection with the course_requests database.
    // if connection is unsuccessful, prints the detail message of the
    // exception (a string associated with it on construction), a 5
    // character code associated with the SQL state, and the vendor-specific
    // error code associated with the error.
    public void connect() {
        System.out.println("Attempting to connect...");
        try {
            this.conn = DriverManager.getConnection("jdbc:mysql://localhost/SemImagesDatabase", "user", "password");
            System.out.println("Connection successful");
        } catch (SQLException ex) {
            System.out.print("Error connecting to database: ");
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }

        //SET PACKET SIZE TO A LARGER VALUE
        String sql = "SET GLOBAL max_allowed_packet=1024*1024*1024;";
        try {
            PreparedStatement statement = conn.prepareCall(sql);
            statement.execute();
        } catch (Exception e) {
            System.out.println(e);
        }

    }

    public void initializeTables(String operators) {
        //functions to add some initial operators, a session, and images for testing purposes
        addInitialOperators();
        addSession(operators);
        insertTestImages();
    }

    public void addInitialOperators() {
        addOperator("Ethan", "Netz", "EN");
        addOperator("Sam", "Hinson", "SH");
        addOperator("Gunnar", "Mein", "GM");
    }

    public void addOperator(String firstName, String lastName, String initials) {
        try {
            String sql = "INSERT INTO operators (first_name, last_name, initials) VALUES (?, ?, ?);";
            PreparedStatement statement = conn.prepareCall(sql);
            statement.setString(1, firstName);
            statement.setString(2, lastName);
            statement.setString(3, initials);
            statement.executeUpdate();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void addSession(String operators) {
        try {
            String sessionSql = "INSERT INTO sessions () VALUES ();";
            PreparedStatement sessionStatement = conn.prepareCall(sessionSql);
            sessionStatement.executeUpdate();

            currentSessionID = getLastID();

            String[] opsArray = operators.split(" ");

            for (String op : opsArray) {
                String opsIDSql = "SELECT operator_id FROM operators WHERE operators.initials = '" + op + "' LIMIT 1;";
                PreparedStatement opsIDStatement = conn.prepareCall(opsIDSql);
                ResultSet rs = opsIDStatement.executeQuery();

                if (rs.next()) {
                    int operator_id = rs.getInt(1);
                    String opsSql = "INSERT INTO sessions_operators (session_id, operator_id) VALUES (?, ?);";
                    PreparedStatement opsStatement = conn.prepareCall(opsSql);
                    opsStatement.setInt(1, currentSessionID);
                    opsStatement.setInt(2, operator_id);
                    opsStatement.executeUpdate();
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void insertTestImages() {
        //code to insert 4 images for testing
        //this is only enabled after a rerunning of the sql script.     
        File file = new File("C:/temp/testImage1.png");
        storeImageInDatabase(file, 15, 100, 39, false);

        File file2 = new File("C:/temp/testImage2.png");
        storeImageInDatabase(file2, 15, 10, 39, false);

        File file3 = new File("C:/temp/testImage3.png");
        storeImageInDatabase(file3, 25, 10, 39, false);

        File file4 = new File("C:/temp/testImage4.png");
        storeImageInDatabase(file4, 25, 1000, 39, false);

        File file5 = new File("C:/temp/testImage5.png");
        storeImageInDatabase(file5, 25, 1000, 39, true);
    }

    // closes the conn field's connection, prints exception is unsuccessful
    public void disconnect() {

        try {
            this.conn.close();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void storeImageInDatabase(File file, int kv, int mag, int wd, boolean colored) {

        //now that we have the session we can insert the image
        try {
            String sql = "INSERT INTO images (image,beam_kv,magnification,working_depth,colored,session_id) VALUES (?,?,?,?,?,?);";
            PreparedStatement statement = conn.prepareCall(sql);

            //create file input stream and add blob as parameter
            FileInputStream input = new FileInputStream(file);
            statement.setBinaryStream(1, input, file.length());

            //set the metadata values
            statement.setInt(2, kv);
            statement.setInt(3, mag);
            statement.setInt(4, wd);
            statement.setBoolean(5, colored);
            statement.setInt(6, currentSessionID);

            statement.executeUpdate();

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public int getLastID() {
        //harvested from Mr. Mein's JDBC Project
        //this will be called after a new session is made and will store a variable in the db class that holds the current session
        //this is then given to images
        try {
            int lastID = 0;
            Statement stmt = conn.createStatement();
            ResultSet rs;

            rs = stmt.executeQuery("SELECT LAST_INSERT_ID() AS id;");

            if (rs.next()) {
                lastID = rs.getInt(1);
                System.out.println("last id: " + lastID);
            }
            return lastID;
        } catch (Exception e) {
            return -1;
        }
    }

    public byte[] retrieveImage(int index) {
        InputStream image = null;
        byte[] data = null;
        try {
            String sql = "SELECT image FROM images WHERE images.image_id=" + index + ";";
            PreparedStatement statement = conn.prepareCall(sql);

            ResultSet rs = statement.executeQuery();
            rs.next();
            Blob b = rs.getBlob(1);
            data = b.getBytes(1, (int) b.length());

        } catch (Exception e) {
        }

        return data;
    }

    public String getOperators(int session_id) {
        String operators = "";
        try {
            String sql = "SELECT initials from operators, sessions_operators where sessions_operators.session_id = " + session_id + " and sessions_operators.operator_id = operators.operator_id;";
            PreparedStatement statement = conn.prepareCall(sql);
            ResultSet rs = statement.executeQuery();
            
            while(rs.next()){
                operators += rs.getString("initials") + ", ";
            }
        } catch (Exception e) {
        }
        return operators;
    }

    public String[] retrieveImageData(int index) {
        String[] imageData = null;
        try {
            String sql = "SELECT image_id, beam_kv, magnification, working_depth,up_votes, session_id FROM images WHERE images.image_id=" + index + ";";
            PreparedStatement statement = conn.prepareCall(sql);

            ResultSet rs = statement.executeQuery();
            
            rs.next();
            String imageIndex = Integer.toString(rs.getInt("image_id"));
            String kv = Integer.toString(rs.getInt("beam_kv"));
            String mag = Integer.toString(rs.getInt("magnification"));
            String wd = Integer.toString(rs.getInt("working_depth"));
            String uv = Integer.toString(rs.getInt("up_votes"));
            String ops = getOperators(rs.getInt("session_id"));
            //String ops = "EN, SH, GM";
            imageData = new String[]{imageIndex, kv, mag, wd, uv, ops};
        } catch (Exception e) {
        }

        return imageData;
    }

    public String[] updateVotes(int index, int increment) {
        try {
            String sql = null;
            //this is ugly but it works. Attempting to directly drop in the increment was failing
            if (increment == 1) {
                sql = "UPDATE images SET up_votes=up_votes + 1 WHERE image_id=" + index + ";";
            } else {
                sql = "UPDATE images SET up_votes=up_votes - 1 WHERE image_id=" + index + ";";
            }
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.executeUpdate();
            return retrieveImageData(index);
        } catch (Exception e) {
            System.out.println(e);
        }
        return null;
    }

    public Object[] getIndexesByPopularity() {
        ArrayList<Integer> al = new ArrayList<>();
        try {
            //this sql query will return a list of the indexes for images sorted by the most upvotes
            String sql = "SELECT image_id FROM images ORDER BY up_votes DESC;";
            PreparedStatement statement = conn.prepareStatement(sql);
            ResultSet rs = statement.executeQuery();
            //cram indexes into arraylist for shipping
            while (rs.next()) {
                al.add(rs.getInt("image_id"));
            }

        } catch (Exception e) {
            System.out.println(e);
        }
        return al.toArray();
    }

    public Object[] getIndexesByColored() {
        ArrayList<Integer> al = new ArrayList<>();
        try {
            //this sql query will return a list of the indexes for images sorted by the most upvotes
            String sql = "SELECT image_id FROM images WHERE colored=1 ORDER BY up_votes DESC;";
            PreparedStatement statement = conn.prepareStatement(sql);
            ResultSet rs = statement.executeQuery();
            //cram indexes into arraylist for shipping
            while (rs.next()) {
                al.add(rs.getInt("image_id"));
            }

        } catch (Exception e) {
            System.out.println(e);
        }
        return al.toArray();
    }

    public Object[] getIndexesByTimestamp() {
        ArrayList<Integer> al = new ArrayList<>();
        try {
            //this sql query will return a list of the indexes for images sorted by the most upvotes
            String sql = "SELECT image_id FROM images ORDER BY timestamp DESC;";
            PreparedStatement statement = conn.prepareStatement(sql);
            ResultSet rs = statement.executeQuery();
            //cram indexes into arraylist for shipping
            while (rs.next()) {
                al.add(rs.getInt("image_id"));
            }

        } catch (Exception e) {
            System.out.println(e);
        }
        return al.toArray();
    }
}
