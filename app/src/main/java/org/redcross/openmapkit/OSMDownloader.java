package org.redcross.openmapkit;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

import com.mapbox.mapboxsdk.geometry.BoundingBox;

/**
 * Created by Nicholas Hallahan on 3/24/15.
 * nhallahan@spatialdev.com
 * * * 
 */
public class OSMDownloader extends AsyncTask<Void, String, Long> {

    private static final String OVERPASS_API_URL = "http://overpass-api.de/api/interpreter?data=";
    private static final String PROGRESS_MSG = "Downloading OSM XML from Overpass API:\n\n";
    
    private String queryTemplate = "(way[building]({{bbox}});way[highway]({{bbox}}););out meta;>;out meta qt;";
    private String fileName = "overpass.osm";

    private long downloadId;
    private boolean downloading = true;
    
    private Activity activity;
    private BoundingBox bbox;
    private DownloadManager downloadManager;
    private ProgressDialog progressDialog;
    private BroadcastReceiver broadcastReceiver;


    /**
     * 
     * @param activity  the Activity that is calling the OSMDownloader
     * @param bbox      the bounding box the map is at
     */
    public OSMDownloader(Activity activity, BoundingBox bbox) {
        super();
        this.activity = activity;
        this.bbox = bbox;
        downloadManager = (DownloadManager) activity.getSystemService(Activity.DOWNLOAD_SERVICE);
    }

    /**
     *
     * @param activity  the Activity that is calling the OSMDownloader
     * @param bbox      the bounding box the map is at
     * @param query     the OverpassQL Query
     * @param fileName  the name of the file to be written to disk
     */
    public OSMDownloader(Activity activity, BoundingBox bbox, String query, String fileName) {
        this(activity, bbox);
        this.queryTemplate = query;
        this.fileName = fileName;
    }

    public void cancel() {

    }
    
    @Override
    protected void onPreExecute() {
        setupProgressDialog();    
    }

    /**
     * This background thread is handling the updates for the progress bar.
     * The actual downloading is happening in a separate system service (DownloadManager).
     * * * * 
     * @param nothing
     * @return
     */
    @Override
    protected Long doInBackground(Void... nothing) {
        String query = composeQuery();
        String url = OVERPASS_API_URL + query;
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDestinationInExternalPublicDir(ExternalStorage.getOSMDir(), fileName);
        downloadId = downloadManager.enqueue(request);
        pollDownloadManager();
        return downloadId;
    }

    @Override
    protected void onProgressUpdate(String... msgs) {
        String msg = msgs[0];
        progressDialog.setMessage(msg);
    }

    @Override
    protected void onPostExecute(Long downloadId) {
        
    }

    private String composeQuery() {
        String bboxStr = bbox.getLatSouth() + "," +
                bbox.getLonWest()  + "," +
                bbox.getLatNorth() + "," +
                bbox.getLonEast();
        return queryTemplate.replaceAll("\\{\\{bbox\\}\\}", bboxStr);
    }
    
    private void setupProgressDialog() {
        progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Downloading OSM Data");
        progressDialog.setMessage("Starting download...");
        progressDialog.setIndeterminate(true);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(true);
        progressDialog.show();        
    }
    
    private void setupBroadcastReceiver() {
                
    }
    
    private void pollDownloadManager() {
        while (downloading) {
            DownloadManager.Query q = new DownloadManager.Query();
            q.setFilterById(downloadId);
            Cursor cursor = downloadManager.query(q);
            cursor.moveToFirst();
            final int bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            final String msg = PROGRESS_MSG + ((double)bytesDownloaded) / 1000000.0 + " MB";
            publishProgress(msg);
            statusMessage(cursor, bytesDownloaded);
            if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                downloading = false;
            }
            // throttle the thread
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } 
    }

    private String statusMessage(Cursor c, int bytesDownloaded) {
        String msg;
        switch (c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
            case DownloadManager.STATUS_FAILED:
                msg = "Download failed: " + bytesDownloaded + " bytes downloaded.";
                break;

            case DownloadManager.STATUS_PAUSED:
                msg = "Download paused: " + bytesDownloaded + " bytes downloaded.";
                break;

            case DownloadManager.STATUS_PENDING:
                msg = "Download pending: " + bytesDownloaded + " bytes downloaded.";
                break;

            case DownloadManager.STATUS_RUNNING:
                msg = "Download in progress: " + bytesDownloaded + " bytes downloaded.";
                break;

            case DownloadManager.STATUS_SUCCESSFUL:
                msg = "Download complete: " + bytesDownloaded + " bytes downloaded.";
                break;

            default:
                msg = "STATUS MESSAGE ERROR";
                break;
        }
        return (msg);
    }
}
