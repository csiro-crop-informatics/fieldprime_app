/*
 * Search.java
 * 
 * Class for search dialog.
 * Search dialog currently has fields for row, col, attribute, and attribute search text.
 * See the search function for search semantics.
 * 
 * MFK - There is work to do here, similar to that done for DlgSetNodeOrder.
 * Make robust through restarts. Callback should be on the owning activity,
 * bundle used if necessary rather than static vars (which presumably will be
 * wiped on a recreate).
 * see http://stackoverflow.com/questions/11411395/how-to-get-current-foreground-activity-context-in-android
 */

package csiro.fieldprime;

import java.util.ArrayList;

import csiro.fieldprime.Trial.NodeAttribute;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public class DlgFilter extends DialogFragment {
	private static DlgFilter instance = null;    // only allow one Search instance at a time
	private static VerticalList mMainView;
	private static Spinner mAttSpin;
	
	private Spinner mAttValue;
		
	// Prevent external constructor call, newInstance should be used instead.
//	private DlgFilter() {}

	public static void newInstance() {
		/*
		 * we probably need to put trial id, nat id, attvalue in bundle (if they're needed)
		 * and use getActivity to manage the callbacks.
		 * Perhaps this will break under rotation - test this..
		 * Need to see if bundle parameters are re provided.
		 */
		if (instance == null) instance = new DlgFilter();
		instance.show(Globals.FragMan(), "dlgfilt");
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Trial trl = ((Globals)getActivity().getApplication()).currTrial();
		Pstate pstate = trl.getPstate();
		NodeAttribute nodeAtt = pstate.getFilterAttribute();
		
		/*
		 * Set up vlist with attribute selection spinner, and then when an
		 * attribute is selected a spinner with that attribute's values
		 */
		mMainView = new VerticalList(getActivity());	
		// Add attribute spinner:
		mMainView.addText("Filter Attribute:");
		mAttSpin = mMainView.addSpinner(trl.getAttributes(), "-- No Filter --", nodeAtt);
		mAttSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				Object obj = parent.getItemAtPosition(position);
				if (obj instanceof NodeAttribute) {
					NodeAttribute n = (NodeAttribute) obj;
					Util.toast(n.name());
					mMainView.removeView(mAttValue);
					ArrayList<?> distinctVals = n.getDistinctValues();
					if (distinctVals == null || distinctVals.size() <= 0) {
						Util.toast("No values for attribute " + n.name());
					} else {
						mAttValue = mMainView.addSpinner(distinctVals, null, null);
					}
				} else {
					// Remove filter value list
				}
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {}
		});

		// Add edit text for value, with prompt:
		mMainView.addText("Filter Value:");

		// check nat null case, change on attribute spinner selection
		// MFK - where do we set the current value?
		if (nodeAtt != null) {
			mAttValue = mMainView.addSpinner(nodeAtt.getDistinctValues(), null, pstate.getFilterAttValue());
		}
		
		final AlertDialog dlg = (new AlertDialog.Builder(getActivity())) // the final lets us refer to dlg in the handlers..
			.setTitle("Filter")
			.setMessage("Filter by choosing an attribute, and a value for that attribute. \n" +
			"Only nodes with the given value for the attribute will be shown:")
			.setView(mMainView)
			.setPositiveButton("Apply", null)  // listener to pos button installed below
			.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {}
			}).create();

		dlg.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface dialog) {
				Button b = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
				b.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						// Attribute text:
						String attSearchTxt = null;
						
						NodeAttribute searchAtt = null;
						int spInd = mAttSpin.getSelectedItemPosition();  // need pos as first item is prompt string.
						if (spInd > 0)
							searchAtt = (NodeAttribute) mAttSpin.getSelectedItem();
						
						// Get value if there:
						if (mAttValue != null) {
							Object attValObj = mAttValue.getSelectedItem();
							if (attValObj != null)
								attSearchTxt = attValObj.toString();
						}
						
						((ActTrial)getActivity()).setFilter(searchAtt, attSearchTxt);
						dlg.dismiss();
					}
				});
			}
		});

		return dlg;
	}
}
