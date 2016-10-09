/*
 * Class ButtonBar
 * A horizontal row of button that runs full width of screen.
 * To be placed within a layout (should be either vertical linear or relative). 
 */

package csiro.fieldprime;

import csiro.fieldprime.R;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;



public class ButtonBar {  // MFK should extend LinearLayout no?  but we have to use inflater.
	Context mCtx;
	View.OnClickListener mHandler;
	LinearLayout.LayoutParams mButtonParams;
	int mNumButtons = 0;
	LinearLayout mButtonBar;

	public ButtonBar(Context context, View.OnClickListener handler) {
		mCtx = context;
		mHandler = handler;
		
		LayoutInflater inflater = (LayoutInflater) mCtx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mButtonBar = (LinearLayout) inflater.inflate(R.layout.button_bar, null);
		
		mButtonParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
		mButtonParams.setMargins(Util.dp2px(5), Util.dp2px(5), Util.dp2px(5), Util.dp2px(5));
	}
	public ButtonBar(Context context, ViewGroup vg, View.OnClickListener handler) {
		mCtx = context;
		mHandler = handler;
		
		LayoutInflater inflater = (LayoutInflater) mCtx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mButtonBar = (LinearLayout) inflater.inflate(R.layout.button_bar, vg, false);
		mButtonParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
		mButtonParams.setMargins(Util.dp2px(5), Util.dp2px(5), Util.dp2px(5), Util.dp2px(5));
	}

	/*
	 * addButton()
	 * A button is created and added with given text and id (handler can identify button
	 * pressed by the id).
	 */
	public Button addButton(String txt, int id) {
		mButtonBar.setWeightSum(++mNumButtons);
		Button b = new Button(mCtx);
		Util.setColoursWhiteOnBlack(b);
		b.setText(txt);
		b.setId(id);
		b.setOnClickListener(mHandler);
		mButtonBar.addView(b, mButtonParams);
		return b;
	}
	/*
	 * addImageButton()
	 * A button is created and added with given text and id (handler can identify button
	 * pressed by the id).
	 */
	public void addImageButton(String txt, int id) {
		mButtonBar.setWeightSum(++mNumButtons);
		ImageButton b = new ImageButton(mCtx);
		b.setImageResource(R.drawable.ic_action_help);
		b.setBackgroundResource(R.color.black);
		b.setId(id);
		b.setOnClickListener(mHandler);
		mButtonBar.addView(b, mButtonParams);
	}
	
	/*
	 * resetButtons()
	 * Clear bar and reset with specified buttons.
	 */
	public void resetButtons(String[] captions, int[] ids) {
		mButtonBar.removeAllViews();
		mNumButtons = 0;
		int count = captions.length;
		for (int i = 0; i < count; ++i)
			addButton(captions[i], ids[i]);
	}

	public void removeButton(Button butt) {
		mButtonBar.removeView(butt);
		mNumButtons = mButtonBar.getChildCount();
		mButtonBar.setWeightSum(mNumButtons);
	}
	public void removeAll() {
		mButtonBar.removeAllViews();
		mNumButtons = 0;
		mButtonBar.setWeightSum(mNumButtons);
	}
	public LinearLayout Layout() {
		return mButtonBar;
	}
}
