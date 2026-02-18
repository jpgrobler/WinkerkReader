package za.co.jpsoft.winkerkreader;

import android.content.Context;

import za.co.jpsoft.winkerkreader.data.WinkerkContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Created by Pieter Grobler on 21/08/2017.
 */

public class Installation {
    private static final String INSTALLATION = "INSTALLATION";

    public synchronized static Boolean id(String id, Context context) {
        Boolean  sID = false;
        int key = -1;
        if (id.length()>7){
            key = Character.getNumericValue(id.charAt(1)) + Character.getNumericValue(id.charAt(4)) * Character.getNumericValue(id.charAt(8));}
        if (!sID) {
            File installation = new File(context.getFilesDir(), INSTALLATION);
            try {
                sID = readInstallationFile(installation, id);
            } catch (Exception e) {
                sID = false;
            }
        }
        return sID;
    }

    public synchronized static Boolean write(String id, Context context) throws IOException {
        File installation = new File(context.getFilesDir(), INSTALLATION);
        try {
            writeInstallationFile(installation,id);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return id(WinkerkContract.winkerkEntry.id ,context);
    }

    private static Boolean readInstallationFile(File installation, String id) throws IOException {
        RandomAccessFile f = new RandomAccessFile(installation, "r");
        byte[] bytes = new byte[(int) f.length()];
        f.readFully(bytes);
        f.close();
        String lid = new String(bytes);
        int key = Character.getNumericValue(id.charAt(1)) + Character.getNumericValue(id.charAt(4)) * Character.getNumericValue(id.charAt(8));

        return lid.equals(Integer.toString(key));
    }

    private static void writeInstallationFile(File installation, String id) throws IOException {
        FileOutputStream out = new FileOutputStream(installation);
        out.write( id.getBytes());
        out.close();
    }
}
