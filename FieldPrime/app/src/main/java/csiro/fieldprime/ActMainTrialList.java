package csiro.fieldprime;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

public class ActMainTrialList extends VerticalList.VLActivity {
	static final private int BUTTON_IMPORT_TRIAL = 0;
	static final private int BUTTON_EXPORT_TRIAL = 1;
	static final private int BUTTON_OPEN_TRIAL = 2;
	String [] mTrialNames;
	String mCurrName;

	@Override
	Object[] objectArray() {
		return mTrialNames = Trial.GetTrialList(null);
	}

	@Override
	String heading() {
		return "Current Trials";
	}

	@Override
	void listSelect(int index) {
		mCurrName = mTrialNames[index];
	}

	@Override
	void refreshData() {
		// TODO Auto-generated method stub
		
	}

	@Override
	void hideKeyboard() {
		// TODO Auto-generated method stub
		
	}

	@Override
	View getBottomView() {
		ButtonBar bb = new ButtonBar(this, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				switch (v.getId()) {
				case BUTTON_IMPORT_TRIAL:
					Util.msg("hallo");
					break;
				case BUTTON_EXPORT_TRIAL:
					Util.msg("world");
					break;
				case BUTTON_OPEN_TRIAL:
					Trial.startScoringActivity(ActMainTrialList.this, mCurrName);
					//openTrial(mCurrName);
					break;
				default:
					Util.msg("?");
					break;
				}
			}
		});
		bb.addButton("Import Trial", 0);
		bb.addButton("Export", 1);
		bb.addButton("Open", 2);
		return bb.Layout();
	}

	@Override
	View getMidView() {
		TextView mStats = super.makeEditText();
		mStats.setEnabled(false);
		//HideKeyboard();
		return mStats;
	}

	
	private void openTrial(String name) {
		// Only open if not busy, we only require not busy on this trial, but safer
		// to wait til not busy with anything I think.
		if (Globals.isBusy())
			return;

		//OpenTrial(mTrialNames[index]);
		// MFK cloned code from OpenTrial in MainActivity

		Result res = Trial.getSavedTrial(name);
		if (res.bad()) {
			Util.msg(res.errMsg());
			g.setTrial(null);
			return;
		}
		Trial trial = (Trial) res.obj();	
		// Abort if no nodes:  MFK, should be a call to Close?
		if (trial.getNumNodes() <= 0) {
			Util.msg("Trial " + trial.getName() + " has no nodes");
			g.setTrial(null);
			return;
		}
		g.setTrial(trial);   // Scoring activity scores g.cTrial
//		Intent intent = new Intent(this, ActScoring.class);
//		startActivity(intent);
		
		Intent intent = new Intent(this, ActTrial.class);
		intent.putExtra(ActTrial.ACTSCORING_TRIALNAME, name);
		startActivity(intent);
		return;

	}
}
