/*
 * ActScoring.java
 * Michael Kirk 2013
 * 
 * Scoring/display screen for current value of g.cTrial at time of creation (this must be set).
 */

package csiro.fieldprime;
import static csiro.fieldprime.Util.flog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import csiro.fieldprime.ActBluetooth.BluetoothConnection;
import csiro.fieldprime.Datum.CallOut;
import csiro.fieldprime.SelectorWidget.Selectee;
import csiro.fieldprime.TraitPhoto.DatumPhoto;
import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.Node.Note;
import csiro.fieldprime.Trial.NodeAttribute;
import csiro.fieldprime.Trial.NodeProperty;
import csiro.fieldprime.Trial.RepSet;
import csiro.fieldprime.Trial.SortDirection;
import csiro.fieldprime.Trial.SortType;
import csiro.fieldprime.Trial.TraitInstance;

public class ActTrial extends Globals.Activity
	implements CallOut, OnSharedPreferenceChangeListener, ActBluetooth.handlers {
	
	// CONSTANTS: ====================================================================================================
	static public final String ACTSCORING_TRIALNAME = "fieldprime.trialName";
	static private final String ACTSCORING_TRIAL_POS = "fieldprime.trialPos";
	static private final String ACTSCORING_PHOTO_DATUM = "fieldprime.photoDatum";
	static private final String ACTSCORING_SCORESETS = "fieldprime.scoresets";
	static private final String ACTSCORING_SELECTEES = "fieldprime.selectees";
	static private final String ACTSCORING_CURRENT_SELECTEE = "fieldprime.currentSelectee";
	static private final String ACTSCORING_LOGGING = "fieldprime.logging";
	
	static private final int ARC_IMPORT_TRAIT = 2;
	static public final int ARC_CAPTURE_IMAGE = 5;
	static protected final int ARC_NOTES_ACTIVITY = 6;
	static protected final int ARC_SCORE_SET_LIST_ACTIVITY = 7;
	static protected final int ARC_BLUETOOTH_ACTIVITY = 8;
	
	// DATA-INSTANCE: ====================================================================================================
	private Context mCtx = this;
	private Trial mTrial = null;                                     // should be null if no trial open.
	private ArrayList<RepSet> mScoreSets = new ArrayList<RepSet>();   // repSets currently being scored
	private ArrayList<RepSet> mBrowseSets = new ArrayList<RepSet>(); // repSets currently being browsed
	private boolean [] mBrowseChecks = null;                         // which repSets currently being browsed (matched against all repsets)
	private Datum mDatum;                                            // currently displayed Datum
	private ActionBar mAcbar;

	// Various screen items used in scoring:
	private FrameLayout mBrowseContentPane;    // View for type specific score collection, or for browsing
	private FrameLayout mScoreContentPane;     // View for type specific score collection, or for browsing
	private ViewPager mViewPager;
	private View mPopupAnchor;
	
	// Text views for display of 2 node properties (each showing prop name and value):
//	private TextView mNodePropPrompt1;
//	private TextView mNodePropPrompt2;
	private TextView mNodePropValue1;
	private TextView mNodePropValue2;
//	private TextView mTVRep;
	
//	private boolean mRepsPresent = false;
	private VerticalList mTopLL;
	private SelectorWidget mTraitSelector;                 // Traits Instances currently scoring
	private ArrayList<PropertyWidget> mPropertyWidgets;
	private int mNumPropertyWidgets = 2;                  // default number of property widgets to display
	
	private boolean mDoNotProgress = false;   // prevent the next auto progress 
	
	private ArrayList<BluetoothConnection> mBlueteeth = new ArrayList<BluetoothConnection>();

	static public ActTrial mActTrial;     // MFK this being static might stop garbage cleanup.
	                                          // Perhaps set to null on activity exit?
	
	private TraitPhoto.DatumPhoto datumAwaitingPhotoResult = null; // hack for setDatumAwaitingPhotoResult()
	private boolean mLogging;
	

	// METHODS-INSTANCE: =================================================================================================
/*
 * load curr score set selection, currently selected for scoring, sort order.
 * Are we really reloading everything from the database on screen rotation?
 * To save curr score set, bundle up mScoreSets, also currently scoring,
 * Or isn't that automatic? No User may have fingered out of order button,
 * Need to preserve that I guess.
 * Simplest to make sort changes permanent anyway.
 * Need to also record number of property widgets and their settings
 * 
 * Todo:
 * Remove old sorting from preferences
 * Move trial position storage from shared preferences.
 * Make scoring faster, preload att vals, or should we use sql?
 * Suspect we should reload from db on sort or filter rather than
 * do in memory.
 * 
 */
	
 	@Override
	protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    mActTrial = this;
	    String trialName;
	    boolean isRestart = (savedInstanceState != null);

	    // Get the trial name and open:
	    if (isRestart) {
	    	// Trial name is in the bundle:
	    	trialName = savedInstanceState.getString(ACTSCORING_TRIALNAME);
	    	mLogging = savedInstanceState.getBoolean(ACTSCORING_LOGGING);
	    	Util.setLogging(mLogging);
	    } else {
	    	Intent i = getIntent();
	    	trialName = i.getExtras().getString(ACTSCORING_TRIALNAME);
	    }
	    flog("lifecycle", "onCreate " + (isRestart ? "recreate" : "new"));
		Result res = Trial.getSavedTrial(trialName);
		if (res.bad()) {
			Util.toast(res.errMsg());
			finish();  // NB onPause not called when finish() called from onCreate()
			return;
		}
		mTrial = (Trial) res.obj();
		if (mTrial == null) { // Abort if no trial (shouldn't happen):
			Util.toast("No trial open");
			finish(); // NB onPause not called when finish() called from onCreate()
			return;
		}				
		mTrial.restoreScoringState(savedInstanceState);
        // Old way: mTrial.sortNodes(g.prefs().GetWalkOrder(), g.prefs().GetWalkReverse());
		// mTrial.sortNodes(msSortCode, g.prefs().GetWalkReverse());

		// Old way get trial pos: place in pstate.  
//		Prefs.TrialPos currPos = g.prefs().getTrialPos();
//		if (currPos != null && currPos.trialId == mTrial.getID()) // Goto previous position if one is stored for this trial
//			mTrial.gotoNodebyId(currPos.nodeId);

		g.setTrial(mTrial);
	    mAcbar = getActionBar();
	    mAcbar.setHomeButtonEnabled(true);
		invalidateOptionsMenu(); // this necessary else first time the menu comes up it is wrong (onPrepareMenuOptions not called)
		mAcbar.setTitle(mTrial.getName());
		
		// Setup up property widgets:
		mPropertyWidgets = new ArrayList<PropertyWidget>();
		for (int i=0; i<mNumPropertyWidgets; ++i)
			mPropertyWidgets.add(new PropertyWidget(this));
		
		VerticalList topLL = setupScreen();
		if (isRestart) 
			myRestoreInstanceState(savedInstanceState);
		mViewPager.setCurrentItem(1);
 		setContentView(topLL);
 		//Util.memoryStats(); // Show some memory stats
	}
// 	@Override
// 	public void onRestoreInstanceState(Bundle bundle) {
// 		
// 	}

 	@Override
 	public void onSaveInstanceState(Bundle bundle) {
 		super.onSaveInstanceState(bundle);
		flog("lifecycle", "onSaveInstanceState");
		bundle.putBoolean(ACTSCORING_LOGGING, mLogging);

 		// trial name:
 		bundle.putString(ACTSCORING_TRIALNAME, mTrial.getName());

 		// Current node:
 		Node currNode = mTrial.getCurrNode();
		if (currNode != null) {
			bundle.putLong(ACTSCORING_TRIAL_POS, currNode.getId());
		}
		
		// ScoreSets selected for scoring:
		if (mScoreSets != null && mTraitSelector != null) {  //NB attow neither mScoreSets nor mTraitSelector can be null here
			int count = mScoreSets.size();
			if (count > 0) {
				long[] scoresetIds = new long [count];
				for (int i=0; i<mScoreSets.size(); ++i)
					scoresetIds[i] = mScoreSets.get(i).getId();
				bundle.putLongArray(ACTSCORING_SCORESETS, scoresetIds);
			}
		}
		
		// ScoreSets selected for scoring - in current order within the SelectorWidget.
		if (mScoreSets != null && mTraitSelector != null) {
			ArrayList<Selectee> tis = mTraitSelector.getOrderedList();
			int count = tis.size();
			if (count > 0) {
				long[] sids = new long [count];
				for (int i=0; i<tis.size(); ++i)
					sids[i] = ((TraitInstance)tis.get(i)).getId();
				bundle.putLongArray(ACTSCORING_SELECTEES, sids);
			}
			
			// save the current selection, if any:
			Selectee see = mTraitSelector.getCurrentSelectee();
			if (see != null) 
				bundle.putLong(ACTSCORING_CURRENT_SELECTEE, ((TraitInstance)see).id());
		}
		
		// Current Photo Datum (if we are waiting for a photo from the camera activity).
		DatumPhoto dp = getDatumAwaitingPhotoResult();
		flog("lifecycle", "  onSaveInstanceState dp " + (dp == null ? "null" : ("id " + dp.getId())));
		if (dp != null) {
			bundle.putLong(ACTSCORING_PHOTO_DATUM, dp.getId());
		}
		
		// Property widgets:
 	}
 	
	/*
	 * myRestoreInstanceState()
	 * Restore the state saved in onSaveInstanceState().
	 * NB not using standard onRestoreInstanceState as it is sometimes called
	 * unnecessarily when state is still intact.
	 * 
	 * we need to restore score screen state other than the state
	 * restored in restoreScoringState (which is the state that persists
	 * through closure of the app).
	 * Including:
	 * score set selection,
	 * currently scoring node.
	 * Sort order and Filter state. (this in pstate)
	 * . Number and selection of property widgets
	 * . Bluetooth connections
	 */
 	private void myRestoreInstanceState(Bundle bundle) {
	    flog("lifecycle", "restoreInstanceState");
 		// Current node:
    	long currNodeId = bundle.getLong(ACTSCORING_TRIAL_POS);
    	if (currNodeId != 0) // NB getLong returns 0 if not set
    		mTrial.gotoNodebyId(currNodeId);

    	// Score sets and selector widget:
    	long [] ssids = bundle.getLongArray(ACTSCORING_SCORESETS);
    	if (ssids != null && ssids.length > 0) {
    		// Reconstruct mScoreSets:
    		mScoreSets = new ArrayList<RepSet>();
    		ArrayList<RepSet> allSsets = mTrial.getRepSetsArrayList();
    		for (long ssid : ssids) {
    			for (RepSet rs : allSsets) {
    				if (rs.getId() == ssid) {
    					mScoreSets.add(rs);
    					break;
    				}
    			}
    		}	
    		setScoringList(mScoreSets);
    		
    		long [] selectionsInOrder = bundle.getLongArray(ACTSCORING_SELECTEES);
    		if (selectionsInOrder != null) {
    			mTraitSelector.setOrder(selectionsInOrder);
    		}
    		
			// restore the current selection, if any:
			long seeId = bundle.getLong(ACTSCORING_CURRENT_SELECTEE);
			if (true && seeId > 0) {
				mTraitSelector.setCurrentSelectee(seeId);
				Selectee sel = mTraitSelector.getCurrentSelectee();
				if (sel == null)
					Util.toast("current selection unexpectedly null");
				else
					selectionChanged(sel, false);
			}
    	}

    	// PhotoDatum stuff, for if recreated while awaiting photo:
    	long datId = bundle.getLong(ACTSCORING_PHOTO_DATUM);
    	flog("lifecycle", String.format("  saved id:%d, mDatum id:%d", datId, mDatum == null ? 0 : mDatum.getId()));
    	if (datId != 0 && mDatum != null) {
    		if (datId == mDatum.getId())  { // MFK we shouldn't insist on this, but this will usually be true
    			                           // it could be false if user navigated awhile while waiting for photo return
    			setDatumAwaitingPhotoResult((TraitPhoto.DatumPhoto)mDatum);
	    		// get the datum from the id
	    		flog("lifecycle", "  restored waiting photo datum");
    		} else flog("lifecycle", "  non matching waiting photo datum found");
    	}
 	}
 	
 	
 	@Override
 	public void onStart() {
 		super.onStart();
		flog("lifecycle", "onStart");
 	}
 	
 	@Override
 	public void onResume() {
 		super.onResume();
		flog("lifecycle", "onResume");
		mActTrial = this;
 		GPSListener.resumeUpdates();
 	}
	@Override
 	public void onPause() {
		// Note this is the last code that is guaranteed to be run before the process is terminated.
		// Will possible exception of onSaveInstanceState
		super.onPause();
		flog("lifecycle", "onPause");
		//mActTrial = null;  
			// MFK we are assuming onResume always called subsequently,
			// But don't do this, since other activities assume mActTrial is set (eg ActBluetooth).
		
		GPSListener.pauseUpdates();
		if (mTrial != null)
			mTrial.saveScoringState();  // NB mTrial may be null from closeTrial
 	}
	@Override
 	public void onStop() {
		super.onStop();
		flog("lifecycle", "onStop");
		//GPSListener.pauseUpdates();
 	}
	@Override
	protected void onDestroy() {
		super.onDestroy();
		flog("lifecycle", "onDestroy");
		if (mTrial != null) {
			saveTrialPos();
			g.setTrial(null);
		}
	}
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
	    MenuItem checkable = menu.findItem(R.id.logging);
	    checkable.setChecked(mLogging);
	    return true;
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.act_scoring, menu);
		
		// Hide or show the print options depending on whether we have a connected printer:
		boolean showPrintStuff =  g.getPrinterMacAddr() != null && g.getPrinterMacAddr().length() > 0;
		menu.findItem(R.id.zprint).setVisible(showPrintStuff);
		menu.findItem(R.id.zebPrinterSetup).setVisible(showPrintStuff);
		return true;
	}
	/*
	 * Menu Handler
	 * Anything that really needs processing specific to it being from menu would
	 * need to be handled specifically here.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
	    case android.R.id.home:
	    	handleHomePress();
	    	break;
		case R.id.logging:
            mLogging = !item.isChecked();
            item.setChecked(mLogging);
			Util.setLogging(mLogging);
            return true;
            
		case R.id.test:
			Util.msg("test");
            return true;

		default: // Anything else:
			genericEventHandler(item.getItemId());
		}
		return true; // NB true ensures no further processing, which we shouldn't need.
	}
	

	/*
	 * onBackPressed()
	 * If user exits with back button we need to close the trial.
	 */
	public void confirmedBackPressed() {
		finish();
		closeTrial();
		super.onBackPressed();
	}
	@Override
	public void onBackPressed() {
		Util.Confirm("Close Trial", "Really close the trial?", 0, new Util.ConfirmHandler() {
			@Override
			public void onYes(long context) {
				confirmedBackPressed();
			}
			@Override
			public void onNo(long context) {
			}
		});
	}
	
	/*
	 * handleHomePress()
	 * Handle user press of the app icon in the action bar.
	 */
	private void handleHomePress() {
		PopupMenu pup = new PopupMenu(this, mPopupAnchor);
		Menu m = pup.getMenu();
		m.add(0, 1, 0, "Add Property Widget");
		m.add(0, 2, 0, "Remove Property Widget");
		pup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem mi) {
				switch (mi.getItemId()) {
				case 1:
					++mNumPropertyWidgets;
					mPropertyWidgets.add(new PropertyWidget(ActTrial.this));
					break;
				case 2:
					if (mNumPropertyWidgets > 0) {
						--mNumPropertyWidgets;
						mPropertyWidgets.remove(mPropertyWidgets.size()-1);
					}
				}
				setupScreen();
				return false;
			}
		});
		pup.show();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences arg0, String key) {
		if (key.equals(getString(R.string.pref_key_walk_order))
				|| key.equals(getString(R.string.pref_walk_reverse))) {
				mTrial.sortNodes(Trial.SortType.fromValue(g.prefs().GetWalkOrder()-1), g.prefs().GetWalkReverse());
				refreshNodeScreen();
		} else if (key.equals(getString(R.string.pref_interleave_tis))) {
			if (mTraitSelector != null) {
				mTraitSelector.Reset();
			}
		} 
	}

	/*
	 * resetNodeScreenDetails()
	 * Refresh the displayed attributes for the current node.
	 */
	private void resetNodeScreenDetails() {
		if (mTrial.mShowRowCol) {
			Node node =  mTrial.getCurrNode();
			if (node.isLocal()) {
				mNodePropValue1.setText("New Node");
				mNodePropValue2.setText("" + node.localNumber());
			}
			else {
				mNodePropValue1.setText(mTrial.getIndexName(0) + " " + node.getRow());
				mNodePropValue2.setText(mTrial.getIndexName(1) + " " + node.getCol());
			}
			// Old way with 2 text views each index
//			if (node.isLocal()) {
//				mNodePropPrompt1.setText("New");
//				mNodePropValue1.setText("Node");
//				mNodePropPrompt2.setText("" + node.localNumber());
//				mNodePropValue2.setText("");
//			}
//			else {
//				mNodePropPrompt1.setText(mTrial.getIndexName(0));    // NB really only need to set this once, on creation?
//				mNodePropValue1.setText("" + node.getRow());
//				mNodePropPrompt2.setText(mTrial.getIndexName(1));
//				mNodePropValue2.setText("" + node.getCol());
//			}
		}
		for (PropertyWidget aw : mPropertyWidgets) {
			aw.RefreshValue();
		}
	}

	
	/*
	 * showScores()
	 * Fill the layout with the scores for the current node for the
	 * specified score sets. ATTOW the layout may be either the browse pane OR
	 * the score pane.
	 * Note we're created all the items for each node, rather than just updating
	 * the contents as is done for the main scoring display.
	 * MFK: For the browse screen, this should show attributes. Need to pass in
	 * list of attributes to show, as user needs to be able to modify the list
	 * rather than just show all.
	 */
	private void showScores() {
		showScores(mBrowseContentPane, mBrowseSets);
		if (mDatum == null) {
			showScores(mScoreContentPane, mScoreSets,
					(mScoreSets.size() == 0) ? "No traits selected to score" : "Fully scored");		
		}
	}
	private void showScores(FrameLayout frm, ArrayList<RepSet> ssets) {
		showScores(frm, ssets, "Scores:");
	}
	private void showScores(FrameLayout frm, ArrayList<RepSet> ssets, String scoresPrompt) { 
		frm.removeAllViews();    // clear 
		ScrollView sv = new ScrollView(this);
		sv.setBackgroundResource(R.color.black);
		VerticalList details = new VerticalList(this, null);
		//details.setBackgroundColor(BLACK);
		details.setBackgroundResource(R.color.black);
		sv.addView(details);

		// Scores
		details.addText(scoresPrompt);
		for (RepSet rs : ssets) {
			details.addLine();
			boolean singleton = rs.numReps() == 1;
			if (!singleton) {
				details.addTextNormal(rs.getDescription());
			}
			for (TraitInstance ti : rs.getTraitInstanceList()) {
				String tiText;
				if (singleton)
					tiText = ti.getFullName();
				else
					tiText = "  sample " + ti.getSampleNum();
				tiText += ": " + ti.getDatum().ValueAsString("Not Scored");
				details.addTextNormal(tiText);
			}
		}
		
		// Notes
		Node tu = mTrial.getCurrNode();
		ArrayList<Node.Note> notes = tu.getNoteList();
		if (notes.size() > 0) {
			details.addText("Notes:");
			details.addLine();
			for (Note n : notes) {
				details.addText(n.getText());
				details.addLine();
			}
		}
		
		frm.addView(sv);
	}
	
	/*
	 * selecteesFromRepSets()
	 * Return an ArrayList of Selectees from the given repSets.
	 * These will be the TraitInstances, if interleaving is specified in the preferences,
	 * then the tis will be interleaved.
	 */
	private ArrayList<Selectee> selecteesFromRepSets(RepSet [] repSets) {
		// Check that at most one repset for each trait has been chosen:
		// perhaps should be returning boolean to indicate failure, note in call
		// we are setting up things with the passed repSets which should be unset if wrong.
		if (!DlgChooseScoreSets.noRepeatedTraits(repSets))  {
			Util.msg("You cannot score multiple instances of the same trait simultaneously");
			return null;
		}
		
		// Get the set of TIs to display, storing in mTIList:
		ArrayList<Selectee> tiList = new ArrayList<Selectee>();
		for (RepSet rs : repSets) {
			if (rs.isDynamic()) { // Only add the first if dynamic
				tiList.add(rs.getTraitInstanceList()[0]);
			} else {
				for (Selectee ti : rs.getTraitInstanceList()) {
					tiList.add(ti);
				}
			}
		}
		
		// Sort mTIList if necessary, eg to interleave multiple samples for traits
		if (Globals.g.prefs().GetInterleaveTIs()) {
				Collections.sort(tiList, new TraitInstanceComparator(TraitInstanceComparator.INTERLEAVE));
		}

		ArrayList<Selectee> selList = new ArrayList<Selectee>();
		for (Selectee ti : tiList)
			selList.add(ti);
		return selList;
	}
	class TraitInstanceComparator implements Comparator<Selectee> {
		public static final int INTERLEAVE = 0;
		public static final int SAMPLES_TOGETHER = 1;
		private int mOrdering;  // not used, but might be if we want multiple orders
		public TraitInstanceComparator(int order) {
			mOrdering = order;
		}

		@Override
		public int compare(Selectee s0, Selectee s1) {
			TraitInstance ti0 = (TraitInstance)s0;
			TraitInstance ti1 = (TraitInstance)s1;
			if (mOrdering == INTERLEAVE) {
				int sampNum0 = ti0.getSampleNum();
				int sampNum1 = ti1.getSampleNum();
				if (sampNum0 < sampNum1) return -1;
				if (sampNum0 > sampNum1) return 1;
			}
			if (ti0.getTrait().getId() < ti1.getTrait().getId()) return -1;
			if (ti0.getTrait().getId() > ti1.getTrait().getId()) return 1;			
			return 0;
		}
	}
	
	/*
	 * clearScoringList()
	 * Clear the list of scoring repsets.
	 * And the browsing set.
	 * MFK - not removing current score view..
	 */
	public void clearScoringList() {
		mScoreSets.clear();
		mBrowseChecks = null;
		mBrowseSets.clear();
		mTraitSelector.Reset(selecteesFromRepSets(mScoreSets.toArray(new RepSet[mScoreSets.size()])));
		refreshNodeScreen();
	}
	
	/*
	 * setScoringList()
	 * Set the list of scoring repsets.
	 */
	private void setScoringList(ArrayList<RepSet> scoreSets) {
		mScoreSets = scoreSets;
		mTraitSelector.Reset(selecteesFromRepSets(mScoreSets.toArray(new RepSet[mScoreSets.size()])));
		mViewPager.setCurrentItem(1);
		refreshNodeScreen();
	}
	
	/*
	 * resetDynamicsInSelector()
	 * Reset the ti currently show for a dynamic scoreset to the first one.
	 */
	private void resetDynamicsInSelector() {
		Selectee [] currList = mTraitSelector.currList();
		for (Selectee s : currList) {
			TraitInstance ti = (TraitInstance)s;
			if (ti.isDynamic()) {
				mTraitSelector.replace(ti, ti.getRepset().getFirst());
			}
		}
	}
	
	/*
	 * setStickies()
	 * For traits that have been set "sticky", scores may need to be set automatically.
	 */
	private void setStickies() {
		Selectee [] tiList = mTraitSelector.currList();
		
		if (tiList.length <= 0)
			return;
		
		// Don't do this if everything is sticky, else might loop thru whole data set due to auto-progress. (hackish)
		boolean doNotDoIt = true;
		for (Selectee s : tiList) {
			TraitInstance ti = (TraitInstance)s;
			if (!ti.getTrait().isSticky()) {
				doNotDoIt = false;
				break;
			}
		}
		if (!doNotDoIt) {
			for (Selectee s : tiList) {
				TraitInstance ti = (TraitInstance)s;
				ti.getDatum().setStickyValue();
			}
		}
	}

	/*
	 * refreshNodeScreen()
	 * 
	 * Update the screen to reflect the current node (according to the trial).
	 * It is expected that we have come to this node from another, or after
	 * some significant change.
	 * The first unscored ti is displayed for scoring.
	 * If there are no unscored tis, then currently nothing is displayed,
	 * but a summary of the scores would be good.
	 * 
	 */
	//private void refreshNodeScreen() {
	public void refreshNodeScreen() {
		{
			Node n = mTrial.getCurrNode();
			if (n.isLocal()) {
				Util.toast("local node");
				// Display attribute nodes in traitSelector
				// have to get the traitInstances and add them
			} else {
				// Remove attribute nodes in traitSelector
			}
		}
		
		resetNodeScreenDetails(); // Refresh the displayed node attributes, MFK, don't need to if tu not changed
		resetDynamicsInSelector();
		setStickies();
		mDatum = null;   // important, indicates nothing currently being scored.
		if (mTraitSelector.gotoFirstUnscored()) {
			mDatum = ((TraitInstance)mTraitSelector.getCurrentSelectee()).getDatum();
			mDatum.Display(this, mScoreContentPane, this);	
		}
		showScores();
	}

	public void AutoProgress() {
		if (mDoNotProgress) {
			mDoNotProgress = false;
			return;
		}
		boolean autoWithin = g.prefs().getAutoProgressWithin();
		boolean autoBetween = g.prefs().getAutoProgressBetween();
		
		// Something may have been changed, so depending on how autoprogress is configured, set what to score now:
		if (autoWithin) {
			// temp hack - don't auto progress for dynamic trait
			TraitInstance currTi = (TraitInstance)mTraitSelector.getCurrentSelectee();
			int currTIpos = mTraitSelector.getSelectionIndex();
			if (currTi.isDynamic()) {
				TraitInstance firstUnscored = currTi.getRepset().getFirstUnscored();
				if (firstUnscored != null) {
					//mTraitSelector.setDynamicRepSetCurrentTI(firstUnscored);
					mTraitSelector.replace(currTi, firstUnscored);
				}
				mDatum = ((TraitInstance)mTraitSelector.getCurrentSelectee()).getDatum(); // NB these two lines dupe code
				mDatum.Display(this, mScoreContentPane, this);

				//currTi.dynamicAutoProgress();
			}
			else {	
				refreshNodeScreen();  // This will move to next unscored
			}
		}
		if (autoBetween) {
			if (mTraitSelector.FirstUnscoredIndex() < 0)
				Next();
		}
		// If there is no auto progession, we may need to update screen explicitly:
		if (!autoWithin && !autoBetween)
			refreshNodeScreen();
	}

	private void Next() {
		if (mDatum != null)
			mDatum.cleanup();

		int fromIndex = mTrial.getNodeIndex();
		if (g.prefs().GetAutoProgressSkip()) { // If skip configured we have skip over nodes that are already scored
			while (mTrial.gotoNextNode() && mTraitSelector.FirstUnscoredIndex() == -1);
			if (mTrial.getNodeIndex() == fromIndex)
				Util.toast("No more next unscored nodes");			
		} else {
			if (!mTrial.gotoNextNode())
				Util.toast("Already at last node");
		}

		refreshNodeScreen();
		notifyNodeChange(fromIndex, mTrial.getNodeIndex());
	}

	private void Prev() {
		if (mDatum != null)
			mDatum.cleanup();

		int fromIndex = mTrial.getNodeIndex();
		if (g.prefs().GetAutoProgressSkip()) {
			while (mTrial.gotoPrevNode() && mTraitSelector.FirstUnscoredIndex() == -1);
			if (mTrial.getNodeIndex() == fromIndex)
				Util.toast("No more previous unscored nodes");			
		} else {
			if (!mTrial.gotoPrevNode())
				Util.toast("Already at first node");
		}
		refreshNodeScreen();
		notifyNodeChange(fromIndex, mTrial.getNodeIndex());
	}

	/*
	 * notifyNodeChange()
	 * Say something, or beep, when changing node according to notification preferences.
	 */
	private void notifyNodeChange(int fromIndex, int toIndex) {
		Node tuFrom = mTrial.getNodeByIndex(fromIndex);
		Node tuTo = mTrial.getNodeByIndex(toIndex);
		
		// Skip notifications:
		int numSkipped = Math.abs(toIndex - fromIndex) - 1;
		if (numSkipped > 0) {
			if (g.prefs().GetNotifications().contains("sb")) {
				Globals.cToneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 400);
			}
			if (g.prefs().GetNotifications().contains("sv")) {
				String whatToSay = "Skipping " + numSkipped;
				Globals.mTts.speak(whatToSay, TextToSpeech.QUEUE_ADD, null);
			}
		}
			
		// Row change notifications:
		if (tuFrom.getRow() != tuTo.getRow()) {
			if (g.prefs().GetNotifications().contains("rb")) {
				Globals.cToneGenerator.startTone(ToneGenerator.TONE_DTMF_1, 200);
			}
			if (g.prefs().GetNotifications().contains("rv")) {
				String whatToSay = String.format("%s %d", mTrial.getIndexName(0), tuTo.getRow());
				Globals.mTts.speak(whatToSay, TextToSpeech.QUEUE_ADD, null);
			}
		}
		
		// Column change notifications:
		if (tuFrom.getCol() != tuTo.getCol()) {
			if (g.prefs().GetNotifications().contains("cb")) {
				Globals.cToneGenerator.startTone(ToneGenerator.TONE_DTMF_8, 200);
			}
			if (g.prefs().GetNotifications().contains("cv")) {
				String whatToSay = String.format("%s %d", mTrial.getIndexName(1), tuTo.getCol());
				Globals.mTts.speak(whatToSay, TextToSpeech.QUEUE_ADD, null);
			}
		}
	}


	/*
	 * class AttributeWidget
	 * Screen row displaying selectable attribute name and associated value for current node.
	 * NB - Assumes cTrial set for creation and duration.
	 * MFK - perhaps the popup menu should be static, i.e. common to all widgets.
	 * Might need to update it when a new ti is created.
	 */
	private class PropertyWidget extends ViewLine implements OnMenuItemClickListener {
		TextView mAttVal;
		TextView mAttName;
		Trial.NodeProperty mProperty;
		PopupMenu mPop;
		HashMap<MenuItem, NodeProperty> mMap = new HashMap<MenuItem, NodeProperty>();
		
		public PropertyWidget(Globals.Activity context) {
			super(context, false, null, false);
			setWeightSum(1);

			// Setup clickable attribute name:
			mAttName = new TextView(mCtx);
			Util.setColoursWhiteOnBlack(mAttName);
			mAttName.setTextSize(g.TextSize(Globals.SIZE_MEDIUM));
			mAttName.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.33f));

			mPop = new PopupMenu(mCtx, mAttName);
			Menu m = mPop.getMenu();

			for (NodeProperty tup : mTrial.getNodeProperties()) {
				MenuItem item = m.add(tup.name());
				mMap.put(item, tup);
			}
			mPop.setOnMenuItemClickListener(this);
			mAttName.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View arg0) {
					mPop.show();
				}
			});
			addView(mAttName);

			// Slideable divider:
			View div = new View(mCtx);
			div.setLayoutParams(new LayoutParams(Util.dp2px(13), LayoutParams.MATCH_PARENT));
			div.setBackgroundResource(R.color.red);
			div.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent me) {
					switch (me.getAction()) {
					case MotionEvent.ACTION_MOVE:
						float spinFrac = me.getRawX() / g.cScreenWidthPx;
						mAttName.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, spinFrac));
						mAttVal.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1 - spinFrac));
						break;
					}
					return true;
				}

			});
			addView(div);

			// Setup TextView to display attribute value:
			mAttVal = new TextView(mCtx);
			mAttVal.setHorizontallyScrolling(true);
			Util.setColoursWhiteOnBlack(mAttVal);
			mAttVal.setTextSize(g.TextSize(Globals.SIZE_MEDIUM));
			mAttVal.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.67f));
			addView(mAttVal);
		}
		
		public void RefreshValue() {
			if (mProperty != null) {
				String val = mProperty.valueString(mTrial.getCurrNode());
				if (val != null && val.contains("\\u0007")) {
					Globals.cToneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 400);
					val = val.replace("\\u0007", "");
				}
				mAttVal.setText(val);
			}
		}

		@Override
		public boolean onMenuItemClick(MenuItem mi) {
			mProperty = (NodeProperty) mMap.get(mi);
			mAttName.setText(mProperty.name());
			RefreshValue();
			return true;
		}
	}

	/*
	 * Class Hline
	 * Horizontal line across screen
	 */
	class Hline extends View {	
		public Hline(Context context, int color) {
			super(context);
			setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util.dp2px(1)));
			this.setBackgroundResource(color);
		}
		
		public Hline(Context context) {
			this(context, R.color.white);
		}
	}

	/*
	 * scoreVLayoutBiggerItem()
	 * Note dependence on screen size.
	 * Dynamic equivalent of xml style @style/scoreVLayoutBiggerItem:
	 * <style name="scoreVLayoutBiggerItem" parent="whiteOnBlack">
	 * <item name="android:layout_width">match_parent</item>
	 * <item name="android:layout_height">50dp</item>
	 * <item name="android:background">@color/silver</item>
	 * </style>
	 */
	private void scoreVLayoutBiggerItem(View v) {
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
				Util.dp2px(g.screenIsBig() ? 100 : 50));
		params.gravity = Gravity.CENTER;
		v.setBackgroundResource(R.color.silver);
		//v.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, Util.dp2px(g.screenIsBig() ? 100 : 50)));
		v.setLayoutParams(params);
	}
	
	/*
	 * setBrowseAll()
	 * Get the current repSets and select all of them for browsing.
	 * 
	 * Previously we had by default that all scoresets were selected in the browse pane.
	 * This function was called on initializing scoring and when the set of score sets
	 * may have change. But currently we've changed to a default of browsing none.
	 * And consequently this func is not called. The problem is that retrieving all the scores
	 * requires DB access, and this may be slow - particularly if there are many
	 * nodes and/or scoresets. And the user may not even be ever looking at the 
	 * browse screen.
	 */
	private RepSet [] setBrowseAll() {
		RepSet [] rsets = mTrial.getRepSetsArray();
		int numRepSets = rsets.length;
		mBrowseSets.clear();
		mBrowseChecks = new boolean[numRepSets];
		for (int i = 0; i<numRepSets; ++i) {
			mBrowseChecks[i] = true;
			mBrowseSets.add(rsets[i]);
		}
		return rsets;
	}
	
	public void selectionChanged(SelectorWidget.Selectee sel, boolean doit) {
		/*
		 * First do any processing necessary for the datum that has been left.
		 * mDatum here is presumed to be the datum that was selected prior to the selection change.
		 */
		if (mDatum != null) {
			if (doit) mDatum.navigatingAwayWithWarning();
		}
		/*
		 * Now process the new selection.
		 */
		TraitInstance ti = (TraitInstance)sel;  // MFK cast instead to nodeProperty? and have interface
		mDatum = ti.getDatum();
		mDatum.Display(mCtx, mScoreContentPane, ActTrial.this);
	}

	/*
	 * setupScreen()
	 * Display scoring screen for (scoring and browsing).
	 * Creates mTraitSelector
	 */
	private VerticalList setupScreen() {
		if (mTopLL != null) // this needed for views that have had this as a parent. Not sure this needed anymore
			// Yes it is actually. Because the propertyWidgets are global and exist between calls to this func.
			// The only reason there are multiple calls to this func is the call on adding or removing prop widgets.
			// We could probably avoid the globals with some rework (and save widget selections in pstate).
			mTopLL.removeAllViews();
		
		mTopLL = new VerticalList(this, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				genericEventHandler(v.getId());
			}
		});
		
		Util.setBackgroundBlack(mTopLL);//SetLayoutBackgroundBlack(mTopLL);
 		setContentView(mTopLL); // Switch to scoring layout.
		
		// Navigation/Info Stuff: -------------------------------------------------------------------------
		//mTopLL.addView(new Hline(this));
		
		/*
		 * Prev/Next node
		 * NB. When Next or Prev is clicked we may need to inform the current Datum that we are
		 * navigating away. And if the datum has a problem with that, we warn don't navigate away.
		 */
 		if (true) {
			ViewLine pnll = new ViewLine(this, false,  new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					// Assuming here that click is Next or Prev, in which case we may
					// need to inform the current Datum that we are navigating away.
					// And if the datum has a problem with that, we warn don't navigate away.
					if (mDatum != null)
						mDatum.navigatingAwayWithWarning();
					switch (v.getId()) {
					case R.id.next:
						Next();
						break;
					case R.id.prev:
						Prev();
						break;
					default:
						Util.msg("Unexpected view code for mTopLL");
					}
				}
			}, Globals.SIZE_BIG, false);
			pnll.setBackgroundResource(R.color.silver);
			pnll.AddButton("Prev", R.id.prev, false);
			pnll.AddButton("Next", R.id.next, false);
			mTopLL.addView(pnll);
		} else {
 			ViewLine pnll = new ViewLine(this, Globals.SIZE_BIG);
 			pnll.setBackgroundResource(R.color.silver);
			pnll.addButton("Prev", new View.OnClickListener() {
 				@Override
 				public void onClick(View v) {
 					if (mDatum != null)
 						mDatum.navigatingAwayWithWarning();
 					Prev();
 				}
 			});
 			pnll.addButton("Next", new View.OnClickListener() {
 				@Override
 				public void onClick(View v) {
 					if (mDatum != null)
 						mDatum.navigatingAwayWithWarning();
 					Next();
 				}
 			});
 			mTopLL.addView(pnll);
 		}


		// Row, Col, AddNode, Notes row: -------------------------------------------------------------------
 		ViewLine rcBar = new ViewLine(this, true);
		mPopupAnchor = rcBar; // popup has to hang off something, and there might be no properties, the hline?
		rcBar.setWeightSum(4f);
		if (mTrial.mShowRowCol) {
			// NB, the values here are overwritten by the call to refreshNodeScreen, but we do need to create these..
			// NB - will perhaps crash if mShowRowCol not true

			/**
			 * Add 4 textviews to show, eg, "Row 1 Col 1". Where the row col values have popup menu
			 * options to navigate to other row/cols. The dropdowns show all distinct values available
			 * for that field (row or col), but if you select a value for which there is no node for the
			 * currently selected other field you cannot navigate there. An alternative approach we could
			 * take would be to only show in the popup the values for which there is a node to navigate to.
			 * NB we use popups instead of spinners, since unless the popup is activated the code is simply
			 * using textviews (eg when changing them after other navigation means) and therefore doesn't have
			 * to find and select the right value in a spinner when things change.
			 *
			 * Note we could probably replace each pair of textviews ("row" "1") with a single one ("row 1")
			 */
			mNodePropValue1 = rcBar.addTextView(null, 2);
			mNodePropValue2 = rcBar.addTextView(null, 2);
//			mNodePropPrompt1 = rcBar.addTextView(mTrial.getIndexName(0), 1);
//			mNodePropValue1 = rcBar.addTextView(null, 1);
//			mNodePropPrompt2 = rcBar.addTextView(mTrial.getIndexName(1), 1);
//			mNodePropValue2 = rcBar.addTextView(null, 1);
			View.OnClickListener dry = new View.OnClickListener() {
				@Override
				public void onClick(View arg0) {
					/**
					 * Make and display popup menu, has to work for both indexes.
					 */
					final boolean ind1 = arg0 == mNodePropValue1;

					PopupMenu mPop = new PopupMenu(mCtx, ind1 ? mNodePropValue1 : mNodePropValue2);
					Menu m = mPop.getMenu();
					NodeProperty np2 = mTrial.getFixedNodeProperty(ind1 ? Trial.FIELD_ROW : Trial.FIELD_COL);
					// Perhaps only get valid distinct values here, given context, also generalize for row and col.
					// need to look at local node special case.
					final ArrayList<Integer> mItems = (ArrayList<Integer>) np2.getDistinctValues();
					for (Integer i : mItems) {
						MenuItem item = m.add(Menu.NONE, i, Menu.NONE, i.toString());
					}
					mPop.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
						@Override
						public boolean onMenuItemClick(MenuItem item) {
							int val = item.getItemId();
							Node nd = mTrial.getCurrNode();
							Node nn = ind1 ?
									mTrial.getNodeByRowCol(val, nd.getCol()) :
									mTrial.getNodeByRowCol(nd.getRow(), val);
							if (nn == null)
								Util.msg("No matching node");
							else
								mTrial.gotoNodebyId(nn.getId());
							refreshNodeScreen();
							return false;
						}
					});
					mPop.show();
				}
			};
			mNodePropValue1.setOnClickListener(dry);
			mNodePropValue2.setOnClickListener(dry);

// Old code: I first used a spinner rather than a popup, and in this case needed the following in resetNodeScreenDetails()
            // use stored adapter or perhaps check if changed before doing this to save time?
			//mNodeProp1SpinPos = ((ArrayAdapter)mNodePropSpin1.getAdapter()).getPosition(node.getRow());
			//mNodePropSpin1.setSelection(mNodeProp1SpinPos);

// And here is the spinner version. Delete me when happy, (and this is in svn)
//			mNodePropPrompt1 = rcBar.addTextView(mTrial.getIndexName(0), 1);
//
//			/*
//			 * Get the list of rows:
//			 */
//			NodeProperty np1 = mTrial.getFixedNodeProperty(Trial.FIELD_ROW);
//			final ArrayList<Integer> mItems = (ArrayList<Integer>) np1.getDistinctValues();
//			mNodePropSpin1 = rcBar.addSpinner(mItems, null, null);
//
//			/*
//			 * Handler for row/col spinner selection:
//			 */
//			mNodePropSpin1.setOnItemSelectedListener(new OnItemSelectedListener() {
//				@Override
//				public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
//					// First check if the selected value is valid when paired with the other index:
//					Node nd = mTrial.getCurrNode();
//					int row = mItems.get(position);
//					Node nn = mTrial.getNodeByRowCol(row, nd.getCol());
//					if (nn == null) {
//						Util.msg("No matching node");
//						// NB the spinner is still changed at this point, but refreshNodeScreen will set it back
//					} else
//						mTrial.gotoNodebyId(nn.getId());
//					refreshNodeScreen();
//				}
//				@Override
//				public void onNothingSelected(AdapterView<?> parentView) {}
//			});

		}

		// If node creation enabled, add button to create node:
		if (mTrial.getBooleanPropertyValue(Trial.NODE_CREATION)) {
			Button addNodeButton = rcBar.addButton("+Node", new View.OnClickListener() {
				@Override
				public void onClick(View v) {
						// Create new node and navigate to it:
						Result res = mTrial.addLocalNode();
						if (res.bad()) {
							Util.msg(res.errMsg());
							return;
						}
						Node n = (Node)res.obj();
						mTrial.gotoNodebyId(n.getId());
						refreshNodeScreen();
				}
			});
			Util.setColoursBlackOnWhite(addNodeButton);
		}		
		
		// Notes button:
		Button notesButton = rcBar.addButton("Notes", new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivityForResult(new Intent(ActTrial.this, ActTUNotes.class), ARC_NOTES_ACTIVITY);
			}
		});
		Util.setColoursBlackOnWhite(notesButton);
		
		// Handle reps:
//		mRepsPresent = mTrial.AttributePresent(Trial.REP_ATTRIBUTE_NAME);
//		if (mRepsPresent) { // add text fields to show rep in the rowColBar
//			if (true) {
//			ViewGroup.LayoutParams llp = mNodePropValue1.getLayoutParams();
//			float px = mNodePropValue1.getTextSize();
//			//LinearLayout rcBar = (LinearLayout) findViewById(R.id.rowColBar);
//			TextView repPrompt = new TextView(this);
//			Util.SetTextViewColours(repPrompt);
//			repPrompt.setTextSize(px);
//			repPrompt.setText("Rep:");
//			rcBar.addView(repPrompt, llp);
//			mTVRep = new TextView(this);
//			mTVRep.setTextSize(px);
//			Util.SetTextViewColours(mTVRep);
//			rcBar.addView(mTVRep, llp);
//			} else {
//				rcBar.AddTextView("Rep", 1);
//				mTVRep = rcBar.AddTextView(null, 1);				
//			}
//		}
		mTopLL.addView(rcBar);
	
		// Node Property display widgets: --------------------------------------------------------------
		for (PropertyWidget aw : mPropertyWidgets) {
			mTopLL.addView(new Hline(this));
			mTopLL.addView(aw);
		}
		
		mTopLL.addView(new Hline(this, R.color.red));  // Mark end of navigation/info area with red line
		// End Navigation/Info stuff ---------------------------------------------------------------------

		/*
		 * Create Browse Pane: ----------------------------------------------------------------------------
		 */
		final VerticalList mBrowseVL = new VerticalList(this, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				genericEventHandler(v.getId());
			}
		});
		
		mBrowseVL.addButton("Select Browse Items", 0, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// Get user selection of available RepSets:
				final RepSet[] repSets = mTrial.getRepSetsArray();
				if (repSets.length <= 0) {
					Util.msg("No existing score sets found - nothing to browse");
					return;
				}
				// If mBrowseChecks already allocated then use existing selections, else allocate and pre-select all
				if (mBrowseChecks == null) {
					mBrowseChecks = new boolean[repSets.length];
					for (int i = 0; i < repSets.length; ++i)
						mBrowseChecks[i] = true;
				}
				DlgList.newInstanceMulti(0, "Choose Score Sets to Browse", repSets, mBrowseChecks,
						new DlgList.ListMultiSelectHandler() {
							@Override
							public void onMultiListDone(int context, boolean[] checks) {
								mBrowseSets.clear();
								for (int i = 0; i < checks.length; ++i)
									if (checks[i])
										mBrowseSets.add(repSets[i]);
								refreshNodeScreen();
							}
						});
			}
		});
		mBrowseContentPane = new FrameLayout(this);
		mBrowseContentPane.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		mBrowseVL.addView(mBrowseContentPane);
			
		/*
		 * Create Scoring Pane: ----------------------------------------------------------------------------
		 */
		final VerticalList scoreVL = new VerticalList(this, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				genericEventHandler(v.getId());
			}
		});

		// add TraitSelector:
		mTraitSelector = new SelectorWidget(this,
				selecteesFromRepSets(mScoreSets.toArray(new RepSet[mScoreSets.size()])),
				new SelectorWidget.SelectorCallback() {
					// Note node is unchanged, so don't need to refresh that.
					@Override
					public void selectionChanged(SelectorWidget.Selectee sel) {
						ActTrial.this.selectionChanged(sel, true);
					}

					@Override
					public void buttonActionNoElements() {
						genericEventHandler(R.id.selectTraits);
					}

					@Override
					public String buttonPromptNoElements() {
						return mCtx.getString(R.string.selectTraits);
					}
				});
		mTraitSelector.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util
				.dp2px(g.ScreenItemSize(Globals.SIZE_BIG))));
		Util.setBackgroundBlack(mTraitSelector);
		scoreVL.addView(mTraitSelector);
		scoreVL.addView(new Hline(this));

		mScoreContentPane = new FrameLayout(this);
		mScoreContentPane.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		scoreVL.addView(mScoreContentPane);
			
		/*
		 * Set up the view pager (to hold the browse and score views) and add it to the layout.
		 */
		mViewPager = new ViewPager(this) {
			@Override
			public boolean onDragEvent(DragEvent event) {
				// Need to get this to the trait selector as the viewPager is intercepting drag events.
				mTraitSelector.dispatchDragEvent(event);
				return true;
			}
		};
		mViewPager.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		PagerAdapter pa = new PagerAdapter() {
			@Override
			public Object instantiateItem(ViewGroup vg, int position) {
				if (position == 0) {
					((ViewPager)vg).addView(mBrowseVL);
					return mBrowseVL;
				} else {
					((ViewPager)vg).addView(scoreVL);
					return scoreVL;	
				}
			}

			@Override
			public int getCount() {
				return 2;
			}

			@Override
			public boolean isViewFromObject(View view, Object obj) {
				return view == obj;
			}
		};
		mViewPager.setAdapter(pa);
		mTopLL.addView(mViewPager);
		
		refreshNodeScreen();
		return mTopLL;
	}

	// ### End Scoring Stuff:###########################################################################################

//	/*
//	 * fillPrinterTemplate()
//	 * Convert template to zebra printer commands. 
//	 * Occurrences of $FP[B|T](<propertyName>) are replaced with 
//	 * with text (T) or barcode (B) versions of the propertyName
//	 * for the node. 
//	 * NB you cannot have both Text and Barcode in same line.
//	 * Line with barcode should have nothing but single $FPB(<propName>)
//	 */
//	Result fillPrinterTemplate(String template, Node node) {
//		String out = "";
//		Scanner scanner = null;
//		try {
//			int startX = 50;
//			int currY = Printer.GAP;
//			scanner = new Scanner(template);
//			while (scanner.hasNextLine()) {
//				String line = scanner.nextLine();
//				int closePos;
//
//				// Barcode line:
//				if (line.startsWith("$FPB(")) {
//					closePos = line.indexOf(')', 5);
//					if (closePos < 0)
//						return new Result(false, String.format("Invalid line (%s)", line));
//					String fieldName = line.substring(5, closePos);
//					String fieldVal = node.getPropertyString(fieldName);
//					if (fieldVal == null) {
//						return new Result(false, "No value for property " + fieldName + " in current node");
//					}
//					int bcType = 128;
//					int narrowBarWidth = 1;
//					int barWidthRatioCode = 1;
//					int height = 50;
//					currY = 10;
//					out += String.format("BARCODE %d %d %d %d %d %d %s\r\n", bcType, narrowBarWidth,
//							barWidthRatioCode, height, startX, currY, fieldVal);
//					currY += height + Printer.BARCODE_SUB_TEXT_HEIGHT + Printer.GAP;
//					continue;
//				}
//		  
//				// Text line, replace variables (if any) then make printer command:
//				int last = 0;
//				int pos = line.indexOf("$FPT(", last);
//				String subbed = "";
//				while (pos != -1) {
//					subbed += line.substring(last, pos);
//					// Read the rest of the insertion
//					closePos = line.indexOf(')', pos);
//					if (closePos < 0) {
//						return new Result(false, "Invalid pattern in line: " + line);
//					}
//					String fieldName = line.substring(pos + 5, closePos);
//					String fieldVal = node.getPropertyString(fieldName);
//					if (fieldVal == null)
//						fieldVal = ""; // For text, let them print empties. MFK what about NA?
//					subbed += fieldVal;
//
//					last = closePos + 1;
//					pos = line.indexOf("$FPT(", last);
//				}
//				subbed += line.substring(last);
//				int fontNum = 4;
//				int fontSize = 0;
//				out += String.format("TEXT %d %d %d %d %s\r\n",
//						fontNum, fontSize, startX, currY, subbed);
//				currY += 47 + Printer.GAP;  // NB according to manual font 4 size 0 is 47 pixels high
//			}
//		} finally {
//			if (scanner != null)
//				scanner.close();
//		}
//		
//		return new Result(out);
//	}
	/*
	 * genericEventHandler() ------------------------------------------------------------------------------------------
	 * For handling events, from menu items or buttons for example. 
	 */
	private void genericEventHandler(int id) {
		switch (id) {
		// Dataset Menu:
		case R.id.closeTrial:
			finish();
			closeTrial();
			break;
		case R.id.selectTraits:
			DlgChooseScoreSets.newInstance(new DlgChooseScoreSets.handler() {
				@Override
				public void onListSelect(ArrayList<RepSet> scoreSets) {
					setScoringList(scoreSets);
				}
			}, mTrial);
			break;
		case R.id.manageScoreSets:
			Intent intent = new Intent(this, ActScoreSets.class);
			startActivityForResult(intent, ARC_SCORE_SET_LIST_ACTIVITY);
			break;
		case R.id.preferences:
			startActivity(new Intent(this, Preferences.class));
			break;
		case R.id.search:
			if (TrialOpen())
				DlgSearch.newInstance(Globals.FragMan(), mTrial, new DlgSearch.SearchHandler() {
					@Override
					public void foundNode(Node tu) {
							mTrial.gotoNodebyId(tu.getId());
							refreshNodeScreen();
					}
				});
			else
				Util.msg("You must open a trial to search");
			break;
		case R.id.filter:
			if (TrialOpen())
				DlgFilter.newInstance();
			else
				Util.msg("You must open a trial to filter"); // MFK - I think we can assume a trial is open now..
			break;
		case R.id.zprint:
			Node node = mTrial.getCurrNode();
			if (node == null) {
				Util.toast("No current node, will not print");
			}
			String macAddr = g.getPrinterMacAddr(); //"ac3fa41500ff";
			if (macAddr == null) {
				Util.msg("No printer selected, goto Bluetooth Devices");
				break;
			}
			//DlgPrintSetup.PrintSetupState prState = new DlgPrintSetup.PrintSetupState(mTrial.getId());
			DlgPrintSetup.PrintSetupState prState =
					DlgPrintSetup.PrintSetupState.loadFromDb(mTrial.getId(), Globals.g.db());
			Result res = prState.fillPrinterTemplate(node);
			if (res.bad()) {
				Util.msg(String.format("Cannot print (%s)", res.errMsg()));
			}
			String data = (String)res.obj();
			Printer prt = new Printer(mCtx, macAddr);
			prt.send(data, prState);
			break;
		case R.id.zebPrinterSetup:
			DlgPrintSetup.newInstance(mTrial.getId(), this);
			break;
		case R.id.walkOrder:
			DlgSetNodeOrder.newInstance();
			break;
		case R.id.connectBluetooth:
			startActivityForResult(new Intent(this, ActBluetooth.class), ARC_BLUETOOTH_ACTIVITY);
			break;
		case R.id.about:
			startActivity(new Intent(this, ActAbout.class));
			break;		
		case R.id.reset:
			recreate();
			break;
						
//		case R.id.traitDialog:
//			if (TrialOpen())
//				TraitDialog.newInstance(this, Globals.FragMan(), mTrial, new TraitDialog.importTraitsHandler() {				
//					@Override
//					public void eventHandler(int fred) {
//						GenericEventHandler(fred);
//					}
//				});
//			break;
//		case R.id.importTraits:
//			Intent loadTraitsIntent = new Intent(this, FileBrowse.class);
//			loadTraitsIntent.putExtra("filter", ".*\\.csv");
//			startActivityForResult(loadTraitsIntent, ARC_IMPORT_TRAIT);
//			break;
//		case R.id.help:
//			if (cTrial == null) { // no trial open
//
//			} else { // trial open
//
//			}
//			break;		
//		case R.id.filterTrial:
//			Util.msg("filter");
//			break;
		}
	}


	public void setNodeOrder(int option, NodeAttribute att1, int dir1, NodeAttribute att2, int dir2, boolean reverse) {
		if (mTrial == null) return;
		Pstate pstate = mTrial.getPstate();
		pstate.setSortCode(SortType.fromValue(option));
		pstate.setSortAttribute(0, att1);
		pstate.setSortAttribute(1, att2);
		pstate.setSortDirection(0, SortDirection.fromValue(dir1));
		pstate.setSortDirection(1, SortDirection.fromValue(dir2));
		pstate.setReverse(reverse);
		pstate.sortList(null, mTrial);
		refreshNodeScreen();
	}
	
	/*
	 * setFilter()
	 * Basically a call back for DlgFilter to handle the user selections.
	 */
	public void setFilter(NodeAttribute att, String attval) {
		if (mTrial == null) return;
		mTrial.setFilter(att, attval);
		refreshNodeScreen();
	}

	// trial should always be open, I think. Not sure if the activity object is really
	// destroyed on Finish(). What if blue tooth device has callback to it?
	private boolean TrialOpen() {
		// TODO Auto-generated method stub
		return true;
	}
	private void saveTrialPos() { // this moved from Trial - remove or change
		Node ctu = mTrial.getCurrNode();
		if (ctu != null) {
			g.prefs().setTrialPos(mTrial.getId(), ctu.getId());
		}
	}

	private void closeTrial() {
		if (mTrial == null)
			return;
		saveTrialPos();
		/*
		 * Close bluetooth connections. A good idea anyway, but if we don't,
		 * it seems other asynctasks (eg to upload data) will not work unless
		 * called in a special way to allow multitasking with the bluetooth
		 * background task.
		 */
		if (mBlueteeth != null) {
			for (BluetoothConnection bc : mBlueteeth) {
				bc.myCancel();
			}
		}
		//mTrial = null;
		g.setTrial(null);
		mAcbar.setTitle(Globals.getUsername());  // Clear trial name from action bar
	}

	/*
	 * MFK fix/hack attempt for bug where user navigates to different node
	 * (eg with barcode scanner) before accepting photo. We were saving the
	 * metadata to the current node via mDatum, which has changed.
	 */
	public boolean setDatumAwaitingPhotoResult(TraitPhoto.DatumPhoto dat) {
		if (datumAwaitingPhotoResult != null)
			return false;
		datumAwaitingPhotoResult = dat;
		flog("setDatumAwaitingPhotoResult", "" + dat.getId());
		return true;
	}
	private void clearDatumAwaitingPhotoResult() {
		datumAwaitingPhotoResult = null;
	}
	private TraitPhoto.DatumPhoto getDatumAwaitingPhotoResult() { return datumAwaitingPhotoResult; }
	private boolean mRecreating;
	private int mRecCode;
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) { // should be called before onResume when restarting
		flog("lifecycle", "onActivityResult " + 
				((requestCode == ARC_CAPTURE_IMAGE) ? "ARC_CAPTURE_IMAGE" : (""+requestCode))
				);
		switch (requestCode) {
		case ARC_IMPORT_TRAIT:
			if (resultCode == RESULT_OK) {
				String fname = data.getStringExtra("result");
				Result res = mTrial.ImportTraitFile(fname);
				if (res.good()) {
					//this.GenericEventHandler(R.id.traitDialog);
				} else {
					Util.msg("Error importing file " + fname + " (" + res.errMsg() + ")");
					break;
				}
			}
			break;
		case ARC_CAPTURE_IMAGE:
			TraitPhoto.DatumPhoto photoDat = getDatumAwaitingPhotoResult();
			flog("lifecycle", "  onActivityResult " + (photoDat == null ? "no waiting datum" : ("got datum " + photoDat.getId())));
			if (photoDat == null) {
				Util.msg("Cannot save - unexpected problem with photo");
				break;
			}
			// This is for DatumPhoto, and it's unfortunate that we have to have
			// specific code here. We could get around this by have a register (and deregister)
			// handler function, which maintains a list of requestCodes and
			// objects to handle them (eg a map Integer -> Callable object).
			// To get here, we should have been in an open trial, in a datum
			if (resultCode == RESULT_OK) {
				photoDat.PhotoAccepted();
				// Image captured and saved to fileUri specified in the Intent
				//Util.msg("Image saved to:\n");// + data.getData());
			} else if (resultCode == RESULT_CANCELED) {
				Util.msg("photo cancelled"); // User cancelled the image capture		
			}
			else Util.msg("photo failed"); // Image capture failed, advise user
			clearDatumAwaitingPhotoResult();
			break;
		case ARC_NOTES_ACTIVITY:
			// The set of notes for the current node may have changed, the notes are shown
			// in various places depending on the circumstances, and this is all done in showScores().
			// NB, we can't just call refreshNodeScreen, as this would disturb things if we are in the
			// middle of recording a score (eg having typed in some digits, but not hit Done).
			showScores();
			break;
		case ARC_SCORE_SET_LIST_ACTIVITY:
			if (resultCode == RESULT_OK)
				clearScoringList(); // have state? this called before onResume according to docs
			break;
		case ARC_BLUETOOTH_ACTIVITY:
			// We may have connected to bluetooth printer, in which case menu might need adjusting:
			invalidateOptionsMenu();
			break;
		}
	}
	
		
	/*
	 * handleNavigationValue()
	 * Value received from bluetooth device, intended for navigating to a plot.
	 */
	@Override
	public void handleNavigationValue(String barcode) {
		if (TrialOpen()) {
			/*
			 *  See if any of the scoring traits have a barcode attribute.
			 *  If so, look up the barcode. First match found is used.
			 */
			for (RepSet rs : mScoreSets) {
				Trait trt = rs.getTrait();
				NodeAttribute bcAtt = mTrial.getAttribute(trt.getBarcodeAttributeId());
				if (bcAtt != null) {
					// look up barcode in this attribute:
					// MFK note no checks for ambiguous barcodes.
					ArrayList<Long> nids = mTrial.getMatchingNodes(bcAtt, barcode);
					if (nids.size() == 1) {
						mTrial.gotoNodebyId(nids.get(0));
						refreshNodeScreen();
						gotoTraitFirstUnscored(trt, true);
						return;
					}
				}
			}
			
			if (mTrial.gotoNode(barcode))
				refreshNodeScreen();
			else
				Util.msg("No node found with barcode " + barcode);
		}
	}
	
	
	/*
	 * GotoTraitFirstUnscored()
	 * Goto the first unscored instance of the specified trait.
	 * Return true iff one is found.
	 * 
	 * Version with failToFirstScored parameter.
	 * If this false, behaviour is the same as the version without the parameter.
	 * If it is true, however, then there are instances of the specified trait,
	 * but all of them are already scored, then we go to the first instance,
	 * but still return false.
	 */
	public boolean gotoTraitFirstUnscored(Trait trt, boolean failToFirstScored) {
		int index = 0;
		int failIndex = -1;
		TraitInstance failTi = null;
		
		Selectee [] currList = mTraitSelector.currList();
		for (SelectorWidget.Selectee s : currList) {
			TraitInstance ti = (TraitInstance)s;
			if (ti.getTrait() == trt)
				if (!s.hasScore()) {
					mTraitSelector.setSelection(s);
					return true;
				} else {
					if (failIndex < 0) {
						failIndex = index;
						failTi = ti;
					}
				}
			++index;
		}
		if (failToFirstScored) {
			mTraitSelector.setSelection(failTi);
		}
		return false;
	}
	

	/*
	 * HandleBluetoothDataValue()
	 * Value received from bluetooth device, intended for scoring.
	 * Note - if a trait has been associated with the device (devTrait not null),
	 * then we search for an unscored score set of that trait within the
	 * trait selector (i.e. the currently selected traits), if one is found (note
	 * there could be multiple is the trait has multiple reps) then we set the
	 * first one found to the specified value. Note the trait selector doesn't have
	 * to currently be on an instance of the specified trait.
	 * If devTrait is null, then we assume the score is intended for the current datum.
	 */
	@Override
	public void HandleBluetoothDataValue(String val, Trait devTrait) {
		if (TrialOpen()) {
			if (mDatum != null) {
				Trait trt = mDatum.GetTrait();
				if (devTrait != null && trt != devTrait && !gotoTraitFirstUnscored(devTrait, false)) {
				        Util.msg("No unscored instance of trait " + devTrait.getCaption() + " in current node");
				        return;
				}
				if (!mDatum.processTextValue(val)) {
					Util.msg("Could not set score");
				}
			} else
				Util.msg("Not currently entering a score");
		}
	}

	/*
	 * addBluetoothConnection()
	 * Called when a new bluetooth connection is made for this trial.
	 */
	public void addBluetoothConnection(BluetoothConnection bc) {
		mBlueteeth.add(bc);
	}


	@Override
	public void dynSetTI(TraitInstance ti) {
		// MFK check if null etc..
		RepSet rs = ti.getRepset();
		Selectee [] currList = mTraitSelector.currList();
		for (Selectee s : currList) {
			TraitInstance listTi = (TraitInstance)s;
			if (listTi.isDynamic() && listTi.getRepset() == rs) {
				mTraitSelector.replace(listTi, ti);
				break;
			}
		}
		
		mDatum = ((TraitInstance)(mTraitSelector.getCurrentSelectee())).getDatum();
		mDatum.Display(this, mScoreContentPane, this);	
	}	
}

// put this at very start of onCreate for testing
// see http://stackoverflow.com/questions/11340257/sqlite-android-database-cursor-window-allocation-of-2048-kb-failed
//if (devMode)
//{
//     StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
//     .detectLeakedSqlLiteObjects()
//     .detectLeakedClosableObjects()
//     .penaltyLog()
//     .penaltyDeath()
//     .build());
//}

