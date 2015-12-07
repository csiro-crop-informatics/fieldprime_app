/* Class TraitInteger
 * Michael Kirk 2013.
 * 
 * Contain code specific for integer traits.
 * Some code replication with TraitDecimal, see comments there.
 */

package csiro.fieldprime;

import static csiro.fieldprime.DbNames.DM_VALUE;
import static csiro.fieldprime.DbNames.TABLE_DATUM;
import static csiro.fieldprime.Trait.Datatype.T_INTEGER;

import org.json.JSONException;
import org.json.JSONObject;

import android.database.Cursor;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import csiro.fieldprime.R;
import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.RepSet;
import csiro.fieldprime.Trial.TraitInstance;

/*
 *  NB, several classes extend this, so if too much stuff ends up in here that is
 *  really specific to (unextended) integers, then perhaps we should make an
 *  abstract integer base class, and have all integer types extend that.
 */

public class TraitInteger extends TraitNumeric {
	private static final int METHOD_KEYPAD = 1;
	private static final int METHOD_RANGES = 2;
	
	private WidgetRange mRange;
	private int mMethod;
	
	@Override
	public Datatype getType() {
		return T_INTEGER;
	}
	@Override
	Datum CreateDatum(TraitInstance ti, Node tu) {
		return new DatumInteger(ti, tu);
	}
	@Override
	boolean WriteDatumJSON(JSONObject jdatum, Cursor csr)  throws JSONException {
		jdatum.put(DM_VALUE, csr.getInt(csr.getColumnIndex(DM_VALUE)));
		return true;
	}


	/*
	 * int2StringRepresentation()
	 * Converts int to string, this is a function so it can be overridden by classes
	 * extending this one if they have a different way to represent their int values.
	 * Note we already have the abstract function GetValidValueAsString() in datum,
	 * which has a similar purpose, but it converts the value in a datum, whereas
	 * this function can do an int value not in a datum, which is what we need for the
	 * histogram.
	 */
	String int2StringRepresentation(int i) {
		return Integer.toString(i);
	}
	
	/*
	 * HistogramRepSetStats()
	 * Generate display histogram of the values in the db for the repset.
	 * NB, this may be used by classes overriding traitInteger so the display
	 * of the value needs to be traittype specific. 
	 */
	protected String HistogramRepSetStats(RepSet rs) {		
		// Stats functions ignore nulls it seems, so not explicitly excluding them.
		String q = String.format("select %s, count(*) from %s " +
				"where traitInstance_id in %s group by %s", DM_VALUE, TABLE_DATUM, rs.SQLFormatTI_IDList(), DM_VALUE);
		
		// Retrieve the system traits from the db:
		String out = "Histogram: ----------------\n";
		Cursor ccr = null;
		try {
			ccr = g_db().rawQuery(q, null);
			if (ccr.moveToFirst())
				do {
					out += (ccr.isNull(0) ? "NA" : int2StringRepresentation(ccr.getInt(0))) + " : " + ccr.getInt(1) + "\n";
				} while (ccr.moveToNext());
		} finally { if (ccr != null) ccr.close(); }
		
		return out;
	}
	
	
	@Override
	public String repSetStats(RepSet rs) {
		String out = super.repSetStats(rs);
		out += HistogramRepSetStats(rs);  // for integer we add a histogram as well as standard numeric stats.
		return out;
	}

	// SUB-CLASSES: ======================================================================================================

	/*
	 * WidgetRange
	 * Holds min and max value for button scoring widget.
	 */
	public class WidgetRange {
		int min;
		int max;

		public WidgetRange(int min, int max) {
			this.min = min;
			this.max = max;
		}
		
		public void setWidgetRange(int min, int max) {
			this.min = min;
			this.max = max;
		}
	}
	
	
	public class DatumInteger extends DatumIntegerBase {
		// CONSTANTS: ========================================================================================================
		private final static int NUM_SCORE_BUTS = 8;
		private static final int RANGE_MAX = 120;
		
		// DATA-STATIC: ======================================================================================================
		// We keep the range bar settings between nodes for the same trait
		//static TraitRangeSet smTraitRanges = new TraitRangeSet(); // MFK this needs to be reset occasionally? changing trial?
		
		
		// DATA-INSTANCE: ====================================================================================================
		private int sbMin = 0;
		private int sbMax = 100;
		private LinearLayout mLevel2ScoreButtonsLayout;
		private int[] mButMins;
		private int[] mButMaxs;
		private KeyboardLayout mKeypad;     // used if mMethod == METHOD_KEYPAD
    	ScoreText mMyScoreText;             // used if mMethod == METHOD_RANGES


		// METHODS-INSTANCE: =================================================================================================

		public DatumInteger(Trial.TraitInstance ti, Node tu) {
			super(ti, tu);
		}
		
		/*
		 * processTextValue()
		 * Validate and set from text value.
		 * Returns boolean indicating if value was set.
		 */
		@Override
		public boolean processTextValue(String txtVal) {
			int val;
			try {
				val = Integer.parseInt(txtVal);
			} catch(Exception e) {
				return false;
			}
			return CheckAndSave(val);
		}
		
		@Override
		protected String GetValidValueAsString() {
			return Integer.toString(mValue);
		}
				
		@Override
		protected void drawSavedScore() {
			if (mMethod == METHOD_KEYPAD) {
//				if (isNA())  // note if NA we want to set as a prompt, not a value
//					mKeypad.setPrompt("NA");
//				else
//					mKeypad.setValue(ValueAsString(""));
				mKeypad.setPrompt(ValueAsString(""));
			}
			else mMyScoreText.drawSavedScore();
		}

		/*
		 * navigatingAway()
		 * Save entered score if user navigates to a different node.
		 */
		@Override
		public boolean navigatingAway() {
			super.navigatingAway();
			switch (mMethod) {
			case METHOD_KEYPAD:
				String currScore = mKeypad.getValue();  // If METHOD_KEYPAD mKeypad shouldn't be null
				//if (currScore.length() > 0 && mKeypad.isDirty())
				if (currScore.length() > 0 && isDirty())
					return processTextValue(currScore);
				break;
			case METHOD_RANGES:
				// nothing to do here as range buttons save immediately.
				break;
			}
			return true;
		}

		
		/*
		 * ScoreView()
		 * Two options here, which is used depends on configured user preference and (possibly) the range.
		 * Ranges method (aka Colin's widget): User selects from ranges till narrowed down to a value.
		 * Keyboard: User types value into custom numberic keypad (just buttons - not system keyboard).
		 */
		@Override
		protected View scoreView() {
			int min = getIntMin();
			int max = getIntMax();
			final Datum datum = this;
			long range = (long)getIntMax() - getIntMin();  // need to go to bigger type else may overflow giving wrong result below
			if (Globals.g.prefs().GetIntInputMethod() == Prefs.INT_INPUT_METHOD_RANGE_BUTTONS && (range < RANGE_MAX - 1)) {
				mMethod = METHOD_RANGES;
				final LinearLayout scoreButtonsLayout = new LinearLayout(mCtx);
				LinearLayout ilayout = new LinearLayout(mCtx); // top layout (vertical) for integer scoring
				ilayout.setOrientation(LinearLayout.VERTICAL);

				sbMin = min;
				sbMax = max;
				RangeSeekBar<Integer> seekBar = new RangeSeekBar<Integer>(min, max, mCtx);
				//Range jcurrRange = mRange; //smTraitRanges.GetTraitRange(trt);
				if (mRange != null) {
					sbMin = mRange.min;
					sbMax = mRange.max;
					seekBar.setSelectedMinValue(sbMin);
					seekBar.setSelectedMaxValue(sbMax);
				} else mRange = new WidgetRange(sbMin, sbMax);

				seekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener<Integer>() {
					@Override
					public void onRangeSeekBarValuesChanged(RangeSeekBar<?> bar, Integer minValue, Integer maxValue) {
						// handle changed range values
						sbMin = minValue;
						sbMax = maxValue;
						ReconfigureScoreButtons(scoreButtonsLayout);
						mRange.setWidgetRange(minValue, maxValue);
					}
				});
				seekBar.setBackgroundResource(R.color.white);
				seekBar.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 320));
				ilayout.addView(seekBar);
				
				// MFK Now we should work out how much screen space left
				mMyScoreText = new ScoreText(ilayout, this, mCtx);  // place score text box at top position of ilayout.
				// MFK Now we should work out how much screen space left
				
				// Now add row of buttons within a horizontal layout:
				//final LinearLayout scoreButtonsLayout = new LinearLayout(mCtx);
				scoreButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
				scoreButtonsLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120));
				ReconfigureScoreButtons(scoreButtonsLayout);
				ilayout.addView(scoreButtonsLayout);

				// Add level 2 score buttons (should be no height until needed):
				mLevel2ScoreButtonsLayout = new LinearLayout(mCtx);
				mLevel2ScoreButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
				mLevel2ScoreButtonsLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120));
				mLevel2ScoreButtonsLayout.setVisibility(View.GONE);
				ilayout.addView(mLevel2ScoreButtonsLayout);
				return ilayout;
			} else {
				mMethod = METHOD_KEYPAD;
				mKeypad = new KeyboardLayout(mCtx, false, true, new KeyboardLayout.OutCalls() {		
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
						datum.setDirty(true);
					}
				});
				drawSavedScore();
				return mKeypad;
			}
		}

		/*
		 * CheckAndSave()
		 * Check the val, first against the trait validation parameters.
		 * And then (if passed) the parent Datum's CheckAndProcessScore
		 * function is called to do any further (non trait specific) checks,
		 * and to save the value if all checks OK.
		 * NB may involve user interaction, so asynchronous.
		 * Returns false if validation fails - else true.
		 * Note that true return doesn't imply value saved, as CheckAndProcessScore()
		 * may not save score.
		 */
		private boolean CheckAndSave(final int val) {
			if (!validateValue(val, this.mNode))
				return false;
			saveValue(val);
			return true;
		}

		private void ReconfigureScoreButtons(LinearLayout scoreButtonsLayout) {
			scoreButtonsLayout.removeAllViews();

			// Check for special case where range small enough to score with single level of buttons:
			boolean singleLevel = 1 + sbMax - sbMin <= NUM_SCORE_BUTS;
			int numScoreButs = singleLevel ? 1 + sbMax - sbMin : NUM_SCORE_BUTS;

			scoreButtonsLayout.setWeightSum(numScoreButs);
			mButMins = new int[numScoreButs];
			mButMaxs = new int[numScoreButs];
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1);
			params.setMargins(Util.dp2px(1), Util.dp2px(5), Util.dp2px(1), Util.dp2px(5));
			for (int i = 0; i < numScoreButs; ++i) {
				// set the ranges:
				if (singleLevel) {
					mButMins[i] = mButMaxs[i] = sbMin + i;
				} else {
					mButMins[i] = sbMin + (i * (sbMax - sbMin)) / numScoreButs;
					mButMaxs[i] = (i + 1 == numScoreButs) ? sbMax : (sbMin + (((i + 1) * (sbMax - sbMin)) / numScoreButs) - 1);
				}

				Button b = new Button(mCtx);
				if (mButMaxs[i] == mButMins[i])
					b.setText("" + mButMins[i]);
				else
					b.setText("" + mButMins[i] + "-" + mButMaxs[i]);
				b.setBackgroundResource(R.color.white);
				b.setWidth(Util.dp2px(150)); // MFK Find screen size! Weight is used instead, use 0?
				b.setId(i);
				b.setPadding(0, 0, 0, 0);
				//b.settexts
				b.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						int id = ((Button) v).getId();
						if (mButMaxs[id] > mButMins[id])
							Level2ScoreButtons(mButMins[id], mButMaxs[id]);
						else {
							final int val = mButMaxs[id];
							CheckAndSave(val);
						}
					}
				});
				scoreButtonsLayout.addView(b, params);
			}
		}

		private void Level2ScoreButtons(int min, int max) {
			int range = max - min + 1;
			mLevel2ScoreButtonsLayout.removeAllViews();
			mLevel2ScoreButtonsLayout.setWeightSum(range);
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1);
			params.setMargins(Util.dp2px(1), Util.dp2px(5), Util.dp2px(1), Util.dp2px(5));
			for (int i = min; i <= max; ++i) {
				Button b = new Button(mCtx);
				b.setId(i);
				b.setText("" + i);
				b.setPadding(0, 0, 0, 0);
				b.setBackgroundResource(R.color.white);
				b.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						int val = ((Button) v).getId();
						mLevel2ScoreButtonsLayout.setVisibility(View.GONE);
						// Record the score even if changed, for the timestamp/location
						CheckAndSave(val);
					}
				});
				mLevel2ScoreButtonsLayout.addView(b, params);
			}
			mLevel2ScoreButtonsLayout.setVisibility(View.VISIBLE);
		}

		@Override
		protected void cleanup() {
			if (mMyScoreText != null) {
				mMyScoreText.cleanup();
			}
		}
	}
}
