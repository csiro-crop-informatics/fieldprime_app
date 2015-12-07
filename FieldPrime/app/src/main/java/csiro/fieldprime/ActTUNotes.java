package csiro.fieldprime;

import java.util.ArrayList;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import csiro.fieldprime.R;
import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.Node.Note;


public class ActTUNotes extends VerticalList.VLActivity {
	private EditText mStats;
	private MyBB mBB;
	private Node mNode;
    private ArrayList<Note> mNoteList =  new ArrayList<Trial.Node.Note>();
	
	class MyBB extends ButtonBar {
		static final int DELETE = 1;
		static final int EDIT = 2;
		static final int CREATE = 3;
		static final int SAVE = 4;
		static final int CANCEL = 5;

		final String[] editingButtonCaptions = new String[] { "Cancel", "Save" };
		final int[] editingButtonIds = new int[] { CANCEL, SAVE };
		final String[] browsingButtonCaptions = new String[] { "Add", "Edit", "Delete" };
		final int[] browsingButtonIds = new int[] { CREATE, EDIT, DELETE };

		public MyBB(Context context) {
			super(context, new MyBBHandler());
			BrowseMode();
		}
		public void EditMode() {
			Util.setColoursBlackOnWhite(mStats);
			mMidViewSV.setBackgroundResource(R.color.white);
			mStats.setEnabled(true);
			mStats.requestFocus();
			Util.ShowSoftKeyboard(mStats);
			ResetButtons(editingButtonCaptions, editingButtonIds);
		}
		public void BrowseMode() {
			ResetButtons(browsingButtonCaptions, browsingButtonIds);
		}
	}

	class MyBBHandler implements View.OnClickListener {
		public void onClick(final View v) {
			switch (v.getId()) {
			case MyBB.DELETE: {
				if (mCurrSelection < 0) {
					Util.msg("No Note selected");
					return;
				}
				final Note n = mNoteList.get(mCurrSelection);
				if (!n.isLocal()) {
					Util.msg("This note is not local, cannot delete");
					return;
				}
				Util.Confirm("Delete Note", "Really delete this note?", 0, new Util.ConfirmHandler() {
					@Override
					public void onYes(long context) {
						n.Delete();
						fillScreen();
					}

					@Override
					public void onNo(long context) {
					}
				});
				break;
			}
			case MyBB.EDIT:
				if (mCurrSelection < 0) {
					Util.msg("No Note selected");
					return;
				}
				if (mCurrSelection >= 0)
					mStats.setText(mNoteList.get(mCurrSelection).getText());
				mBB.EditMode();
				break;
			case MyBB.CREATE:
				mCurrSelection = -1;
				mStats.setText(null);
				mBB.EditMode();
				break;
				
			/*
			 * Options when editing: Save and Cancel.
			 */
			case MyBB.SAVE:
				String value = mStats.getText().toString();
				if (value.length() < 1) {
					Util.msg("Not saving empty note");
				} else if (mCurrSelection < 0) {  // adding new note:
					Note newNote = mNode.new Note(value);
					mNoteList.add(0, newNote);
					newNote.Save();
				} else {  // editing existing note:
					Note n = mNoteList.get(mCurrSelection);
					n.setText(value);
					n.Update(false);
				}
				fillScreen();
				break;
			case MyBB.CANCEL:
				fillScreen();
				break;
			}
		}
	}

	@Override
	Object[] objectArray() {
		return mNoteList.toArray();
	}

	@Override
	String heading() {
		return "Node Notes";
	}

	@Override
	void listSelect(int index) {
		mStats.setText(mNoteList.get(mCurrSelection).Details());
	}

	@Override
	void refreshData() {
	    mNode = g.currTrial().getCurrNode();
		mNoteList = mNode.getNoteList();
	}

	@Override
	void hideKeyboard() {
		Util.HideSoftKeyboard(mStats);
	}

	@Override
	View getBottomView() {
		mBB = new MyBB(this);
		return mBB.Layout();
	}

	@Override
	View getMidView() {
		mStats = super.makeEditText();
		mStats.setEnabled(false);
		//HideKeyboard();
		return mStats;
	}
}
