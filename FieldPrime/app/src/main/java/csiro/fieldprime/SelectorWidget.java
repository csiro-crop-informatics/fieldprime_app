/*
 * TraitSelector.java
 * Michael Kirk 2013
 * 
 * Class for displaying a LinearLayout that allows user to select between the set
 * of trait instances from a specified set of RepSets.
 * Callback is used to indicate selection change.
 * 
 * What I would like this to be:
 * A widget that displays a button for each of a list of arbitrary objects.
 * The buttons can be reordered by the user. Callouts are made on user actions,
 * such as button selections. This module should know as little as possible
 * about the objects. Perhaps an interface could be passed in on creation,
 * or the objects themselves could be an interface (i.e. you can pass in anything
 * objects that meet the interface).
 * The widget can manage the color of the buttons to reflect state.
 * 
 * What it currently does:
 * . Displays button if there are no objects. This has a prompt provided by the callback,
 *   and the action is provided by the callback too. 
 * . Sets color of buttons according to whether scored, or is current selection.
 *   the IS SCORED ASSESSMENT needs to be moved out.
 * . It has the concept of the first unscored.
 * . Dynamic tis.
 *   
 *   
 *          
 *
 * Todo
 * 
 */

package csiro.fieldprime;

import java.util.ArrayList;

import csiro.fieldprime.R;

import android.content.Context;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

/*
 *  Class SelectorWidget:
 *  This class intended to provide a display widget that will show (in one way or another)
 *  a selectable list of things.
 *  Any selection is reported to the creator.
 *  The selection can be changed programmatically.
 */
class SelectorWidget extends FrameLayout {
	// CONSTANTS: ========================================================================================================
	static private final int TS_NONE = 0;
	static private final int TS_SPINNER = 1;
	static private final int TS_BUTTONS = 2;
	
	// Colors for buttons to indicate scored/scoring state:
	static private final int COLOUR_SCORED = R.color.gray;
	static private final int COLOUR_SCORING = R.color.yellow;
	static private final int COLOUR_NOT_SCORED = R.color.white;

	// DATA-INSTANCE: ====================================================================================================
	private Context mCtx;
	
	private ArrayList<Selectee> mInitTIList;
		// This is the original list (in original order) passed into reset, or constructor.
	
	private ArrayList<Selectee> mItems = new ArrayList<Selectee>();
		// This list starts off the same as mInitTIList, but may be re-ordered by the user, or programmatically.
	private ArrayList<Button> mButtons = new ArrayList<Button>(); 
		// Buttons for the selectees in mItems - needs to be in sync with mItems
	private int mCurrIndex = -1;
		// Index of current scoring selection, with mItems and mButtons.
	private HorizontalScrollView mScroller;
		// scroll view for buttons
	private LinearLayout mButtonHLayout;
		// Holder for the buttons
	private LinearLayout.LayoutParams mButtParams;
		// Global only to save recreating it in 2 places, doesn't really need to be..
	
	private SelectorCallback mSelChangeHandler;
	private boolean mMuteSelectionChangeHandler = true;   // prevent unwanted initial selection change callback
	
	// Items for spinner list of TIs - which is no longer used.
	private int mDisplayType = TS_NONE;
	private Spinner mSpin;
	private ArrayAdapter<CharSequence> mAdapter; // adapter for spinner
		
	
	/*
	 * Two interfaces.
	 * One top-level one, and one for the elements.
	 */
	public interface SelectorCallback {
		// Callback for selection changed, passes new selection:
		public void selectionChanged(Selectee selection);
		
		/*
		 * When there are no selectable objects, a button will be displayed.
		 * These two functions must specify the button text and action.
		 * These could perhaps be taken out of this interface, and instead
		 * be passed in either to the constructor, or to the Reset function
		 * where they are used.
		 */
		public String buttonPromptNoElements();
		public void buttonActionNoElements();
	}
	
	public interface Selectee {
		/*
		 * hasScore()
		 * Has value in db for current node. Or at least that's what it means for 
		 * TraitInstance. More generically, we just want to know the state of this
		 * element, so as to know how to color it, and whether to navigate to it
		 * in the FirstUnscored type functions.
		 * This should perhaps return a state code.
		 */
		boolean hasScore();
		String selectText();           // what to show on the button (or whatever) Get scoring name
		void selected();
		void unselected();
		long selId();
	}
	
	// METHODS-INSTANCE: =================================================================================================
	
	/*
	 * Constructor
	 */
	public SelectorWidget(Context ctx, ArrayList<Selectee> elements, SelectorCallback sc) {
		super(ctx);
		mCtx = ctx;
		mSelChangeHandler = sc;
		Reset(elements);
	}

	private void Clear() {
		this.removeAllViews();
		this.setVisibility(View.INVISIBLE);
		mDisplayType = TS_NONE;
		mAdapter = null;
		mItems.clear();
		mCurrIndex = -1;
	}

	private void setCurrent(int index) {
		mCurrIndex = index;
	}
	private Button currButt() {
		return (mCurrIndex >=0) ? mButtons.get(mCurrIndex) : null;
	}
	
	/*
	 * get/set CurrentSelectee()
	 */
	public Selectee getCurrentSelectee() {
		// Return the currently selected Selectee, if there is one, else null
		return (mCurrIndex >=0) ? mItems.get(mCurrIndex) : null;
	}
	private void setCurrentSelectee(Selectee sel) {
		if (sel == null)
			mCurrIndex = -1;
		else
			mCurrIndex = mItems.indexOf(sel);
	}
	/*
	 * setCurrentSelectee(long selId)
	 * Set selection by ID, refreshes screen also.
	 */
	public void setCurrentSelectee(long selId) {
		for (int i=0; i<mItems.size(); ++i)
			if (mItems.get(i).selId() == selId) {
				mCurrIndex = i;
				refresh();
				break;
			}
	}
			

	
	/*
	 * Position2Colour()
	 * index indicates which selectee within mItems. Return value
	 * is the color to show for the button for this instance. Where the colors
	 * indicate currently scoring, already scored, or not yet scored.
	 * 
	 * NB, callout to hasScore() may invoke db access, which hopefully is not too slow.
	 */
	private int Position2Colour(int index) {
		if (index == mCurrIndex)
			return COLOUR_SCORING;
		else if (mItems.get(index).hasScore())
			return COLOUR_SCORED;
		else
			return COLOUR_NOT_SCORED;
	}


	public int getSelectionIndex() {
		// TODO Auto-generated method stub
		return 0;
	}


	/*
	 * replace()
	 * Replaces first occurrence of oldOne with newOne.
	 * MFK Might be good to expose index and use this, eg:
	 *   public int getSelectionIndex()
	 *   public boolean gotoIndex()
	 *   public void replace(int pos, TI newOne)
	 */
	public void replace(Selectee oldOne, Selectee newOne) {
		int index = mItems.indexOf(oldOne);
		if (index >= 0) {
			mItems.set(index, newOne);	
			// Update the associated button:
			Button b = mButtons.get(index);
			b.setText(newOne.selectText(), TextView.BufferType.SPANNABLE); //NB spannable required or drag and drop crashes on some devices
			b.setTag(newOne);
			refresh(index);
		}
	}
	
	/*
	 * remove()
	 * Remove first occurrence of sel.
	 */
	public void remove(Selectee sel) {
		int ind = mItems.indexOf(sel);
		if (ind < 0)
			return;
		mItems.remove(ind);
		mButtonHLayout.removeViewAt(ind);
		mButtons.remove(ind);
		if (mCurrIndex >= ind) {
			--mCurrIndex;
		}
	}
	
	/*
	 * add()
	 * Add sel at specified index.
	 */
	public void add(int index, Selectee sel) {
		if (index < 0 || index > mItems.size()) {
			throw new IndexOutOfBoundsException();
		}
		if (mCurrIndex >= index) {
			++mCurrIndex;
		}
		mItems.add(index, sel);
		Button b = addButtonForSelectee(index, sel);
		mButtonHLayout.addView(b, index, mButtParams);
		mButtons.add(index, b);
		//mButtonHLayout.requestLayout();
	}
	

	/*
	 * currList()
	 * Returns current list of Selectees (in current order) as array.
	 */
	public Selectee [] currList() {
		return mItems.toArray(new Selectee[mItems.size()]);
	}
	
	/*
	 * FirstUnscoredIndex()
	 * Return the trait selector index of the first trait (in current scoring set) without a score,
	 * or -1 if all are already scored,
	 * or -2 if there are no selected trait instances.
	 * MFK - note this may be slow when skipping many, need to speed up, presumably
	 * it is the db access that is slow.
	 */
	public int FirstUnscoredIndex() {
		if (mItems.size() <= 0)
			return -2;
		int index = 0;
		for (Selectee s : mItems) {
			if (!s.hasScore())
				return index;
			++index;
		}
		return -1;
	}

	/*
	 * gotoFirstUnscored()
	 * Move to the first unscored, if there is one,
	 * Returns bool indicating if there was.
	 */
	public boolean gotoFirstUnscored() {
		// Set current ti to first unscored one:
		int firstUnscored = FirstUnscoredIndex();
		boolean foundUnscored = firstUnscored >= 0;
		if (foundUnscored)
			gotoScore(firstUnscored);
		else {
			mCurrIndex = -1;
			refresh();
		}
		
		return foundUnscored;
	}

	/*
	 * gotoScore()
	 * Programmatic selection of current scoring ti, as specified by index
	 * which reflect the current order in mTIList.
	 * NB for internal use only, the selectionChanged callback is NOT called.
	 * 
	 * MFK SHOULD PROB CALL mSelChangeHandler.selectionChanged
	 * 
	 */
	private void gotoScore(int index) {
		if (index >= mItems.size())
			return;
		switch (mDisplayType) {
		default:
		case TS_NONE:
			return;
		case TS_SPINNER:
			mSpin.setSelection(index);
			break;
		case TS_BUTTONS: // MFK code dupe with OnClick
			// NB - Refresh call below will sort out the colors.
			setCurrent(index);
			mScroller.scrollTo((int)currButt().getX(), 0);
		}
		refresh();
	}
	
	/*
	 * setSelection()
	 * Programmatic selection of given element.
	 * If the element is in the current list, it is selected, and
	 * the selectionChanged callback is called.
	 * Returns boolean indicating if the element was found.
	 */
	public boolean setSelection(Selectee sel) {
		boolean found = false;
		for (int i=0; i<mItems.size(); ++i) {
			if (mItems.get(i) == sel) {
				gotoScore(i);
				found = true;
				break;
			}
		}
		if (found)
			mSelChangeHandler.selectionChanged(sel);
		return found;
	}

	/*
	 * Reset() --------------------------------------------------------------------------------------------------------
	 * Any change to the set of trait instances to display should cause a call to here. Thus we can determine
	 * what kind of selector display to use based on number of things to display. MFK note assumptions about trial
	 * state here.
	 * 
	 * Parameter checks indicates which of the current trial repsets should be available for selection.
	 * If this is null then none will be available.
	 * Assumes size and order of checks matches current trial RepSet list.
	 * MFK we could instead just pass in array(list) of repsets, and this would remove coupling with database
	 * repset order.
	 */
	// Version with no parameters, calls main version with same parameters as last call.
	// Quick hack to enable easy redisplay when order change is specified.
	public void Reset() {
		//Reset(mRepSets);
		Reset(mInitTIList);
	}
	
	
	// historical, now I think the height is determined elsewhere (I'm guessing in the vertical list it ends up in
	private Button makeTenthButton(Context ctx, String txt, OnClickListener handler) {
		LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT); // note could have weight parameter here, but then button height will depend on the number of element in the layout
				//Util.dp2px((int) (Globals.cScreenHeightDp / 10))); // note could have weight parameter here, but then button height will depend on the number of element in the layout
		buttonParams.setMargins(Util.dp2px(5), Util.dp2px(5), Util.dp2px(5), Util.dp2px(5));
		Button b = new Button(ctx);
		b.setBackgroundResource(R.color.white);
		b.setText(txt);
		b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Globals.cScreenHeightDp / 20);
		b.setOnClickListener(handler);
		b.setLayoutParams(buttonParams);
		return b;
	}
	
	// Main version:
	public void Reset(ArrayList<Selectee> initTis) {
		Clear();
		mInitTIList = initTis;
		mItems = new ArrayList<Selectee>(initTis);

		if (mItems.size() <= 0) {
			// just show button for user to Select Traits.
			this.addView(makeTenthButton(mCtx, mSelChangeHandler.buttonPromptNoElements(), new OnClickListener() {
				@Override
				public void onClick(View arg0) {
					mSelChangeHandler.buttonActionNoElements();
				}
			}));
			this.setVisibility(View.VISIBLE);
			return;
		}
				
		this.setVisibility(View.VISIBLE);
		makeTIButtons(); // show as buttons
		refresh();  // why need this?
	}

	
	/*
	 * addButtonForSelectee()
	 * 
	 */
	private Button addButtonForSelectee(int index, Selectee el) {
		Button b = new Button(mCtx);
		b.setText(el.selectText(), TextView.BufferType.SPANNABLE); //NB spannable required or drag and drop crashes on some devices
		b.setTag(el);
		b.setId(index);
		b.setBackgroundResource(COLOUR_NOT_SCORED);
		b.setWidth(Util.dp2px(150)); // MFK Find screen size!
		
		// MFK try on touch instead of on click to see if we can get touch location,
		// and perhaps use that for adding dynamic trait instances.
//		b.setOnTouchListener(new View.OnTouchListener() {
//			@Override
//			public boolean onTouch(View v, MotionEvent m) {
//				return false;  // MFK or should that be true?
//			}
//		});
		
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				/*
				 *  We need to redo the coloring of the buttons (previously and now selected)
				 *  and inform the creator of the selection change.
				 */
				Button oldButt = currButt();  // remember prev button for recoloring
				Button newButt = (Button) v;
				Selectee selection = (Selectee) newButt.getTag();
				setCurrentSelectee(selection);   // change the current
				mSelChangeHandler.selectionChanged(selection);
				
				// Redo colors:
				if (oldButt != null)
					oldButt.setBackgroundResource(Position2Colour(oldButt.getId()));
				newButt.setBackgroundResource(COLOUR_SCORING);
			}
		});
		b.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				v.startDrag(null, new View.DragShadowBuilder(v), v, 0);
				return true;
			}});
		b.setOnDragListener(mDragListener);
		return b;
	}
	
	/*
	 * makeTIButtons()
	 * Make a set of buttons according to mItems.
	 */
	private void makeTIButtons() {
		this.removeAllViews();  // need this for some reason, on button reorder..
		mScroller = new HorizontalScrollView(mCtx);
		mScroller.setScrollbarFadingEnabled(false);
		mScroller.setOnDragListener(mDragListener);
		mButtonHLayout = new LinearLayout(mCtx);		
		mDisplayType = TS_BUTTONS;
		mButtonHLayout.setWeightSum(mItems.size());  // MFK not sure this used
		mButtParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1);
		mButtParams.setMargins(Util.dp2px(5), 0, Util.dp2px(5), 0); // left, top, right, bottom
		mButtons.clear(); // clear buttons array as we are about to fill it
		for (int index = 0; index < mItems.size(); ++index) {
			/*
			 * Create a button for the ti.
			 * The ti is stored as the button tag, and the index (into mTIList and mButtons)
			 * is stored as the button id.
			 */
			Selectee el = mItems.get(index);
			Button b = addButtonForSelectee(index, el);
			mButtonHLayout.addView(b, mButtParams);
			mButtons.add(b);
			mButtonHLayout.requestLayout();
		}
		mScroller.addView(mButtonHLayout,
				new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1));
		this.addView(mScroller);
	}
	
	// Drag listener used for both buttons and scrollview, although I think
	// the scrollview never get a LOCATION events because the buttons fill it.
	private OnDragListener mDragListener = new OnDragListener() {
		@Override
		public boolean onDrag(View dragTarget, DragEvent event) {
			int action = event.getAction();
			switch (action) {
			case DragEvent.ACTION_DRAG_STARTED:
				return true;
			case DragEvent.ACTION_DROP: 
				if (dragTarget == mScroller)
					return false;
				// Find the new and old position in the order for the draggee (which is the local state):
				Button draggee = (Button) event.getLocalState();
				int oldPos = mButtons.indexOf(draggee);
				int newPos = mButtons.indexOf(dragTarget);
				
				Selectee currSelection = getCurrentSelectee(); 
					// remember current selection as its index position may change and will need to be reset.
				
				/*
				 *  rearrange trait instance list:
				 *  NB, if dragged onto a lower index (leftwards), the draggee is inserted BEFORE the target,
				 *  whereas if dragged onto a higher index (rightwards), it is inserted AFTER.
				 *  We used to use the position within the box to decide, i.e. if it was dropped
				 *  in the first half it went before, otherwise after. But this current setup seems
				 *  to work OK with the (now) horizontally scrolling button layout.
				 */
				if (newPos == oldPos) break;  // nothing to do
				int min = newPos < oldPos ? newPos : oldPos;
				int max = newPos > oldPos ? newPos : oldPos;
				ArrayList<Selectee> newList = new ArrayList<Selectee>();
				for (int i=0; i<mItems.size(); ++i) {
					if (i < min || i > max) newList.add(mItems.get(i));
					else if (i == newPos) newList.add(mItems.get(oldPos));
					else if (newPos < oldPos) newList.add(mItems.get(i-1));
					else newList.add(mItems.get(i+1));
				}
				mItems = newList;
				setCurrentSelectee(currSelection);   // reset the current selection
				makeTIButtons();
				refresh();				
				return true;
				
			case DragEvent.ACTION_DRAG_LOCATION:
				float screenX;
				if (dragTarget == mScroller) {
					float x = event.getX();
					screenX = x;
				} else {
					float bx = event.getX();
					int dtl = dragTarget.getLeft();
					int scrollPos = mScroller.getScrollX();
					screenX = dtl - scrollPos + bx;
					//return false;
				}
			
				int width = mScroller.getWidth();
				int threshold = width / 5;
				//float x = event.getX();
				int jump = 10;   // MFK could modify jump according to x (or w-x) - bigger jump for closer to edge
				if (screenX < threshold)
					mScroller.scrollBy(-jump, 0);
				if (screenX > width - threshold)
					mScroller.scrollBy(jump, 0);					
				return false;
			case DragEvent.ACTION_DRAG_ENTERED:	 
			case DragEvent.ACTION_DRAG_EXITED:
			case DragEvent.ACTION_DRAG_ENDED:
			default:
				break;
			}
			return false;
		}
	};

	/*
	 * makeTISpinner()
	 * Display a spinner for the current mTIList.
	 * MFK No longer used, since button bar now scrolls and can fit lots of buttons.
	 */
	private void makeTISpinner() {
		mDisplayType = TS_SPINNER;
		mAdapter = new ArrayAdapter<CharSequence>(mCtx, android.R.layout.simple_spinner_item) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View view = super.getView(position, convertView, parent);
				int color = Position2Colour(position);
				view.setBackgroundResource(color);
				parent.setBackgroundResource(color);
//				view.setOnLongClickListener(new OnLongClickListener() {
//					@Override
//					public boolean onLongClick(View arg0) {
//						Util.toast("get view");
//						return true;
//					}
//				});
				return view;
			}
	
			@Override
			public View getDropDownView(int position, View convertView, ViewGroup parent) {
				View view = super.getDropDownView(position, convertView, parent);
				view.setBackgroundResource(Position2Colour(position));
//				view.setOnLongClickListener(new OnLongClickListener() {
//					@Override
//					public boolean onLongClick(View arg0) {
//						Util.toast("get dropdown");
//						return true;
//					}
//				});
				return view;
			}
		};
		for (Selectee s : mItems)
			mAdapter.add(s.selectText());
	
		mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpin = new Spinner(mCtx);
		this.addView(mSpin);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT);
		mSpin.setLayoutParams(params);
		mSpin.setAdapter(mAdapter);
		if (mSpin.getChildCount() > 0) {
			View v = mSpin.getChildAt(0);
			v.setBackgroundResource(COLOUR_SCORED);
		}
	
		mSpin.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				// hackette to avoid callbacks on programmatic selection changes - NB conceivable might not work
				// occasionally, if user is real quick so that a real sel change precedes and is processed prior to
				// programmed one.
				if (!mMuteSelectionChangeHandler)
					mSelChangeHandler.selectionChanged(mItems.get(position));
				else
					mMuteSelectionChangeHandler = false;
			}
	
			@Override
			public void onNothingSelected(AdapterView<?> parentView) {
			}
		});
	}

	/*
	 * refresh()
	 * Adjust things as necessary to reflect current node (which may have changed) In particular colour
	 * the buttons to reflect scored status. And perhaps text on the button (which due to current hack/request
	 * dynamically shows the number scored).
	 */
	private void refresh(int index) {  // Refresh a single selectee (by index)
		Button b = mButtons.get(index);
		b.setText(mItems.get(index).selectText(), TextView.BufferType.SPANNABLE); //NB spannable required or drag and drop crashes on some devices
		b.setBackgroundResource(Position2Colour(index));
	}
	private void refresh() {  // Refresh all the selectees
		switch (mDisplayType) {
		case TS_SPINNER:
			mAdapter.notifyDataSetChanged();
			break;			
		case TS_BUTTONS:
			for (int i = 0; i < mItems.size(); ++i) {
				refresh(i);
			}
			break;
		}
	}

	/*
	 * getOrderedList()
	 * Return copy of current list, in displayed order.
	 */
	public ArrayList<Selectee> getOrderedList() {
		return new ArrayList<Selectee>(mItems);
	}

	/*
	 * setOrder()
	 * Order the selectees as specified.
	 * Returns false if something went wrong, in which case the order is not changed.
	 */
	public boolean setOrder(long[] selIds) {
		ArrayList<Selectee> newList = new ArrayList<Selectee>();
		for (int i=0; i<selIds.length; ++i) {
			long id = selIds[i];
			boolean found = false;
			for (Selectee s : mItems) {
				if (s.selId() == id) {
					found = true;
					newList.add(s);
					break;
				}
			}
			if (!found) return false;
		}
		mItems = newList;
		return true;
	}
}
