/*
 * Class ScoreText
 * 
 * Value added EditText
 * Which may not be worth the trouble.
 * 
 */

package csiro.fieldprime;

import csiro.fieldprime.Trial.IDatum;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

public class ScoreText extends EditText {
	private String mPrompt = "- Enter Score -";
	Trial.IDatum mIdat;
	Context mCtx;
	
	/*
	 * SetScoreText()
	 * Create mScoreText and initialize it to the current value:
	 */
	public ScoreText(ViewGroup parent, int inputType, boolean editable,
			final Trial.IDatum idat, Context ctx, String prompt) {
		super(ctx);
		mIdat = idat;
		mCtx = ctx;
		//mScoreText = new EditText(mCtx);
		this.setInputType(inputType);
		Util.SetupScreenWidthTextView(this);
		if (parent != null)
			parent.addView(this, 0);
		if (editable) {
			// The score edit may have some informational text in it, eg "Not Entered", or "NA",
			// this needs to be removed so the user doesn't have to manually clear it.
			final EditText me = this;
			this.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					if (hasFocus) {
						// MFK - could we just write the value here, with "" for na or missing?
						if (!mIdat.hasDBValue() || mIdat.isNA())
							me.setText("");
					}
				}
			});
		}
		if (prompt != null)
			mPrompt = prompt;
		drawSavedScore();
		//return mScoreText;
	}
	
	// version with no prompt
	public ScoreText(ViewGroup parent, int inputType, boolean editable,
			final Trial.IDatum idat, Context ctx) {
		this(parent, inputType, editable, idat, ctx, null);
	}

	/*
	 * SetScoreText()
	 * Create mScoreText and initialize it to the current value:
	 */
	public ScoreText(ViewGroup parent, Trial.IDatum idat, Context ctx) {
		this(parent, InputType.TYPE_CLASS_PHONE, true, idat, ctx, null);
	}
	
	/*
	 * DrawSavedScore()
	 * Write string score value (or prompt) in mScoreText.
	 * Draw the current database value.
	 * NB - may be overridden.
	 */
	protected void drawSavedScore() {
		this.setText(mIdat.ValueAsString(mPrompt));
	}

	/*
	 * cleanup()
	 * Hides the soft keyboard if currently in use for scoring.
	 */
	public void cleanup() {
		Util.HideSoftKeyboard(this);
	}
}
