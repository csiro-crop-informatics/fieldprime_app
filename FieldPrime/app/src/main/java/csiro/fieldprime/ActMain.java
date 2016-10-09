/*
 * MainActivity.java
 * Michael Kirk 2013
 * 
 * Entry point for the app.
 * 
 * Todo:
 * . Check what happens when trial list too long on first page (need to make it scroll, and leave space below).
 * . When in scoring, back button exits app. Perhaps we should trap back button, and make it
 *   a "close dataset", going back to initial screen. Perhaps with "do you really want to close trial?" msg.
 * . Replace menu with initial screen with big buttons?
 *   Might still need menu while dataset is open.
 * . Select traits for recording dialog. 
 * . Replace menu with button screen?
 * . On "select traits to score screen" show number of reps, and maybe individual name parts
 * . Browse mode
 * . Node information button (or menu item or something) to show all the available node info.
 * . Support monotonic increasing scores.
 * 
 * Little Improvements Todo:
 * . On the "Set Traits to Score" dialog, show the number of reps (eg in brackets)
 * . Make the Spinner version of the trait selector more obviously a dropdown list.
 * . Get rid of the trait selector bar when no score sets selected.
 * . Help button when scoring to show trait description
 * . If import trait failing because of trait caption already present, then perhaps
 *   tell user which trial, or perhaps we should relax this condition.
 *   
 * Current todo:
 * Remove global instance vars of activities as much as poss, and put in getInstance functions.
 * Can activities be flagged single instance?  If so, should we do so where appropriate?
 * 
 * May need to look at saving and restoring state, such as whether we are busy at the moment.
 * Async task could check for current instance of this activity and call stopBusy on that.
 * But consider what if async finishes while there is no instance, then it cannot call stopBusy,
 * and in that case when there is an instance, how will it know the async has finished?
 * 
 * 
 * Photos:
 * On Chris's tablet. No confirm overwrite when taking another photo.
 * Sometimes, now it's not doing it.
 * Gps Disabled message everytime. Clearly restarting everytime.
 * When photos are displayed (when you go to re score a photo), they
 * don't fill the screen, just a thumbnail. Ideally it should size up until
 * the screen is full in one dimension. Also, is it possible to detect correct orientation,
 * i.e. should it be display portrait or landscape?
 */

package csiro.fieldprime;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;
import csiro.fieldprime.R;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.app.ActionBar.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;
import static csiro.fieldprime.DbNames.*;

public class ActMain extends Globals.Activity {

	// ### Constants: ##################################################################################################

	// ListSelectHandler contexts:
	private static final int LS_OPEN_DATASET = 1;
	private static final int LS_EXPORT_DATASET_CSV = 5;
	private static final int LS_EXPORT_TRIAL_TO_SERVER = 6;
	private static final int LS_DELETE_TRIAL = 9;
	private static final int LS_UPDATE_TRIAL_FROM_SERVER = 10;
	
	// Activity Result Codes:
	private static final int ARC_IMPORT_TRIAL = 1;
	private static final int ARC_IMPORT_TRIAL_XML = 3;


	/* ### Data: ###################################################################################################
	 * Note these named with c prefix (rather than m), so easy to find when debugging amongst all the
	 * inherited m* variables.
	 */

	static private ActMain mInstance;         // MFK Potential memory leak, all for progress bar
	
	private String[] cTrialNames; // list of names of available datasets
	private ArrayList<Long> cTrialIDs;
	private String[] cTrialURLsFromServer = null;
	private VerticalList mToplist;
	private ProgressBar mProgressBar;

	// ### Functions: ##################################################################################################
	private void toast(String msg) {
    	Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ExceptionHandler.setUncaughtExceptionHandler(); // Set up custom exception handler
		setContentView(R.layout.activity_main);         // set layout	
		Util.Init(this);                                // Set up the Utilities.
		
		mInstance = this;

		// Create the application root dir if necessary:
		// First check we have access to storage:
		String state = Environment.getExternalStorageState();
		if (!Environment.MEDIA_MOUNTED.equals(state)) {
		    // We cannot read and write the media (Note at this stage we can't call Util.msg)
			toast(String.format("Cannot access storage for database (%s)", state));
			return;
		}

		// Get last userid and signin:
		DlgSignin.newInstance(0, "Please enter your CSIRO Ident", g.prefs().GetLastIdent(), new DlgSignin.SigninHandler() {		
			@Override
			public void signinUsername(int context, String username) {
				Globals.setUsername(username);
				getActionBar().setTitle("User:" + username);
				g.prefs().SetLastIdent(username);  // Remember the last user
			}
		});

		// Set up buttons on initial screen reflecting current state of the world.
		SetupOptions();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (g.db() != null) {
			g.db().close();
		}
	}

//	@Override
//	protected void onPause() {
//		super.onPause();
//	}
//	@Override
//	protected void onResume() {
//		super.onResume();
//	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
	}
	
	/*
	 * trialOperation
	 * 
	 * Performs specified operation on trial with index trlIndex in
	 * global vars cTrialNames, and cTrialIDs.
	 */
	private void trialOperation(int operation, int trlIndex) {
		String tname = cTrialNames[trlIndex];
		switch (operation) {
		case LS_OPEN_DATASET:
			openTrial(tname);
			break;
		case LS_DELETE_TRIAL:
			Util.Confirm("Delete Trial", String.format("Really delete trial %s? All unsaved data will be lost", tname),
					trlIndex, new Util.ConfirmHandler() {
						@Override
						public void onYes(long trialIndex) {
							Trial.DeleteTrial(cTrialIDs.get((int) trialIndex));
							Util.msg("Deleted trial " + cTrialNames[(int) trialIndex]);
							SetupOptions();
						}

						@Override
						public void onNo(long trialIndex) {
						}
					});
			break;
		case LS_EXPORT_DATASET_CSV:
		case LS_EXPORT_TRIAL_TO_SERVER:
		case LS_UPDATE_TRIAL_FROM_SERVER:
			Result res = Trial.getSavedTrial(tname); // MFK retrieving whole trial seems a bit much if updating
			if (res.bad()) {
				Util.msg(res.errMsg());
				break;
			}
			final Trial eset = (Trial) res.obj();
			switch (operation) {
			case LS_EXPORT_DATASET_CSV:
				String extStorageDirectory = Environment.getExternalStorageDirectory().getPath();
				String outfileName = extStorageDirectory + "/" + eset.getName() + "_" + Util.JapaneseDayString() + ".csv";
				res = eset.ExportCSV(outfileName);
				Util.msg(res.good() ? "Trial data exported to " + outfileName : "Trial Export Failed: " + res.errMsg());
				break;
			case LS_EXPORT_TRIAL_TO_SERVER:
				// Maybe this should be in, say, trial.promptAndExport() 
				if (!eset.isFromServer()) {
					Util.msg("Trial can not be uploaded as it was not originally from the server");
					break;
				}
				/*
				 *  Get list of available repsets:
				 *  User can select which to export, in addition there is a "Notes"
				 *  option, to send nodeNotes, and if node creation is configured,
				 *  and there are locally create nodes to send, an option for this
				 *  has to be added as well.
				 */
				String[] tiNames = eset.GetRepSetNames();
				int numReps = tiNames.length;

				// Find out if we need "Upload Created Nodes" option:
				int numCreatedNodes = eset.getNumLocalNodes();
				final boolean nodesOption = (eset.getBooleanPropertyValue(Trial.NODE_CREATION) && numCreatedNodes > 0);
				final int numExtras = 1 + (nodesOption ? 1 : 0);

				// Create array for list of selectable options:
				final String[] selStrings = new String[numReps + numExtras];
				selStrings[0] = "Notes";
				if (nodesOption)
					selStrings[1] = "Created Nodes (" + numCreatedNodes + ")";
				for (int i = 0; i < numReps; ++i)
					selStrings[i + numExtras] = tiNames[i];
				boolean[] selections = new boolean[numReps + numExtras];
				/*
				 * Display list for user to select what to upload. We pre select everything,
				 * but ideally this would be the tis that have no been uploaded since last changed.
				 */
				for (int i = 0; i < selections.length; ++i)
					selections[i] = true;
				DlgList.newInstanceMulti(0, "Choose Data to Export", selStrings, selections, new DlgList.ListMultiSelectHandler() {
					@Override
					public void onMultiListDone(int context, boolean[] checks) {
						if (setBusy("Exporting Trial " + eset.getName())) {
							eset.exportToServer(checks[0], nodesOption && checks[1], Arrays.copyOfRange(checks, numExtras, checks.length));
						}
					}
				});
				break;

			case LS_UPDATE_TRIAL_FROM_SERVER:
				if (!eset.isFromServer()) {
					Util.msg("Trial can not be updated as it was not originally from the server");
					break;
				}
				if (setBusy("Updating Trial " + eset.getName())) {
					eset.UpdateFromServer();
				}
				break;
			}
			break;
		}
	}
	
	/*
	 * ChooseDatasetAndDoSomething()
	 * Present list of available datasets for user selection (with given prompt as title).
	 * If selection is made, handle it. Note this func is coupled with func trialOperation,
	 * which must know about each doWhat value.
	 */
	private void ChooseTrialAndDoSomething(int doWhat, String prompt) {
		cTrialNames = Trial.GetTrialList(cTrialIDs = new ArrayList<Long>());
		if (cTrialNames == null) {
			Util.msg("No existing trials found in database");
			return;
		}
		// Show list to user for selection. Handler is in the onListSelect function.
		DlgList.newInstanceSingle(doWhat, prompt, cTrialNames, new DlgList.ListSelectHandler() {	
			@Override
			public void onListSelect(int context, int which) { trialOperation(context, which); }
		});
	}

	/*
	 * GenericEventHandler() ------------------------------------------------------------------------------------------
	 * For handling events, from menu items or buttons for example. 
	 */
	public void GenericEventHandler(int id) {
		switch (id) {
		case R.id.openTrial:
			ChooseTrialAndDoSomething(LS_OPEN_DATASET, "Open Trial");
			break;
		case R.id.deleteTrial:
			ChooseTrialAndDoSomething(LS_DELETE_TRIAL, "Delete Trial");
			break;
		case R.id.importTrialCSV:
			Intent loadTrialIntent = new Intent(this, FileBrowse.class);
			loadTrialIntent.putExtra("filter", ".*\\.csv");
			startActivityForResult(loadTrialIntent, ARC_IMPORT_TRIAL);
			break;
		case R.id.importTrialXML:
			startActivityForResult(new Intent(this, FileBrowse.class), ARC_IMPORT_TRIAL_XML);
			break;
		case R.id.importTrialServer:
			getTrialListFromServer();
			break;
//		case R.id.exportTrial2CSV:
//			ChooseTrialAndDoSomething(LS_EXPORT_DATASET_CSV, "Export Trial Data");
//			break;
		case R.id.exportTrial2Server:
			ChooseTrialAndDoSomething(LS_EXPORT_TRIAL_TO_SERVER, "Select Trial to Export");
			break;						
		case R.id.updateTrialFromServer:
			ChooseTrialAndDoSomething(LS_UPDATE_TRIAL_FROM_SERVER, "Select Trial to Update");
			break;
		case R.id.backupDatabase:
			/*
			 * MFK, note simply backing up the database file will potentially lose data
			 * not in the db, in particular photos scores. There are also category images,
			 * but since these are from the server it matters less.
			 */
		    AlertDialog.Builder alert = new AlertDialog.Builder(this);
		    alert.setMessage("Choose a file name for the database backup");
		    alert.setTitle("Database Backup");
		    EditText input = new EditText(this);
			final int fudge = View.generateViewId();
		    input.setId(fudge);
		    alert.setView(input);
		    alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
		        @Override
		        public void onClick(DialogInterface dialog, int which) {
		        	Dialog d = (Dialog)dialog;
		            EditText input = (EditText) ((Dialog)dialog).findViewById(fudge);
		            // need to check filename and create dirs if necessary:
		            String fname = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + input.getText().toString();
		            File f = new File(fname);
		            f.getParentFile().mkdirs();
		            //file.createNewFile();
		            if (((Globals)getApplication()).getDatabase().copyDatabase(fname))
		            	Util.msg("Database saved as " + fname);
		            else
		            	Util.msg("Error, cannot save " + fname);
		        }
		    });
		    alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int which) {}});
		    alert.show();		
			break;
		case R.id.trialList:
			if (false)
				Util.msg("Trial list not implemented yet");
			else {
				Intent intent = new Intent(this, ActMainTrialList.class);
				startActivity(intent);
			}
			break;
		case R.id.preferences:
			startActivity(new Intent(this, Preferences.class));
			break;
		case R.id.connectBluetooth:
			startActivity(new Intent(this, ActBluetooth.class));
			break;
		case R.id.about:
			startActivity(new Intent(this, ActAbout.class));
			break;	
//		case R.id.test:
//			Test.TestFunc();
//			break;
		}
	}

	public void ButtonHandler(View v) {
		GenericEventHandler(v.getId());
	}

	/*
	 * Menu Handler
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		// Anything that really needs processing specific to it being from menu would need to be handled specifically here.
		default: // Anything else:
			GenericEventHandler(item.getItemId());
		}
		return true; // NB true ensures no further processing, which we shouldn't need.
	}

	//------------------------------------------------------------------------------------------------------------

	private void openTrial(String name) {
		Trial.startScoringActivity(this, name);
//		Intent intent = new Intent(this, ActScoring.class);
//		intent.putExtra(ActScoring.ACTSCORING_TRIALNAME, name);
//		startActivity(intent);
		return;
	}

	/*
	 * onActivityResult()
	 * Handler for startActivityFor Result calls.
	 * There can only be on ActivityResult handler and it's unfortunate that we therefore have to put
	 * code here to do stuff that really should be class specific (if they don't have their own activities).
	 * We could get round this by having a registry, where classes can register their own callback functions. 
	 */
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case ARC_IMPORT_TRIAL_XML:
			if (resultCode == RESULT_OK) {
				String fname = data.getStringExtra("result");
				Result res = KatXMLParser.ImportTrialKatmandooXMLFile(fname, ((Globals)getApplication()).getDatabase());
				if (!res.good()) {
					Util.msg("Error importing file " + fname + " (" + res.errMsg() + ")");
					break;
				}
				openTrial(res.string()); // Open the trial for scoring.
			} // if (resultCode == RESULT_CANCELED) we do nothing anyway..
			break;

		case ARC_IMPORT_TRIAL:
			if (resultCode == RESULT_OK) {
				String fname = data.getStringExtra("result");
				Result res = Trial.ImportCSVTrialFile(fname);
				if (!res.good()) {
					Util.msg("Error importing file " + fname + " (" + res.errMsg() + ")");
					break;
				}
				openTrial(res.string());
			} // if (resultCode == RESULT_CANCELED) we do nothing anyway..
			break;
		}
	}



	/*
	 * SetupOptions()
	 * Configure buttons or menus to reflect options in current context:
	 */
	void SetupOptions() {
		// Get top level view of main activity layout (which we assume we are currently in)
		FrameLayout frontPage = (FrameLayout) findViewById(R.id.frontPage);
		frontPage.removeAllViews();   // clear before filling
		mToplist = new VerticalList(this, new View.OnClickListener() {
			@Override
			public void onClick(View v) { GenericEventHandler(v.getId()); }	
		});
		frontPage.addView(mToplist);

		// No trial open. See if any in database
		cTrialNames = Trial.GetTrialList(cTrialIDs = new ArrayList<Long>());
		if (cTrialNames == null) {
			mToplist.addTextCentre("No trials found in the database");
		} else {
			ListView trialList = mToplist.addList("Open Trial:", cTrialNames, new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
					// Only open if not busy, we only require not busy on this trial, but safer
					// to wait til not busy with anything I think.
					if (!Globals.isBusy())
						openTrial(cTrialNames[position]);
				}
			});
			registerForContextMenu(trialList);
			mToplist.addTextCentre("Import new trial:");
		}
		mToplist.addButton("Import Server Trial", R.id.importTrialServer);
		//mToplist.addButton("Import CSV Trial", R.id.importTrialCSV);		
	}
	
	/*
	 * Context menu stuff for long click on trial list.
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo) menuInfo;
		menu.setHeaderTitle(cTrialNames[acmi.position]);		
		menu.add(Menu.NONE, LS_EXPORT_TRIAL_TO_SERVER, 1, "Export trial");
		menu.add(Menu.NONE, LS_UPDATE_TRIAL_FROM_SERVER, 2, "Update trial");
		menu.add(Menu.NONE, LS_DELETE_TRIAL, 3, "Delete trial");
	}
	@Override
	public boolean onContextItemSelected(MenuItem item) {
	    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		String trialName = cTrialNames[info.position];
	    switch (item.getItemId()) {
	        case LS_EXPORT_TRIAL_TO_SERVER:
	            Util.toast("export " + trialName);
	            break;
	        case LS_UPDATE_TRIAL_FROM_SERVER:
	            Util.toast("update " + trialName);
	            break;
	        case LS_DELETE_TRIAL:
	            Util.toast("delete " + trialName);
	            break;
	        default:
	            return super.onContextItemSelected(item);
	    }
		trialOperation(item.getItemId(), info.position);
		return true;
	}
	
	/*
	 * setBusy(), stopBusy()
	 * Local wrapper for Globals.setBusy and Globals.stopBusy, adds progress widget to toplist.
	 * MFK NB stopBusy is static public so that asyncTasks can call it when finished. Unfortunately
	 * this may lead to memory leaks, or perhaps a crash if the activity is destroyed before the
	 * async task finishes.  We probably need to look at using a service instead of async task,
	 * and rethink how to prevent db changes while uploads are in progress.
	 */
	private boolean setBusy(String op) {
		if (mToplist == null)
			return false; // shouldn't happen
		if (Globals.setBusy(op)) {
			// Set up progress bar
			mProgressBar = new ProgressBar(this);
			mToplist.addHView(mProgressBar);
			return true;
		}
		return false;			
	}
	static public void stopBusy() {
		if (mInstance != null && mInstance.mToplist != null && mInstance.mProgressBar != null)
			mInstance.mToplist.removeView(mInstance.mProgressBar);
		Globals.stopBusy();
	}
	
	// ===================================================================================================================
	// SUB-CLASSES: ======================================================================================================
	// ===================================================================================================================

	//===  Server Communication: =========================================================================================

	public interface ShowProgress {
		void showProgress(String msg);
	}

	/*
	 * getTrialListFromServer()
	 * Get trial list from server (in background).
	 * MFK check all OK if this doesn't work, eg no network..
	 */
	private void getTrialListFromServer() {
		if (setBusy("get trial list")) {
			getTrialListFromServerAsync as = new getTrialListFromServerAsync();
			// NB would have to use executeOnExecutor if multitasking required.
			// eg: as.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			as.execute();
		}
	}

	private class getTrialListFromServerAsync extends AsyncTask<Void, Void, Void> {
		private Exception exception;
		private String[] trialNamesFromServer = null;
		private String errMsg = null;

		protected Void doInBackground(Void... server) {
			try {
				Result res = Server.getTrialList();
				if (res.bad()) {
					errMsg = res.errMsg();
					return null;
				}
				// Process the received JSON:
				// We are expecting:
				// { "trials" : [ {"name":<NAME>, "url":<URL}* ] }
				JSONObject job = (JSONObject) res.obj();
				JSONArray jArray = job.getJSONArray("trials");
				cTrialURLsFromServer = new String[jArray.length()];
				trialNamesFromServer = new String[jArray.length()];
				for (int i = 0; i < jArray.length(); ++i) {
					JSONObject jtrial = jArray.getJSONObject(i);
					trialNamesFromServer[i] = jtrial.getString(JTRIAL_LIST_NAME);
					cTrialURLsFromServer[i] = jtrial.getString(JTRIAL_LIST_URL);
				}
			} catch (Exception e) {
				this.exception = e;
				errMsg = "Error:" + exception.getMessage();
			}
			return null;
		}

		protected void onPostExecute(Void v) {
			stopBusy();
			if (errMsg != null) {
				//Util.exceptionHandler(exception);
				Util.msg(errMsg);
			} else
				ServerTrialImport();
		}

		public void ServerTrialImport() {
			if (trialNamesFromServer == null || trialNamesFromServer.length == 0) {
				Util.msg("No trials available from server");
			} else {
				DlgList.newInstanceSingle(0, "Choose Trial to Import", trialNamesFromServer, new DlgList.ListSelectHandler() {
					@Override
					public void onListSelect(int context, int which) {
						getTrialFromServer(trialNamesFromServer[which], cTrialURLsFromServer[which]);
					}
				});
			}
		}
	}

	/*
	 * GetTrialFromServer()
	 * Download trial from url, if system not already busy.
	 */
	private void getTrialFromServer(String trialName, String trialURL) {
		// Check if we already have this trial:
		if (Trial.checkTrialExists(trialName) != 0) {
			Util.msg("A trial by that name already exists");
			return;
		}
		if (setBusy("Downloading trial " + trialName))
			new GetTrialFromServerAsync().execute(new String[] { trialURL });
	}

	private class GetTrialFromServerAsync extends AsyncTask<String, String, Void> implements ShowProgress {
		private Exception exception;
		String mErrMsg;

		@Override
		protected Void doInBackground(String... urls) {
			try {
				// We have the URL already, but we need to add password and androidId parameters
				Result res = Server.getJSON(urls[0]);
				if (res.bad()) {
					mErrMsg = res.errMsg();
					return null;
				}
				JSONObject job = (JSONObject) res.obj();
				publishProgress("Finished server download, now processing..");
				res = Trial.ImportJSONTrial(job, this);
				if (res.bad())
					mErrMsg = res.errMsg();
			} catch (Exception e) {
				//Log.i("json", json);
				this.exception = e;
			}
			return null;
		}

		@Override
		protected void onProgressUpdate (String... values) {
			Util.toast(values[0]);
		}

		@Override
		protected void onPostExecute(Void v) {
			stopBusy();
			if (exception == null && mErrMsg == null) {
				Util.msg("Trial imported");
				SetupOptions();
				return;
			}
			if (exception != null) {
				Util.exceptionHandler(exception, "GetTrialFromServerAsync:onPostExecute");
			} else if (mErrMsg != null) {
				Util.msg("Error: " + mErrMsg);
			}
		}
		
		public void showProgress(String msg) {
			publishProgress(msg);
		}
	}


	//===  End Server Communication ==============================================================================

	/*
	 * Class lButtonBar
	 * wraps xml button bar and provides AddButton method.
	 */
	class lButtonBar {
		Context mCtx;
		View.OnClickListener mHandler;
		LinearLayout.LayoutParams mButtonParams;
		int mNumButtons = 0;
		LinearLayout mButtonBar;

		public lButtonBar(Context context, View.OnClickListener handler, LinearLayout bbar) {
			mCtx = context;
			mHandler = handler;
			mButtonBar = bbar;
			mButtonParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
			mButtonParams.setMargins(Util.dp2px(5), Util.dp2px(5), Util.dp2px(5), Util.dp2px(5));
		}

		public void AddButton(String txt, int id) {
			mButtonBar.setWeightSum(++mNumButtons);
			Button b = new Button(mCtx);
			b.setBackgroundResource(R.color.white);
			b.setText(txt);
			b.setId(id);
			b.setOnClickListener(mHandler);
			mButtonBar.addView(b, mButtonParams);
		}
	}
}

