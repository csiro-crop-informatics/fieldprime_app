package csiro.fieldprime;

import java.util.HashSet;
import java.util.Set;

import csiro.fieldprime.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

/*
 * Class Prefs
 * Intended to bundle up all the preferences stuff.
 * 
 * Note: To add a preference, currently you need to do the following:
 * . add a class variable
 * . add private and public get functions
 * . add a set function if needed
 * . update the constructor
 * . update onSharedPreferenceChanged().
 * 
 * If you need the new preference to appear on the preferences screen then you need
 * to add it to preferences.xml.
 * 
 * ??
 * I would prefer to have each new preference a single initiated object, not sure if this is possible.
 * 
 * 
 * 
 * 
 */
public class Prefs implements OnSharedPreferenceChangeListener {
	public final static int INT_INPUT_METHOD_RANGE_BUTTONS = 1;
	public final static int INT_INPUT_METHOD_KEYPAD = 2;

	// Keys for saved preferences:
	private static final String PKEY_LAST_IDENT = "lastIdent"; // replace with R string? but this not in xml prefs..
	private static final String PKEY_TRIAL_ID = "trialId";
	private static final String PKEY_TRIAL_NODE_ID = "nodeId";

	private SharedPreferences mPrefs;
	private Context mCtx;

	// Cached preference vals:
	private int mIntInputMethod;
	private String mLastIdent;
	private int mWalkOrder;
	private boolean mWalkReverse;
	private boolean mAutoProgressWithin;
	private boolean mAutoProgressBetween;
	private String mServerURL;
	private String mServerUser;
	private String mServerPassword;

	private boolean mNotifyRow;
	private boolean mNotifyCol;
	private boolean mNotifySkip;
	private boolean mNotifyBeeps;
	private Set<String> mNotifications;
	private boolean mInterleaveTIs;
	private int mMaxGPSDelta;

	Prefs(Context ctx) {
		mCtx = ctx;
		mPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		mPrefs.registerOnSharedPreferenceChangeListener(this); // Register a listener for preference changes:

		// Get initial values:
		mIntInputMethod = _GetIntInputMethod();
		mLastIdent = _GetLastIdent();
		mWalkOrder = _GetWalkOrder();
		mWalkReverse = _GetWalkReverse();
		mAutoProgressWithin = _getAutoProgressWithin();
		mAutoProgressBetween = _getAutoProgressBetween();
		mAutoProgressSkip = _GetAutoProgressSkip();
		mServerURL = _GetServerURL();
		mServerUser = _GetServerUser();
		mServerPassword = _GetServerPassword();
		mNotifyRow = _GetNotifyRow();
		mNotifyCol = _GetNotifyCol();
		mNotifySkip = _GetNotifySkip();
		mNotifyBeeps = _GetNotifyBeeps();
		mNotifications = _GetNotifications();
		mInterleaveTIs = _GetInterleaveTIs();
		mMaxGPSDelta = _GetMaxGPSDelta();
	}

	//----------------------------------------------------------------------------------------
	private String _GetServerURL() {
		return mPrefs.getString(mCtx.getString(R.string.pref_server_address), mCtx.getString(R.string.def_server_address));
	}

	public String GetServerURL() {
		return mServerURL;
	}

	//----------------------------------------------------------------------------------------
	private String _GetServerUser() {
		return mPrefs.getString(mCtx.getString(R.string.pref_server_user), "");
	}

	public String GetServerUser() {
		return mServerUser;
	}

	//----------------------------------------------------------------------------------------
	private String _GetServerPassword() {
		return mPrefs.getString(mCtx.getString(R.string.pref_server_password), "");
	}

	public String GetServerPassword() {
		return mServerPassword;
	}

	//----------------------------------------------------------------------------------------
	private int _GetWalkOrder() {
		String s = mPrefs.getString(mCtx.getString(R.string.pref_key_walk_order), "1");
		return Integer.parseInt(s);
	}

	public int GetWalkOrder() {
		return mWalkOrder;
	}

	//----------------------------------------------------------------------------------------
	private boolean _GetWalkReverse() {
		return mPrefs.getBoolean(mCtx.getString(R.string.pref_walk_reverse), false);
	}

	public boolean GetWalkReverse() {
		return mWalkReverse;
	}

	//----------------------------------------------------------------------------------------
	private boolean _GetInterleaveTIs() {
		return mPrefs.getBoolean(mCtx.getString(R.string.pref_interleave_tis), false);
	}

	public boolean GetInterleaveTIs() {
		return mInterleaveTIs;
	}
	//----------------------------------------------------------------------------------------
	private int _GetMaxGPSDelta() {
		String sVal = mPrefs.getString(mCtx.getString(R.string.pref_gps_max_delta), "5");
		return Integer.parseInt(sVal);
	}

	public int GetMaxGPSDelta() {
		return mMaxGPSDelta;
	}
	
	//----------------------------------------------------------------------------------------
	private boolean _getAutoProgressWithin() {
		return mPrefs.getBoolean(mCtx.getString(R.string.pref_autoprogress_within_nodes), true);
	}

	public boolean getAutoProgressWithin() {
		return mAutoProgressWithin;
	}

	//----------------------------------------------------------------------------------------
	private boolean _getAutoProgressBetween() {
		return mPrefs.getBoolean(mCtx.getString(R.string.pref_autoprogress_between_nodes), false);
	}

	public boolean getAutoProgressBetween() {
		return mAutoProgressBetween;
	}

	//----------------------------------------------------------------------------------------
	private boolean mAutoProgressSkip;

	private boolean _GetAutoProgressSkip() {
		return mPrefs.getBoolean(mCtx.getString(R.string.pref_autoprogress_skip_already_scored), false);
	}

	public boolean GetAutoProgressSkip() {
		return mAutoProgressSkip;
	}

	//----------------------------------------------------------------------------------------
	private int _GetIntInputMethod() {
		String s = mPrefs.getString(mCtx.getString(R.string.pref_key_int_input_method), "1");
		return Integer.parseInt(s);
	}

	public int GetIntInputMethod() {
		return mIntInputMethod;
	}

	//----------------------------------------------------------------------------------------
	private boolean _GetNotifyRow() {
		return mPrefs.getBoolean(mCtx.getString(R.string.pref_notify_row), true);
	}

	public boolean GetNotifyRow() {
		return mNotifyRow;
	}

	private boolean _GetNotifyCol() {
		return mPrefs.getBoolean(mCtx.getString(R.string.pref_notify_column), true);
	}

	public boolean GetNotifyCol() {
		return mNotifyCol;
	}

	private boolean _GetNotifySkip() {
		return mPrefs.getBoolean(mCtx.getString(R.string.pref_notify_skip), true);
	}

	public boolean GetNotifySkip() {
		return mNotifySkip;
	}
	
	private boolean _GetNotifyBeeps() {
		return mPrefs.getBoolean(mCtx.getString(R.string.pref_notify_beeps), true);
	}
	public boolean GetNotifyBeeps() {
		return mNotifyBeeps;
	}
	
	private Set<String> _GetNotifications() {
		return mPrefs.getStringSet(mCtx.getString(R.string.pref_notify_selections), new HashSet<String>());
	}
	public Set<String> GetNotifications() {
		return mNotifications;
	}

	//----------------------------------------------------------------------------------------
	private String _GetLastIdent() {
		return mPrefs.getString(PKEY_LAST_IDENT, "abc123");
	}

	public String GetLastIdent() {
		return mLastIdent;
	}

	void SetLastIdent(String username) {
		SharedPreferences.Editor editor = mPrefs.edit();
		editor.putString(PKEY_LAST_IDENT, username);
		editor.commit();
	}

	//----------------------------------------------------------------------------------------

	/*
	 * Trial position storage.
	 * Stuff here to record the current position for a single trial (eg the last used).
	 * Note another option would be to store a position for each trial,
	 * in which case we might better use the db instead.
	 * 
	 * 13/1/15. Currently only position saved, and this only when user explicitly closes
	 * trial. Request for auto save so if app crashes position is there, also if possible
	 * the currently selected score sets and perhaps bluetooth connections. 
	 * 
	 */
	public class TrialPos {
		long trialId;
		long nodeId;

		TrialPos(long id, long nodeId) {
			this.trialId = id;
			this.nodeId = nodeId;
		}
	}

	public void setTrialPos(long trialId, long nodeId) {
		SharedPreferences.Editor editor = mPrefs.edit();
		editor.putLong(PKEY_TRIAL_ID, trialId);
		editor.putLong(PKEY_TRIAL_NODE_ID, nodeId);
		editor.commit();
	}

	public TrialPos getTrialPos() {
		long trialId = mPrefs.getLong(PKEY_TRIAL_ID, -1);
		long nodeId = mPrefs.getLong(PKEY_TRIAL_NODE_ID, -1);
		if (trialId == -1 || nodeId == -1)
			return null;
		else
			return new TrialPos(trialId, nodeId);
	}

	//----------------------------------------------------------------------------------------

	@Override
	public void onSharedPreferenceChanged(SharedPreferences arg0, String key) {
		// Cache the values, so that the get functions don't have to do a lookup everytime
		if (key.equals(mCtx.getString(R.string.pref_key_walk_order)))
			mWalkOrder = _GetWalkOrder();
		else if (key.equals(mCtx.getString(R.string.pref_walk_reverse)))
			mWalkReverse = _GetWalkReverse();
		else if (key.equals(mCtx.getString(R.string.pref_key_int_input_method)))
			mIntInputMethod = _GetIntInputMethod();
		else if (key.equals(mCtx.getString(R.string.pref_autoprogress_within_nodes)))
			mAutoProgressWithin = _getAutoProgressWithin();
		else if (key.equals(mCtx.getString(R.string.pref_autoprogress_between_nodes)))
			mAutoProgressBetween = _getAutoProgressBetween();
		else if (key.equals(mCtx.getString(R.string.pref_autoprogress_skip_already_scored)))
			mAutoProgressSkip = _GetAutoProgressSkip();
		else if (key.equals(mCtx.getString(R.string.pref_notify_row)))
			mNotifyRow = _GetNotifyRow();
		else if (key.equals(mCtx.getString(R.string.pref_notify_column)))
			mNotifyCol = _GetNotifyCol();
		else if (key.equals(mCtx.getString(R.string.pref_notify_skip)))
			mNotifySkip = _GetNotifySkip();
		else if (key.equals(mCtx.getString(R.string.pref_notify_beeps)))
			mNotifyBeeps = _GetNotifyBeeps();
		else if (key.equals(mCtx.getString(R.string.pref_notify_selections)))
			mNotifications = _GetNotifications();
		else if (key.equals(mCtx.getString(R.string.pref_server_address)))
			mServerURL = _GetServerURL();
		else if (key.equals(mCtx.getString(R.string.pref_server_user)))
			mServerUser = _GetServerUser();
		else if (key.equals(mCtx.getString(R.string.pref_server_password)))
			mServerPassword = _GetServerPassword();
		else if (key.equals(mCtx.getString(R.string.pref_interleave_tis)))
			mInterleaveTIs = _GetInterleaveTIs();
		else if (key.equals(mCtx.getString(R.string.pref_gps_max_delta)))
			mMaxGPSDelta = _GetMaxGPSDelta();

		// Let the creator know:
		((OnSharedPreferenceChangeListener) mCtx).onSharedPreferenceChanged(arg0, key);
		
	}

}
