/* Globals
 * Michael Kirk 2013
 * 
 * Extension of Application Class for holding application wide variables.
 * Provides also an extension of Activity which adds a variable g - an instance
 * of the Globals class (which holds application wide variables).
 * 
 * NB Global vars in this class that are set by activities cannot be assumed to
 * be set. In particular is Activity A sets a variable, which is then referenced
 * by Activity B, then if there is a restart while B is active, the variable may
 * not be set (since after restart activity B will start directly, without A running
 * first to set the var).
 */
package csiro.fieldprime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

import csiro.fieldprime.R;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

public class Globals extends Application implements OnSharedPreferenceChangeListener {
	static Globals g;   // Have static copy available for reference that
	                    // don't have their activity's g variable in scope.
	                    // Although it should be possible for this not to be static.
	
	// MFK - all should be static? init()?
	private Trial cTrial;  // Current open trial, should be null if none open
	
	/* mCurrentActivity static as static function FragMan() uses it. This function
	 * should probably not be used - it is used mainly be dialogFragments, and they
	 * should have access to the activity and its fragment manager directly (have to
	 * pass activity into static newInstance) see DlgPrintSetup.
	 */
    static private Activity mCurrentActivity = null;
    private Prefs mPrefs;
    private String cAndroidID;
    
    private Database mDatabase = null;
	private final String DBNAME = "fprime.fpdb";
	private String mAppRootDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/FieldPrime/";
	 
    // Screen metrics:
	static public float cScreenHeightDp;
	public float cScreenWidthDp;
	public int cScreenHeightPx;
	public int cScreenWidthPx;
	private boolean cScreenBig;
	public float cScreenDensity;
	
	private String mPrinterMacAddr;  // This only here till I think of how to do it properly..
	
	/*
	 * Screen size.
	 * We want to have a record of whether we are on a big or small screen.
	 */
	public enum ScreenSize {
		SMALL_SCREEN,
		BIG_SCREEN
	}
	ScreenSize mScreenSize;
	/*
	 *  Screen item size names. Rather than setting sizes explicitly for every screen item,
	 *  the idea is that we just specify whether they are small, medium or large items.
	 *  And then have a lookup table for appropriate heights for these categories (taking into
	 *  account the screen size.
	 */
	static final int SIZE_DEFAULT = 0;
	static final int SIZE_SMALL = 1;
	static final int SIZE_MEDIUM = 2;
	static final int SIZE_BIG = 3;
	
	static public String mAppName;
	
	// Various text sizes intended for use in setTextSize functions: 
	static public Float cDefTextSize = 20f;   // fixed size
	static public Float cMidTextSize;        // dependent on screen height
	static public Float cSmallTextSize;      // dependent on screen height

	static private String cUsername = "nobody";
	static public TextToSpeech mTts;
	static private GPSListener cGPS;
	static public ToneGenerator cToneGenerator;
	
	static private String cCurrentAsyncOp = null;

	// Constructor: ----------------------------------------------------------

	/*
	 * init()
	 * Initialises stuff as necessary. Should be called as early as possible
	 * (i.e. before any use).
	 * MFK - the  listeners/receivers probably need closing in an onStopfor the
	 * activity class.
	 * Or the TextToSpeech thing should probably be set up in ActScoring, or only
	 * when speech is specified in preferences (although these can be set outside of
	 * an open trial, which may be an issue).
	 */
	private void init() {
		mAppName = getResources().getString(R.string.app_name);
		getScreenMetrics();
		cMidTextSize = cScreenHeightDp / 20;
		cSmallTextSize = cMidTextSize / 2;
		cAndroidID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID); // Get and save the ANDROID_ID
		mPrefs = new Prefs(this);
		
		// Setup Text to Speech:
		mTts = new TextToSpeech(this, new OnInitListener(){
			@Override
			public void onInit(int status) {
				if (status == TextToSpeech.SUCCESS) {
					int result = mTts.setLanguage(Locale.UK);
					if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
						Util.msg("Text to Speech: This Language is not supported");
					}
				} else {
					Util.msg("Text to Speech: Initilization Failed");
				}
			}	
		});
		
		// Fire up the GPS  (maybe this should happen only when scoring?)
		cGPS = new GPSListener(this); // does this have to persist? Util?

		// Create tone generator for notifications:
		//cToneGenerator = new ToneGenerator(AudioManager.STREAM_DTMF, ToneGenerator.MAX_VOLUME);
		cToneGenerator = new ToneGenerator(AudioManager.STREAM_DTMF, 80);
		g = this;
	}
	
	/*
	 * getAppRootDir()
	 * Return the full path to app root dir, creating it first if necessary.
	 */
	public String getAppRootDir() {
		File fRoot = new File(mAppRootDir);
		if (!fRoot.exists()) {
			if (!fRoot.mkdir())
				return null;
		}
		return mAppRootDir;
	}
	
	/*
	 * getDb()
	 * Returns SQLiteDatabase, opening it if necessary.
	 * NB we must fetch the SQLiteDatabase from mDatabase each time, not cached, since this
	 * may change.
	 */
	public SQLiteDatabase db() {
		SQLiteDatabase fred = getDatabase().getDatabase();
		return fred;
	}
	
	/*
	 * getDatabase()
	 * Get the Database object in use.
	 */
	public Database getDatabase() {
		if (mDatabase == null || !mDatabase.getDatabase().isOpen()) { // MFK NOT CLEAR why 2nd check needed, but crashes about
			// every second time if not there - what's closing the db?
			mDatabase = new Database(this, mAppRootDir + DBNAME);
		}
		return mDatabase;
	}
		
	/*
	 * Current Trial.
	 */
	public Trial currTrial() { return cTrial; }
	public void setTrial(Trial trl) { cTrial = trl; }
	private boolean trialOpen() { return cTrial != null; }

	/*
	 * Screen size stuff:
	 */
	private void getScreenMetrics() {
		// Get the screen dimensions:
		WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();	
		//Display display = ctx.getWindowManager().getDefaultDisplay(); // old way when had ctx Activity param - DEL when happy
		DisplayMetrics outMetrics = new DisplayMetrics();
		display.getMetrics(outMetrics);
		cScreenDensity = getResources().getDisplayMetrics().density;
		cScreenHeightPx = outMetrics.heightPixels;
		cScreenWidthPx = outMetrics.widthPixels;
		cScreenHeightDp = px2dp(cScreenHeightPx);
		cScreenWidthDp = px2dp(cScreenWidthPx);
		cScreenBig = px2dp(cScreenHeightPx) >= 700;
		mScreenSize = cScreenBig ? ScreenSize.BIG_SCREEN : ScreenSize.SMALL_SCREEN;
	}
	public ScreenSize screenSize() { return mScreenSize; }
	public boolean screenIsBig() { return cScreenBig; }

	/*
	 * TextSize for display in spp, dependent on screen size:
	 * NB, the sizeRange here indicates whether the caller want small, medium, or
	 * big text, relative to other screen items. How big big is, will depend on the
	 * screen size.
	 */
	public int TextSize(int sizeRange) {
		switch (sizeRange) {
		case SIZE_SMALL:
			return g.screenIsBig() ? 20 : 12;
		case SIZE_MEDIUM:
			return g.screenIsBig() ? 40 : 20;
		case SIZE_BIG:
			return g.screenIsBig() ? 40 : 25;
		}
		return 10;
	}
	
	/*
	 * Size for display in dp, dependent on screen size:
	 */
	public int ScreenItemSize(int sizeRange) {
		switch (sizeRange) {
		case SIZE_SMALL:
			return g.screenIsBig() ? 30 : 20;
		case SIZE_MEDIUM:
			return g.screenIsBig() ? 60 : 40;
		case SIZE_BIG:
			return g.screenIsBig() ? 100 : 60;
		}
		return 10;
	}

	/*
	 * FragMan()
	 * Returns fragment manager from current activity (and will crash if there is none).
	 */
	static public android.app.FragmentManager FragMan() {
		return mCurrentActivity.getFragmentManager();
	}

	/*
	 * px2dp()
	 * Converts pixels to dp units.
	 */
	public int px2dp(int px) {
		return (int) ((px / cScreenDensity) + 0.5);
	}
	
    public Activity getCurrentActivity(){
          return mCurrentActivity;
    }
    public void setCurrentActivity(Activity currentActivity){
          mCurrentActivity = currentActivity;
    }
	
	public Prefs prefs() { return mPrefs; }
	public String AndroidID() { return cAndroidID; }
	
	/*
	 * Class Globals.Activity
	 * 
	 * Extends Activity by adding a variable g, an instance of the Globals class.
	 * Activities that need access to the global vars can extend this instead of Activity
	 * and then will have a "g" variable holding the globals in scope.
     */
	static public class Activity extends android.app.Activity{
		static Globals g;
	 	@Override
		protected void onCreate(Bundle savedInstanceState) {
		    super.onCreate(savedInstanceState);
		    if (g == null) {
		    	g = (Globals) getApplicationContext();
		    	g.init();
		    }
		    g.setCurrentActivity(this);
	 	}
		@Override
		protected void onResume() {
			super.onResume();
		    if (g == null) {
		    	g = (Globals) getApplicationContext();
		    	g.init();
		    }
			g.setCurrentActivity(this);
		}
//		@Override
//		public void onRestoreInstanceState(Bundle savedInstanceState) {
//			super.onRestoreInstanceState(savedInstanceState);
//			// TODO Auto-generated method stub
//			
//		}
		@Override
		protected void onDestroy() {
		    //Close the Text to Speech Library
		    if(mTts != null) {
		        mTts.stop();
		        mTts.shutdown();
		        Log.d("Globals.Activity", "TTS Destroyed");
		    }
		    super.onDestroy();
		}
		
//	    protected MyApp mMyApp;
//
//	    public void onCreate(Bundle savedInstanceState) {
//	        super.onCreate(savedInstanceState);
//	        mMyApp = (MyApp)this.getApplicationContext();
//	    }
//	    protected void onResume() {
//	        super.onResume();
//	        mMyApp.setCurrentActivity(this);
//	    }
//	    protected void onPause() {
//	        clearReferences();
//	        super.onPause();
//	    }
//	    protected void onDestroy() {        
//	        clearReferences();
//	        super.onDestroy();
//	    }
//
//	    private void clearReferences(){
//	        Activity currActivity = mMyApp.getCurrentActivity();
//	        if (currActivity != null && currActivity.equals(this))
//	            mMyApp.setCurrentActivity(null);
//	    }
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (mCurrentActivity != null) {
			// MFK should use reflection to see if pref change supported by current act.
			//mCurrentActivity.getClass().getMethod(", parameterTypes)" +
			if (mCurrentActivity instanceof OnSharedPreferenceChangeListener) {
				((OnSharedPreferenceChangeListener) mCurrentActivity).onSharedPreferenceChanged(sharedPreferences, key);
			}
		}
		
	}
	
	/*
	 * setBusy()
	 * If the system is busy, display busy message and return false,
	 * else mark the system as busy, and return true.
	 */
	static public boolean setBusy(String operation) {
		boolean busy = isBusy();
		if  (busy)
			return false;
		// not busy, so set operation and return true:
		cCurrentAsyncOp = operation;
		return true;
	}
	/*
	 * isBusy()
	 * Return boolean indicating if system is busy or not.
	 * If we are busy, then message is posted on screen.
	 */
	static public boolean isBusy() {
		if (cCurrentAsyncOp != null) {
			Util.msg("Operation already in progress (" + cCurrentAsyncOp + "). Please try again later");
			return true;
		}
		return false;
	}
	/*
	 * stopBusy()
	 * Mark system as not busy.
	 */
	static public void stopBusy() {
		cCurrentAsyncOp = null;
	}
	
	static public String getUsername() {
		return cUsername;
	}
	static public void setUsername(String username) {
		cUsername = username;
	}
	
	/*
	 * ReadFileIntoString MFK Need to report back error description somehow
	 */
	static public String ReadFileIntoString(String filename, boolean elideLineSeps) {
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(filename));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
		String line = null;
		StringBuilder stringBuilder = new StringBuilder();
		String ls = System.getProperty("line.separator");
		try {
			while ((line = reader.readLine()) != null) {
				stringBuilder.append(line);
				if (!elideLineSeps)
					stringBuilder.append(ls);
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return stringBuilder.toString();
	}

	public void setPrinterMacAddr(String address) { mPrinterMacAddr = address; }
	public String getPrinterMacAddr() { return mPrinterMacAddr; }
}


// Old Code: -------------------------------------------------------------------------------------------------
//	static int getListViewHeight(ListView list) {
//		ListAdapter adapter = list.getAdapter();
//		list.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED),
//				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
//		return list.getMeasuredHeight() * adapter.getCount() + (adapter.getCount() * list.getDividerHeight());
//	}
