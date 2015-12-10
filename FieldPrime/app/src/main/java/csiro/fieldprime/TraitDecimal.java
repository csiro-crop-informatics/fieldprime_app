/* Class TraitDecimal
 * Michael Kirk 2013.
 * 
 * Contain code specific for decimal traits.
 * NB - not using mScoreText;
 * 
 * Note this could perhaps be extend a common base class
 * with traitInteger, with a couple of extra abstract functions
 * to do type specific things. If sqlite supports a decimal type
 * (with exact integer values) then database table could be common
 * too (eg trialTraitInteger).
 */
package csiro.fieldprime;

import static csiro.fieldprime.DbNames.DM_VALUE;
import static csiro.fieldprime.Trait.Datatype.T_DECIMAL;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.database.Cursor;
import android.view.View;
import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.TraitInstance;

public class TraitDecimal extends TraitNumeric {
	
	@Override
	public Datatype getType() {
		return T_DECIMAL;
	}
	@Override
	Datum CreateDatum(TraitInstance ti, Node tu) {
		return new DatumDecimal(ti, tu);
	}
	@Override
	boolean WriteDatumJSON(JSONObject jdatum, Cursor csr)  throws JSONException {
		jdatum.put(DM_VALUE, csr.getDouble(csr.getColumnIndex(DM_VALUE)));
		return true;
	}

	
	// SUB-CLASSES: ====================================================================================================
	class DatumDecimal extends Datum {
		// INSTANCE DATA: ==============================================================================================
		private double mValue;
		private double mCachedValue; // caching for score until ConfirmValue() call.
		private KeyboardLayout mKeypad;

		// INSTANCE METHODS: ===========================================================================================
		protected DatumDecimal(Trial.TraitInstance ti, Node tu) {
			super(ti, tu);
		}
		
		/*
		 * processTextValue()
		 * Process text score, updating value in db if valid, else issuing message.
		 * Returns boolean indicating whether text was valid.
		 */
		@Override
		public boolean processTextValue(String txt) {
			if (txt.matches("-?\\d*(\\.\\d+)?")) {
				// perhaps should just do this parse in a try/catch
				// That must do range check too, note range check below
				// on default range (system limits) won't work, as the
				// parseDouble will presumably fail.
				// Note the parse should enforce sys limits, so could default
				// mMin, mMax to null, then compare only when non null.
				double val;
				try {
					val = Double.parseDouble(txt);
				} catch (Exception e) {
					Util.msg("Invalid number");
					return false;
				}
				mCachedValue = val;
				if (!validateValue(val, this.mNode))
					return false;

				checkAndProcessScore();
				return true;
			} else {
				Util.msg("Invalid decimal number, please re-enter");
				return false;
			}
		}

		/*
		 * setValue()
		 */
		public void setValue(double val) {
			SetNA(false);
			mValue = val;
		}

		@Override
		public void confirmValue() {
			setValue(mCachedValue);			
		}

		/*
		 * navigatingAway()
		 * Save entered score if user navigates to a different node.
		 */
		@Override
		public boolean navigatingAway() {
			super.navigatingAway();
			if (mKeypad != null) {
				String currScore = mKeypad.getValue();
				if (currScore.length() > 0 && mKeypad.isDirty())
					return processTextValue(currScore);
			}
			return true;
		}
		
		@Override
		protected void drawSavedScore() {
//			if (isNA())  // note if NA we want to set as a prompt, not a value
//				mKeypad.setPrompt("NA");
//			else
//				mKeypad.setValue(ValueAsString(""));	
			mKeypad.setPrompt(ValueAsString(""));
		}

		// ABSTRACT METHOD IMPLEMENTATIONS: ===============================================================================================

		@Override
		protected void WriteValue(String valKey, ContentValues values) {
			values.put(valKey, mValue);
			mKeypad.setValue(""); // Assuming here that datum is about to be written,
								  // so no longer need to write if navigatingAway.
		}

		@Override
		protected String GetValidValueAsString() {
			return Double.toString(mValue);
		}

		@Override
		protected void SetValueFromDB(String valKey, Cursor crs) {
			mValue = crs.getDouble(crs.getColumnIndex(valKey));
		}

		@Override
		protected View scoreView() {
			mKeypad = new KeyboardLayout(mCtx, true, true, new KeyboardLayout.OutCalls() {		
				@Override
				public void done(String value) {
	        		int len = value.length();
	        		if (len <= 0) {
	        			Util.msg("Please enter a value");
	        		} else {
	        			processTextValue(value);
	        		}
				}

				@Override
				public void setDirty() {
					// TODO Auto-generated method stub
					
				}
			});
			
			drawSavedScore();   // Set the current keypad value to the current db value
			return mKeypad;
		}
		
		@Override
		protected void cleanup() {}
	}
}
