/* DialogSetNodeOrder
 * Michael Kirk 2015
 * 
 * Dialog to allow user to set the current node order according to node attributes.
 */

package csiro.fieldprime;

import java.util.ArrayList;
import java.util.Arrays;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import csiro.fieldprime.Trial.NodeAttribute;
import csiro.fieldprime.Trial.SortDirection;
import csiro.fieldprime.Trial.SortType;

// MFK HOPEFULLY ROBUST THROUGH RESTARTS
public class DlgSetNodeOrder extends DialogFragment {	
	private static VerticalList mCustom;
	private static Spinner [] mSpinners = new Spinner[2];
	private static Spinner [] mDirSpins = new Spinner[2];
	private static ViewLine [] mOrderAtts = new ViewLine[2];
	private static RadioGroup mWalkOrders;
	private static ArrayList<NodeAttribute> mAttList;
	private static CheckBox mAtt2Check;
	private static CheckBox mReverse;

	// Public constructor with no params is necessary for restarts. We don't need it,
	// as if missing it is created by default, but here just to note we need one.
	public DlgSetNodeOrder() {}
	
	public static void newInstance() {
		DlgSetNodeOrder instance = new DlgSetNodeOrder();
		instance.show(Globals.FragMan(), "dsno");
	}

	/*
	 * setupScreen()
	 * Populate the dialog. Called from onCreateDialog().
	 */
	private View setupScreen(final Globals.Activity ctx) {
		// Get the trial. Currently from global, hoping it's set.
		// An alternative might be to have the trial id stored in the bundle and we pass that
		// to global getTrial(), which will just return currTrial if it is the same id, otherwise
		// a new instance, but that's no good - we need the onCreate to have run..
		final Trial trl = ((Globals)getActivity().getApplication()).currTrial();
		Pstate pstate = trl.getPstate();
		VerticalList topVL = new VerticalList(ctx, Globals.SIZE_SMALL);
//		topVL.addButton("RECREATE", 73, new OnClickListener(){
//			@Override
//			public void onClick(View v) {
//				getActivity().recreate();
//				
//			}});
		
		String a = Trial.SortType.SORT_COLUMN_SERPENTINE.text(trl);
		Util.toast(a);
		mWalkOrders = topVL.addVerticalRadioGroup(
				new String [] {Trial.SortType.SORT_COLUMN_SERPENTINE.text(trl),
						Trial.SortType.SORT_ROW_SERPENTINE.text(trl),
						Trial.SortType.SORT_COLUMN.text(trl),
						Trial.SortType.SORT_ROW.text(trl),
						Trial.SortType.SORT_CUSTOM.text(trl)},
				new int [] {Trial.SortType.SORT_COLUMN_SERPENTINE.ordinal(),
						Trial.SortType.SORT_ROW_SERPENTINE.ordinal(),
						Trial.SortType.SORT_COLUMN.ordinal(),
						Trial.SortType.SORT_ROW.ordinal(),
						Trial.SortType.SORT_CUSTOM.ordinal()},
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						RadioButton rb = (RadioButton)v;
						RadioGroup rg = (RadioGroup)rb.getParent();
						boolean custom = ((RadioButton)rg.getChildAt(4)).isChecked();
						mCustom.setVisibility(custom ? View.VISIBLE : View.GONE);
						mReverse.setVisibility(custom ? View.GONE : View.VISIBLE);
						if (!custom) {
							String lsd = "";
							switch (getSorttype()) {
							case SORT_COLUMN:
							case SORT_COLUMN_SERPENTINE:
								lsd = trl.getIndexName(1);
								break;
							case SORT_ROW:
							case SORT_ROW_SERPENTINE:
								lsd = trl.getIndexName(0);
								break;
							case SORT_CUSTOM:
							default:
								break;
							}
							mReverse.setText("Reverse direction within " + lsd);
						}
						//mSpinners[1].setEnabled(custom);  // what the hey?  
					}
				});
		Trial.SortType sortCode = pstate.getSortCode();
		if (sortCode != null) {
			mWalkOrders.check(sortCode.ordinal());
		}
		
		// Reverse field:
		mReverse = topVL.addCheckBox("Reverse direction");
		mReverse.setChecked(pstate.getReverse());

		/*
		 * Custom options:
		 */
		mCustom = new VerticalList(ctx, null, false); // 2 H layouts with attribute selector and radio group.
		mAttList = trl.getAttributes();
		for (int i=0; i<2; ++i) {
			topVL.addLine();
			if (i == 0) {
				ViewLine firstAttPrompts = new ViewLine(ctx, false, null, Globals.SIZE_SMALL, false);
				firstAttPrompts.addTextView("First sort attribute:", 0.7f);
				firstAttPrompts.addTextView("Direction:", 0.3f);
				mCustom.addView(firstAttPrompts);
			} else {
				ViewLine secondAtt = new ViewLine(ctx, false, null, Globals.SIZE_SMALL, false);
				mAtt2Check = secondAtt.addCheckBox("Second sort attribute:");
				mAtt2Check.setOnCheckedChangeListener(new OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						mOrderAtts[1].setVisibility(isChecked ? View.VISIBLE : View.GONE);
					}});
				mCustom.addView(secondAtt);
			}
			
			// Create hlayout for attribute and order spinners:
			ViewLine orderAtt = mOrderAtts[i] = new ViewLine(ctx, Globals.SIZE_MEDIUM);
			ArrayList<Object> oblist = new ArrayList<Object>(mAttList); // make object version so we can insert string prompt
			mSpinners[i] = orderAtt.addSpinnerWithWeight(oblist, null, pstate.getSortAttribute(i), 0.6f);
			// Create and initialize the direction spinners:
			if (i == 0) {
				ArrayList<SortDirection> half = new ArrayList<SortDirection>();
				half.add(SortDirection.ASC);
				half.add(SortDirection.DESC);
				mDirSpins[i] = orderAtt.addSpinnerWithWeight(half, null, pstate.getSortDirection(i), 0.4f);
			}
			else {
				mDirSpins[i] = orderAtt.addSpinnerWithWeight(new ArrayList<SortDirection>(Arrays.asList(SortDirection.values())),
						null, pstate.getSortDirection(i), 0.4f);
			}
			
			mCustom.addView(orderAtt);
			mCustom.addLine();
		}
		mCustom.setVisibility(sortCode == SortType.SORT_CUSTOM ? View.VISIBLE : View.GONE);
		mReverse.setVisibility(sortCode != SortType.SORT_CUSTOM ? View.VISIBLE : View.GONE);
		
		// Set stuff according to whether second sort is set:
		boolean secondActive = pstate.secondSortAttributeSet();
		mAtt2Check.setChecked(secondActive);
		mOrderAtts[1].setVisibility(secondActive ? View.VISIBLE : View.GONE);
		
		topVL.addView(mCustom);
		return topVL;
	}
	
	NodeAttribute mAtt1 = null;
	NodeAttribute mAtt2 = null;
	int mDir1=0;
	int mDir2=-1; 
	void getCustomSortSelections(int option) {
		if (option == Trial.SortType.SORT_CUSTOM.ordinal()) {
			boolean both = mAtt2Check.isChecked();
			mAtt1 = mAttList.get(mSpinners[0].getSelectedItemPosition());
			mDir1 = SortDirection.values()[mDirSpins[0].getSelectedItemPosition()].ordinal();
			if (mAtt2Check.isChecked()) {
				mAtt2 = mAttList.get(mSpinners[1].getSelectedItemPosition());
				if (mAtt1 == mAtt2) {
					Util.msg("The two attributes must be different");
					return;
				}
				mDir2 = SortDirection.values()[mDirSpins[1].getSelectedItemPosition()].ordinal();		
			}
		}
	}

	private SortType getSorttype() {
		return SortType.fromValue(mWalkOrders.getCheckedRadioButtonId());
	}

	@Override
	public void onSaveInstanceState (Bundle outState) {
		// Note no call to super, and (i think) this stops automatic state saving
		// buggering things up because some of the view ids are repeated.
		
		// Save state:
		int option = mWalkOrders.getCheckedRadioButtonId();
		outState.putInt("frodo", option+1);
		if (option == Trial.SortType.SORT_CUSTOM.ordinal()) {
			getCustomSortSelections(option);
			if (mAtt1 != null) {
				
			}
		}
	}
	
	@Override
	public void onActivityCreated(Bundle inState) {
		super.onActivityCreated(inState);
		if (inState != null) {
			int option = inState.getInt("frodo");
			if (option > 0)
				mWalkOrders.check(option-1);
		}
	}
//	@Override
//	public void onDestroyView() {
//	    if (getDialog() != null && getRetainInstance())
//	        getDialog().setDismissMessage(null);
//	    super.onDestroyView();
//	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		//setRetainInstance(true);
		Globals.Activity act = (Globals.Activity)getActivity();
		final AlertDialog dlg = new AlertDialog.Builder(act)
				.setTitle("Set Node Order")
				.setView(setupScreen(act))
				.setPositiveButton("Apply", null)
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {}
				}).create();
		
		/*
		 * Override the onShowListener.
		 * This seems seems to be the only way to prevent the dialog
		 * closing when, for example, a validation check fails, and you want the user to fix it. 
		 */
		dlg.setOnShowListener(new DialogInterface.OnShowListener() {
		    @Override
		    public void onShow(DialogInterface dialog) {
		        Button b = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
		        b.setOnClickListener(new View.OnClickListener() {
		            @Override
		            public void onClick(View view) {
		            	int option = mWalkOrders.getCheckedRadioButtonId();
		            	NodeAttribute att1 = null;
		            	NodeAttribute att2 = null;
		            	int dir1=0;
		            	int dir2=-1;
		            	boolean reverse = mReverse.isChecked();
						if (option == SortType.SORT_CUSTOM.ordinal()) {
							boolean both = mAtt2Check.isChecked();
							att1 = mAttList.get(mSpinners[0].getSelectedItemPosition());
							dir1 = SortDirection.values()[mDirSpins[0].getSelectedItemPosition()].ordinal();
							if (mAtt2Check.isChecked()) {
								att2 = mAttList.get(mSpinners[1].getSelectedItemPosition());
								if (att1 == att2) {
									Util.msg("The two attributes must be different");
									return;
								}
								dir2 = SortDirection.values()[mDirSpins[1].getSelectedItemPosition()].ordinal();		
							}
						}
						((ActTrial)getActivity()).setNodeOrder(option, att1, dir1, att2, dir2, reverse);
		                dlg.dismiss();
		            }
		        });
		    }
		});
		return dlg;
	}
}
