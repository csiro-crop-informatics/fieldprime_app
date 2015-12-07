/* Class TraitNumeric
 * Michael Kirk 2014.
 * 
 * Contain code common to integer and decimal traits.
 * Note there is still some code duplication in here to deal
 * with the two types (see switch statements, or functions with
 * versions for each type also in BexpNumericComparisonUnary),
 * so there may be some consolidation possible, but at least the
 * duplications are colocated here (rather than in separate files).
 * Perhaps we could solve the problem with the BigDecimal type.
 */

package csiro.fieldprime;

import static csiro.fieldprime.DbNames.*;

import java.math.BigDecimal;

import org.json.JSONException;
import org.json.JSONObject;

import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.RepSet;
import csiro.fieldprime.Trial.TraitInstance;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public abstract class TraitNumeric extends Trait {
	protected boolean mValidation = false;       // true if validation condition is present (apart from min/max)
	/*
	 * Note how we deal with max/min. These may or may not be specified by the server,
	 * so we preset them to the system type min or max, and then these are overridden
	 * if specified by the server.
	 */
	private Double mDMin = -Double.MAX_VALUE;
	private Double mDMax = Double.MAX_VALUE;
	private int mIMin = Integer.MIN_VALUE;
	private int mIMax = Integer.MAX_VALUE;
	
//	private BigDecimal mMin = -Double.MAX_VALUE;
//	private BigDecimal mMax = Double.MAX_VALUE;
	
	private String mCond;       // Stored comparison string, parsed into the fields below (attId and op).
	private BexpNumericComparisonUnary mValidationBexp;

	protected int getIntMin() { return mIMin; }
	protected int getIntMax() { return mIMax; }
	
	/*
	 * InitTraitJSON()
	 * Read the validation data from the json. Which, if present, can include
	 * min and max values, and a text condition that must be parsed.
	 * Currently validation json is not mandatory. If not present, then no validation
	 * other than the min/max imposed by the type.
	 * The validation data is set in this trait instance, AND stored in the database.
	 * However, the mValidationBexp is NOT set, so either it should be or we needn't
	 * bother setting min and max.
	 */
	@Override
	Result initTraitJSON(JSONObject jtrt, Trial trl, boolean update) throws JSONException {
		mValidation = false;
		
		// Get the int specific details from the json:
		if (jtrt.has(JTU_VALIDATION)) {
			JSONObject jvalid = jtrt.getJSONObject(JTU_VALIDATION);
			switch (getType()) {
			case T_INTEGER:
				if (jvalid.has(TTN_MIN)) mIMin = jvalid.getInt(TTN_MIN);
				if (jvalid.has(TTN_MAX)) mIMax = jvalid.getInt(TTN_MAX);
				break;
			case T_DECIMAL:
				if (jvalid.has(TTN_MIN)) mDMin = jvalid.getDouble(TTN_MIN);
				if (jvalid.has(TTN_MAX)) mDMax = jvalid.getDouble(TTN_MAX);
				break;
			}
			if (!jvalid.isNull(TTN_COND)) {
				mCond = jvalid.getString(TTN_COND);
			}
		}
		
		// MFK - why creating new record if no validation?
		// Perhaps because could be update, and absence might
		// mean any old values might need to be removed?
		// If no validation json then we are just creating a table
		// record with the default min and max, and no cond.
		
		// Add to DB:
		ContentValues values = new ContentValues();
		values.put(TTN_TRIALID, trl.getId());
		values.put(TTN_TRAITID, getId());
		switch (getType()) {
		case T_INTEGER:
			values.put(TTN_MIN, mIMin);
			values.put(TTN_MAX, mIMax);
			break;
		case T_DECIMAL:
			values.put(TTN_MIN, mDMin);
			values.put(TTN_MAX, mDMax);
			break;
		}
		if (mCond != null && mCond.length() > 0)
			values.put(TTN_COND, mCond);
		try {
			if (g_db().insertWithOnConflict(TABLE_TRIALTRAITNUMERIC, null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0)
				return new Result(false, "Database error");
		} catch (Exception e) {
			return new Result(false, "Database error");
			
		}
		return new Result();
	}
	
	/*
	 * SetFromDB()
	 * Trait type specific setup.
	 * Decimal trait have validation data in TABLE_TRIALTRAITNUMERIC.
	 * Note commonality with TraitInteger, which perhaps could also
	 * use numeric.
	 */
	@Override
	void SetFromDB(Trial trl) {
		// Read validation from trialTraitNumeric
		String qry = String.format("select %s,%s,%s FROM %s WHERE %s = %d AND %s = %d",
				TTN_MIN, TTN_MAX, TTN_COND, TABLE_TRIALTRAITNUMERIC,
				TTN_TRAITID, getId(), TTN_TRIALID, trl.getId());
		Cursor ccr = null;
		try {
			ccr = g_db().rawQuery(qry, null);
			if (ccr.moveToFirst()) {
				switch (getType()) {
				case T_INTEGER:
					mIMin = ccr.getInt(ccr.getColumnIndex(TTN_MIN));
					mIMax = ccr.getInt(ccr.getColumnIndex(TTN_MAX));
					break;
				case T_DECIMAL:
					mDMin = ccr.getDouble(ccr.getColumnIndex(TTN_MIN));
					mDMax = ccr.getDouble(ccr.getColumnIndex(TTN_MAX));
					break;
				}
	
				if (ccr.isNull(ccr.getColumnIndex(TTN_COND)))
					mCond = null;
				else
					mCond = ccr.getString(ccr.getColumnIndex(TTN_COND));
				
				// Initialize the validation:
				if (mCond != null && mCond.length() > 0) {
					mValidationBexp = BexpNumericComparisonUnary.makeFromString(mCond, trl);
					if (mValidationBexp != null)
						mValidation = true;
				}
			}
		} finally { if (ccr != null) ccr.close(); }
	}

	/*
	 * RepSetStats()
	 * The RepSetStats() function is basically the same for integer and decimal,
	 * so has a common implementation here.
	 */
	@Override
	public String repSetStats(RepSet rs) {
		String q = String.format("select min(%s), max(%s), avg(%s), sum(%s * %s), count(*) from %s " +
				"where traitInstance_id in %s and %s is not null",
				DM_VALUE, DM_VALUE, DM_VALUE, DM_VALUE, DM_VALUE, TABLE_DATUM, rs.SQLFormatTI_IDList(), DM_VALUE);
		g_db().rawQuery(q, null);
		
		// Retrieve the system traits from the db:
		String out = "";
		Cursor ccr = null;
		try {
			ccr = g_db().rawQuery(q, null);
			if (!ccr.moveToFirst()) return "Cannot retrieve stats";
			double mean = ccr.getDouble(2);
			out += "Min: " + ccr.getDouble(0) + "\n";
			out += "Max: " + ccr.getDouble(1) + "\n";
			out += "Mean: " + mean + "\n";
			
			double sumSquares = ccr.getDouble(3);
			int count = ccr.getInt(4);
			double var; 
			if (count < 2)
				var = 0;
			else 
				var = (count / (count-1)) * (sumSquares / count - mean * mean);
			out += String.format("STD: %.2f\n", Math.sqrt(var));
		} finally { if (ccr != null) ccr.close(); }
		return out;
	}

	/*
	 * SupportsBluetoothScoring()
	 * All numeric types should support bluetooth scoring.
	 */
	@Override
	boolean SupportsBluetoothScoring() {
		return true;
	}
	
	/*
	 * validateValue()
	 * Check the value against the validation parameters, if present.
	 * Returns boolean indicating whether acceptable or not.
	 * MFK note commonality with version with int val below.
	 */
	protected boolean validateValue(double val, Node nd) {
		// Check value is within range:
		if (val < mDMin) {
			Util.msg("The value must be at least " + mDMin);
			return false;
		}
		if (val > mDMax) {
			Util.msg("The value must be at most " + mDMax);
			return false;
		}
		
		if (!mValidation)
			return true;
		if (!mValidationBexp.getValue(nd, val)) {
			Util.msg("Failed validation condition (" + mValidationBexp.getDescription() + ")");
			return false;
		}
		return true;
	}
	
	/*
	 * validateValue()
	 * Check the value against the validation parameters, if present.
	 * Returns boolean indicating whether acceptable or not.
	 * MFK note commonality with double version.
	 */
	protected boolean validateValue(int val, Node nd) {
		// Check value is within range:
		if (val < mIMin) {
			Util.msg("The value must be at least " + mIMin);
			return false;
		}
		if (val > mIMax) {
			Util.msg("The value must be at most " + mIMax);
			return false;
		}
		
		if (!mValidation)
			return true;
		if (!mValidationBexp.getValue(nd, val)) {
			Util.msg("Failed validation condition (" + mValidationBexp.getDescription() + ")");
			return false;
		}
		return true;
	}
}
