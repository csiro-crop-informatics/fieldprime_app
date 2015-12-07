/*
 * Class TraitString
 * 
 * NB Does not use mScoreText
 * 
 */

package csiro.fieldprime;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static csiro.fieldprime.DbNames.*;
import static csiro.fieldprime.Trait.Datatype.T_STRING;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.RepSet;
import csiro.fieldprime.Trial.TraitInstance;

public class TraitString extends Trait {
	private String mValidationPatternString;  // Regex pattern string for validation
	private Pattern mValidationPattern;       // Compiled pattern string
	
	private String mStickyLastValue = "";
	private int mStickyLastNodeIndex = -1;
	
		
	@Override
	public Datatype getType() {
		return T_STRING;
	}
	
	@Override
	Datum CreateDatum(TraitInstance ti, Node tu) {
		return new DatumString(ti, tu);
	}
	@Override
	boolean WriteDatumJSON(JSONObject jdatum, Cursor csr)  throws JSONException {
		jdatum.put(DM_VALUE, csr.getString(csr.getColumnIndex(DM_VALUE)));
		return true;
	}


	/*
	 * InitTraitJSON()
	 * Read the validation data, if present, from the json.
	 */
	@Override
	Result initTraitJSON(JSONObject jtrt, Trial trl, boolean update) throws JSONException {
		mValidationPatternString = null;
		
		// Get the validation details from the json:
		if (jtrt.has(JTU_VALIDATION)) {
			JSONObject jvalid = jtrt.getJSONObject(JTU_VALIDATION);
			if (jvalid.has(JTV_PATTERN)) 
				setValidationPattern(jvalid.getString(JTV_PATTERN));
		}
		
		// Clear or update the db record:
		if (mValidationPatternString == null || mValidationPatternString == "") {
			g_db().delete(TABLE_TRAITSTRING, String.format("%s = %d", TTS_TRAITID, getId()), null);
		} else {
			// Add to DB:
			ContentValues values = new ContentValues();
			values.put(TTN_TRAITID, getId());
			values.put(TTS_PATTERN, mValidationPatternString);
			try {
				if (g_db().insertWithOnConflict(TABLE_TRAITSTRING, null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0)
					return new Result(false, "Database error");
			} catch (Exception e) {
				return new Result(false, "Database error");
				
			}
		}
		return new Result();
	}

	
	private void setValidationPattern(String pattern) {
		if (pattern == null) {
			mValidationPatternString = null;
			mValidationPattern = null;
		}
			
		mValidationPatternString = pattern;
		mValidationPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
	}
	private boolean matchesPattern(String matchee) {
		if (mValidationPattern == null)
			return true;  // if not pattern set, then everything matches.
		return mValidationPattern.matcher(matchee).matches();
	}

	@Override
	public String repSetStats(RepSet repSet) {
		return "";
	}

	/*
	 * SetFromDB()
	 * Trait type specific setup.
	 * Need to retrieve from table traitString.
	 */
	@Override
	void SetFromDB(Trial trl) {
		// Read validation from traitString:
		String qry = String.format("select %s FROM %s WHERE %s = %d",
				TTS_PATTERN, TABLE_TRAITSTRING, TTS_TRAITID, getId());
		Cursor ccr = null;
		try {
			ccr = g_db().rawQuery(qry, null);
			if (ccr.moveToFirst()) {
				if (ccr.isNull(ccr.getColumnIndex(TTS_PATTERN)))
					setValidationPattern(null);
				else
					setValidationPattern(ccr.getString(ccr.getColumnIndex(TTS_PATTERN)));
			}
		} finally { if (ccr != null) ccr.close(); }
	}

	@Override
	boolean SupportsBluetoothScoring() {
		return true;
	}
	
    class DatumString extends Datum {
    	ScoreText mMyScoreText;
		private String mValue;
		private String mCachedValue;
		private boolean mDirty = false;

		// INSTANCE METHODS: =========================================================================================

		public DatumString(Trial.TraitInstance ti, Node tu) {
			super(ti, tu);
		}

		private void setValue(String s) {
			mValue = s;
			SetNA(false);
			if (GetTrait().isSticky()) {
				mStickyLastValue = s;
				mStickyLastNodeIndex = getTrial().getNodeIndex();
			}
		}
		
		/*
		 * setStickyValue()
		 * Set sticky value in database if the following conditions are met:
		 * . The trait is sticky.
		 * . There is no present value in the database.
		 * . There is a non empty sticky value to set.
		 * . This node is the next node in the current order. This basically
		 *   means you don't get a sticky value if you make an unexpected
		 *   navigational jump. But wait, it looks like you'll get it when
		 *   you go to the next node after the one you jumped to, unless
		 *   you entered a value for the jump destination node, and that's
		 *   now the sticky val, which would probably be ok.
		 */
		@Override
		public void setStickyValue() {
			if (GetTrait().isSticky()
				&& !hasDBValue()
				&& mStickyLastValue.length() > 0
				&& this.getTrial().getNodeIndex() == mStickyLastNodeIndex + 1)
			{
				setValue(mStickyLastValue);
				DBWrite();
			}
		}
		
		private void CacheValue(String s) {
			mCachedValue = s;
		}
		@Override
		protected void confirmValue() {
			setValue(mCachedValue);
		}

		@Override
		public boolean processTextValue(String sval) {
			cleanup();
			return saveValue(sval);
		}
		
		/*
		 * validValue()
		 * Returns whether trait specific validation is passed.
		 * If no validation for this trait then it passes.
		 */
		private boolean validValue(String txt) {
			if (matchesPattern(txt)) {
				return true;
			} else {
				Util.msg("The value must match this:\n" + mValidationPatternString);
				return false;
			}
		}

		/*
		 * saveValue()
		 * Validates and if passes calls CheckAndProcessScore().
		 * Return true iff local validation passes (regardless of CheckAndProcessScore results).
		 */
		private boolean saveValue(String txt) {
			if (!validValue(txt)) return false;
			CacheValue(txt);
			checkAndProcessScore();
			return true;
		}

		// ABSTRACT METHOD IMPLEMENTATIONS: ===============================================================================================

		@Override
		protected void SetValueFromDB(String valKey, Cursor crs) {
			mValue = crs.getString(crs.getColumnIndex(valKey));
		}
		
		@Override
		protected String GetValidValueAsString() {
			return mValue;
		}
		
		@Override
		protected void WriteValue(String valKey, ContentValues values) {
			values.put(valKey, mValue);
		}
		
		@Override
		protected View scoreView() {
			mMyScoreText = new ScoreText(null, InputType.TYPE_CLASS_TEXT, true, this, mCtx);
			TextView.OnEditorActionListener textEditListener = new TextView.OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
					if (actionId == EditorInfo.IME_ACTION_DONE) {
						String txt = v.getText().toString();
						mDirty = true;
						saveValue(txt);
					}
					return false;
				}
			};
			mMyScoreText.setOnEditorActionListener(textEditListener);
			return mMyScoreText;
		}
		
		/*
		 * navigatingAway()
		 * Save entered score if user navigates to a different node.
		 * MFK maybe temp hack for sticky
		 */
		@Override
		public boolean navigatingAway() {
			super.navigatingAway();
			if (mDirty) {
				String txt = mMyScoreText.getText().toString();
				saveValue(txt);
			}
			return true;
		}

		@Override
		protected void drawSavedScore() {
			mMyScoreText.drawSavedScore();
		}
		
		/*
		 * cleanup()
		 * Hides the soft keyboard if currently in use for scoring.
		 */
		@Override
		public void cleanup() {
			if (mMyScoreText != null) {
				mMyScoreText.cleanup();
			}
		}
	}
}
