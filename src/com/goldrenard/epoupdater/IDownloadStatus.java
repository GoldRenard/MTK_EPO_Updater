package com.goldrenard.epoupdater;

/**
 * ���������-������� ������� ��������
 * @author Renard Gold (���� ������)
 */
public interface IDownloadStatus {
	public void onPreExecute();
	public void onProgressUpdate(Integer... progress);
	public void onPostExecute(String result);
}
