/*
 * Signin.java
 * Michael Kirk 2013
 * 
 * Class for collecting a user name in a modal dialog.
 * Todo
 * . Show list of existing usernames.
 * 
 */

package csiro.fieldprime;

import csiro.fieldprime.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class DlgSignin extends DialogFragment {
	private static DlgSignin mInstance;
	private String mTitle;
	private EditText mSignin;
	private String mLastIdent;
	private int mCaller;       // passed in by caller to identify in selectHandler (not currently used)
	private SigninHandler mHandler;
	
	public interface SigninHandler {
		public void signinUsername(int context, String userName);
	}

	public static void newInstance(int caller, String title, String lastIdent, SigninHandler handler) {
		if (mInstance == null)
			mInstance = new DlgSignin();
		mInstance.mCaller = caller;
		mInstance.mTitle = title;
		mInstance.mLastIdent = lastIdent;
		mInstance.mHandler = handler;
		mInstance.show(Globals.FragMan(), "tag");
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(mTitle);
		LayoutInflater inflater = getActivity().getLayoutInflater();

		// Inflate and set the layout for the dialog
		// Pass null as the parent view because its going in the dialog layout
		View signinView = inflater.inflate(R.layout.signin, null);
		mSignin = (EditText) signinView.findViewById(R.id.username);
		mSignin.setText(mLastIdent);
		builder.setView(signinView);
		builder.setPositiveButton("Sign in", null);  // listener to pos button installed below
		final AlertDialog d = builder.create();
		d.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface dialog) {
				Button b = d.getButton(AlertDialog.BUTTON_POSITIVE);
				b.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						String uname = mSignin.getText().toString().toLowerCase();
						if (uname.matches("[a-z][a-z][a-z][0-9][0-9][a-z0-9]")) {
							if (mHandler == null) {
								Util.msg("null mhandler in signin");
								d.dismiss();
								return;
							}
							mHandler.signinUsername(mCaller, uname);
							d.dismiss();
						} else {
							Util.msg("Please enter a valid CSIRO ident (3 letters, followed by 2 digits, then digit or letter");
						}
					}
				});
			}
		});

		return d;
	}
}
