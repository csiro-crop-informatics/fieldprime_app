/*
 * DialogChooseScoreSets.java
 * 
 * Dialog to display multiselect list of all existing score sets, for user to select
 * which to score. There is also a button to bring up separate dialog for creating
 * score sets, collecting a rep count for each one.
 * 
 * Todo
 * . 
 * 
 */

package csiro.fieldprime;

import java.util.ArrayList;
import java.util.Arrays;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import csiro.fieldprime.Trial.RepSet;

//MFK NOT ROBUST THROUGH RESTARTS
@SuppressLint("NewApi")
public class DlgChooseScoreSets extends DialogFragment {
	private static handler mCreateHandler;
	private static Trial mTrial;
	private static ArrayList<RepSet> mRepsets;
	private static boolean[] mChecks;
	private static ArrayAdapter<RepSet> mAdapter;
	private static ListView mRepSetListView;
	private static TextView mNoSetsText;
	private static Button mCreateButton;
	
	public interface handler {
		public void onListSelect(ArrayList<RepSet> scoreSets);
	}

	// Prevent external constructor call, newInstance should be used instead.
	private DlgChooseScoreSets() {}
	
	public static void newInstance(handler lhs, Trial trial) {
		if (trial.GetTraitCaptionList() == null) { // Exit if no trait
			Util.msg("No existing traits found for dataset");
			return;
		}

		mTrial = Globals.g.currTrial();  // NB OK to use trial in this func, but we shouldn't remember it, (it may get wiped).
		mCreateHandler = lhs;
		DlgChooseScoreSets instance = new DlgChooseScoreSets();
		instance.show(Globals.FragMan(), "tag");
	}

	/*
	 * setupScreen()
	 * Populate the dialog. Called from onCreateDialog().
	 */
	private View setupScreen(final Context ctx) {
		final VerticalList topVL = new VerticalList(ctx, null);

		/*
		 *  Get repset list and set mChecks to a equivalent size list of booleans, initially false.
		 *  If there are no repset yet, show text explaining that (which will have to be removed
		 *  if the user creates some).
		 */
		mRepsets = mTrial.getRepSetsArrayList();
		if (mRepsets.size() == 0) {
			mNoSetsText = topVL.addText("No score sets found");
		}
		mChecks = new boolean[mRepsets.size()];	
		
		// List of available score sets:
		mRepSetListView = new ListView(ctx);
		mRepSetListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		mRepSetListView.setOnItemClickListener(new ListView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
		    	mChecks[position] = ((CheckedTextView)v).isChecked();
			}
        });
		mRepSetListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
				/*
				 * Currently long click is only for setting trait stickiness,
				 * and this (attow) is only relevant for string traits.
				 */
				RepSet rs = mRepsets.get(position);
				Trait trt = rs.getTrait();
				if (trt.getType() != Trait.Datatype.T_STRING)
					return false;
							
				Util.Confirm("Stickiness", "Make this trait sticky?", 0, new Util.ConfirmHandler() {			
					@Override
					public void onYes(long context) {
						RepSet rs = mRepsets.get(position);
						rs.getTrait().setSticky(true);
					}
					@Override
					public void onNo(long context) {
						RepSet rs = mRepsets.get(position);
						rs.getTrait().setSticky(false);
					}
				});
				return true;
			}
		});
		mAdapter = new ArrayAdapter<RepSet>(ctx, android.R.layout.simple_list_item_multiple_choice, mRepsets);
		mRepSetListView.setAdapter(mAdapter);
		//uncheckAllChildrenCascade(mRepSetListView);
		topVL.addView(mRepSetListView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1));
		
		/*
		 * Add Button to bring up create score sets dialog:
		 */
		mCreateButton = new Button(ctx);
		mCreateButton.setText("Create New Score Sets");
		if (mRepsets.size() == 0) {
			Animation mAnimation = new AlphaAnimation(1, 0);
		    mAnimation.setDuration(200);
		    mAnimation.setInterpolator(new LinearInterpolator());
		    mAnimation.setRepeatCount(Animation.INFINITE);
		    mAnimation.setRepeatMode(Animation.REVERSE); 
		    mCreateButton.startAnimation(mAnimation);
		}
		mCreateButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//uncheckAllChildrenCascade(mRepSetListView);
				DlgCreateScoreSets.newInstance(new DlgCreateScoreSets.handler() {
					@Override
					public void onListSelect(int[] repCounts) {
						/*
						 * User has closed the CreateInstance dialog, so there may be new
						 * score sets and hence we need to refresh the list. NB, I tried
						 * simply creating a whole new adapter, but it was a world of pain..
						 */
						mAdapter.clear();
						mAdapter.addAll(mTrial.getRepSetsArray());
						mAdapter.notifyDataSetChanged();
						mChecks = new boolean[mAdapter.getCount()];
						checkAll(false); // Clear any checks:
						if (mNoSetsText != null) {
							topVL.removeView(mNoSetsText);
							mCreateButton.clearAnimation();
						}
					}
				});
			}
		});
		topVL.addLine();
		topVL.addView(mCreateButton, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		
		return topVL;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Globals.Activity ctx = (Globals.Activity)getActivity();
		final AlertDialog dlg = new AlertDialog.Builder(ctx).setCustomTitle(titleWithAllCheckbox(ctx))
			.setView(setupScreen(ctx))
			.setPositiveButton("Done", null)
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
						ArrayList<RepSet> scoreSets = new ArrayList<RepSet>();
						for (int i=0; i<mChecks.length; ++i)
							if (mChecks[i])
								scoreSets.add(mRepsets.get(i));
						
						if (!noRepeatedTraits(scoreSets.toArray(new RepSet[scoreSets.size()]))) {
							Util.msg("You cannot score multiple instances of the same trait simultaneously");
							return;
						}
						
						mCreateHandler.onListSelect(scoreSets);
		                dlg.dismiss();
		            }
		        });
		    }
		});
		return dlg;
	}
	
	private void checkAll(boolean checked) {
	    for (int i = 0; i < mRepSetListView.getCount(); i++) {
	    	mRepSetListView.setItemChecked(i, checked);
	    	mChecks[i] = checked;
	    }
	
	}
	LinearLayout titleWithAllCheckbox(Globals.Activity ctx) {
		ViewLine tbar = new ViewLine(ctx);
		TextView tv = tbar.addTextViewPlain("Select Traits To Score");
		
		// Adding spacer view in desperation as way to right align the checkbox properly: 
		tbar.addTextViewPlain("", 1);
		
		tv.setTextColor(Color.BLUE);
		tv.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
		CheckBox cb = tbar.addCheckBox("All");
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
			cb.setLayoutDirection(LinearLayout.LAYOUT_DIRECTION_RTL);  // put checkbox text on left
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        cb.setLayoutParams(params);
        //cb.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
		cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				checkAll(isChecked);
//			    for (int i = 0; i < mRepSetListView.getCount(); i++) {
//			    	mRepSetListView.setItemChecked(i, isChecked);
//			    	mChecks[i] = isChecked;
//			    }
			}
		});

		return tbar;
	}

	
	/*
	 * noRepeatedTraits()
	 * Returns boolean indicating whether or not the traits in the list
	 * are all different.
	 */
	static public boolean noRepeatedTraits(RepSet [] repSets) {
		long[] tmpset = new long[repSets.length];
		int tmpsetIndex = 0;
		for (int i = 0; i < repSets.length; ++i) {
				long tid = repSets[i].getTrait().getId();
				for (int j = 0; j < tmpsetIndex; ++j)
					if (tid == tmpset[j])
						return false;
				tmpset[tmpsetIndex++] = tid;
		}
		return true;
	}
}

