/*
 * ViewLine.java
 * Michael Kirk 2015
 */

// old comment from original ScoreHLayout class
	/*
	 * Class ScoreHLayout
	 * Horizontal LinearLayout standardized for the scoring screen. 
	 * The parameters in here should match the styles in the xml, while they are still in use.
	 * Could be merged with VerticalList..
	 */

package csiro.fieldprime;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.LinearLayout.LayoutParams;

/*
 * ViewLine - common functionality for horizontal and vertical linearlayouts.
 * NB Weight sum set by default to 1.
 *
 */
public class ViewLine extends LinearLayout {
	protected Globals.Activity mCtx;
	protected int mSize = Globals.SIZE_MEDIUM;   // NB not yet set for verticals
	protected View.OnClickListener mHandler;
	protected LinearLayout.LayoutParams mButtonParams;
	protected int mTextSize;
	protected int mSmallTextSize;
	protected LinearLayout.LayoutParams mTextParams;
	protected boolean mBlackBackground = true;

	/*
	 * Constructors:
	 */
	public ViewLine(Globals.Activity context, boolean vertical, View.OnClickListener handler, int size, boolean blackBackground) {
		super(context);
		mCtx = context;
		mHandler = handler;
		mTextSize = (int) (Globals.cScreenHeightDp / 20);
		mSmallTextSize = mTextSize / 2;
		mBlackBackground = blackBackground;
		mSize = size;
		if (vertical) {
			setWeightSum(1);
			setOrientation(LinearLayout.VERTICAL);
			setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
			
			// Layout params for buttons and text fields. Note we could have weight parameters here,
			// but then button height would depend on the number of element in the layout.
			mButtonParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Util.dp2px((int) (Globals.cScreenHeightDp / 10))); // note could have weight parameter here, but then button height will depend on the number of element in the layout
			mButtonParams.setMargins(Util.dp2px(5), Util.dp2px(5), Util.dp2px(5), Util.dp2px(5));
			mTextParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); // note could have weight parameter here, but then button height will depend on the number of element in the layout
			mTextParams.setMargins(Util.dp2px(5), Util.dp2px(5), Util.dp2px(5), Util.dp2px(5));

		} else {
			setOrientation(LinearLayout.HORIZONTAL);
			if (mSize != Globals.SIZE_DEFAULT)
				setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
						Util.dp2px(globals().ScreenItemSize(mSize))));
			// Set up button params - note weight default to 1:
			mButtonParams =
					new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, 1);
			int margin = (mSize == Globals.SIZE_BIG) ? 3 : 1;
			mButtonParams.setMargins(Util.dp2px(margin), Util.dp2px(margin), Util.dp2px(margin),
					Util.dp2px(margin));
		}
	}
	
	// Version without size param. Default value SIZE_MEDIUM.
	public ViewLine(Globals.Activity context, boolean vertical, View.OnClickListener handler, boolean blackBackground) {
		this(context, vertical, handler, Globals.SIZE_MEDIUM, blackBackground);
	}
	
	/*
	 *  Version with just context.
	 *  Defaults are:
	 *    orientation - horizontal
	 *    size - SIZE_MEDIUM.
	 *    handler - null
	 *    background - white
	 */
	public ViewLine(Globals.Activity context) {
		this(context, false, null, Globals.SIZE_MEDIUM, false);
	}
	/*
	 *  Version with just background.
	 *  Defaults are:
	 *    orientation - horizontal
	 *    size - SIZE_MEDIUM.
	 */
	public ViewLine(Globals.Activity context, boolean blackBackground) {
		this(context, false, null, Globals.SIZE_MEDIUM, blackBackground);
	}
	public ViewLine(Globals.Activity context, int size) {
		this(context, false, null, size, false);
	}
	
	//****************************************************************************
	
	/*
	 * globals()
	 * Return the globals object associated with context provided in constructor.
	 */
	Globals globals() {
		return (Globals)mCtx.getApplication(); // or could be: mCtx.g;
	}
	
	private TextView addText(String txt, int height, boolean centred, int textSize) {
		TextView prompt1 = new TextView(mCtx);
		if (mBlackBackground)
			Util.setColoursWhiteOnBlack(prompt1);
		prompt1.setTextSize(textSize);
		prompt1.setGravity(Gravity.CENTER_VERTICAL | (centred ? Gravity.CENTER_HORIZONTAL : 0));
		prompt1.setText(txt);
		if (height > 0)
			prompt1.setHeight(Util.dp2px(height));
		addView(prompt1, mTextParams);
		return prompt1;
	}
	public TextView addTextCentre(String txt) {
		return addText(txt, 0, true, mTextSize);
	}
	public TextView addText(String txt) {
		return addText(txt, 0, false, mTextSize);
	}
	public TextView addTextNormal(String txt) {
		return addText(txt, 0, false, mSmallTextSize);
	}

	/*
	 * addSpinner()
	 * Add spinner 
	 */
	Spinner addSpinner(ArrayList<?> items, String nullPrompt, Object initialSelection) {
		ArrayList<Object> oblist = new ArrayList<Object>(items); // make object version so we can insert string prompt
		if (nullPrompt != null)
			oblist.add(0, nullPrompt);
		Spinner spin = new Spinner(mCtx);
		ArrayAdapter<Object> adapter = new ArrayAdapter<Object>(mCtx, android.R.layout.simple_spinner_item, oblist) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View view = super.getView(position, convertView, parent);
				TextView tv = (TextView) view;
				tv.setTextSize(30);
				if (mBlackBackground)
					Util.setColoursWhiteOnBlack(tv);
				return view;
			}
			@Override
			public View getDropDownView(int position, View convertView, ViewGroup parent) {
				View view = super.getDropDownView(position, convertView, parent);
				view.setMinimumHeight(60);
				TextView tv = (TextView) view;
				tv.setTextSize(30);
				return view;
			}
		};
		spin.setAdapter(adapter);
		if (initialSelection != null) {
			spin.setSelection(adapter.getPosition(initialSelection)); // set current value
		}
		addView(spin);
		return spin;
	}
	Spinner addSpinnerWithWeight(ArrayList<?> items, String nullPrompt, Object initialSelection, float weight) {
		ArrayList<Object> oblist = new ArrayList<Object>(items); // make object version so we can insert string prompt
		if (nullPrompt != null)
			oblist.add(0, nullPrompt);
		Spinner spin = new Spinner(mCtx);
		spin.setLayoutParams(new LinearLayout.LayoutParams(0, 100));//LayoutParams.MATCH_PARENT));
		//ArrayAdapter<Object> adapter = new ArrayAdapter<Object>(mCtx, android.R.layout.simple_spinner_item, oblist) {
		ArrayAdapter<Object> adapter = new ArrayAdapter<Object>(mCtx, android.R.layout.simple_dropdown_item_1line, oblist) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View view = super.getView(position, convertView, parent);
				TextView tv = (TextView) view;
				tv.setTextSize(30);
				return view;
			}
			@Override
			public View getDropDownView(int position, View convertView, ViewGroup parent) {
				View view = super.getDropDownView(position, convertView, parent);
				view.setMinimumHeight(60);
				TextView tv = (TextView) view;
				tv.setTextSize(30);
				return view;
			}
		};
		spin.setAdapter(adapter);
		if (initialSelection != null) {
			spin.setSelection(adapter.getPosition(initialSelection)); // set current value
		}
		addViewWithWeight(spin, weight);
		return spin;
	}
	
	public CheckBox addCheckBox(String txt) {
		CheckBox cb = new CheckBox(mCtx);
		cb.setText(txt);
		if (mSize != Globals.SIZE_DEFAULT) {
			cb.setTextSize(globals().TextSize(mSize));
		}
		addView(cb);
		return cb;
	}
	
	/*
	 * Horizontal functions - may not work for vertical:
	 */
	/*
	 * textViewPlain()
	 * Create text view with given text and no styling other than size.
	 */
	private TextView textViewPlain(String txt) {
		TextView tv = new TextView(mCtx);
		if (mSize != Globals.SIZE_DEFAULT)
			tv.setTextSize(globals().TextSize(mSize));
		if (txt != null)
			tv.setText(txt);
		return tv;
	}
	public TextView addTextViewPlain(String txt) {
		TextView tv = textViewPlain(txt);
		addView(tv);
		return tv;
	}
	public TextView addTextViewPlain(String txt, float weight) {
		TextView tv = addTextViewPlain(txt);
		tv.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight));
		return tv;
	}

	private TextView addTextView(String txt) {
		TextView tv = textViewPlain(txt);
		if (mBlackBackground)
			Util.setColoursWhiteOnBlack(tv);
		tv.setGravity(Gravity.CENTER_VERTICAL);
		addView(tv);
		return tv;
	}

	public TextView addTextView(String txt, float weight) {
		TextView tv = addTextView(txt);
		tv.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight));
		return tv;
	}

	/*
	 * addButton()
	 * Add button with given id and text. Coloring is black (text) on white (background)
	 * by default, but this can be reversed by calling with whiteOnBlack = false.
	 */
	public Button AddButton(String txt, int id, boolean whiteOnBlack) {
		Button b = new Button(mCtx);
		if (whiteOnBlack)
			Util.setColoursBlackOnWhite(b);
		else
			Util.setColoursWhiteOnBlack(b);
		b.setText(txt);
		b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, globals().TextSize(mSize));
		b.setId(id);
		b.setOnClickListener(mHandler);
		//b.setPadding(0,0,0,0);
		addView(b, mButtonParams);
		b.setPadding(1, 1, 1, 1);
		return b;
	}
	public Button AddButton(String txt, int id) {
		return AddButton(txt, id, true);
	}
	public Button addButton(String txt, View.OnClickListener handler) {
		Button b = new Button(mCtx);
		if (mBlackBackground)
			Util.setColoursBlackOnWhite(b);
		else
			Util.setColoursWhiteOnBlack(b);
		b.setText(txt);
		b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, globals().TextSize(mSize));
		b.setOnClickListener(handler);
		addView(b, mButtonParams);
		b.setPadding(1, 1, 1, 1);
		return b;
	}

	public void addViewWithWeight(View v, float weight) {
		addView(v, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight));
	}
	
	public RadioGroup addHorizontalRadioGroup(String [] texts, int [] ids, View.OnClickListener handler) {
		RadioGroup rg = new RadioGroup(mCtx);
		rg.setOrientation(RadioGroup.HORIZONTAL);
		for (int i=0; i < texts.length; ++i) {
			RadioButton rb = new RadioButton(mCtx);
			rb.setText(texts[i]);
			rb.setId(ids[i]);
			if (handler != null)
				rb.setOnClickListener(handler);
			rg.addView(rb);
		}
		addView(rg);
		return rg;
	}
	//*** End Horizontal Functions *************************************************************************************
}
