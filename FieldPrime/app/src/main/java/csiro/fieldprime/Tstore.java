/*
 * Michael Kirk 2015
 * 
 * tstore table stuff.
 * This is a table for generic storage.
 * tstore table record is KEY, REF, VALUE.
 * The Tstore enum here provides the keys. If you want to store something in the
 * table, you need a key. Define it here, and access the table from here too.
 * NB - keep values as ordinals if possible, we may rely on this.
 */

package csiro.fieldprime;

import static csiro.fieldprime.DbNames.TABLE_TSTORE;
import static csiro.fieldprime.DbNames.TS_KEY;
import static csiro.fieldprime.DbNames.TS_REF;
import static csiro.fieldprime.DbNames.TS_VALUE;

import java.util.Locale;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public enum Tstore {
	DUMMY_FIRST(0) {int y; void sety(){y=1;}},
	
    TRIAL_NEXT_LOCAL_ID(1),
    	// Designed to provide an id for the node table that will be unique for
    	// a given trial download (i.e. a server token).
    
    // Persistent scoring state for trials:
	TRIAL_SCORE_STATE_SORT_MODE(2),
	TRIAL_SCORE_STATE_SORT_ATT1(3),
	TRIAL_SCORE_STATE_SORT_DIR1(4),
	TRIAL_SCORE_STATE_SORT_ATT2(5),
	TRIAL_SCORE_STATE_SORT_DIR2(6),
	
	// Persistent filter state for trials:
	TRIAL_SCORE_FILTER_ATTRIBUTE(7),
	TRIAL_SCORE_FILTER_VALUE(8),
	
	TRIAL_SCORE_STATE_SORT_REVERSE(9),
	
	TRIAL_ZPRINT(10),
	TRIAL_ZPRINT_TOP_OF_FORM(11), // remove these if blob works
	TRIAL_ZPRINT_TEMPLATE(12);

    private final int value;
    private Tstore(final int newValue) {
        value = newValue;
    }
    public int getValue() {
    	return value;
	}
    public Tstore getFromValue(int value) {
    	if (value < 0 || value >= values().length)
    		return null;
    	return values()[value];
    }
	
    
    private Cursor getCursor(SQLiteDatabase sdb, long id) {
		return sdb.query(TABLE_TSTORE, new String[] { TS_VALUE },
			String.format(Locale.US, "%s=%d and %s=%d", TS_KEY, getValue(), TS_REF, id),
			null, null, null, null);
    }
    
	/*
	 * getIntValue()
	 * Returns Integer value from the tstore table for given key and id,
	 * null is return if db value is null, or missing.
	 */
	public Integer getIntValue(SQLiteDatabase sdb, long id) {
		Integer ret = null;
		Cursor ccr = null;
		try {
			try {		
				ccr = getCursor(sdb, id);
			} catch (Exception e1) {
			   //String fred = e1.getMessage();
			   return null;
			}
			
			if (ccr.moveToFirst()) {
				if (ccr.isNull(0))
					ret = null;
				else
					ret = (Integer)ccr.getInt(0);
			}
			return ret;
		} finally { if (ccr != null) ccr.close(); }
	}
	/*
	 * getLongValue()
	 * Returns Long value from the tstore table for given key and id,
	 * null is return if db value is null, or missing.
	 */
	public Long getLongValue(SQLiteDatabase sdb, long id) {
		Long ret = null;
		Cursor ccr = null;
		try {
			try {		
				ccr = getCursor(sdb, id);
			} catch (Exception e1) {
			   return null;
			}
			if (ccr.moveToFirst()) {
				if (ccr.isNull(0))
					ret = null;
				else
					ret = (Long)ccr.getLong(0);
			}
			return ret;
		} finally { if (ccr != null) ccr.close(); }
	}
	
	/*
	 * setIntValue()
	 * Sets integer value in the tstore table for given key and id.
	 * Returns boolean indicating success (true) or not (false).
	 */
	public boolean setIntValue(SQLiteDatabase sdb, long id, Integer val) {
		ContentValues values = new ContentValues();
		values.put(TS_KEY, getValue());
		values.put(TS_REF, id);
		values.put(TS_VALUE, val);
		if (sdb.insertWithOnConflict(TABLE_TSTORE, null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0)
			return false;
		return true;
	}
	
	/*
	 * getBlobValue()
	 * Returns blob from the tstore table for given key and id,
	 * null is return if db value is null, or missing.
	 */
	public byte[] getBlobValue(SQLiteDatabase sdb, long id) {
		byte[] ret = null;
		Cursor ccr = null;
		try {
			try {		
				ccr = getCursor(sdb, id);
			} catch (Exception e1) {
			   //String fred = e1.getMessage();
			   return null;
			}
			if (ccr.moveToFirst()) {
				if (ccr.isNull(0))
					ret = null;
				else
					ret = ccr.getBlob(0);
			}
			return ret;
		} finally { if (ccr != null) ccr.close(); }
	}

	/*
	 * setBlobValue()
	 * Sets blob value in the tstore table for given key and id.
	 * Returns boolean indicating success (true) or not (false).
	 */
	public boolean setBlobValue(SQLiteDatabase sdb, long id, byte[] blob) {
		ContentValues values = new ContentValues();
		values.put(TS_KEY, getValue());
		values.put(TS_REF, id);
		values.put(TS_VALUE, blob);
		if (sdb.insertWithOnConflict(TABLE_TSTORE, null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0)
			return false;
		return true;
	}
	
	/*
	 * setLongValue()
	 * Sets long value in the tstore table for given key and id.
	 * Returns boolean indicating success (true) or not (false).
	 */
	public boolean setLongValue(SQLiteDatabase sdb, long id, Long val) {
		ContentValues values = new ContentValues();
		values.put(TS_KEY, getValue());
		values.put(TS_REF, id);
		values.put(TS_VALUE, val);
		if (sdb.insertWithOnConflict(TABLE_TSTORE, null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0)
			return false;
		return true;
	}
	
	/*
	 * getAndIncrementSysValueInt()
	 * Return Integer value giving the current (at time of call) value in the tstore table for
	 * given key and id. If the value is missing or null the specified defaultValue is returned.
	 * The database value is reset to the returned value plus 1.
	 * If the reset operation fails, null is returned.
	 */
	public Integer getAndIncrementIntValue(SQLiteDatabase sdb, int id, int defaultValue) {
		Integer Val = getIntValue(sdb, id);
		if (Val == null) {
			Val = defaultValue;
		}	
		if (setIntValue(sdb, id, Val + 1))
			return Val;
		else
			return null;
	}
	
	/*
	 * setStringValue()
	 * Sets string value in the tstore table for given key and id.
	 * Returns boolean indicating success (true) or not (false).
	 */
	public boolean setStringValue(SQLiteDatabase sdb, long id, String val) {
		ContentValues values = new ContentValues();
		values.put(TS_KEY, getValue());
		values.put(TS_REF, id);
		values.put(TS_VALUE, val);
		if (sdb.insertWithOnConflict(TABLE_TSTORE, null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0)
			return false;
		return true;
	}
	
	/*
	 * getStringValue()
	 * Returns Integer value from the tstore table for given key and id,
	 * null is return if db value is null, or missing.
	 */
	public String getStringValue(SQLiteDatabase sdb, long id) {
		String ret = null;
		Cursor ccr = null;
		try {
			try {		
				ccr = getCursor(sdb, id);
			} catch (Exception e1) {
			   //String fred = e1.getMessage();
			   return null;
			}
			
			if (ccr.moveToFirst()) {
				if (ccr.isNull(0))
					ret = null;
				else
					ret = (String)ccr.getString(0);
			}
			return ret;
		} finally { if (ccr != null) ccr.close(); }
	}

}
