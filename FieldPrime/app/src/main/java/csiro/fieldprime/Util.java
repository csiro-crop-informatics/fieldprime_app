/*
 * Util class
 * Place for generally useful static functions.
 */

package csiro.fieldprime;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.http.HttpStatus;

import csiro.fieldprime.R;
import csiro.fieldprime.Util.LogMethod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Location;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class Util {
	static Globals g;
	static Context mAppCtx;

	/*
	 * Init()
	 * Store context for later use, and do some initialization.
	 * This should be called before any other functions.
	 */
	public static void Init(Context ctx) {
		mAppCtx = ctx;
		g = Globals.g;
	}

	/*
	 * msg()
	 * Displays message - NB, this is asynchronous, so if you display call twice in a row,
	 * you will see the second message first (because it's on top) and then the first.
	 */
	public static void msg(String msg, String title, DialogInterface.OnClickListener listener) {
		// Toast.makeText(cCtx, m, Toast.LENGTH_LONG).show();
		AlertDialog.Builder builder = new AlertDialog.Builder(g.getCurrentActivity());
	    builder.setIcon(R.drawable.ic_launcher)
	    .setTitle(title)
		.setMessage(msg)
		.setPositiveButton("OK", listener)
		.show();
	}

	public static void msg(String msg, String title) {
		msg(msg, title, null);
	}
	public static void msg(String msg) {
		msg(msg, Globals.mAppName, null);
	}

	public static void toast(String msg) {
    	Toast.makeText(g.getCurrentActivity(), msg, Toast.LENGTH_SHORT).show();
	}
	
	/*
	 * exceptionHandler()
	 * Displays exception message.
	 */
	public static void exceptionHandler(Exception e, String tag) {
		StackTraceElement ste = e.getStackTrace()[0];
		Log.e("exception", Log.getStackTraceString(e));
		//e.printStackTrace();  // redundant?
		msg(tag + "exception in " + ste.getMethodName() + ":" + ste.getLineNumber() + ": " + e.getMessage());
	}

	/*
	 * dp2px()
	 * Converts dp units to pixels.
	 */
	public static int dp2px(int dp) {
		return (int) ((dp * g.cScreenDensity) + 0.5);
	}

	/*
	 * px2dp()
	 * Converts pixels to dp units.
	 */
	public static int px2dp(int px) {
		return g.px2dp(px);
		//return (int) ((px / g.cScreenDensity) + 0.5);
	}

	/*
	 * isNumeric()
	 * Test if string is a number with optional '-' and decimal point.
	 */
	public static boolean isNumeric(String str) {
		return str.matches("-?\\d+(\\.\\d+)?");
	}

	/*
	 * isInteger()
	 * Test if string is a number containing only digits.
	 */
	public static boolean isInteger(String str) {
		return str.matches("\\d+");
	}

	/*
	 * JapaneseDayString() Returns current date as yyyymmdd string.
	 * JapaneseDayNumber() Returns current date as integer version of yyyymmdd.
	 */
	public static String JapaneseDayString() {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		Date date = new Date();
		return dateFormat.format(date);
	}
	
	static private String [] monthShortNames = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
	public static String JapaneseDayNiceString(int date) {
//		SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy");
//		//Date date = new Date();
//		Date date = new Date(1923, 5, 5);
		return monthShortNames[JapaneseDateMonth(date)-1] + " " + JapaneseDateDay(date) + " " + JapaneseDateYear(date);
	}

	public static int JapaneseDayNumber() {
		return Integer.parseInt(JapaneseDayString());
	}
	
	public static int JapaneseDateYear(int jDate) {
		return jDate / 10000;
	}
	public static int JapaneseDateMonth(int jDate) {
		return (jDate % 10000) / 100;
	}
	public static int JapaneseDateDay(int jDate) {
		return jDate % 100;
	}
	public static int JapaneseDate(int year, int month, int day) {
		return year * 10000 + month * 100 + day;
	}
	
	public static String timestamp2String(long timestamp) {
		Date date = new Date(timestamp);
		return date.toString();
	}

	/*
	 * DisplayScreenSizeInfo()
	 * For development purposes. Shows screen size category, and the height and width in dp.
	 */
	static void DisplayScreenSizeInfo() {
		Configuration config = mAppCtx.getResources().getConfiguration();
		switch (config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) {
		case Configuration.SCREENLAYOUT_SIZE_XLARGE:
			Util.msg("X large");
			break;
		case Configuration.SCREENLAYOUT_SIZE_LARGE:
			Util.msg("large");
			break;
		case Configuration.SCREENLAYOUT_SIZE_NORMAL:
			Util.msg("normal");
			break;
		}
	}

	/*
	 * Background and text colours:
	 */
	static void setColoursWhiteOnBlack(TextView v) {
		v.setBackgroundResource(R.color.black);
		v.setTextColor(Color.parseColor("#FFFFFF"));
	}
	static void setColoursBlackOnWhite(TextView v) {
		v.setBackgroundResource(R.color.white);
		v.setTextColor(Color.parseColor("#000000"));
	}
	static void setBackgroundBlack(View v) {
		v.setBackgroundResource(R.color.black);
	}
	
	
	/*
	 * SetupScreenWidthTextView()
	 * Format a textview (eg edit box or button) to fill the screen horizontally,
	 * and be an appropriate height.
	 * Colour set to white on black, or black on white as specified.
	 */
	static void SetupScreenWidthTextView(TextView tv, boolean whiteOnBlack) {
		// Set colors:
		if (whiteOnBlack)
			setColoursWhiteOnBlack(tv);
		else 
			setColoursBlackOnWhite(tv);
		
		// Set height and font size appropriate for the screen size:
		if (g.cScreenHeightDp < 1000) {
			tv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp2px(60)));
			tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 40);
		} else {
			tv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp2px(120)));
			tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 80);
		}
	}
	// Default version set to white on black:
	static void SetupScreenWidthTextView(TextView tv) {
		SetupScreenWidthTextView(tv, true);
	}
	
	
	/*
	 * Confirm()
	 * Check user really wants to do something.
	 * Caller must provide handler, context may be used to distinguish multiple calls
	 * if necessary, or could be an index into an array, a database id, or whatever.
	 */
	public interface ConfirmHandler {
		public void onYes(long context);
		public void onNo(long context);
	}
	static public void Confirm(String title, String question, final long context, final ConfirmHandler handler) {
		new AlertDialog.Builder(g.getCurrentActivity())
		.setTitle(title)
		.setMessage(question)
		.setNegativeButton("No",  new DialogInterface.OnClickListener() {
	        @Override
			public void onClick(DialogInterface dialog, int whichButton) {handler.onNo(context);}})
		.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	        @Override
			public void onClick(DialogInterface dialog, int whichButton) {handler.onYes(context);}
		})
		.show();
	}

	
	/*
	 * HideSoftKeyboard()
	 * Hides the soft keyboard for the tv.
	 */
	static public void HideSoftKeyboard(TextView tv) {
		if (tv != null) {
			InputMethodManager imm = (InputMethodManager) g.getCurrentActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
			// imm.hideSoftInputFromWindow(getWindow().getCurrentFocus().getWindowToken(), 0);
			imm.hideSoftInputFromWindow(tv.getWindowToken(), 0);
		}
	}
	/*
	 * ShowSoftKeyboard()
	 * Show the soft keyboard for the tv.
	 */
	static public void ShowSoftKeyboard(TextView tv) {
		if (tv != null) {
			InputMethodManager imm = (InputMethodManager) g.getCurrentActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
			// imm.hideSoftInputFromWindow(getWindow().getCurrentFocus().getWindowToken(), 0);
			imm.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT);
		}
	}
	
	/*
	 * Version functions (to get version name, or version code):
	 * GetVersionNameOrCode()
	 */
	static private String getVersionNameOrCode(boolean name) {
		String version = "?";
		try {
			Activity currAct = g.getCurrentActivity();
			PackageInfo pInfo = currAct.getPackageManager().getPackageInfo(currAct.getPackageName(), 0);
			if (name) version = pInfo.versionName;
			else version = String.valueOf(pInfo.versionCode);
		} catch (NameNotFoundException e) {
		}
		return version;
	}
	static String getVersionName() { return getVersionNameOrCode(true); }
	static String getVersionCode() { return getVersionNameOrCode(false); }
	static String getFullVersionString() {
		return getVersionName() + " (Build " + Util.getVersionCode() + ")";
	}

	
	/*
	 * MakeLocation()
	 * Unfortunately Location has no constructor which just takes a lat and long,
	 * so we have one here.
	 */
	public static Location MakeLocation(double latitude, double longitude) {
		Location loc = new Location("");
		loc.setLatitude(latitude);
		loc.setLongitude(longitude);
		return loc;
	}
	
	
	/*
	 * copyFile()
	 * Returns boolean indicating success.
	 */
	public static boolean copyFile(String srcFilename, String dstFilename) {
		try {
			File src = new File(srcFilename);
			File dst = new File(dstFilename);
			InputStream in = new FileInputStream(src);
			OutputStream out = new FileOutputStream(dst);
			// Transfer bytes from in to out:
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			in.close();
			out.close();
			return true;
		}
		catch (IOException ioe) {
			return false;
		}
	}
	
	public static void memoryStats() {
 		android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
 		android.app.ActivityManager activityManager = (android.app.ActivityManager) g.getSystemService(Context.ACTIVITY_SERVICE);
 		activityManager.getMemoryInfo(mi);
 		String out = String.format("avail: %d, total: %d, class:%d", mi.availMem, mi.totalMem, 
 				activityManager.getMemoryClass());
 		Util.msg(out);
	}
	
	/*
	 * Logging stuff:
	 */
	static public void logToFile(String cat, String text)
	{       
	   File logFile = new File(Globals.g.getAppRootDir() + "log.txt");
	   if (!logFile.exists()) {
	      try {
	         logFile.createNewFile();
	      } 
	      catch (IOException e) {
	         // TODO Auto-generated catch block
	         e.printStackTrace();
	      }
	   }
	   try {
	      //BufferedWriter for performance, true to set append to file flag
	      BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true)); 
	      buf.append(cat + " : " + text);
	      buf.newLine();
	      buf.close();
	   } catch (IOException e) {
	      // TODO Auto-generated catch block
	      e.printStackTrace();
	   }
	}
	public static void setLogging(boolean isOn, LogMethod method) {
		if (isOn) {
			logMethod = method;
			flog("Logging Enabled", timestamp2String(System.currentTimeMillis()));
		} else {			
			flog("Logging Disabled", timestamp2String(System.currentTimeMillis()));
			logMethod = LogMethod.LOG_NONE;
		}
	}
	public static void setLogging(boolean isOn) {
		setLogging(isOn, LogMethod.LOG_FILE);
	}
	public enum LogMethod {
		LOG_NONE, LOG_CAT, LOG_FILE;
	}
	static LogMethod logMethod = LogMethod.LOG_FILE;
	public static void flog(String category, String msg) {
		switch (logMethod) {
		case LOG_NONE:
			break;
		case LOG_CAT:
			Log.d(category, msg);
			break;
		case LOG_FILE:
			Calendar now = Calendar.getInstance();
			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
			String timeString = sdf.format(now.getTime());
			logToFile(timeString + " : " + category, msg);
		}
	}

	// Long.compare not available until API 19
	public static int compare(long a, long b) {
		return a < b ? -1 : (a > b ? 1 : 0);
	}
}


	
// Template comment lines for partition of things within a class:
// SUB-CLASSES: ======================================================================================================
// CONSTANTS: ========================================================================================================
// DATA-INSTANCE: ====================================================================================================
// DATA-STATIC: ======================================================================================================
// METHODS-INSTANCE: =================================================================================================
// METHODS-STATIC: ===================================================================================================

