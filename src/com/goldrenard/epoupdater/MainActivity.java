package com.goldrenard.epoupdater;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.goldrenard.epoupdater.R;
import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.exceptions.RootDeniedException;
import com.stericson.RootTools.execution.Command;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Главное активити
 * @author Renard Gold (Илья Егоров)
 */
public class MainActivity extends Activity implements IDownloadStatus {

	private EditText mFileUrl;
	private EditText mEpoFile;
	private ImageButton mUrlReset;
	private ImageButton mEpoReset;
	private LinearLayout mStatusContainer;
	private TextView mStatus;
	private ProgressBar mProgress;
	
	private MenuItem mDownloadMenuItem;
	
	private SharedPreferences mPreferences;
	private final String PREF_EPO_URL = "pref_epo_url";
	private final String PREF_EPO_FILE = "pref_epo_file";
	private String DEST_DOWNLOAD_PATH = Environment.getExternalStorageDirectory().getPath() + "/EPO.DAT";
	private boolean isWorking = false;
	
	private DownloadTask mDownloader;
	private WakeLock mWakeLock;
	
	/**
	 * Инициализация активити
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		
		mFileUrl = (EditText)findViewById(R.id.epo_url);
		mUrlReset = (ImageButton)findViewById(R.id.reset_btn);
		mEpoFile = (EditText)findViewById(R.id.epo_file);
		mEpoReset = (ImageButton)findViewById(R.id.epo_reset_btn);
		
		mStatusContainer = (LinearLayout)findViewById(R.id.status_container);
		mStatus = (TextView)findViewById(R.id.status_text);
		mProgress = (ProgressBar)findViewById(R.id.status_progressBar);
		
		mFileUrl.setText(getRemoteURL());
		mUrlReset.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				mPreferences.edit().remove(PREF_EPO_URL).commit();
				if (mFileUrl != null) {
					mFileUrl.setText(getRemoteURL());
				}
			}
		});
		
		mEpoFile.setText(getLocalPath());
		mEpoReset.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				mPreferences.edit().remove(PREF_EPO_FILE).commit();
				if (mEpoFile != null) {
					mEpoFile.setText(getLocalPath());
				}
			}
		});
	}
	
	/**
	 * Старт загрузки
	 */
	private void doStart() {
		if (!Utils.isOnline(this)) {
			Toast.makeText(this, R.string.err_no_internet, Toast.LENGTH_SHORT).show();
			return;
		}
		setStatus(true, getString(R.string.epo_status_starting), 0);
		mUrlReset.setEnabled(false);
		mFileUrl.setEnabled(false);
		mEpoReset.setEnabled(false);
		mEpoFile.setEnabled(false);
		mDownloadMenuItem.setTitle(android.R.string.cancel);
		
		mDownloader = new DownloadTask(this);
		mDownloader.execute(mFileUrl.getText().toString(), DEST_DOWNLOAD_PATH);
		isWorking = true;
	}
	
	/**
	 * Стоп загрузки
	 */
	private void doStop(boolean isSuccess) {
		setStatus(false, null, null);
		mUrlReset.setEnabled(true);
		mFileUrl.setEnabled(true);
		mEpoReset.setEnabled(true);
		mEpoFile.setEnabled(true);
		mDownloadMenuItem.setTitle(R.string.epo_btn_download);
		
		mDownloader.cancel(false);
		isWorking = false;
		
		if (isSuccess) {
			setLocalPath(mEpoFile.getText().toString());
			setRemoteURL(mFileUrl.getText().toString());
		}
	}
	
	/**
	 * Установка нового EPO
	 */
	private void doInstall() {
		if (RootTools.isRootAvailable() && RootTools.isAccessGiven()) {
			setStatus(true, getString(R.string.epo_status_installing), 100);
			final String epoFile = mEpoFile.getText().toString();

			// Проверим, существует ли вообще скачанный файл
			File file = new File(DEST_DOWNLOAD_PATH);
			if (!file.exists()) {
				stopWithMessage(false, getString(R.string.err_download_no_file));
				return;
			}
			
			// Удаление старого, установка нового и установка новых прав
			String[] mCommands = new String[] { 
					String.format("rm \"%s\"", epoFile),
					String.format("cp \"%s\" \"%s\"", DEST_DOWNLOAD_PATH, epoFile),
					String.format("rm \"%s\"", DEST_DOWNLOAD_PATH),
					String.format("chown gps:nvram \"%s\"", epoFile),
					String.format("chmod 664 \"%s\"", epoFile)
			};
			
			try {
				RootTools.getShell(true).add(new Command(0, mCommands) {
					@Override
				    public void commandOutput(int id, String line) { }
				    @Override
				    public void commandTerminated(int id, String reason) {
				    	stopWithMessage(false, getString(R.string.epo_status_failed));
				    }
				    @Override
				    public void commandCompleted(int id, int exitCode) {
				    	stopWithMessage(true, getString(R.string.epo_status_completed));
				    }
				});
			} catch (IOException e) {
				stopWithMessage(false, getString(R.string.err_root_failed_ioex) + e.getLocalizedMessage());
			} catch (TimeoutException e) {
				stopWithMessage(false, getString(R.string.err_root_failed_timeout) + e.getLocalizedMessage());
			} catch (RootDeniedException e) {
				stopWithMessage(false, getString(R.string.err_root_required));
			}
		} else {
			stopWithMessage(false, getString(R.string.err_root_required));
		}
	}
	
	/**
	 * Перед загрузкой ставим вейллок
	 */
    @Override
    public void onPreExecute() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        mWakeLock.acquire();
        setStatus(true, null, null);
    }

    /**
     * Обновляем статус загрузки
     */
    @Override
    public void onProgressUpdate(Integer... progress) {
    	
    	if (progress[0] == -100) {
    		mDownloadMenuItem.setEnabled(false);
    		return;
    	}
    	
    	if (progress[0] == -101) {
    		mDownloadMenuItem.setEnabled(true);
    		return;
    	}
    	
        setStatus(true, 
        		getString(
        				R.string.epo_status_downloading, 
        				Utils.readableFileSize(progress[1]), 
        				Utils.readableFileSize(progress[2])
        		), progress[0]);
    }

    /**
     * Обработка загруженных данных
     */
    @Override
    public void onPostExecute(String result) {
        mWakeLock.release();
        if (result != null) {
        	stopWithMessage(false, getString(R.string.err_download) + result);
        } else {
        	setStatus(true, getString(R.string.epo_status_root_req), 100);
        	mFileUrl.post(new Runnable() {
				@Override
				public void run() { doInstall(); }
			});
        }
    }
    
    /**
     * Создание меню
     */
    public boolean onCreateOptionsMenu(Menu menu) {
    	mDownloadMenuItem = menu.add(0, 1, 0, R.string.epo_btn_download);
    	mDownloadMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return super.onCreateOptionsMenu(menu);
      }
    
    /**
     * Обработчик меню
     */
    public boolean onOptionsItemSelected(MenuItem menuitem){
        if (menuitem.getItemId() == 1) {
			if (isWorking) {
				doStop(false);
			} else {
				doStart();
			}
        }
        return super.onOptionsItemSelected(menuitem);
    }
    
    /**
     * Показ сообщения и остановка
     */
    private void stopWithMessage(boolean isSuccess, String error_text) {
    	Toast.makeText(this, error_text, Toast.LENGTH_LONG).show();
        doStop(isSuccess);
    }
	
	/**
	 * Установка статуса
	 * @param isVisible Видимо ли
	 * @param text Текст статуса
	 * @param progress Прогресс статуса (максимум 100)
	 */
	private void setStatus(Boolean isVisible, String text, Integer progress) {
		if (mStatusContainer != null && isVisible != null) {
			mStatusContainer.setVisibility(isVisible ? View.VISIBLE : View.GONE);
		}
		if (mStatus != null && text != null) {
			mStatus.setText(text);
		}
		if (mProgress != null && progress != null) {
			mProgress.setProgress(progress);
		}
	}
	
	/**
	 * Получение стандартного EPO FILE URL
	 * @return
	 */
	private String getRemoteURL() {
		return mPreferences.getString(PREF_EPO_URL, getString(R.string.epo_def_url));
	}
	
	/**
	 * Установка нового URL
	 * @param url Новый URL
	 */
	private void setRemoteURL(String url) {
		mPreferences.edit().putString(PREF_EPO_URL, url).commit();
	}
	
	/**
	 * Получение стандартного EPO FILE URL
	 * @return
	 */
	private String getLocalPath() {
		return mPreferences.getString(PREF_EPO_FILE, getString(R.string.epo_def_file));
	}
	
	/**
	 * Установка нового пути
	 * @param path Новый путь
	 */
	private void setLocalPath(String path) {
		mPreferences.edit().putString(PREF_EPO_FILE, path).commit();
	}
}
