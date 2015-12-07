/*
 * ExceptionHandler.java
 * Michael Kirk 2015.
 * 
 * Currently a set of static functions for setting an uncaught exception handler
 * (which logs crash details to the file system), and for sending the crash reports
 * to the server.
 * 
 * Originally this activity implemented java.lang.Thread.UncaughtExceptionHandler,
 * but that seems unnecessary.
 */

package csiro.fieldprime;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import android.os.Build;
/*
 * Todo
 * Need to record:
 * SVN version number
 * Info to try identify user/project - can we get current database/trial, user
 * 
 */
public class ExceptionHandler { //implements java.lang.Thread.UncaughtExceptionHandler {
	private static final String SEP = "\n";
	
//	private final Activity myContext;
//	public ExceptionHandler(Activity context) {
//		myContext = context;
//	}

	public static void reportCrash(Throwable exception) {
		StringWriter stackTrace = new StringWriter();
		exception.printStackTrace(new PrintWriter(stackTrace));
		StringBuilder errorReport = new StringBuilder();
		errorReport.append("************ CAUSE OF ERROR ************" + SEP + SEP);
		errorReport.append(stackTrace.toString());
		errorReport.append(SEP + "************ DEVICE INFORMATION ***********" + SEP);
		errorReport.append("Brand: " + Build.BRAND + SEP);
		errorReport.append("Device: " + Build.DEVICE + SEP);
		errorReport.append("Model: " + Build.MODEL + SEP);
		errorReport.append("Id: " + Build.ID + SEP);
		errorReport.append("Product: " + Build.PRODUCT + SEP);
		errorReport.append(SEP + "************ FIRMWARE ************" + SEP);
		errorReport.append("SDK: " + Build.VERSION.SDK_INT + SEP);
		errorReport.append("Release: " + Build.VERSION.RELEASE + SEP);
		errorReport.append("Incremental: " + Build.VERSION.INCREMENTAL + SEP);
		
		String err = errorReport.toString();
		writeCrashFile(err);
//		String userMsg = "Sorry, FieldPrime has crashed. Hit Back to restart.";
//		Intent intent = new Intent(myContext, ActLastWords.class);
//		intent.putExtra("error", userMsg);
//		myContext.startActivity(intent);
//
//		android.os.Process.killProcess(android.os.Process.myPid());
//		System.exit(10);
	}
	
//	private static void uncaughtException(Thread thread, Throwable exception) {
//		StringWriter stackTrace = new StringWriter();
//		exception.printStackTrace(new PrintWriter(stackTrace));
//		StringBuilder errorReport = new StringBuilder();
//		errorReport.append("************ CAUSE OF ERROR ************" + SEP + SEP);
//		errorReport.append(stackTrace.toString());
//		errorReport.append(SEP + "************ DEVICE INFORMATION ***********" + SEP);
//		errorReport.append("Brand: " + Build.BRAND + SEP);
//		errorReport.append("Device: " + Build.DEVICE + SEP);
//		errorReport.append("Model: " + Build.MODEL + SEP);
//		errorReport.append("Id: " + Build.ID + SEP);
//		errorReport.append("Product: " + Build.PRODUCT + SEP);
//		errorReport.append(SEP + "************ FIRMWARE ************" + SEP);
//		errorReport.append("SDK: " + Build.VERSION.SDK_INT + SEP);
//		errorReport.append("Release: " + Build.VERSION.RELEASE + SEP);
//		errorReport.append("Incremental: " + Build.VERSION.INCREMENTAL + SEP);
//		
//		String err = errorReport.toString();
//		writeCrashFile(err);
//		//ActScoring.mSysDefExHandler.uncaughtException(thread, exception);
//		
////		String userMsg = "Sorry, FieldPrime has crashed. Hit Back to restart.";
////		Intent intent = new Intent(myContext, ActLastWords.class);
////		intent.putExtra("error", userMsg);
////		myContext.startActivity(intent);
////
////		android.os.Process.killProcess(android.os.Process.myPid());
////		System.exit(10);
//	}
	
	/*
	 * crashReportDir()
	 * There is a directory used to store crash reports until they are sent
	 * to the server, and then they are moved to a sibling folder to avoid
	 * repeated sends of the same report. The directories are "sent" and "unsent"
	 * under a "crash" directory under app root dir.
	 */
	static private File crashReportDir(boolean sent) {
		File root = new File(Globals.g.getAppRootDir() + "/crash", sent ? "sent" : "unsent");
        if (!root.exists()) {
            root.mkdirs();
        }
		return root;
	}

	static private void writeCrashFile(String txt) {
		// Get the crash dir to write the file into:
        File root = crashReportDir(false);
        // Make the filename: crash.<epochTime>.txt:
        long time = System.currentTimeMillis();
        String fileName = "crash." + time + ".txt"; 
		
		File file = new File(root, fileName);
		BufferedWriter writer = null;
		try {
		    writer = new BufferedWriter(new FileWriter(file));
			writer.write(txt);
			writer.flush();
			writer.close();
		} catch (Throwable e)  {
			Util.msg("problem with crash dump");
		}
	}
	
	/*
	 * uploadCrashReports()
	 * If there are any unsent crash reports in the file system, then
	 * try to send them to the server.
	 */
	static public void uploadCrashReports() {
		String serverURL = Globals.g.prefs().GetServerURL();
		String url = serverURL + "/crashReport";
		
		// Iterate over the crash report files:
		File unsentDir = crashReportDir(false);
		File sentDir = crashReportDir(true);
		File[] directoryListing = unsentDir.listFiles();
		if (directoryListing != null) {
			for (File child : directoryListing) {
				// upload the file:
				Result res = Server.uploadFile(url, child.getAbsolutePath(), child.getName());
				if (res.good()) {
					File sentFile = new File(sentDir, child.getName());
					child.renameTo(sentFile);
				} else {
					// hope for better luck next time?
				}
			}
		}
	}
	
	/*
	 * crash()
	 * Crash the process (for test purposes).
	 */
	static public void crash() {
		int i = 1 / 0;
	}
	
	/*
	 * Uncaught exception handling.
	 * As I understand it, all activities run in the same thread so we cover most
	 * code by replacing the uncaught exception handler in the main activity.
	 * Note this might not cover the various asynchronous threads that are used.
	 * But having said that, it seems to.
	 */
	static private Thread.UncaughtExceptionHandler mSysDefExHandler = null;
	static private Thread.UncaughtExceptionHandler mExHandler = new Thread.UncaughtExceptionHandler() {
		@Override
		public void uncaughtException(Thread thread, Throwable ex) {
			ExceptionHandler.reportCrash(ex);                // write crash report
			if (mSysDefExHandler != null)
				mSysDefExHandler.uncaughtException(thread, ex);  // call the original handler
		}
	};
	static public void setUncaughtExceptionHandler() {
		if (mSysDefExHandler == null)
			// Store the default uncaught exception handler and replace it.
			mSysDefExHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(mExHandler);
	}
}


//*** OLD CODE: ***********************************************************************************


// This was a separate file, an activity intended to display a crash error to the user.
// Now not used. We aim to simply add our logging to the normal android crash behaviour
// (which, it has to be said, is a bit strange - it seems to try to shut down one activity
// only and resume with the rest of the activity stack, or something. The documentation is
// limited it seems):

///* 
// * ActLastWords.java
// * Michael Kirk 2015
// * 
// * Activity to display message provided in bundle.
// * Used to display message to user on crash.
// */
//package csiro.fieldprime;
//
//import android.content.Intent;
//import android.os.Bundle;
//import android.widget.ScrollView;
//import android.widget.TextView;
//import csiro.fieldprime.Globals.Activity;
//
//public class ActLastWords extends Activity {
// 	@Override
//	protected void onCreate(Bundle savedInstanceState) {
//	    super.onCreate(savedInstanceState);
//	    //Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(this)); 
//	    Thread.setDefaultUncaughtExceptionHandler(null); 
//	    ScrollView sv = new ScrollView(this);
//	    TextView tv = new TextView(this);
//	    tv.setTextSize(40);
//	    sv.addView(tv);
//		setContentView(sv);
//		tv.setText(getIntent().getStringExtra("error"));
// 	}
//	@Override
//	public void onBackPressed() {
////		int ouch = 1 / 0;  // Force normal crash handling - no this don't work either :(
////		Thread.UncaughtExceptionHandler t = Thread.getDefaultUncaughtExceptionHandler();
////		if (t == null) {
////			int i = 4;
////		}
////		// This necessary else app restarts without full initialization (and can then
////		// for example crash if text to speech is invoked, since it won't be initialized).
////		android.os.Process.killProcess(android.os.Process.myPid());
////		System.exit(10);
////		
////		// This gobbledygook seems to be required to force a restart of the app.
////		// Just doing a kill process seems to only take you one activity back, which is not
////		// good if you are more that one activity deep.
////		Intent i = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
////		i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
////		startActivity(i);
//	}
//}