package za.co.jpsoft.winkerkreader.data;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by Pieter Grobler on 15/03/2018.
 */

public class UpdateApp extends AsyncTask<Object, Void, Boolean> {
    Context context;

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }



    @Override
    protected Boolean doInBackground(Object... params) {
        Context context;

        this.context = (Context) params[0];

        //get destination to update file and set Uri
        //TODO: First I wanted to store my update .apk file on internal storage for my app but apparently android does not allow you to open and install
        //aplication with existing package from there. So for me, alternative solution is Download directory in external storage. If there is better
        //solution, please inform us in comment
        String destination = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/";
        String fileName = "WinkerkReader.apk";
        destination += fileName;
        final Uri uri = Uri.parse("file://" + destination);

        //Delete update file if exists
        File file = new File(destination);
        if (file.exists())
            //file.delete() - test this, I think sometimes it doesnt work
            file.delete();

        //get url of app on server
        String url = "http://www.jpsoft.co.za/wkr/WinkerkReader.apk";//Main.this.getString(R.string.update_app_url);

        //set downloadmanager
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDescription("Downloading WinkerkReader Update");
        request.setTitle("WinkerkReader");

        //set destination
        request.setDestinationUri(uri);

        // get download service and enqueue file
        final DownloadManager manager = (DownloadManager) this.context.getSystemService(Context.DOWNLOAD_SERVICE);
        final long downloadId = manager.enqueue(request);

        //set BroadcastReceiver to install app when .apk is downloaded
        BroadcastReceiver onComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                long reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId == reference) {

                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(reference);
                    Cursor cursor = manager.query(query);

                    cursor.moveToFirst();
                    int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(columnIndex);

                    int fileNameIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME);
                    String savedFilePath = cursor.getString(fileNameIndex);

                    int columnReason = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                    int reason = cursor.getInt(columnReason);

                    switch (status) {
                        case DownloadManager.STATUS_SUCCESSFUL:
                            Intent install = new Intent(Intent.ACTION_VIEW);
                            install.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            install.setDataAndType(uri,
                                    manager.getMimeTypeForDownloadedFile(downloadId));
                            ctxt.startActivity(install);
                            break;
                        case DownloadManager.STATUS_FAILED:
                            Toast.makeText(ctxt,"FAILED: " + reason, Toast.LENGTH_LONG).show();
                            break;
                        case DownloadManager.STATUS_PAUSED:
                            Toast.makeText(ctxt,"PAUSED: " + reason, Toast.LENGTH_LONG).show();
                            break;
                        case DownloadManager.STATUS_PENDING:
                            Toast.makeText(ctxt,"PENDING! ", Toast.LENGTH_LONG).show();
                            break;
                        case DownloadManager.STATUS_RUNNING:
                            Toast.makeText(ctxt,"RUNNING! ", Toast.LENGTH_LONG).show();
                            break;
                    }
                }
                ctxt.unregisterReceiver(this);
                //finish();
            }
        };
        //register receiver for when .apk download is compete
        this.context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        return null;
    }

    //  CHECK FOR APP UPDATE ON WEBSITE
// Download On My Mobile SDCard or Emulator.
    public void DownloadOnSDcard(String urlpath)
    {
        try{
            URL url = new URL(urlpath); // Your given URL.

            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("GET");
            c.setDoOutput(true);
            c.connect(); // Connection Complete here.!

            //Toast.makeText(getApplicationContext(), "HttpURLConnection complete.", Toast.LENGTH_SHORT).show();

            String PATH = Environment.getExternalStorageDirectory() + "/download/";
            File file = new File(PATH); // PATH = /mnt/sdcard/download/
            if (!file.exists()) {
                file.mkdirs();
            }
            File outputFile = new File(file, "WinkerkReader.apk");
            FileOutputStream fos = new FileOutputStream(outputFile);

            //      Toast.makeText(getApplicationContext(), "SD Card Path: " + outputFile.toString(), Toast.LENGTH_SHORT).show();

            InputStream is = c.getInputStream(); // Get from Server and Catch In Input Stream Object.

            byte[] buffer = new byte[1024];
            int len1 = 0;
            while ((len1 = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len1); // Write In FileOutputStream.
            }
            fos.close();
            is.close();//till here, it works fine - .apk is download to my sdcard in download file.
            // So plz Check in DDMS tab and Select your Emualtor.

            //Toast.makeText(getApplicationContext(), "Download Complete on SD Card.!", Toast.LENGTH_SHORT).show();
            //download the APK to sdcard then fire the Intent.
        }
        catch (IOException e)
        {

        }
    }

    private String downloadText() {
        int BUFFER_SIZE = 2000;
        InputStream in = null;
        try {
            in = openHttpConnection();
        } catch (IOException e1) {
            return "";
        }

        String str = "";
        if (in != null) {
            InputStreamReader isr = new InputStreamReader(in);
            int charRead;
            char[] inputBuffer = new char[BUFFER_SIZE];
            try {
                while ((charRead = isr.read(inputBuffer)) > 0) {
                    // ---convert the chars to a String---
                    String readString = String.copyValueOf(inputBuffer, 0, charRead);
                    str += readString;
                    inputBuffer = new char[BUFFER_SIZE];
                }
                in.close();
            } catch (IOException e) {
                return "";
            }
        }
        return str;
    }
    private InputStream openHttpConnection() throws IOException {
        InputStream in = null;
        int response = -1;

        URL url = new URL("http://www.jpsoft.co.za/wkr/version10.txt");
        URLConnection conn = url.openConnection();

        if (!(conn instanceof HttpURLConnection))
            throw new IOException("Not an HTTP connection");

        try {
            HttpURLConnection httpConn = (HttpURLConnection) conn;
            httpConn.setConnectTimeout(60000);
            httpConn.setAllowUserInteraction(false);
            httpConn.setInstanceFollowRedirects(true);
            httpConn.setRequestMethod("GET");
            httpConn.connect();

            response = httpConn.getResponseCode();
            if (response == HttpURLConnection.HTTP_OK) {
                in = httpConn.getInputStream();
            }
        } catch (Exception ex) {
            throw new IOException("Error connecting");
        }
        return in;
    }

}


