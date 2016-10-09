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

import csiro.fieldprime.Trial.NodeProperty;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;

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
		NodeProperty currProp = pstate.getFilterProperty();
		
		/*
		 * Set up vlist with attribute selection spinner, and then when an
		 * attribute is selected a spinner with that attribute's values
		 */
		mMainView = new VerticalList(getActivity(), false);
		// Add attribute spinner:
		mMainView.addTextNormal("Filter Attribute:");

		Trial.NodeProperty rowProp = trl.getFixedNodeProperty(Trial.FIELD_ROW);
		Trial.NodeProperty colProp = trl.getFixedNodeProperty(Trial.FIELD_COL);
		ArrayList<NodeProperty> propList = trl.getNodeProperties();

		mAttSpin = mMainView.addSpinner(propList, "-- No Filter --", currProp);
		mAttSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				Object obj = parent.getItemAtPosition(position);
				if (obj instanceof NodeProperty) {
					NodeProperty np = (NodeProperty) obj;
					Util.toast(np.name());
					mMainView.removeView(mAttValue);
					ArrayList<?> distinctVals = np.getDistinctValues();
					if (distinctVals == null || distinctVals.size() <= 0) {
						Util.toast("No values for attribute " + np.name());
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
		mMainView.addTextNormal("Filter Value:");

		// check nat null case, change on attribute spinner selection
		// MFK - where do we set the current value?
		if (currProp != null) {
			mAttValue = mMainView.addSpinner(currProp.getDistinctValues(), null, pstate.getFilterPropVal());
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
						
						NodeProperty searchAtt = null;
						int spInd = mAttSpin.getSelectedItemPosition();  // need pos as first item is prompt string.
						if (spInd > 0)
							searchAtt = (NodeProperty) mAttSpin.getSelectedItem();
						
						// Get value if there:
						if (mAttValue != null) {
							Object attValObj = mAttValue.getSelectedItem();
							if (attValObj != null) {
								attSearchTxt = attValObj.toString();
								((ActTrial) getActivity()).setFilter(searchAtt, attSearchTxt);
							}
						}
						

						dlg.dismiss();
					}
				});
			}
		});

		return dlg;
	}
}
