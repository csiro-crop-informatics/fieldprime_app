/* DlgPrintSetup
 * Michael Kirk 2015
 * 
 * Dialog to allow user to set printing attributes.
 */

package csiro.fieldprime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.Scanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import csiro.fieldprime.Trial.Node;

// MFK HOPEFULLY ROBUST THROUGH RESTARTS
public class DlgPrintSetup extends DialogFragment {	
	private EditText mEditTemplate;	
	private EditText mEditSetTOF;
	private EditText mEditXoffset;
	private EditText mEditLabelHeight;
	private CheckBox mCbCenter;
	
	private PrintSetupState mPstate;

//	enum Ctype {
//		INT, CHECK, TEXT;
//	}
//	class Control {
//		Ctype mType;
//		View mView;
//		int mId;
//		Control(Ctype type, int id) {
//			mType = type;
//			mId = id;
//		}
//		void setView(View root) {
//			mView = root.findViewById(mId);
//		}
//	}
//	private Control [] controls  = {
//			new Control(Ctype.TEXT, R.id.zpTemplate),
//			new Control(Ctype.INT, R.id.zpTopOfForm),
//			new Control(Ctype.INT, R.id.zpLabelHeight),
//			new Control(Ctype.INT, R.id.zpXOffset),
//			new Control(Ctype.CHECK, R.id.zpCenter)
//	};

	// Public constructor with no params is necessary for restarts. We don't need it,
	// as if missing it is created by default, but here just to note we need one.
	public DlgPrintSetup() {}

	public static void newInstance(long trialId, Activity act) {
		DlgPrintSetup instance = new DlgPrintSetup();
		Bundle args = new Bundle();
	    args.putLong("trialId", trialId);
	    instance.setArguments(args);
	    instance.show(act.getFragmentManager(), "dpsu");
	}

	/*
	 * setupScreen()
	 * Populate the dialog. Called from onCreateDialog().
	 */
	private View setupScreen(final Globals.Activity ctx, Bundle savedState) {
//		topVL.addButton("RECREATE", 73, new OnClickListener(){
//			@Override
//			public void onClick(View v) {
//				getActivity().recreate();
//				
//			}});

		// Dynamic construction:
//		VerticalList topVL = new VerticalList(ctx, Globals.SIZE_SMALL);
//		mEditSetTOF = new EditText(ctx);
//		mEditTemplate = new EditText(ctx);
//		mEditSetTOF.setText("" + mPstate.mTopOfForm);		
//		topVL.addView(mEditSetTOF);	
//		mEditTemplate.setText(mPstate.mTemplate);
//		topVL.addView(mEditTemplate);
//		return topVL;

		/*
		 *  XML:
		 */
		LinearLayout topVL = (LinearLayout) getActivity().getLayoutInflater().inflate(R.layout.print_setup, null);
		
		// Get controls:
		mEditTemplate = (EditText) topVL.findViewById(R.id.zpTemplate);
		mEditSetTOF = (EditText) topVL.findViewById(R.id.zpTopOfForm);
		mEditXoffset = (EditText) topVL.findViewById(R.id.zpXoffset);
		mEditLabelHeight = (EditText) topVL.findViewById(R.id.zpLabelHeight);
		mCbCenter = (CheckBox)topVL.findViewById(R.id.zpCenter);

		
//		for (Control c : controls) {
//			c.setView(topVL);
//		}
		
		// Set values:
		if (savedState == null) {
			mEditTemplate.setText(mPstate.mTemplate);
			mEditSetTOF.setText("" + mPstate.mTopOfForm);		
			mEditXoffset.setText("" + mPstate.mOffset);
			mEditLabelHeight.setText("" + mPstate.mLabelHeight);
			mCbCenter.setChecked(mPstate.mCenter);
		} else {
			mEditSetTOF.setText(savedState.getString("tof"));		
			mEditTemplate.setText(savedState.getString("template"));
			mEditXoffset.setText(savedState.getString("xoffset"));
			mEditLabelHeight.setText(savedState.getString("labelHeight"));
			mCbCenter.setChecked(savedState.getBoolean("center"));
		}

		// Restart testing:
//		Button b = new Button(ctx);
//		b.setText("RESTART");
//		b.setId(123);
//		b.setOnClickListener(new View.OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				getActivity().recreate();
//			}
//		});
//		topVL.addView(b);
		
		return topVL;
	}

	@Override
	public void onSaveInstanceState (Bundle outState) {
		// Note no call to super, and (i think) this stops automatic state saving
		// buggering things up because some of the view ids are repeated.
		outState.putString("tof", mEditSetTOF.getText().toString());
		outState.putString("template", mEditTemplate.getText().toString());
		outState.putString("xoffset", mEditXoffset.getText().toString());
		outState.putString("labelHeight", mEditLabelHeight.getText().toString());
		outState.putBoolean("center", mCbCenter.isChecked());
	}

	private Integer intFromEdit(EditText et) {
		String sval = et.getText().toString();
		Integer val = null;
    	try {
    		val = Integer.parseInt(sval);
    	} catch (NumberFormatException nfe) {
    		Util.msg("cannot parse " + sval);
    		et.setText("");
    		throw nfe;
    	}
    	return val;
	}
	
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		long trialId = getArguments().getLong("trialId");
	    mPstate = PrintSetupState.loadFromDb(trialId, Globals.g.db());

		final AlertDialog dlg = new AlertDialog.Builder(getActivity())
				.setTitle("Printer Setup")
				.setView(setupScreen((Globals.Activity)getActivity(), savedInstanceState))  // MFK NB param not used atm
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
		            	// get fields:
		            	mPstate.mTemplate = mEditTemplate.getText().toString();
		            	try {
		            		mPstate.mTopOfForm = intFromEdit(mEditSetTOF); 
		            		mPstate.mLabelHeight = intFromEdit(mEditLabelHeight);
		            		mPstate.mOffset = intFromEdit(mEditXoffset);
		                } catch (NumberFormatException nfe) {
		            		return;
		            	}
		            	mPstate.setCentered(mCbCenter.isChecked());
		            	
						mPstate.save();
		                dlg.dismiss();
		            }
		        });
		    }
		});
		return dlg;
	}

	static class PrintSetupState implements Serializable {
		private static final long serialVersionUID = 1L;
		private long mTrialId;
		
		// Form field values
		String mTemplate = "$FPB(barcode)\nPed: $FPT(pedigree)\nPlain Text";
		int mTopOfForm = 50;
		int mLabelHeight = 210;
		int mOffset = 0;
		private boolean mCenter = true;

		private PrintSetupState(long trialId) {
			mTrialId = trialId;
		}
		
		private SQLiteDatabase db() { return Globals.g.db(); }
		
		void save() {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutput out = null;
			try {
			  out = new ObjectOutputStream(bos);   
			  out.writeObject(this);
			  Tstore.TRIAL_ZPRINT.setBlobValue(db(), mTrialId, bos.toByteArray());
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
			  try {
			    if (out != null)
			      out.close();
			    bos.close();
			  } catch (IOException ex) {} // ignore close exception
			}
		}
		
		/*
		 * loadFromDb()
		 * Returns new PrintSetupState, with values from db, if there is one there
		 * for the specified trial, otherwise just a new one with default values.
		 */
		static public PrintSetupState loadFromDb(long trialId, SQLiteDatabase db) {
			byte[] bytes = Tstore.TRIAL_ZPRINT.getBlobValue(db, trialId);
			if (bytes == null)
				return new PrintSetupState(trialId);
			ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
			ObjectInput in = null;
			try {
			  in = new ObjectInputStream(bis);
			  PrintSetupState newPss = (PrintSetupState)in.readObject(); 
			  return newPss;
			} catch (StreamCorruptedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} finally {
			  try {
			    bis.close();
			  } catch (IOException ex) {} // ignore close exception
			  try {
			    if (in != null) in.close();
			  } catch (IOException ex) {} // ignore close exception
			}
			return new PrintSetupState(trialId);
		}
		
		/*
		 * fillPrinterTemplate()
		 * Convert template to zebra printer commands. 
		 * Occurrences of $FP[B|T](<propertyName>) are replaced with 
		 * with text (T) or barcode (B) versions of the propertyName
		 * for the node. 
		 * NB you cannot have both Text and Barcode in same line.
		 * Line with barcode should have nothing but single $FPB(<propName>)
		 */
		Result fillPrinterTemplate(Node node) {
			String out = "";
			Scanner scanner = null;
			try {
				int startX = 50;
				int currY = Printer.GAP;
				scanner = new Scanner(mTemplate);
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine();
					int closePos;

					// Barcode line:
					if (line.startsWith("$FPB(")) {
						closePos = line.indexOf(')', 5);
						if (closePos < 0)
							return new Result(false, String.format("Invalid line (%s)", line));
						String fieldName = line.substring(5, closePos);
						String fieldVal = node.getPropertyString(fieldName);
						if (fieldVal == null) {
							return new Result(false, "No value for property " + fieldName + " in current node");
						}
						int bcType = 128;
						int narrowBarWidth = 1;
						int barWidthRatioCode = 1;
						int height = 50;
						currY = 10;
						out += String.format("BARCODE %d %d %d %d %d %d %s\r\n", bcType, narrowBarWidth,
								barWidthRatioCode, height, startX, currY, fieldVal);
						currY += height + Printer.BARCODE_SUB_TEXT_HEIGHT + Printer.GAP;
						continue;
					}
			  
					// Text line, replace variables (if any) then make printer command:
					int last = 0;
					int pos = line.indexOf("$FPT(", last);
					String subbed = "";
					while (pos != -1) {
						subbed += line.substring(last, pos);
						// Read the rest of the insertion
						closePos = line.indexOf(')', pos);
						if (closePos < 0) {
							return new Result(false, "Invalid pattern in line: " + line);
						}
						String fieldName = line.substring(pos + 5, closePos);
						String fieldVal = node.getPropertyString(fieldName);
						if (fieldVal == null)
							fieldVal = ""; // For text, let them print empties. MFK what about NA?
						subbed += fieldVal;

						last = closePos + 1;
						pos = line.indexOf("$FPT(", last);
					}
					subbed += line.substring(last);
					int fontNum = 4;
					int fontSize = 0;
					out += String.format("TEXT %d %d %d %d %s\r\n",
							fontNum, fontSize, startX, currY, subbed);
					currY += 47 + Printer.GAP;  // NB according to manual font 4 size 0 is 47 pixels high
				}
			} finally {
				if (scanner != null)
					scanner.close();
			}
			
			return new Result(out);
		}
		
		public boolean centered() { return mCenter; }
		public void setCentered(boolean cent) { mCenter = cent; }
	}
}
