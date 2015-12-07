/* Class DatumIntegerBase
 * Michael Kirk 2013
 * This class is here because there are multiple trait types for which the datum
 * is an integer. Since each Trait type has a nested Datum Class, one of these can
 * extend another's Datum type, so it seems we need a standalone (not nested) base
 * type for them all to extend.
 * 
 */

package csiro.fieldprime;

import android.content.ContentValues;
import android.database.Cursor;
import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.TraitInstance;

public abstract class DatumIntegerBase extends Datum {
	protected int mValue;
	private int mCachedValue;

	public DatumIntegerBase(TraitInstance ti, Node tu) {
		super(ti, tu);
	}
	
	@Override
	protected void SetValueFromDB(String valKey, Cursor crs) {
		mValue = crs.getInt(crs.getColumnIndex(valKey));		
	}
	
	@Override
	protected void WriteValue(String valKey, ContentValues values) {
		values.put(valKey, mValue);
	}

	/*
	 * setValue()
	 */
	public void setValue(int val) {
		SetNA(false);
		mValue = val;
	}

	@Override
	public void confirmValue() {
		setValue(mCachedValue);			
	}

	
	/*
	 * saveValue()
	 * Cache the value then go through the usual confirmation and type non-specific checks.
	 * If confirmation/checks are passed then the cached value will be saved via a call
	 * to confirmValue().
	 */
	protected void saveValue(int val) {
		mCachedValue = val;		
		checkAndProcessScore();
	}
}
