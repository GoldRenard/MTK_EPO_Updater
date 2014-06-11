package com.goldrenard.epoupdater;

/**
 * Интерфейс-враппер статуса загрузки
 * @author Renard Gold (Илья Егоров)
 */
public interface IDownloadStatus {
	public void onPreExecute();
	public void onProgressUpdate(Integer... progress);
	public void onPostExecute(String result);
}
