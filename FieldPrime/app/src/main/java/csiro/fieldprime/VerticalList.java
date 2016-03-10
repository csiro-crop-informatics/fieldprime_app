/*
 * VerticalListjava
 * Michael Kirk 2015
 */

package csiro.fieldprime;

import csiro.fieldprime.R;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/*
 * VerticalList
 * Provides a Vertical Linear Layout with methods to add text or buttons.
 * NB handler in constructor is only used for buttons, and not essential then,
 * probably the handler should be just passed in to addButton.
 */
public class VerticalList extends ViewLine {
	/*
	 * Constructors:
	 */
	public VerticalList(Context context, View.OnClickListener handler) {
		super((Globals.Activity)context, true, handler, true);
		// Note we are assuming context is Globals.Activity
		// NB black background by default
	}
	public VerticalList(Context context, View.OnClickListener handler, boolean blackBackground) {
		super((Globals.Activity)context, true, handler, blackBackground);  // Note we are assuming context is Globals.Activity
	}
	public VerticalList(Context context) {
		this(context, null);
	}
	public VerticalList(Context context, boolean blackBackground) {
		super((Globals.Activity)context, true, null, blackBackground);
	}
	public VerticalList(Context context, int size) {
		super((Globals.Activity)context, true, null, size, true);
	}
	//***********************************************************************************

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

	/*
	 * addButton()
	 * 2 versions. Adds button with specified text, and passed handler.
	 * Version with no hander uses mHandler.
	 */
	public void addButton(String txt, int id) {
		addButton(txt, id, mHandler);
	}
	public void addButton(String txt, int id, View.OnClickListener handler) { // MFK do we need id?
		Button b = new Button(mCtx);
		b.setBackgroundResource(R.color.white);
		b.setText(txt);
		b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mTextSize);
		b.setId(id);
		b.setOnClickListener(handler);
		addView(b, mButtonParams);
	}
	
	public RadioGroup addVerticalRadioGroup(String [] texts, int [] ids, View.OnClickListener handler) {
		RadioGroup rg = new RadioGroup(mCtx);
		rg.setOrientation(RadioGroup.VERTICAL);
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
	
	/*
	 * addHView()
	 * Add arbitrary view, which is assumed to be something to
	 * fill the screen horizontally, and be the same height as a button.
	 * NB possibly could override ViewGroup generateDefaultLayoutParams()
	 * to have the same effect (then inherited addView(View) function could
	 * be used.
	 */
	public void addHView(View v) {
		addView(v, mButtonParams);
	}
	
	public void addLine() {
		View div = new View(mCtx);
		div.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, Util.dp2px(2)));
		div.setBackgroundResource(R.color.gray);
		addView(div);
	}
	
	ListView addList(String prompt, final String [] items, OnItemClickListener handler) {
		if (prompt != null) addTextCentre(prompt);

		// Add a listview:
		ListView lv = new ListView(mCtx);
		//lv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util.dp2px((int) (cScreenHeightDp / 2))));
		// MFK perhaps could just define own xml list_item with desired parameters?
		//ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, cTrialNames) {
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(mCtx, R.layout.front_list_item, items) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				// First let's verify the convertView is not null
				if (convertView == null) {
					// This a new view we inflate the new layout
					LayoutInflater inflater = (LayoutInflater) mCtx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					convertView = inflater.inflate(R.layout.front_list_item, parent, false);
				}
				// Now we can fill the layout with the right values
				TextView tv = (TextView) convertView.findViewById(R.id.text1);
				tv.setText(items[position]);
				tv.setBackgroundResource(R.color.white);
				tv.setGravity(Gravity.CENTER);
				tv.setTextSize(20);  //MFK should be lookup, based on screen
				//Util.SetupScreenWidthTextView(tv, false);
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
				params.setMargins(0, 0, 0, 0);
				tv.setLayoutParams(params);
				tv.setPadding(0, 0, 0, 0);
				parent.setBackgroundResource(R.color.white);
				return convertView;
			}
		};
		lv.setAdapter(adapter);

		/*
		 *  Check height and resize if too big:
		 *  Here we are limiting it to 40% of screen height. Perhaps this should be a parameter,
		 *  probably as a fraction of container height?
		 */
		lv.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED),
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		int list_height = lv.getMeasuredHeight() * adapter.getCount() + (adapter.getCount() * lv.getDividerHeight());
		if (list_height > globals().cScreenHeightPx * 0.4)
			lv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
					(int) (globals().cScreenHeightPx * 0.4)));
		lv.setOnItemClickListener(handler);
		lv.setScrollbarFadingEnabled(false);
		addView(lv);
		return lv;
	}
	
	/*
	 * ListHeight()
	 * Returns height of list if all items displayed (I think).
	 */
	int ListHeight(ListView lv, ArrayAdapter<?> adapter) {
		lv.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED),
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		return lv.getMeasuredHeight() * adapter.getCount() + (adapter.getCount() * lv.getDividerHeight());
	}
	
	MyListView addSelectList(String prompt, Object [] items, double weight, final OnItemClickListener handler) {
	//ArrayAdapter<Object> AddSelectList(String prompt, ArrayList<Object> items, double weight, final OnItemClickListener handler) {
		if (prompt != null) addTextCentre(prompt);
		MyListView lv = new MyListView(mCtx);
		final MyAdapter adapter = new MyAdapter(mCtx, android.R.layout.simple_list_item_1, items);
		lv.setAdapter(adapter);

		/*
		 *  Check height and resize if too big:
		 *  Here we are limiting it to fraction weight of screen height. 
		 *  Probably should be fraction of container height rather than screen?
		 */
//		if (ListHeight(lv, adapter) > g.cScreenHeightPx * weight)
//			lv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (g.cScreenHeightPx * weight), 1));
//		else
		
		// NB, height is set at zero, so the relative proportions will be determined on weight alone
		lv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				0, (float)weight));
			
			//MFK set weight sum above to 1 and pass in fractions for Views
			
		lv.setScrollbarFadingEnabled(false);   // scrollbars always visible

		// OnClick - in addition to provided handler, we need to manage displaying the selection.
		lv.setOnItemClickListener(new OnItemClickListener() {
		    @Override
		    public void onItemClick(AdapterView<?> parent, View v, int position, long rowid) {
		    	adapter.setSelectedIndex(position);
		    	handler.onItemClick(parent, v, position, rowid);
		    }
		});
		addView(lv);
		return lv;
	}
	
	class MyListView extends ListView {
		MyAdapter mAdapter;
		public MyListView(Context context) {
			super(context);
		}
		void DeleteObject(Object o) {
			
		}
	}
	class MyAdapter extends ArrayAdapter<Object> {
	    private Context mCtx;
	    private Object [] mList;
	    int mSelectionIndex;
	    int mItemResource;

		public MyAdapter(Context ctx, int resource, Object[] objects) {
			super(ctx, resource, objects);
	        this.mCtx = ctx;
	        this.mList = objects;
	        this.mSelectionIndex = -1;
	        this.mItemResource = resource;
		}
	    public void setSelectedIndex(int ind){
	    	mSelectionIndex = ind;
	        notifyDataSetChanged();
	    }
	    final class ViewHolder {
	        TextView tv;
	    }
	    
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View vi = convertView;
			ViewHolder holder;
			// First let's verify the convertView is not null
			if (convertView == null) {
				// This a new view we inflate the new layout
	            vi = LayoutInflater.from(mCtx).inflate(mItemResource, null);
	            holder = new ViewHolder();
	            holder.tv = (TextView) vi;
	            vi.setTag(holder);
			} else
				holder = (ViewHolder) vi.getTag();
			
	 		if (position == mSelectionIndex)
	 			holder.tv.setBackgroundResource(R.color.green);
	 		else
	 			holder.tv.setBackgroundResource(R.color.white);
	 		
			// Now we can fill the layout with the right values
	 		holder.tv.setText(mList[position].toString());
//			SetViewSelected(tv, position == mSelectionIndex);
//	 		holder.tv.setGravity(Gravity.CENTER);
//	 		holder.tv.setTextSize(25);
//			parent.setBackgroundResource(R.color.white);
			
			return vi;
		}
	}
	
	/*
	 *##########################################################################################
	 *##########################################################################################
	 *##########################################################################################
	 *
	 * VLActivity
	 * Activity class which provides a VerticalList with a list at the
	 * top plus a configurable middle and bottom view.
	 */
	static abstract class VLActivity  extends Globals.Activity {
		int mCurrSelection;
		private VerticalList mTop;
		ScrollView mMidViewSV;
		
		@Override
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			getActionBar().setHomeButtonEnabled(true);
			mTop = new VerticalList(this, null);
			setContentView(mTop);
			this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN); // wo this, keyboard auto pops up when no list items
			fillScreen();
		}
		
		@Override
		public boolean onOptionsItemSelected(MenuItem menuItem) {
			switch (menuItem.getItemId()) {
		    case android.R.id.home:
		      // ProjectsActivity is my 'home' activity
		      Util.toast("ooh!");
		      return true;
			}
		  return (super.onOptionsItemSelected(menuItem));
		}
		
		/*
		 *  Abstracts - concrete class must override:
		 */
		
		/*
		 * objectArray()
		 * Return the list of items to be displayed in the list, as objects.
		 * These objects should have a toString function to generate the list text.
		 */
		abstract Object [] objectArray();
		
		/*
		 * heading()
		 * Return a string to display at the top of the screen.
		 */
		abstract String heading();
		
		/*
		 * listSelect()
		 * Handle user selection of the list item specified by (zero based) index.
		 */
		abstract void listSelect(int index);
		
		/*
		 * refreshData()
		 * The concrete implementation should do anything necessary to prepare its data,
		 * so that subsequent calls to ObjectArray() will work correctly.
		 * NB, this should not call FillScreen().
		 */
		abstract void refreshData();
		
		/*
		 * hideKeyboard()
		 * This is called at the start of the FillScreen function (i.e. when drawing or refreshing
		 * the screen). In some case, there may be a soft keyboard still on screen, and since removing
		 * the keyboard (afaik) needs knowledge of the view the keyboard was in use for, this has to
		 * be a call out to the concrete class. It can safely do nothing, if soft keyboards are not a problem.
		 */
		abstract void hideKeyboard();
		
		/*
		 * getBottomView()
		 * Return a view to place on the bottom of the screen (typically a button bar).
		 * This view will not be weighted (i.e. it will remain the same size).
		 */
		abstract View getBottomView();
				
		/*
		 * getMidView()
		 * Return a view to place below the list (typically a text view to display details
		 * about the current list selection).
		 * This view will be weighted in size, sharing the spare screen space with the list.
		 */
		abstract View getMidView();

		
		/*
		 * fillScreen()
		 * Draw the initial screen, used on creation, but also after any change,
		 * as an easy way to get everything back to rights.
		 */
		void fillScreen() {
			hideKeyboard();
			mTop.removeAllViews();	
			mCurrSelection = -1;
			
			// Get data source ready:
			refreshData();
			
			// Add the list:
			mTop.addSelectList(heading(), objectArray(), 0.5, new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
					mCurrSelection = position;
					listSelect(mCurrSelection);
				}
			});

			/*---------------------------------------------------------------------------------
			 *  Add a divider:
			 *  MFK - could make this draggable to adjust weight between list and text pane.
			 */
			View v = new View(this);
			v.setBackgroundResource(R.color.red);
			mTop.addView(v, new LayoutParams(LayoutParams.MATCH_PARENT, 5));
			
			/*---------------------------------------------------------------------------------
			 *  Add middle view.
			 *  
			 *  This is typically a text/edit view that we place inside a scrollview. Both should
			 *  be set to black background. The ScrollView given same weight as the list, and zero size,
			 *  so they both should have half the available screen (after the other views).
			 */					
			View midView = getMidView();
			if (midView != null) {
				mMidViewSV = new ScrollView(this);
				Util.removeViewFromParent(midView);
				mMidViewSV.setBackgroundResource(R.color.black); // in case the text view is smaller than scrollview
				mMidViewSV.addView(midView, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
				mTop.addView(mMidViewSV, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.5f));
			}
			
			/*---------------------------------------------------------------------------------
			 * Add bottom view (typically a button bar)
			 */
			View bottomView = getBottomView();
			if (bottomView != null)
				mTop.addView(bottomView);
		}
		
		
		/*
		 * Handy function for child class to create mid views that are styled right.
		 */
		private TextView styleTextView(TextView tv) {
			Util.setColoursWhiteOnBlack(tv);
			tv.setTextSize(Globals.cDefTextSize);
			return tv;
		}
		public EditText makeEditText() {
			// MFK: I have no memory of why this version has a FillScreen on the back button
			// press. But it does cause the back button to need to be pressed twice to exit
			// the activity when the list is empty.  So it's replaced now with a version without
			// the back button trap until someone notices why it was needed.
//			EditText et = new EditText(this) {
//				@Override
//				public boolean onKeyPreIme(int keyCode, KeyEvent event) {
//					if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
//						FillScreen();
//						return true;
//					}
//					return super.onKeyPreIme(keyCode, event);
//				}
//			};
			EditText et = new EditText(this);
			return (EditText)styleTextView(et);
		}
		public TextView makeTextView() {
			return styleTextView(new TextView(this));
		}
	}
}
