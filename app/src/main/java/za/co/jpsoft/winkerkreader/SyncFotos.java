package za.co.jpsoft.winkerkreader;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;

import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import za.co.jpsoft.winkerkreader.data.WinkerkContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;

import static android.R.attr.id;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.CacheDir;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.FotoDir;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_FOTO_PATH;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_GROUP;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_LIDMAAT_GUID;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.INFO_LOADER_FOTO_URI;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_LIDMAATGUID;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_PICTUREPATH;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.THUMBSIZE;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.WINKERK_DB;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by Pieter Grobler on 13/09/2017.
 */

public class SyncFotos  extends AppCompatActivity {
    final String MEDIA_PATH = Environment.getExternalStorageDirectory()
            .getPath() + "/";
    final String MEDIA_PATH2 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .getPath() + "/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //ArrayList<HashMap<String, String>> fileList = new ArrayList<HashMap<String, String>>();

        ArrayList<HashMap<String, String>> CacheList = new ArrayList<>();
        ArrayList<HashMap<String, String>> FotoList = new ArrayList<>();
        ArrayList<HashMap<String, String>> casheList = new ArrayList<>();
        String mPattern = WINKERK_DB;
        Boolean delete = false;

        String path = "";
        FotoList = getFileList(FotoDir);
        CacheList = getFileList(CacheDir);

        if (FotoList != null)
        if (FotoList.size() > 0) {
            for (int i = 0; i <= (FotoList.size() - 1); i++) {
                FotoList.get(i);
                path = FotoList.get(i).get("Title");

                copyFoto(FotoList.get(i).get("Path"), FotoList.get(i).get("Title"));
                ContentValues values = new ContentValues();
                values.put(INFO_FOTO_PATH, path);
                values.put(INFO_LIDMAAT_GUID, path.substring(0,path.length()-4));
                values.put(INFO_GROUP, "");

                Uri currentLidmaat = ContentUris.withAppendedId(INFO_LOADER_FOTO_URI, 1);
                if (values.size() > 0) {
                    if (getContentResolver().update(INFO_LOADER_FOTO_URI,
                            values,
                            INFO_LIDMAAT_GUID + " =?",
                            new String[]{ path.substring(0,path.length()-4) }) == 0) {
                        getContentResolver().insert(INFO_LOADER_FOTO_URI, values);
                    }
                }
                values.clear();
                values.put(LIDMATE_PICTUREPATH, path);
                //values.put(INFO_LIDMAAT_GUID, mLidmaatGUID);

                currentLidmaat = ContentUris.withAppendedId(WinkerkContract.winkerkEntry.CONTENT_URI, id);
                if (values.size() > 0) {
                    getContentResolver().update(currentLidmaat,
                            values,
                            "quote(" + LIDMATE_LIDMAATGUID + ") =?",
                            new String[]{ path.substring(0,path.length()-4) });
                }
            }
        }
        Toast.makeText(getApplicationContext(), FotoList.size() + " Fotos gesinkroniseer", Toast.LENGTH_SHORT).show();
    finish();
    }

    private String copyFoto(String path, String GUID) {

        if ( path.isEmpty()) {return "";}
        //Bitmap bitmap = BitmapFactory.decodeFile(path);
        WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = THUMBSIZE;//size.x;
        int height = THUMBSIZE;//size.y;
        OutputStream outStream = null;
        Bitmap bitmap;

        File file = new File(CacheDir);
        if(!file.exists()) {
            file.mkdirs();
        }
        file = new File(CacheDir, GUID);
        if (file.exists()) {
            file.delete();
            file = new File(CacheDir, GUID );
            Log.e("file exist", file + ", Bitmap= " + GUID);
        }
        try {
            // make a new bitmap from your file
            bitmap = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(path) , width, height);
            outStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
            outStream.flush();
            outStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.e("file", "" + file);

        return path;
    }

    /**
     * Function to read all winkerk_droid.db files and store the details in
     * ArrayList
     * */
    private ArrayList<HashMap<String, String>> getFileList(String searchpath) {
        ArrayList<HashMap<String, String>> fileList = new ArrayList<>();
        System.out.println(searchpath);
        if (searchpath != null) {
            File home = new File(searchpath);
            File[] listFiles = home.listFiles();
            if (listFiles != null) {
                for (File file : listFiles) {
                    System.out.println(file.getAbsolutePath());
                    if (!file.isDirectory()) {
                        //   scanDirectory(file);
                        //} else {
                        fileList.add(addFileToList(file));
                    }
                }
            }
        }
        // return file list array
        return fileList;
    }

    private void scanDirectory(File directory) {
        if (directory != null) {
            File[] listFiles = directory.listFiles();
            if (listFiles != null) {
                for (File file : listFiles) {
                    if (file.isDirectory()) {
                        scanDirectory(file);
                    } else {
                        addFileToList(file);
                    }

                }
            }
        }
    }

    private HashMap<String, String> addFileToList(File mfile) {
        ArrayList<HashMap<String, String>> fileList = new ArrayList<>();
            HashMap<String, String> fileMap = new HashMap<>();
            fileMap.put("Title", mfile.getName());
            fileMap.put("Path", mfile.getPath());
            // Adding each file to mfileList
            return fileMap;

    }

    // procedure to write file to disk
    private static void writeExtractedFileToDisk(InputStream in, OutputStream outs) throws IOException {
        byte[] buffer = new byte[1024];
        int length;
        while ((length = in.read(buffer)) > 0) {
            outs.write(buffer, 0, length);
        }
        outs.flush();
        outs.close();
        in.close();
    }


    private boolean checkPermission() { // write permissions
        /** int result = ContextCompat.checkSelfPermission(this, Manifest.permission.);
        if (result == PackageManager.PERMISSION_GRANTED) {
            result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            if (result == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }*/
        return false;
    }
}