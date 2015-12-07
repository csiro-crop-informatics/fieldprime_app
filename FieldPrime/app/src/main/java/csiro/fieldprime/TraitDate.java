package csiro.fieldprime;

import static csiro.fieldprime.Trait.Datatype.T_DATE;

import java.util.Calendar;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.TextView;
import csiro.fieldprime.R;
import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.RepSet;
import csiro.fieldprime.Trial.TraitInstance;

public class TraitDate extends TraitInteger {
	final String ENTER_PROMPT = "Enter Date";
	
	@Override
	Datum CreateDatum(TraitInstance ti, Node tu) {
		return new DatumDate(ti, tu);
	}
	
	@Override
	public Datatype getType() {
		return T_DATE;
	}
	
	@Override
	public String repSetStats(RepSet rs) {
		return HistogramRepSetStats(rs);
	}
	

//	public static class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener {
//		static DatumDate mDat;
//		static private DatePickerFragment mInstance;
//		
//		public static void newInstance(DatumDate dat) {
//			mInstance = new DatePickerFragment();			
//			mDat = dat;
//			mInstance.show(Globals.FragMan(), "datePicker");
//		}
//
//		@Override
//		public Dialog onCreateDialog(Bundle savedInstanceState) {
//			// Initialise date to current value, or today if not set:
//			int year, month, day;
//			if (mDat.hasValue() && !mDat.isNA()) {
//				int date = mDat.getDate();
//				year = Util.JapaneseDateYear(date);
//				month = Util.JapaneseDateMonth(date) - 1;
//				day = Util.JapaneseDateDay(date);
//			} else {
//				final Calendar c = Calendar.getInstance();
//				year = c.get(Calendar.YEAR);
//				month = c.get(Calendar.MONTH);
//				day = c.get(Calendar.DAY_OF_MONTH);
//			}
//
//			// Create a new instance of DatePickerDialog and return it
//			Dialog dialog = new DatePickerDialog(getActivity(), this, year, month, day);
//			DatePicker dp = ((DatePickerDialog)dialog).getDatePicker();
//			dp.setCalendarViewShown(false);
//			dp.setSpinnersShown(true);
//			dialog.setCanceledOnTouchOutside(false);
//			return dialog;
//		}
//		
//		@Override
//		public void onDateSet(DatePicker view, int year, int month, int day) {
//			// MFK workaround no longer required it seems, not sure what changed..
//		    if (true || view.isShown()) { // Workaround: onDateSet is called twice when Done pressed, this filters one of them.
//				final int date = Util.JapaneseDate(year, month + 1, day);
//				mDat.CacheValue(date);
//				mDat.CheckAndProcessScore();
//		    }
//		}
//	}
	
	
	// SUB-CLASSES: ======================================================================================================

	/*
	 * Date is stored as an integer, so we can reuse a bit of DatumInteger.
	 */
	//class DatumDate extends DatumIntegerBase  { //DatumInteger {
	class DatumDate extends DatumIntegerBase {
		private TextView mPrompt;  // holds current saved date value, or prompt to enter one
		private int mCachedDate;   // stores current, user set, date picker value, in case we need to auto save
		private boolean firstTime = true;

		public DatumDate(TraitInstance ti, Node tu) {
			super(ti, tu);
		}

		@Override
		protected String GetValidValueAsString() {
			return Util.JapaneseDayNiceString(mValue);
		}

		@Override
		protected View scoreView() {
			LinearLayout ilayout = new LinearLayout(mCtx); // top layout (vertical) for integer scoring
			ilayout.setOrientation(LinearLayout.VERTICAL);
			
			// Create textview to display date:
			mPrompt = new TextView(mCtx);
			mPrompt.setText(ValueAsString(ENTER_PROMPT));
			FormatDateButton(mPrompt);
			ilayout.addView(mPrompt);

			LinearLayout hlayout = new LinearLayout(mCtx); // horiz layout for datepicker and set button
			hlayout.setOrientation(LinearLayout.HORIZONTAL);
			hlayout.setBackgroundResource(R.color.white);
			hlayout.setWeightSum(3);
			hlayout.setGravity(Gravity.CENTER_VERTICAL);
			
			// DatePicker:
			final DatePicker dpw = new DatePicker(mCtx);  // text doesn't show..
			dpw.setCalendarViewShown(false);
			// Initialise date to current value, or today if not set:
			int year, month, day;
			if (this.hasDBValue() && !this.isNA()) {
				int date = this.getDate();
				year = Util.JapaneseDateYear(date);
				month = Util.JapaneseDateMonth(date) - 1;
				day = Util.JapaneseDateDay(date);
			} else {
				final Calendar c = Calendar.getInstance();
				year = c.get(Calendar.YEAR);
				month = c.get(Calendar.MONTH);
				day = c.get(Calendar.DAY_OF_MONTH);
			}
			dpw.init(year, month, day, new DatePicker.OnDateChangedListener() {
				@Override
				public void onDateChanged(DatePicker view, int year, int month, int day) {
					if (!firstTime)  // This is called when control is initialised, we don't want to cache result
						             // unless the user enters a score.
						mCachedDate = Util.JapaneseDate(year, month + 1, day);
					firstTime = false;
				}
			});
			
			dpw.updateDate(year, month, day);
			LinearLayout.LayoutParams dpwLayout =
					new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
							LinearLayout.LayoutParams.WRAP_CONTENT);
			dpwLayout.weight = 2;
			hlayout.addView(dpw, dpwLayout);
			
			// Save Button
			Button setButt = new Button(mCtx);	
			setButt.setText("Save Date");
			setButt.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					// Get the date from the datepicker and set the date:
					int year = dpw.getYear();
					int month = dpw.getMonth();
					int day = dpw.getDayOfMonth();
					int date = Util.JapaneseDate(year, month + 1, day);
					saveValue(date);
				}
			});
			LinearLayout.LayoutParams buttLayout =
					new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
							LinearLayout.LayoutParams.WRAP_CONTENT);
			buttLayout.weight = 1;
			hlayout.addView(setButt, buttLayout);
			ilayout.addView(hlayout);
			
			return ilayout;
		}

		public int getDate() {
			return mValue;
		}
		
		// MFK should try remove reference to screen height here.
		protected LinearLayout.LayoutParams FormatDateButton(TextView b) {
			Util.setColoursWhiteOnBlack(b);
			LinearLayout.LayoutParams params;
			if (Globals.cScreenHeightDp < 1000) {
				params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util.dp2px(60));
				b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 40);
			} else {
				params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util.dp2px(120));
				b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 80);
			}
			params.setMargins(Util.dp2px(5), Util.dp2px(5), Util.dp2px(5), Util.dp2px(5));
			b.setLayoutParams(params);
			return params;
		}

		@Override
		protected void drawSavedScore() {
			mPrompt.setText(ValueAsString(ENTER_PROMPT));
		}

		/*
		 * navigatingAway()
		 * Save entered date if user navigates to a different node.
		 */
		@Override
		public boolean navigatingAway() {
			super.navigatingAway();
			// We should validate here, and return false if non-valid.
			if (mCachedDate > 0) {
				saveValue(mCachedDate);
			}
			return true;
		}

		@Override
		protected void cleanup() {}
	}
}
