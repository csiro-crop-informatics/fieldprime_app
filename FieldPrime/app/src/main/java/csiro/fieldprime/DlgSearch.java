/*
 * Search.java
 * 
 * Class for search dialog.
 * Search dialog currently has fields for row, col, attribute, and attribute search text.
 * See the search function for search semantics.
 * 
 */

package csiro.fieldprime;

import java.util.ArrayList;

import csiro.fieldprime.R;
import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.NodeAttribute;
import csiro.fieldprime.Trial.TuAtt;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import static csiro.fieldprime.DbNames.*;


public class DlgSearch extends DialogFragment {
	private static DlgSearch instance = null;    // only allow one Search instance at a time
	private static Trial mTrial;
	private static FragmentManager mFm;
	private static View mSearchView;
	private static Spinner mAttSpin;
	private static ArrayList<TuAtt> mTuAtts;
	
	private static SearchHandler mCallback;
	public interface SearchHandler {
		public void foundNode(Node node);
	}
	
	public static void newInstance(FragmentManager fm, Trial ds, SearchHandler callback) {
		if (instance == null) instance = new DlgSearch();
		mFm = fm;
		mTrial = ds;
		mCallback = callback;
		instance.show(mFm, "tag");
	}
	
	// Prevent external constructor call, newInstance should be used instead.
	private DlgSearch() {}


	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("Search");
		builder.setMessage("Seach for nodes by row, column, and attribute value. Any combination of these may be used:");
		LayoutInflater inflater = getActivity().getLayoutInflater();

		// Inflate and set the layout for the dialog
		// Pass null as the parent view because its going in the dialog layout
		mSearchView = inflater.inflate(R.layout.search, null);
		
		builder.setView(mSearchView);
		builder.setPositiveButton("Search", null);  // listener to pos button installed below
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {}
		});

		// Set custom index names:
		((TextView) mSearchView.findViewById(R.id.searchTV1)).setText(mTrial.getIndexName(0));
		((TextView) mSearchView.findViewById(R.id.searchTV2)).setText(mTrial.getIndexName(1));
		
		ArrayList<NodeAttribute> attList = mTrial.getAttributes();
		ArrayList<Object> oblist = new ArrayList<Object>(attList); // make object version so we can insert string prompt
		oblist.add(0, "-- Select Attribute --");
		mAttSpin = (Spinner) mSearchView.findViewById(R.id.attributeSpinner);
		ArrayAdapter<Object> adapter = new ArrayAdapter<Object>(getActivity(), android.R.layout.simple_spinner_item, oblist) {
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

		mAttSpin.setAdapter(adapter);

		final AlertDialog dlg = builder.create();     // the final lets us refer to d in the handlers..
		dlg.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface dialog) {
				Button b = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
				b.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						int row, col;
						//String txt;
						
						// get row:
						String sRow = ((EditText)mSearchView.findViewById(R.id.row)).getText().toString();
						if (sRow.length() == 0) row = -1;
						else if (sRow.matches("\\d+")) row = Integer.parseInt(sRow);
						else {
							Util.msg("Digits only please!");
							return;
						}
						
						// get col:
						String sCol = ((EditText)mSearchView.findViewById(R.id.col)).getText().toString();
						if (sCol.length() == 0) col = -1;
						else if (sCol.matches("\\d+")) col = Integer.parseInt(sCol);
						else {
							Util.msg("Digits only please!");
							return;
						}
						
						// Attribute text:
						String attSearchTxt = null;
						NodeAttribute searchAtt = null;
						int spInd = mAttSpin.getSelectedItemPosition();
						if (spInd > 0)
							searchAtt = (NodeAttribute) mAttSpin.getSelectedItem();
						attSearchTxt = ((EditText)mSearchView.findViewById(R.id.searchText)).getText().toString();			
						if (search(row, col, searchAtt, attSearchTxt.length() > 0 ? attSearchTxt : null))
							dlg.dismiss();
					}
				});
			}
		});

		return dlg;
	}

	private boolean search(int row, int col, NodeAttribute att, String attTxt) {
		mTuAtts = mTrial.getMatchingNodes(row, col, att, attTxt);  // Could take this line to caller, only use of params..
		if (mTuAtts.size() == 0) {
			Util.msg("No nodes found");
			return false;
		}
		if (mTuAtts.size() == 1) {
			TuAtt f = mTuAtts.get(0);
			mCallback.foundNode(mTrial.getNodeById(f.tuid));
			Util.toast("Going to single matching search result");
		} else {		
			String[] foundlings = new String[mTuAtts.size()];
			for (int i = 0; i < mTuAtts.size(); ++i) {
				TuAtt f = mTuAtts.get(i);
				foundlings[i] = String.format("%s %d %s %d", mTrial.getIndexName(0), f.row, mTrial.getIndexName(1), f.col);
				if (f.atName.length() > 0)
					foundlings[i] += " " + f.atName + " : " + f.atVal;
			}
			DlgList.newInstanceSingle(0, String.format("%d Matching nodes", foundlings.length),
					foundlings, new DlgList.ListSelectHandler() {			
				@Override
				public void onListSelect(int context, int which) {
					mCallback.foundNode(mTrial.getNodeById(mTuAtts.get(which).tuid));
				}
			});
		}
		return true;
	}
}
