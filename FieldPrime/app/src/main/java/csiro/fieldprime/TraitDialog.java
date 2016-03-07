package csiro.fieldprime;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import csiro.fieldprime.Trait.Datatype;

public class TraitDialog extends DialogFragment implements View.OnClickListener {
	private final static int NEW_TRAIT = 1;
	private final static int DELETE_TRAIT = 2;
	private final static int IMPORT_TRAITS = 3;
	
	private static Trial mDs;
	private static TraitDialog mInstance;
	private static FragmentManager mFm;
	private static Trait[] mTraitList;
	private static View mTopView;
	private static ArrayAdapter<CharSequence> mTraitsAdapter;
	private static ArrayAdapter<CharSequence> mTypesAdapter;
	private static Context mCtx;
	private Dialog mDialog;

	private static Spinner mSpinTraits;
	private static Spinner mSpinTypes;
	private static EditText mCaption;
	private static EditText mDescription;
	private static LinearLayout mButtonLayout;
	private static EditText mMin;
	private static EditText mMax;
	private final static int MODE_DISPLAY = 0;
	private final static int MODE_CREATE = 1;
	private static int mMode = MODE_DISPLAY;
	private static LinearLayout mTypeSpecific;    // Layout within which the stuff may be type specific

	public interface importTraitsHandler {
		void eventHandler(int fred);
	}
	private static importTraitsHandler mHandler;
	
	public static void newInstance(Context ctx, FragmentManager fm, Trial ds, importTraitsHandler grump) {
		if (mInstance == null)
			mInstance = new TraitDialog();
		mFm = fm;
		mDs = ds;
		mCtx = ctx;
		mHandler = grump;
		mInstance.show(mFm, "tag");
	}

	/*
	 * FillFields()
	 * Fill the fields of the dialog according to the current list of traits for this dataset as
	 * retrieved from the database.
	 */
	private void FillFields() {
		// Trait selection spinner:
		mTraitList = mDs.getTraitList();
		mTraitsAdapter = new ArrayAdapter<CharSequence>(mCtx, android.R.layout.simple_spinner_item);
		for (Trait t : mTraitList)
			mTraitsAdapter.add(t.getCaption());
		mTraitsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpinTraits.setAdapter(mTraitsAdapter);
		mSpinTraits.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				Trait trt = mTraitList[position];
				mCaption.setText(trt.getCaption());
				mDescription.setText(trt.mDescription);
				mSpinTypes.setSelection(trt.getType().value());
				mSpinTypes.setEnabled(false);
				SetEditable(false);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parentView) {
			}
		});
		mSpinTraits.setVisibility(View.VISIBLE);

		// Type selection spinner:
		mSpinTypes = (Spinner) mTopView.findViewById(R.id.traitTypeList);
		mTypesAdapter = new ArrayAdapter<CharSequence>(mCtx, android.R.layout.simple_spinner_item);
		for (String s : new String[]{ Trait.DT_INTEGER, Trait.DT_DECIMAL, Trait.DT_STRING })   // was Trait.TRAIT_TYPE_NAMES, but we don't support all yet
			mTypesAdapter.add(s);

		mTypesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpinTypes.setAdapter(mTypesAdapter);
		mSpinTypes.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				Trait trt = null;
				if (mTraitList.length > 0) // can't assume there will be an existing trait.
					trt = mTraitList[mSpinTraits.getSelectedItemPosition()];
				mTypeSpecific.removeAllViews();
				Datatype dt = Datatype.fromInt(position);
				switch (dt) {
				case T_INTEGER:
//					mMin = EditTextWithAPrompt(mTypeSpecific, "Minimum:", trt == null ? "" : Integer.toString((int) ((TraitInteger)trt).mMin));
//					mMax = EditTextWithAPrompt(mTypeSpecific, "Maximum:", trt == null ? "" : Integer.toString((int) ((TraitInteger)trt).mMax));
					break;
				case T_DECIMAL:
//					mMin = EditTextWithAPrompt(mTypeSpecific, "Minimum:", trt == null ? "" : Double.toString(trt.min));
//					mMax = EditTextWithAPrompt(mTypeSpecific, "Maximum:", trt == null ? "" : Double.toString(trt.max));
					break;
				case T_STRING:
//					mMax = EditTextWithAPrompt(mTypeSpecific, "Maximum Length:", trt == null ? "" : Integer.toString((int) trt.max));
					break;
				}
				SetEditable(mMode == MODE_CREATE);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parentView) {
			}
		});

		AddButtons(3);
		SetEditable(mMode == MODE_CREATE);  // don't allow edit by default, this must be enabled when appropriate.
		mSpinTraits.requestFocus();
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("Traits");
		mTopView = getActivity().getLayoutInflater().inflate(R.layout.traits, null);
		mCaption = (EditText) mTopView.findViewById(R.id.traitCaption);
		mDescription = (EditText) mTopView.findViewById(R.id.traitDescription);
		mTypeSpecific = (LinearLayout) mTopView.findViewById(R.id.traitTypeSpecific);
		mSpinTraits = (Spinner) mTopView.findViewById(R.id.traitList);

		FillFields();

		builder.setView(mTopView);
		builder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
			}
		});
		mDialog = builder.create();
		mDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		return mDialog;
	}

	/*
	 * EditTextWithAPrompt() ------------------------------------------------------------------------------------------
	 * Creates a linear layout containing a textview showing prompt and an EditText. The layout is added to vg, and the
	 * EditText returned.
	 */
	EditText EditTextWithAPrompt(ViewGroup vg, String prompt, String val) {
		LinearLayout ll = new LinearLayout(mCtx);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Util.dp2px(60));
		ll.setOrientation(LinearLayout.HORIZONTAL);
		ll.setLayoutParams(params);
		ll.setWeightSum(3);

		TextView tv = new TextView(mCtx);
		tv.setTextSize(20);
		tv.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
		tv.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
		tv.setText(prompt);
		ll.addView(tv);

		EditText et = new EditText(mCtx);
		et.setTextSize(20);
		et.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 2f));
		et.setText(val);
		ll.addView(et);

		vg.addView(ll);
		return et;
	}

//	void sAddMinMax(LinearLayout vg, Trait trt) {
//		mMin = EditTextWithAPrompt(vg, "Minimum:", Double.toString(trt.min));
//		mMax = EditTextWithAPrompt(vg, "Maximum:", Double.toString(trt.max));
//	}

	void SetEditable(boolean which) {
		mCaption.setEnabled(which);
		mDescription.setEnabled(which);
		mSpinTypes.setEnabled(which);
		if (mMin != null)
			mMin.setEnabled(which);
		if (mMax != null)
			mMax.setEnabled(which);
	}

	void AddButtons(int numButtons) {
		mButtonLayout = (LinearLayout) mTopView.findViewById(R.id.traitButtonLayout);
		mButtonLayout.removeAllViews();
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
				1);
		params.setMargins(Util.dp2px(5), Util.dp2px(5), Util.dp2px(5), Util.dp2px(5));

		switch (numButtons) {
		case 3: // add 3 buttons
			mButtonLayout.setWeightSum(3);

			// New Trait Button:
			Button bNew = new Button(mCtx);
			bNew.setId(NEW_TRAIT);
			bNew.setText("New");
			bNew.setBackgroundResource(R.color.white);
			bNew.setOnClickListener(this);
			mButtonLayout.addView(bNew, params);

			
			Button bIMP = new Button(mCtx);
			bIMP.setId(IMPORT_TRAITS);
			bIMP.setText("Import Traits");
			bIMP.setBackgroundResource(R.color.white);
			bIMP.setOnClickListener(this);
			mButtonLayout.addView(bIMP, params);

//			Button b = new Button(mCtx);
//			//b.setId();
//			b.setText("Edit");
//			b.setBackgroundResource(R.color.white);
//			b.setOnClickListener(new View.OnClickListener() {
//				public void onClick(View v) {
//					Util.msg("Not implemented yet");
//					//SetEditable(true);
//				}
//			});
//			mButtonLayout.addView(b, params);

			Button bDel = new Button(mCtx);
			bDel.setId(DELETE_TRAIT);
			bDel.setText("Delete");
			bDel.setBackgroundResource(R.color.white);
			bDel.setOnClickListener(this);
			mButtonLayout.addView(bDel, params);
			break;
		case 1:
			mButtonLayout.setWeightSum(1);
			Button bSave = new Button(mCtx);
			bSave.setText("Save");
			bSave.setBackgroundResource(R.color.white);
			bSave.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					SaveNewTrait();
				}
			});
			mButtonLayout.addView(bSave, params);
			break;
		}
	}


	/*
	 * SaveNewTrait()
	 */
	void SaveNewTrait() {
		double dmin = Trait.DEFAULT_MIN, dmax = Trait.DEFAULT_MAX;

		// Caption. Needs to be non empty and unique within dataset.
		String caption = mCaption.getText().toString();
		if (!caption.matches("\\S+")) {
			Util.msg("Invalid caption field");
			return;
		}
		for (Trait t : mTraitList) {
			if (caption.equalsIgnoreCase(t.getCaption())) {
				Util.msg("There is already a trait in this trial called " + t.getCaption());
				return;
			}
		}

		String description = mDescription.getText().toString();

		// Type specific error checking:
		int type = mSpinTypes.getSelectedItemPosition();
		String smin = null;
		String smax = null;
		if (mMin != null) smin = mMin.getText().toString();
		if (mMax != null) smax = mMax.getText().toString();
		Datatype dt = Datatype.fromInt(type);
		switch (dt) {
		case T_INTEGER:
			if (smin.matches("\\d+") && smax.matches("\\d+")) {
				dmin = Double.parseDouble(smin);
				dmax = Double.parseDouble(smax);
			} else {
				Util.msg("Minimum and Maximum fields for integer trait type must only contain digits");
				return;
			}
			break;
		case T_DECIMAL:
			if (Util.isNumeric(smin) && Util.isNumeric(smax)) {
				dmin = Double.parseDouble(smin);
				dmax = Double.parseDouble(smax);
			} else {
				Util.msg("Invalid Minimum or Maximum field for decimal trait");
				return;
			}
			break;
		case T_STRING:
			if (smax.matches("\\d+")) {
				dmin = 0;
				dmax = Double.parseDouble(smax);
			} else {
				Util.msg("Maximum length field string trait type must only contain digits");
				return;
			}
			break;
		case T_CATEGORICAL:
			Util.msg("Categorical type not (yet) supported for non server trials");
			return;
		}

		Result res = mDs.AddAdHocTrait(caption, description, dt, "", "", dmin, dmax);
		if (res.bad())
			Util.msg(res.errMsg());
		mMode = MODE_DISPLAY;
		FillFields();
		return;
		// go back to mode display?
	}

	void DeleteTrait(final Trait trt) {
		(new DialogFragment() {
			@Override
			public Dialog onCreateDialog(Bundle savedInstanceState) {
				AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
				builder.setMessage("Are you sure you want to delete trait " + trt.getCaption() + "?");
				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						Result ret = mDs.DeleteTrait(trt);
						if (ret.bad())
							Util.msg(ret.errMsg());
						FillFields();
					}
				});
				builder.setNegativeButton("No", null);
				return builder.create();
			}
		}).show(mFm, "tag");
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case NEW_TRAIT:
			// New Trait, create empty trait struct for editting and add save buttons.
			mSpinTraits.setVisibility(View.INVISIBLE);
			mCaption.setText("");
			mDescription.setText("");
			mMode = MODE_CREATE;
			AddButtons(1); // add save button:
			SetEditable(true);
			break;
		case DELETE_TRAIT:
			Trait trt = mTraitList[mSpinTraits.getSelectedItemPosition()];
			DeleteTrait(trt);
			break;
// MFK commented out as I've disable the import traits menu item, which defined R.id.importTraits			
//		case IMPORT_TRAITS:
//			//startActivityForResult(new Intent(this, FileBrowse.class), 2);//IMPORT_TRAIT_RESULT);
//			// MFK make file browse dialog, we can't create activity from here
//
//			
//			mHandler.eventHandler(R.id.importTraits);
//			mDialog.cancel();
//			
//			//FillFields();
//			break;
		}
	}

}
