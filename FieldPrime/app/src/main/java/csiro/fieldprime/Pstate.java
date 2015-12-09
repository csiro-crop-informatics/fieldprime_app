/*
 * Pstate.java
 * Michael Kirk 2015
 * 
 * Trial scoring state that may need to persist through restarts:
 * From shared prefs method: 13/1/15. Currently only position saved, and this only when user explicitly closes
 * trial. Request for auto save so if app crashes position is there, also if possible
 * the currently selected score sets and perhaps bluetooth connections. 
 * 
 * This to hold all state associated with an trial open for scoring.
 * Including persisting it . It knows what state needs persisting through
 * system restarts and what through user closure of trials.
 * 
 * This could be in ActScoring if it is restricted to scoring state, or in own file.
 */

package csiro.fieldprime;

import java.util.ArrayList;

import android.database.sqlite.SQLiteDatabase;

import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.NodeAttribute;
import csiro.fieldprime.Trial.SortDirection;
import csiro.fieldprime.Trial.SortType;


public class Pstate {
	/*
	 * Cached values when sorting to avoid db if possible.
	 * Put row/col in attributes. Otherwise no functionality to start from last row/col (I think).
	 * Ensure searching works on all datatypes, then could use scores. Perhaps need a canBeSorted()
	 * function to NodeProperty, and it would be false for photo scores for example.
	 */
	// DATA: -------------------------------------------------------------------------
	private SortType msSortCode = SortType.SORT_COLUMN_SERPENTINE;
	private NodeAttribute [] msSortAttributes = {null, null};
	private SortDirection [] msSortDirections = {null, null};
	private boolean mReverse = false;
	
	// Filtering stuff:
	private boolean mFiltering = false;
	private NodeAttribute msFilterAttribute;
	private String msFilterAttValue;
			
	// METHODS: -------------------------------------------------------------------------
	static private SQLiteDatabase g_db() {
		return Globals.g.db();
	}

	//*** Sorting stuff: ***************************************************************
	
	SortType getSortCode() { return msSortCode; }
	public void setSortCode(SortType sc) { msSortCode = sc; }
	public NodeAttribute getSortAttribute(int index) {
		if (index < 0 || index > 1) return null;
		return msSortAttributes[index];
	}
	public void setSortAttribute(int index, NodeAttribute att) {
		if (index < 0 || index > 1) return;
		msSortAttributes[index] = att;
	}
	SortDirection getSortDirection(int index) {
		if (index < 0 || index > 1) return null;
		//return msSortDirections[index];
		return msSortDirections[index] == null ? SortDirection.ASC : msSortDirections[index]; // hacky?
	}
	public void setSortDirection(int index, SortDirection sd) {
		if (index < 0 || index > 1) return;
		msSortDirections[index] = sd;
	}
	public boolean secondSortAttributeSet() {
		return msSortAttributes[1] != null;
	}
	
	/*
	 * sortList()
	 * Sorts node list of trl, could be parameterized to take list to sort.
	 */
	public void sortList(ArrayList<Node> NOT_USED_list, Trial trl) {
		SortType sc = getSortCode();
		if (sc == null) {
			Util.toast("No sort method specified");
			return;
		}
		switch (sc) {
		case SORT_COLUMN_SERPENTINE:
		case SORT_ROW_SERPENTINE:
		case SORT_COLUMN:
		case SORT_ROW:
			trl.sortNodes(getSortCode(), getReverse());
			Util.toast("Sorted: " + getSortCode().text(trl));
			break;
		case SORT_CUSTOM:
			SortDirection sd0 = getSortDirection(0);
			NodeAttribute nat0 = getSortAttribute(0);
			if (sd0 != null && nat0 != null) {
				trl.sortNodes3(nat0, sd0, 
						getSortAttribute(1), getSortDirection(1));
				Util.toast("Custom sort applied" + getSortAttribute(0).name() + getSortDirection(0).toString()
						+ ((getSortAttribute(1) == null) ? "" : 
							" " + getSortAttribute(1).name() + " " + getSortDirection(1).toString()));
			} else
				Util.toast("No sort applied, invalid custom sort options");
			break;
		}
	}

	//*** Filtering stuff: ***************************************************************
	
	/*
	 * setFilterAttribute()
	 * NB caller should ensure msFilterAttribute is cleared.
	 */
	private void setFilterAttribute(NodeAttribute nat) {
		msFilterAttribute = nat;
		// MFK maybe here we should set msFilterAttValue to null
	}
	
	public NodeAttribute getFilterAttribute() { return msFilterAttribute; }
	public String getFilterAttValue() { return msFilterAttValue; }
	public void setFilterAttValue(String attval) { msFilterAttValue = attval; }
	
	/*
	 * setFilter()
	 * Set both the attribute and chosen value.
	 */
	public void setFilter(NodeAttribute att, String attval) {
		if (att == null)
			clearFilter();
		mFiltering = true;
		setFilterAttribute(att);
		setFilterAttValue(attval);
	}
	public void clearFilter() {
		msFilterAttribute = null;
		msFilterAttValue = null;
		mFiltering = false;
	}
	public boolean isFiltering() {
		return mFiltering;
	}

//save and restore the filter stuff
//	then test if sort/filter being reapplied through close and rotation
//	score sets and pos next, look out for scoring states
	/*
	 * save()
	 * Write to db.
	 */
	public void save(long trialId) {
		// Sorting state:
		Tstore.TRIAL_SCORE_STATE_SORT_MODE.setIntValue(g_db(), trialId, 
				(msSortCode == null) ? null : msSortCode.value());
		Tstore.TRIAL_SCORE_STATE_SORT_REVERSE.setIntValue(g_db(), trialId, mReverse ? 1 : 0);

		if (msSortCode == SortType.SORT_CUSTOM) {
			Tstore.TRIAL_SCORE_STATE_SORT_ATT1.setLongValue(g_db(), trialId,
					msSortAttributes[0] == null ? null : msSortAttributes[0].getId());
			Tstore.TRIAL_SCORE_STATE_SORT_DIR1.setIntValue(g_db(), trialId,
					msSortAttributes[0] == null ? null : msSortDirections[0].value());
			Tstore.TRIAL_SCORE_STATE_SORT_ATT2.setLongValue(g_db(), trialId,
					msSortAttributes[1] == null ? null : msSortAttributes[1].getId());
			Tstore.TRIAL_SCORE_STATE_SORT_DIR2.setIntValue(g_db(), trialId,
					msSortAttributes[1] == null ? null : msSortDirections[1].value());
		}
		
		// Filtering state:
		Tstore.TRIAL_SCORE_FILTER_ATTRIBUTE.setLongValue(g_db(), trialId, 
				isFiltering() ? msFilterAttribute.getId() : null);
		Tstore.TRIAL_SCORE_FILTER_VALUE.setStringValue(g_db(), trialId,
				isFiltering() ? getFilterAttValue() : null);
	}
	
	/*
	 * restore()
	 * Reset from db.
	 */
	public boolean restore(Trial trl) {
		// Sorting state:
		Integer Val = Tstore.TRIAL_SCORE_STATE_SORT_MODE.getIntValue(g_db(), trl.getId());
		SortType sc = Val == null ? null : SortType.fromValue(Val);
		setSortCode(sc);
		Integer revI = Tstore.TRIAL_SCORE_STATE_SORT_REVERSE.getIntValue(g_db(), trl.getId());
		this.setReverse(revI == null ? false : revI == 1);
		
		if (sc == SortType.SORT_CUSTOM) {
			Long aid = Tstore.TRIAL_SCORE_STATE_SORT_ATT1.getLongValue(g_db(), trl.getId());
			msSortAttributes[0] = aid == null ? null : trl.getAttributeById(aid);
			Integer dir = Tstore.TRIAL_SCORE_STATE_SORT_DIR1.getIntValue(g_db(), trl.getId());
			msSortDirections[0] = dir == null ? null : SortDirection.fromValue(dir);
			if (aid == null)
				setSortCode(null);
			
			aid = Tstore.TRIAL_SCORE_STATE_SORT_ATT2.getLongValue(g_db(), trl.getId());
			msSortAttributes[1] = aid == null ? null : trl.getAttributeById(aid);
			dir = Tstore.TRIAL_SCORE_STATE_SORT_DIR2.getIntValue(g_db(), trl.getId());
			msSortDirections[1] = dir == null ? null : SortDirection.fromValue(dir);
		}
		
		// Filtering state:
		Long fattId = Tstore.TRIAL_SCORE_FILTER_ATTRIBUTE.getLongValue(g_db(), trl.getId());
		if (fattId == null)
			clearFilter();
		else {
			NodeAttribute fatt = trl.getAttribute(fattId);
			setFilterAttribute(fatt);
			String fval = Tstore.TRIAL_SCORE_FILTER_VALUE.getStringValue(g_db(), trl.getId());
			if (fval == null)
				clearFilter();
			else
				setFilter(fatt, fval);
			mFiltering = true;
		}
		return true;
	}

	public void setReverse(boolean reverse) {
		mReverse = reverse;	
	}
	public boolean getReverse() { return mReverse; }
}