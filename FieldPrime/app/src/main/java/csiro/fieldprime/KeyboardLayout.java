package csiro.fieldprime;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class KeyboardLayout extends LinearLayout {
	private Context mCtx;
	private static final int BACKSPACE = 1;
	private static final int DONE = 2;
	private static final int NOWT = 3;
	private static final int POINT = 4;
	private static final int MINUS = 5;
	private TextView mScoreTV;
	private String mScore;
	private OutCalls mOutCalls;
	private float mButtonTextSize = 40;
	private boolean mDirtyBit = false;  // any change since value set?  MFK maybe this is replaced
										// by setDirty in OutCalls?

	public interface OutCalls {
		public void done(String value);  // called when user hits Done, with the current value
		public void setDirty();          // called when user make any change
	}
	
	public boolean isDirty() {
		return mDirtyBit;
	}

	public KeyboardLayout(Context context, boolean showPoint, boolean showMinus, OutCalls dh) {
		super(context);
		mCtx = context;
		mOutCalls = dh;
		
		this.setOrientation(LinearLayout.VERTICAL);
		mButtonTextSize = Globals.g.screenIsBig() ? 40 : 20;
		mScoreTV = new TextView(mCtx);
		mScore = "";
		Util.SetupScreenWidthTextView(mScoreTV);
		this.addView(mScoreTV);
		
		TableLayout tl = new TableLayout(mCtx);

		tl.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
		tl.setStretchAllColumns(true);
		tl.setWeightSum(4);
						
        TableLayout.LayoutParams rowParams = new TableLayout.LayoutParams(
        		TableLayout.LayoutParams.MATCH_PARENT,
        		TableLayout.LayoutParams.WRAP_CONTENT);
        rowParams.weight = 1;
		TableRow.LayoutParams cellParams = new TableRow.LayoutParams(
				TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT);

		for (int row=0; row<3; ++row) {
			TableRow trh = new TableRow(mCtx);
			for (int col=0; col<3; ++col) {
				int code = ((int)'1')+row*3+col;
				trh.addView(makeKeyboardButton(Character.toString((char)code), code, handler), cellParams);
			}
			
			// Fourth button in row depends on which row:
			if (row == 0)
				if (showMinus)
					trh.addView(makeSpecialKeyboardButton("-", MINUS, 0, handler, Color.rgb(100, 100, 100)), cellParams);
				else
					trh.addView(makeEmptyCell(), cellParams);
			else if (row == 1)
				if (showPoint)
					trh.addView(makeSpecialKeyboardButton(".", POINT, 0, handler, Color.rgb(100, 100, 100)), cellParams);
				else
					trh.addView(makeEmptyCell(), cellParams);
			else if (row == 2)
				trh.addView(makeSpecialKeyboardButton("<--", 1, android.R.drawable.ic_delete, handler, Color.RED), cellParams);

			tl.addView(trh, rowParams);
		}
		
		TableRow trh = new TableRow(mCtx);
		trh.addView(makeKeyboardButton("0", ((int)'0'), handler), cellParams); 
		trh.addView(makeEmptyCell(), cellParams); 
		trh.addView(makeEmptyCell(), cellParams); 
		trh.addView(makeSpecialKeyboardButton("Done", 2, 0, handler, Color.GREEN), cellParams);			
		tl.addView(trh, rowParams);
		
		this.addView(tl, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
	}

	// Button click handler, button id is the ascii code, or a special value.
	private View.OnClickListener handler = new View.OnClickListener() {
		@Override
		public void onClick(View but) {
			int code = but.getId();
			int len = mScore.length();
			switch (code) {
			// Note any case that shouldn't set the dirty bit must return.
			case NOWT: return;
			case DONE:
				if (len <= 0) {
					Util.msg("Please enter a value");
				} else {
					mOutCalls.done(mScore);
				}
				return;
			case BACKSPACE:
				if (len <= 0) {
					// If there is a prompt there, remove it:
					if (mScoreTV.getText().length() > 0)
						mScoreTV.setText("");
					return;
				}
				mScore = mScore.substring(0, len - 1);
				mScoreTV.setText(mScore);
				break;
			case POINT:
				if (mScore.indexOf('.') >= 0) return; // only allow one
				mScore += ".";
				mScoreTV.setText(mScore);
			    break;
			case MINUS:
				if (len != 0) return; // only allow at beginning
				mScore += "-";
				mScoreTV.setText(mScore);
				break;
			default:
				mScore += (char) code;
				mScoreTV.setText(mScore);
				break;
			}
			mDirtyBit = true;
			mOutCalls.setDirty();
		}
	};

	private Button makeKeyboardButton(String label, int code, View.OnClickListener handler) {
		Button b = new Button(mCtx);
		b.setText(label); //Character.toString ((char)code));
		b.setId(code);
		b.setOnClickListener(handler);
		b.setTextColor(Color.WHITE);
		b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mButtonTextSize);
		return b;
	}

	private Button makeSpecialKeyboardButton(String label, int code, int img, View.OnClickListener handler, int color) {
		Button b = new Button(mCtx);
		b.setText(label); //Character.toString ((char)code));
		b.setId(code);
		b.setOnClickListener(handler);
		b.setTextColor(Color.WHITE);
		b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mButtonTextSize);
		if (color != 0) {
			Drawable d = b.getBackground();
			android.graphics.PorterDuffColorFilter filter = new android.graphics.PorterDuffColorFilter(color,
					android.graphics.PorterDuff.Mode.SRC_ATOP);
			d.setColorFilter(filter);
		}
		if (img != 0)
			b.setCompoundDrawablesWithIntrinsicBounds(img, 0, 0, 0);
		return b;
	}
	
	private Button makeEmptyCell() {
		Button b = makeKeyboardButton("", NOWT, handler);
		b.setVisibility(View.INVISIBLE);   // awkward way of having empty cell at beginning of row.
		return b;
	}
	
	/*
	 * setPrompt()
	 * Write something other than a score to the screen, eg a prompt to enter data,
	 * or "NA". These are not stored as scores. They are deleted by any keypad entry.
	 */
	public void setPrompt(String prompt) {
		mScoreTV.setText(prompt);
	}
	
	public void setValue(String val) {
		mScore = val;
		mScoreTV.setText(mScore);
		mDirtyBit = false;
	}
	public String getValue() {
		return mScore;
	}
}


//***  GRAVEYARD: *****************************************************************************

//if (false) {
//// Add backkey - use an ImageButton for this:
//ImageButton dbut = new ImageButton(mCtx);
////dbut.setImageResource(android.R.drawable.ic_input_delete);
//dbut.setImageResource(android.R.drawable.ic_delete);
//dbut.setId(1);
//dbut.setOnClickListener(handler);
//trh.addView(dbut, cellParams);
//} else {
//	Button b = makeKeyboardButton("Back", 1, handler);
//	b.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_delete, 0, 0, 0);
//	trh.addView(b, cellParams);           // Add backkey
////trh.addView(makeKeyboardButton("Back", 1, handler), cellParams);           // Add backkey
//}
////trh.addView(makeKeyboardButton("0", ((int)'0'), handler), cellParams);  // Add zero
////trh.addView(makeKeyboardButton("Done", 2, handler), cellParams);        // Add done



