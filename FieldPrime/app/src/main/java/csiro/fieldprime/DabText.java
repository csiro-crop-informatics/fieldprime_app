package csiro.fieldprime;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.view.View;

public class DabText implements SDatatype {
    	ScoreText mMyScoreText;
		private String mValue;
		private String mCachedValue;
		private boolean mDirty = false;
		private String mStickyLastValue;
		private Context mCtx;
		private boolean mIsNA;

		// INSTANCE METHODS: =========================================================================================

		public DabText(Context ctx) {
			mCtx = ctx;
		}

		private void setValue(String s) {
			mValue = s;
			setNA(false);
		}
		
		private boolean isSticky() {
			// TODO Auto-generated method stub
			return false;
		}

		private void setNA(boolean b) {
			mIsNA = b;
		}

		
		private boolean hasDBValue() {
			// TODO Auto-generated method stub
			return false;
		}

		private void CacheValue(String s) {
			mCachedValue = s;
		}
//		@Override
//		protected void confirmValue() {
//			setValue(mCachedValue);
//		}
//
//		@Override
//		public boolean processTextValue(String sval) {
//			cleanup();
//			return saveValue(sval);
//		}
//		
//		/*
//		 * validValue()
//		 * Returns whether trait specific validation is passed.
//		 * If no validation for this trait then it passes.
//		 */
//		private boolean validValue(String txt) {
//			if (matchesPattern(txt)) {
//				return true;
//			} else {
//				Util.msg("The value must match this:\n" + mValidationPatternString);
//				return false;
//			}
//		}
//
//		/*
//		 * saveValue()
//		 * Validates and if passes calls CheckAndProcessScore().
//		 * Return true iff local validation passes (regardless of CheckAndProcessScore results).
//		 */
//		private boolean saveValue(String txt) {
//			if (!validValue(txt)) return false;
//			CacheValue(txt);
//			CheckAndProcessScore();
//			return true;
//		}

		// ABSTRACT METHOD IMPLEMENTATIONS: ===============================================================================================

		@Override
		public void setValueFromDB(String valKey, Cursor crs) {
			mValue = crs.getString(crs.getColumnIndex(valKey));
		}
		
		@Override
		public String getValidValueAsString() {
			return mValue;
		}
		
		@Override
		public void writeValue(String valKey, ContentValues values) {
			values.put(valKey, mValue);
		}
		
		@Override
		public View scoreView() {
			return mMyScoreText;
		}
	
//		@Override
//		public View scoreView() {
//			mMyScoreText = new ScoreText(null, InputType.TYPE_CLASS_TEXT, true, this, mCtx);
//			TextView.OnEditorActionListener textEditListener = new TextView.OnEditorActionListener() {
//				@Override
//				public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
//					if (actionId == EditorInfo.IME_ACTION_DONE) {
//						String txt = v.getText().toString();
//						mDirty = true;
//						saveValue(txt);
//					}
//					return false;
//				}
//			};
//			mMyScoreText.setOnEditorActionListener(textEditListener);
//			return mMyScoreText;
//		}
		
//		/*
//		 * navigatingAway()
//		 * Save entered score if user navigates to a different node.
//		 * MFK maybe temp hack for sticky
//		 */
//		@Override
//		public boolean navigatingAway() {
//			super.navigatingAway();
//			if (mDirty) {
//				String txt = mMyScoreText.getText().toString();
//				saveValue(txt);
//			}
//			return true;
//		}
//
//		@Override
//		protected void drawSavedScore() {
//			mMyScoreText.drawSavedScore();
//		}
		
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
