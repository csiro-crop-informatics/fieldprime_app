/*
 * ListSelect.java
 * Michael Kirk 2013
 * 
 * Single select, or multi select list dialog.
 * These should be created by an object implementing one of the
 * handlers defined here, passing in a context number if necessary
 * for switching in the handlers.
 * 
 * Rather than passing in arrays of charsequences as the list items to display, we pass in objects,
 * and use the toString methods. You can still pass in arrays of strings, but also
 * just the objects if toString is set up, avoiding the need to construct arrays of names.
 */
package csiro.fieldprime;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

public class DlgList extends DialogFragment {
	private CharSequence mItemNames[];
	private Object mItems[];
	private boolean mChecks[];
	private String mTitle;
	private int mCtx;
	private ListSelectHandler mSelectHandler = null;
	private ListMultiSelectHandler mMultiSelectHandler = null;
	private boolean mSingle; // indicates whether we are in single or multi mode.

	// Handlers, passed in by creators.
	public interface ListSelectHandler {
		public void onListSelect(int context, int which);
	}

	public interface ListMultiSelectHandler {
		public void onMultiListDone(int context, boolean [] checks);
	}

	
//	private newInstance(int cont, String titl, Object listVals[]) {
//		ListSelect instance = newListSelect();
//		
//		// Make list of strings to present from the array of objects:
//		mItems = listVals;
//		mItemNames = new CharSequence[listVals.length];
//		for (int i=0; i < listVals.length; ++i)
//			mItemNames[i] = listVals[i].toString();
//		mTitle = titl;
//		mCtx = cont;
//	}
	
	/*
	 * Constructor
	 * Sets up fields not specific to whether this is multi or single select.
	 * Note, we should not, apparently, have argument in the constructor, but should
	 * be using bundle instead.
	 */
	public DlgList(){}
	DlgList(int cont, String titl, Object listVals[]) {
		// Make list of strings to present from the array of objects:
		mItems = listVals;
		mItemNames = new CharSequence[listVals.length];
		for (int i=0; i < listVals.length; ++i)
			mItemNames[i] = listVals[i].toString();
		mTitle = titl;
		mCtx = cont;
	}
	
	public static void newInstanceSingle(int cont, String titl, Object listVals[], ListSelectHandler lhs) {
		DlgList instance = new DlgList(cont, titl, listVals);
		instance.mSingle = true;
		instance.mSelectHandler = lhs;
		instance.show(Globals.FragMan(), "tag");
	}
	
	/*
	 * newInstanceMulti()
	 * NB, checks will be set according to users selections.
	 */
	public static void newInstanceMulti(int cont, String titl, Object listVals[],
			boolean checks[], ListMultiSelectHandler lhs) {
		DlgList instance = new DlgList(cont, titl, listVals);
		instance.mSingle = false;
		instance.mChecks = checks;
		instance.mMultiSelectHandler = lhs;
		instance.show(Globals.FragMan(), "tag");
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		// Use the Builder class for convenient dialog construction
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(mTitle);
		if (mSingle)
			builder.setItems(mItemNames, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					mSelectHandler.onListSelect(mCtx, which);
				}
			});
		else {
			builder.setMultiChoiceItems(mItemNames, mChecks, new DialogInterface.OnMultiChoiceClickListener() {
				public void onClick(DialogInterface dialog, int which, boolean isChecked) {
					//multiSelectHandler.onMultiListSelect(context, which, isChecked);
				}
			});
			builder.setPositiveButton("Done", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					mMultiSelectHandler.onMultiListDone(mCtx, mChecks);		
					return;
				}
			});
		}
		return builder.create();
	}
}
