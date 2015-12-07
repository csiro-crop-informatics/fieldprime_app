/*
 * CreateInstancesDialog.java
 * 
 * Dialog to display multiselect list of trial traits, collecting a rep count for each one.
 * The trial traits are passed in as names (we don't get the traits), and the counts are
 * returned via passed in int array.
 * 
 * Todo
 * . 
 * 
 */

package csiro.fieldprime;


import csiro.fieldprime.R;
import android.app.ActionBar.LayoutParams;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class DlgCreateScoreSets extends DialogFragment implements android.widget.CompoundButton.OnCheckedChangeListener {
	private static DlgCreateScoreSets mInstance;
	private static handler mCreateHandler;
	private static String[] mTraitNames;
	private static int[] mCreateCounts;
	private static EditText[] mEditboxes;
	private static Trial mTrial;

	public interface handler {  // note the single user of this interface ATTOW does not reference the parameter.
		public void onListSelect(int [] repCounts);
	}

	
	public static void newInstance(handler lhs) {
		mTrial = Globals.g.currTrial();
		mTraitNames = mTrial.GetTraitCaptionList();   // Get list of traits for current trial
		if (mTraitNames == null) {
			Util.msg("No existing traits found for dataset");
			return;
		}
		mCreateCounts = new int[mTraitNames.length];
		if (mInstance == null)
			mInstance = new DlgCreateScoreSets();
		mCreateHandler = lhs;
		mEditboxes = new EditText[mTraitNames.length];
		mInstance.show(Globals.FragMan(), "tag");
	}

	// Default Constructor.
	public DlgCreateScoreSets() {
	}


	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("Create New Score Sets");

		// Get the layout inflater
		LayoutInflater inflater = getActivity().getLayoutInflater();

		View view = inflater.inflate(R.layout.create_instances_dialog, null);
		TableLayout tl = (TableLayout) view.findViewById(R.id.cidTableLayout);
		tl.setStretchAllColumns(true);

		// Write header row
		Context ctx = tl.getContext();
		TableRow trh = new TableRow(ctx);
		trh.setLayoutParams(new android.widget.TableRow.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		TextView h1 = new TextView(ctx);
		h1.setText("Trait");
		trh.addView(h1);
		TextView h2 = new TextView(ctx);
		h2.setText("Samples per Plot");
		trh.addView(h2);

		/* Add row to TableLayout. */
		tl.addView(trh, new TableLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

		/*
		 * For each trait add a row with a checkbox, the trait name and a number field When check boxes are clicked we
		 * need to know which trait and edit text it is associated with, so the trait index in mTraitNames + 1 is set as
		 * the ID, and the edittext set as a tag.
		 */
		for (int i = 0; i < mTraitNames.length; ++i) {
			TableRow tr = new TableRow(ctx);

			// Number field edit text:
			EditText et = new EditText(ctx);
			et.setInputType(InputType.TYPE_CLASS_NUMBER);
			et.setEnabled(false);
			et.setFocusable(false);
			et.setSelectAllOnFocus(true);

			// checkbox:
			CheckBox cb = new CheckBox(ctx);
			cb.setText(mTraitNames[i]);
			cb.setOnCheckedChangeListener(this);
			cb.setId(i + 1);

			mEditboxes[i] = et;
			cb.setTag(et);
			tr.addView(cb);
			tr.addView(et);

			/* Add row to TableLayout. */
			tl.addView(tr, new TableLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		}

		// Inflate and set the layout for the dialog
		// Pass null as the parent view because its going in the dialog layout
		builder.setView(view)
		// Add action buttons
				.setPositiveButton("Create Them!", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						for (int i = 0; i < mTraitNames.length; ++i) {
							String val = mEditboxes[i].getText().toString();
							if (!val.isEmpty())
								mCreateCounts[i] = Integer.parseInt(mEditboxes[i].getText().toString());
							else
								mCreateCounts[i] = -1;
						}
						String err = mTrial.addScoreSets(mCreateCounts);
						if (err != null)
							Util.msg(err);
						else
							mCreateHandler.onListSelect(mCreateCounts);
						return;
					}
				}).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						// LoginDialogFragment.this.getDialog().cancel();
					}
				});
		return builder.create();
	}

	@Override
	public void onCheckedChanged(CompoundButton cb, boolean checked) {
		EditText et = (EditText) cb.getTag();
		int traitIndex = cb.getId() - 1;
		et.setFocusableInTouchMode(checked);
		et.setFocusable(checked);
		et.setEnabled(checked);
		if (checked) {
			if (et.getText().toString().equals(""))
				et.setText("1");
			et.requestFocus();
			et.selectAll();
		} else {
			// set the edit text to "0"
			et.setText("");
			mCreateCounts[traitIndex] = 0;
		}
	}
}

