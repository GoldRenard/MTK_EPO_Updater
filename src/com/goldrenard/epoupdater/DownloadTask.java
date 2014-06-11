package com.goldrenard.epoupdater;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import android.os.AsyncTask;

/**
 * ������ ��������
 * @author Renard Gold (���� ������)
 */
public class DownloadTask extends AsyncTask<String, Integer, String> {
    private IDownloadStatus mListener;
    
    public DownloadTask(IDownloadStatus stListener) {
        mListener = stListener;
    }

    @Override
    protected String doInBackground(String... sUrl) {
        InputStream input = null;
        OutputStream output = null;
        URLConnection connection = null;
        try {
            URL url = new URL(sUrl[0]);
            String dest = sUrl[1];
            connection = (URLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            
            publishProgress(-100);
            connection.connect();
            publishProgress(-101);

            if (connection instanceof HttpURLConnection) {
	            // ������� HTTP 200 OK, ��� ��� �� �� ����� ��������� ����������
	            // �������� ������ ������ �����
            	
            	HttpURLConnection httpconnection = (HttpURLConnection)connection;
	            if (httpconnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
	                return "Server returned HTTP " + httpconnection.getResponseCode()
	                        + " " + httpconnection.getResponseMessage();
	            }
            }

            // �������� ������ ����� (������ �� ����� ���� -1, ���������
            // ��������� ������ �� ������ ���������� ������
            int fileLength = connection.getContentLength();

            // ��������� ����
            input = connection.getInputStream();
            output = new FileOutputStream(dest);

            byte data[] = new byte[4096];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                // ��������� ������ ��������
                if (isCancelled()) {
                    input.close();
                    mListener = null;
                    return null;
                }
                total += count;
                // �������� � ��������
                if (fileLength > 0) { // ������ ���� ������ ����� ��������
                    publishProgress((int) (total * 100 / fileLength), (int)total, fileLength);
                }
                output.write(data, 0, count);
            }
        } catch (Exception e) {
        	publishProgress(-101);
            return e.toString();
        } finally {
            try {
                if (output != null) {
                    output.close();
                }
                if (input != null) {
                    input.close();
                }
            } catch (IOException ignored) { }

            if (connection != null) {
                if (connection instanceof HttpURLConnection) {
    	            ((HttpURLConnection)connection).disconnect();
                }
            }
        }
        return null;
    }
    
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (mListener != null) {
        	mListener.onPreExecute();
        }
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        super.onProgressUpdate(progress);
        if (mListener != null) {
        	mListener.onProgressUpdate(progress);
        }
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        if (mListener != null) {
        	mListener.onPostExecute(result);
        }
    }
}